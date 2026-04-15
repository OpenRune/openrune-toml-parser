package dev.openrune.toml.parser

import dev.openrune.konbini.ParserState
import dev.openrune.konbini.bracket
import dev.openrune.konbini.chain
import dev.openrune.konbini.parser
import dev.openrune.konbini.regex
import dev.openrune.toml.model.TomlValue

internal val inlineTable = parser {
    val items = bracket(openCurly, closeCurly) {
        chain(parser { keyValuePair() }, singleLineCommaSeparator)
    }
    val map = TomlValue.Map(mutableMapOf())
    items.terms.forEach { (key, value) ->
        insertNested(map, key, 0, value)
    }
    map
}

private val openCurly = regex("\\{$ws")
private val closeCurly = regex("$ws}")
private val singleLineCommaSeparator = regex("$ws,$ws")

private tailrec fun ParserState.insertNested(map: TomlValue.Map, key: List<String>, keyIndex: Int, value: TomlValue) {
    val dict = map.properties as MutableMap<String, TomlValue>
    val alreadyDefined = { fail("Key '${key.take(keyIndex + 1).joinToString()}' already defined.") }
    if (keyIndex >= key.lastIndex) {
        dict.putIfAbsent(key[keyIndex], value)?.also { alreadyDefined() }
    } else {
        val newDict = TomlValue.Map(mutableMapOf())
        when (dict.putIfAbsent(key[keyIndex], newDict)) {
            null -> insertNested(newDict, key, keyIndex + 1, value)
            else -> alreadyDefined()
        }
    }
}
