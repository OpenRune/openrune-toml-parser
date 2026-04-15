package dev.openrune.toml.parser

import dev.openrune.konbini.bracket
import dev.openrune.konbini.many
import dev.openrune.konbini.regex
import dev.openrune.konbini.tryParse
import dev.openrune.toml.model.TomlValue

private val openingSquareBracket = regex("\\[($ws($comment)?($newline|$)?)*")
private val closingSquareBracket = regex("($ws($comment)?($newline|$)?)*]")
private val commaSeparator = regex("($ws($comment)?($newline|\$)?)*,($ws(#[^\\n]*)?($newline|$)?)*")

internal val inlineList = bracket(
    openingSquareBracket,
    closingSquareBracket
) {
    val elements = many { value().also { commaSeparator() } }
    val maybeTail = tryParse(value)
    if (maybeTail != null) {
        @Suppress("UNCHECKED_CAST")
        (elements as MutableList<Any>).add(maybeTail)
    }
    TomlValue.List(elements)
}
