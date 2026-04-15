@file:Suppress("UNCHECKED_CAST")

package dev.openrune.toml.transcoding

import dev.openrune.toml.model.TomlException
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.serialization.TomlFieldBuiltInSerializer
import dev.openrune.toml.serialization.TomlFieldDecodeContext
import dev.openrune.toml.serialization.TomlFieldSerializer
import dev.openrune.toml.serialization.fieldSerializerInstance
import dev.openrune.toml.serialization.firstTomlEntryForField
import dev.openrune.toml.serialization.tomlFieldAnnotationForParameter
import dev.openrune.toml.util.KotlinName
import dev.openrune.toml.util.TomlName
import dev.openrune.toml.util.subst
import java.util.SortedMap
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

class TomlDecoder internal constructor(
    internal val decoders: Map<KClass<*>, List<TomlDecoder.(KType, TomlValue) -> Any?>>,
    internal val mappings: Map<KClass<*>, Map<KotlinName, TomlName>>,
    internal val defaultValues: Map<KClass<*>, Any>,
    internal val custom : MutableMap<KClass<*>, MutableList<(TomlValue.Map, Any) -> Unit>> = mutableMapOf()
) {

    /**
     * Thrown by a TOML decoder function to indicate that it can't decode the given TOML into its target type and that
     * the next decoder function for the target type should be given a chance.
     */
    internal object Pass : Throwable()

    /**
     * Called by a decoder function to indicate that it can't decode the given TOML into its target type and that
     * the next decoder function for the target type should be given a chance instead.
     */
    fun pass(): Nothing = throw Pass

    internal fun mappingFor(type: KClass<*>): Map<KotlinName, TomlName> =
        mappings[type] ?: emptyMap()

    internal fun <T : Any?> decoderFor(type: KClass<*>): ((KType, TomlValue) -> T)? =
        decoders[type]?.let { decodersForType ->
            return decoder@{ type, value ->
                decodersForType.forEach { decode ->
                    try {
                        return@decoder (this.decode(type, value) as T)
                    } catch (e: Pass) {
                        /* no-op */
                    }
                }
                throw Pass
            }
        }

    internal fun defaultValueFor(type: KClass<*>, parameter: KParameter): Any? =
        defaultValues[type]?.let { defaultValue ->
            val property = type.memberProperties.single { it.name == parameter.name } as KProperty1<Any, Any>
            property.get(defaultValue)
        }
}

/**
 * Decode the given value into the given target type.
 * Behavior is undefined if [T] is not equal to or a superclass of [target].
 */
fun <T : Any?> TomlDecoder.decode(value: TomlValue, target: KType): T {
    val kClass = requireKClass(target.classifier)
    decoderFor<T>(kClass)?.let { decode ->
        try {
            return@decode decode(target, value)
        } catch (e: TomlDecoder.Pass) {
            /* no-op */
        }
    }
    return when (value) {
        is TomlValue.List -> toList(value, target)
        is TomlValue.Map -> toObject(value, target)
        is TomlValue.String -> toEnum(value, target)
        else -> throw noDecoder(value, target)
    }
}

private fun noDecoder(value: TomlValue, target: KType) =
    TomlException.DecodingError.NoSuchDecoder(value, target)

private fun <T : Any> toEnum(value: TomlValue.String, target: KType): T {
    val kClass = requireKClass(target.classifier)
    if (!kClass.isSubclassOf(Enum::class)) {
        throw noDecoder(value, target)
    }
    val enumValues = kClass.java.enumConstants as Array<Enum<*>>
    val enumValue = enumValues.singleOrNull { it.name == value.value }
        ?: throw TomlException.DecodingError.InvalidEnumValue(value, target)
    return enumValue as T
}

private val anyKType: KType = Any::class.createType()
private val stringKType: KType = String::class.createType()

private fun <T : Any> TomlDecoder.toList(value: TomlValue.List, target: KType): T =
    when (requireKClass(target.classifier)) {
        // Set/List also covers the MutableSet/MutableList cases
        List::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Set::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType).toSet() as T
        Collection::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Iterable::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType).asIterable() as T
        Any::class -> decodeList(value.elements, anyKType) as T
        else -> throw TomlException.DecodingError.IllegalListTargetType(value, target)
    }

private fun TomlDecoder.decodeList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { decode(it, elementType) }

