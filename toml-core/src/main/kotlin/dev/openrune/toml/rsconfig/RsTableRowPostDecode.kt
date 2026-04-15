package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.model.TomlValue

/**
 * Row-level hook referenced from [RsTableHeaders.rowPostDecode], analogous to [dev.openrune.toml.serialization.TomlFieldSerializer]
 * for a single field: you get the same [TomlMapper] used for the document, the row’s [content] map (as with
 * `addDecoder<T> { content, def -> … }`), and the decoded [instance] to mutate or inspect.
 *
 * Use an `object` (or a class with a no-arg constructor) and reference it from the annotation, e.g.
 * `rowPostDecode = MyTypeRowHook::class`. For a typed row instance, subclass [TypedRsTableRowPostDecode] and pass
 * `YourType::class` once (Kotlin has no `Hook<YourType>::class`).
 *
 * **“Set” semantics for [RsTableHeaders.atMostOneOf]:**
 * - [Int], [Short], [Byte]: set if value ≠ 0
 * - [Long]: set if value ≠ 0L
 * - [String] / nullable string: set if non-null and not blank
 * - [Boolean]: set if `true`
 * - Other non-null references: set if non-null
 */
fun interface RsTableRowPostDecode {
    fun apply(mapper: TomlMapper, content: Map<String, TomlValue>, instance: Any)
}
