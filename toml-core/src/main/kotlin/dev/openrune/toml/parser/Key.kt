package dev.openrune.toml.parser

import dev.openrune.konbini.chain1
import dev.openrune.konbini.map
import dev.openrune.konbini.oneOf
import dev.openrune.konbini.regex

private val bareKey = regex("[A-Za-z0-9_-]+")
private val quotedKey = oneOf(basicString, literalString)
internal val key = chain1(oneOf(bareKey, quotedKey), regex("$ws\\.$ws")).map { it.terms }