private fun <T : Any> TomlDecoder.toObject(value: TomlValue.Map, target: KType): T {
    val kClass = requireKClass(target.classifier)
    return when {
        kClass.isSubclassOf(SortedMap::class) ->
            toMap(value, target).toSortedMap(compareBy { it as Comparable<*> }) as T
        kClass.isSubclassOf(MutableMap::class) ->
            toMap(value, target).toMutableMap() as T
        kClass.isSubclassOf(Map::class) ->
            toMap(value, target) as T
        kClass == Any::class -> toMap(value, Any::class.createType()) as T
        kClass.primaryConstructor != null -> toDataClass(value, target, kClass)
        else -> throw TomlException.DecodingError.IllegalMapTargetType(value, target)
    }
}

private fun TomlDecoder.toMap(value: TomlValue.Map, targetMapType: KType): Map<String, Any> {
    if (targetMapType.arguments.firstOrNull()?.type !in setOf(null, anyKType, stringKType)) {
        throw TomlException.DecodingError.IllegalMapKeyType(value, targetMapType)
    }
    val elementType = targetMapType.arguments.getOrNull(1)?.type ?: anyKType
    return value.properties.mapValues { decode(it.value, elementType) }
}

private fun <T : Any> TomlDecoder.toDataClass(
    tomlMap: TomlValue.Map,
    kType: KType,
    kClass: KClass<*>
): T {
    val constructor = kClass.primaryConstructor!!
    val tomlNamesByParameterName = mappingFor(kClass)
    val parameters = mutableMapOf<KParameter, Any?>()
    val substitutions = kClass.typeParameters.zip(kType.arguments) { parameter, projection ->
        parameter to projection.type!!
    }.toMap()

    for (constructorParameter in constructor.parameters) {
        val kotlinParamName = requireNotNull(constructorParameter.name) {
            "constructor parameter has no name"
        }
        val primaryTomlKey = tomlNamesByParameterName[kotlinParamName] ?: kotlinParamName
        val fieldAnn = tomlFieldAnnotationForParameter(constructorParameter)
        val fieldEntry = firstTomlEntryForField(tomlMap, primaryTomlKey, fieldAnn)
        val encodedParameterValue = fieldEntry?.second
        val matchedTomlKey = fieldEntry?.first
        val serializerClass = fieldAnn?.serializer ?: TomlFieldBuiltInSerializer::class
        val substType = constructorParameter.type.subst(substitutions)
        val earlierValues = parameters.entries.associate { entry ->
            requireNotNull(entry.key.name) { "constructor parameter has no name" } to entry.value
        }
        val decodeContext = TomlFieldDecodeContext(
            containerKClass = kClass,
            containerKType = kType,
            parameterName = kotlinParamName,
            siblingToml = tomlMap,
            matchedTomlKey = matchedTomlKey,
            earlierConstructorValues = earlierValues,
        )
        if (encodedParameterValue == null && constructorParameter.isOptional) {
            if (serializerClass == TomlFieldBuiltInSerializer::class) {
                continue
            }
            val decoded = fieldSerializerInstance<Any>(serializerClass).decodeField(
                this,
                substType,
                null,
                decodeContext,
            )
            val parameterValue = decoded ?: defaultValueFor(kClass, constructorParameter)
            if (!constructorParameter.type.isMarkedNullable && parameterValue == null) {
                throw TomlException.DecodingError.MissingNonNullableValue(constructorParameter, tomlMap, kType)
            }
            parameters[constructorParameter] = parameterValue
            continue
        }
        if (encodedParameterValue == null) {
            val parameterValue = defaultValueFor(kClass, constructorParameter)
            if (!constructorParameter.type.isMarkedNullable && parameterValue == null) {
                throw TomlException.DecodingError.MissingNonNullableValue(constructorParameter, tomlMap, kType)
            }
            parameters[constructorParameter] = parameterValue
            continue
        }
        val decodedParameterValue = if (serializerClass == TomlFieldBuiltInSerializer::class) {
            decode<Any?>(encodedParameterValue, substType)
        } else {
            fieldSerializerInstance<Any>(serializerClass).decodeField(
                this,
                substType,
                encodedParameterValue,
                decodeContext,
            )
        }
        val parameterValue = decodedParameterValue ?: defaultValueFor(kClass, constructorParameter)
        if (!constructorParameter.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.DecodingError.MissingNonNullableValue(constructorParameter, tomlMap, kType)
        }
        parameters[constructorParameter] = parameterValue
    }

    if (kClass.visibility == KVisibility.PRIVATE) {
        constructor.isAccessible = true
    }

    val instance = constructor.callBy(parameters) as T

    // Apply any additional modification logic
    TomlDecoderRegistry.applyModifiers(tomlMap.properties, instance)

    return instance
}

internal fun requireKClass(classifier: KClassifier?): KClass<*> =
    requireNotNull(classifier as? KClass<*>) {
        "classifier '$classifier' is not a KClass; you can only decode to concrete types"
    }
