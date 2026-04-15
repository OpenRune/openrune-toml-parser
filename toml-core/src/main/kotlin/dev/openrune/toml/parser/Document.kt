package dev.openrune.toml.parser

import dev.openrune.konbini.Parser
import dev.openrune.konbini.ParserResult
import dev.openrune.konbini.boolean
import dev.openrune.konbini.many
import dev.openrune.konbini.oneOf
import dev.openrune.konbini.parseToEnd
import dev.openrune.konbini.parser
import dev.openrune.konbini.regex
import dev.openrune.konbini.whitespace
import dev.openrune.toml.model.TomlDocument
import dev.openrune.toml.model.TomlException
import dev.openrune.toml.model.TomlValue

private val eol = regex("$ws($comment)?($newline|$)")

internal val value: Parser<TomlValue> = parser {
    when (next) {
        '"' -> escapableString()
        '\'' -> unescapableString()
        '[' -> inlineList()
        '{' -> inlineTable()
        't', 'f' -> TomlValue.Bool(boolean())
        else -> oneOf(dateTime, number)
    }
}

private val statement: TomlBuilder.() -> Unit = {
    whitespace()
    when (next) {
        '[' -> parseTable()
        '#' -> { }
        else -> parseKeyValuePair()
    }
    eol()
}

private val document: TomlBuilder.() -> TomlDocument = {
    many { statement() }
    build()
}

internal fun parseTomlDocument(input: String): TomlDocument =
    when (val result = document.parseToEnd(input, ignoreWhitespace = true, state = TomlBuilder.create())) {
        is ParserResult.Ok -> result.result
        is ParserResult.Error -> throw TomlException.ParseError(result.reason, result.line)
    }
