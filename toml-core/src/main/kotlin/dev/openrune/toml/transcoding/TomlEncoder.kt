package dev.openrune.toml.transcoding

import dev.openrune.toml.model.TomlException
import dev.openrune.toml.model.TomlValue
import dev.openrune.toml.serialization.TomlFieldBuiltInSerializer
import dev.openrune.toml.serialization.TomlFieldEncodeContext
import dev.openrune.toml.serialization.TomlFieldSerializer
import dev.openrune.toml.serialization.fieldSerializerInstance
import dev.openrune.toml.serialization.tomlFieldAnnotationForDataClassProperty
import dev.openrune.toml.util.KotlinName
import dev.openrune.toml.util.TomlName
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

class TomlEncoder internal constructor(
    internal val encoders: Map<KClass<*>, List<TomlEncoder.(Any) -> TomlValue>>,
    private val mappings: Map<KClass<*>, Map<KotlinName, TomlName>>,
) {
    /**
     * Thrown by an encoder function to indicate that it can't encode the given value, and that
     * the next encoder function for the source type should be given a chance instead.
     */
    internal object Pass : Throwable()

    /**
     * Called by an encoder function to indicate that it can't encode the given value, and that
     * the next encoder function for the source type should be given a chance instead.
     */
    fun pass(): Nothing = throw Pass

    internal fun mappingFor(type: KClass<*>): Map<KotlinName, TomlName> =
        mappings[type] ?: emptyMap()

    internal fun encoderFor(type: KClass<*>): ((Any) -> TomlValue)? =
        encoders[type]?.let { encodersForType ->
            return encoder@{ value ->
                encodersForType.forEach { encode ->
                    try {
                        return@encoder this.encode(value)
                    } catch (e: Pass) {
                        /* no-op */
                    }
                }
                throw Pass
            }
        }
}

/**
 * Encode the given value using the receiver encoder.
 */
fun TomlEncoder.encode(value: Any): TomlValue {
    encoderFor(value::class)?.let { encode ->
        try {
            return@encode encode(value)
        } catch (e: TomlEncoder.Pass) {
            /* no-op */
        }
    }
    return when {
        value is Map<*, *> -> fromMap(value)
        value is Iterable<*> -> TomlValue.List(value.mapNotNull { it?.let(::encode) })
        value::class.isData -> fromDataClass(value)
        value::class.isSubclassOf(Enum::class) -> TomlValue.String((value as Enum<*>).name)
        value is Lazy<*> -> value.value?.let { encode(it) }
            ?: throw TomlException.EncodingError.LazyValueEvaluatedToNull(value)
        else -> throw TomlException.EncodingError.NoSuchEncoder(value)
    }
}

private fun TomlEncoder.fromMap(value: Map<*, *>): TomlValue {
    val entries = value.mapNotNull { (key, value) ->
        value?.let { key.toString() to encode(it) }
    }
    return TomlValue.Map(entries.toMap())
}

private fun TomlEncoder.fromDataClass(value: Any): TomlValue.Map {
    val tomlNamesByParameterName = mappingFor(value::class)
    val parameterNames = value::class.primaryConstructor!!.parameters.map { it.name }.toSet()
    val propertiesToEncode = value::class.declaredMemberProperties.filter { it.name in parameterNames }
    val fields = propertiesToEncode.mapNotNull { p ->
        val prop = @Suppress("UNCHECKED_CAST") (p as KProperty1<Any, Any?>)
        val tomlName = tomlNamesByParameterName[prop.name] ?: prop.name
        val oldAccessible = prop.isAccessible
        try {
            if (!prop.isAccessible) {
                prop.isAccessible = true
            }
            prop.get(value)?.let { raw ->
                val fieldAnn = tomlFieldAnnotationForDataClassProperty(value::class, prop.name)
                val serializerClass = fieldAnn?.serializer ?: TomlFieldBuiltInSerializer::class
                val encodeContext = TomlFieldEncodeContext(
                    container = value,
                    containerKClass = value::class,
                    propertyName = prop.name,
                )
                val encoded = if (serializerClass == TomlFieldBuiltInSerializer::class) {
                    encode(raw)
                } else {
                    fieldSerializerInstance<Any>(serializerClass).encodeField(this, raw, encodeContext)
                }
                tomlName to encoded
            }
        } catch (e: IllegalAccessException) {
            throw TomlException.AccessError(prop.name, tomlName, e)
        } finally {
            prop.isAccessible = oldAccessible
        }
    }
    return TomlValue.Map(fields.toMap())
}
