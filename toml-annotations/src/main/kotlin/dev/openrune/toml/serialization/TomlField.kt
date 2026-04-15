package dev.openrune.toml.serialization

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.annotation.MustBeDocumented
import kotlin.reflect.KClass

/** Sentinel for [TomlField.serializer]: use the library’s built-in field codec at runtime. */
object TomlFieldBuiltInSerializer

/**
 * Per-property TOML binding for data class constructor parameters (and matching properties).
 *
 * For a data class primary constructor parameter, use an explicit use-site target, for example
 * `@param:TomlField(...)` (recommended) or `@get:TomlField(...)`, so the compiler does not warn about ambiguous
 * placement between parameter and property.
 *
 * [aliases] are extra TOML keys accepted when **decoding**; the written key when **encoding** is still the Kotlin
 * property name, or the key configured with the mapper’s field mapping.
 *
 * [serializer] must be an `object` or a class with a public no-arg constructor that implements the runtime
 * `TomlFieldSerializer` type from `toml-parser:core`, or [TomlFieldBuiltInSerializer] for the default codec.
 */
@Target(
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER,
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class TomlField(
    val aliases: Array<String> = [],
    val serializer: KClass<*> = TomlFieldBuiltInSerializer::class,
)
