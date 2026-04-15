package dev.openrune.toml.rsconfig

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget
import kotlin.reflect.KClass

/** Default for [RsTableHeaders.rowPostDecode]: no extra row hook. */
object RsTableNoRowHook

/**
 * Marks RS-style config root table names and optional decode-time behavior.
 *
 * @param value Root TOML table keys (e.g. `"params"` for `[[params]]`), same as today.
 * @param atMostOneOf Kotlin property names of which **at most one** may be “set” after decode (see
 * `RsTableRowPostDecode` in `toml-parser:core`). Use for mutually exclusive defaults like int vs string vs long.
 * @param rowPostDecode Class or object implementing `RsTableRowPostDecode` (`toml-parser:core`): runs after TOML→data
 * class with the same `TomlMapper`, the row’s `Map<String, TomlValue>`, and the decoded instance
 * (field-level: `TomlFieldSerializer`; row-level: same info as `addDecoder<T> { content, def -> … }`, plus the mapper).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RsTableHeaders(
    vararg val value: String,
    val atMostOneOf: Array<String> = [],
    val rowPostDecode: KClass<*> = RsTableNoRowHook::class,
)
