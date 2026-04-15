package dev.openrune.toml.serialization

import dev.openrune.toml.model.TomlException
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.transcoding.TomlDecoder
import dev.openrune.toml.transcoding.TomlEncoder
import dev.openrune.toml.transcoding.decode
import dev.openrune.toml.transcoding.encode
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

/**
 * Decoding context for a data class constructor parameter annotated with [TomlField].
 *
 * [siblingToml] is the **whole** TOML table for the object (the same map you would treat as `content`
 * in a hand-written decoder). Use it to read flags like `clearall` or other keys beside this field.
 *
 * [matchedTomlKey] is the actual TOML key that supplied [value] (primary name or an alias), or null if
 * the field was omitted and the serializer is still invoked (optional parameter with a custom serializer).
 */
data class TomlFieldDecodeContext(
    val containerKClass: KClass<*>,
    val containerKType: KType,
    /** Kotlin constructor parameter / property name for this field. */
    val parameterName: String,
    /** Full TOML table for the enclosing object. */
    val siblingToml: TomlValue.Map,
    /** TOML key used for this field's value, or null if the field key was absent. */
    val matchedTomlKey: String?,
    /** Constructor parameters already decoded earlier in primary-constructor declaration order. */
    val earlierConstructorValues: Map<String, Any?> = emptyMap(),
) {
    val fieldTomlPresent: Boolean get() = matchedTomlKey != null
}

/**
 * Encoding context when writing a data class property that uses a custom [TomlField.serializer].
 */
data class TomlFieldEncodeContext(
    val container: Any,
    val containerKClass: KClass<*>,
    val propertyName: String,
)

/**
 * Gson-style adapter for a single data class field: maps between [TomlValue] and Kotlin type [T].
 *
 * Use [TomlFieldBuiltInSerializer] in [TomlField.serializer] to only customize TOML key names via [TomlField.aliases].
 *
 * [value] is null when the TOML table has no key for this field and the parameter is optional; only
 * non-[Default] serializers receive that call. Use [decodeContext.siblingToml] to inspect the full row.
 */
interface TomlFieldSerializer<T : Any> {
    fun decodeField(
        decoder: TomlDecoder,
        targetType: KType,
        value: TomlValue?,
        decodeContext: TomlFieldDecodeContext,
    ): T

    fun encodeField(
        encoder: TomlEncoder,
        value: T,
        encodeContext: TomlFieldEncodeContext,
    ): TomlValue

    companion object Default : TomlFieldSerializer<Any> {
        override fun decodeField(
            decoder: TomlDecoder,
            targetType: KType,
            value: TomlValue?,
            decodeContext: TomlFieldDecodeContext,
        ): Any {
            val v = requireNotNull(value) {
                "missing TOML value for '${decodeContext.parameterName}' (Default serializer requires the key to be present)"
            }
            return decoder.decode(v, targetType)
        }

        override fun encodeField(
            encoder: TomlEncoder,
            value: Any,
            encodeContext: TomlFieldEncodeContext,
        ): TomlValue =
            encoder.encode(value)
    }
}

internal fun tomlFieldAnnotationForParameter(parameter: KParameter): TomlField? =
    parameter.findAnnotation()

internal fun tomlFieldAnnotationForDataClassProperty(kClass: KClass<*>, propertyName: String): TomlField? {
    val prop = kClass.declaredMemberProperties.find { it.name == propertyName } as? KAnnotatedElement
    val fromProp = prop?.findAnnotation<TomlField>()
    val fromParam = kClass.primaryConstructor?.parameters?.find { it.name == propertyName }?.findAnnotation<TomlField>()
    return fromProp ?: fromParam
}

internal fun firstTomlValueForField(
    tomlMap: TomlValue.Map,
    primaryTomlKey: String,
    field: TomlField?,
): TomlValue? =
    firstTomlEntryForField(tomlMap, primaryTomlKey, field)?.second

/** First matching TOML key and value (primary key, then [TomlField.aliases] in order). */
internal fun firstTomlEntryForField(
    tomlMap: TomlValue.Map,
    primaryTomlKey: String,
    field: TomlField?,
): Pair<String, TomlValue>? {
    val keys = LinkedHashSet<String>()
    keys.add(primaryTomlKey)
    field?.aliases?.forEach { keys.add(it) }
    for (key in keys) {
        tomlMap.properties[key]?.let { return key to it }
    }
    return null
}

private val fieldSerializerCache = ConcurrentHashMap<KClass<out TomlFieldSerializer<*>>, TomlFieldSerializer<*>>()

@Suppress("UNCHECKED_CAST")
internal fun <T : Any> fieldSerializerInstance(serializerClass: KClass<*>): TomlFieldSerializer<T> {
    if (serializerClass == TomlFieldBuiltInSerializer::class) {
        return TomlFieldSerializer.Default as TomlFieldSerializer<T>
    }
    if (!TomlFieldSerializer::class.java.isAssignableFrom(serializerClass.java)) {
        throw TomlException.InvalidTomlFieldSerializer(
            serializerClass,
            IllegalArgumentException(
                "${serializerClass.qualifiedName ?: serializerClass.simpleName} must implement TomlFieldSerializer",
            ),
        )
    }
    val kClass = serializerClass as KClass<out TomlFieldSerializer<*>>
    if (kClass == TomlFieldSerializer.Default::class) {
        return TomlFieldSerializer.Default as TomlFieldSerializer<T>
    }
    return fieldSerializerCache.getOrPut(kClass) {
        try {
            kClass.objectInstance
                ?: kClass.java.getDeclaredConstructor().newInstance()
        } catch (e: Throwable) {
            throw TomlException.InvalidTomlFieldSerializer(kClass, e)
        }
    } as TomlFieldSerializer<T>
}
