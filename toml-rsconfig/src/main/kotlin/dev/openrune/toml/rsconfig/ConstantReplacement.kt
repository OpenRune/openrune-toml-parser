package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.configuration.getExtensionSetting
import dev.openrune.definition.constants.ConstantProvider

/**
 * Handles quoted constant replacement before TOML parsing.
 *
 * Example:
 * - `"obj.shark"` -> `385`
 */
internal object ConstantReplacement {

    /** RuneScape constant keys are `table.name`; prose with a period must not match this. */
    private val constantTablePrefix = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    /**
     * Resolves known constant tokens from `ConstantProvider` when enabled for the mapper.
     */
    fun apply(mapper: TomlMapper, input: String): String {
        val options = mapper.getExtensionSetting(RS_CONFIG_OPTIONS_KEY) as? RsConfigOptions
        val constantProviderEnabled = options?.constantProviderEnabled == true
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n")
        if ('"' !in normalized) return normalized

        val hasConstantLikeQuotedToken = hasQuotedTokenWithDot(normalized)
        if (!hasConstantLikeQuotedToken) return normalized

        val tableTypes = if (constantProviderEnabled) {
            ConstantProvider.types.map { it.lowercase() }.toSet()
        } else {
            emptySet()
        }

        return buildString(normalized.length) {
            var cursor = 0
            while (cursor < normalized.length) {
                val quoteStart = normalized.indexOf('"', cursor)
                if (quoteStart < 0) {
                    append(normalized, cursor, normalized.length)
                    break
                }

                append(normalized, cursor, quoteStart)
                val quoteEnd = findStringEnd(normalized, quoteStart + 1)
                if (quoteEnd < 0) {
                    append(normalized, quoteStart, normalized.length)
                    break
                }

                val token = normalized.substring(quoteStart + 1, quoteEnd)
                val tablePrefix = token.substringBefore('.', missingDelimiterValue = "").trim()
                val table = tablePrefix.lowercase()
                val looksLikeConstant =
                    '.' in token && constantTablePrefix.matches(tablePrefix)
                if (!looksLikeConstant) {
                    append(normalized, quoteStart, quoteEnd + 1)
                    cursor = quoteEnd + 1
                    continue
                }

                if (!constantProviderEnabled) {
                    error(
                        "rsconfig found constant-like token \"$token\". " +
                            "Enable it via tomlMapper { rsconfig { enableConstantProvider() } }."
                    )
                }

                if (tableTypes.isEmpty()) {
                    error(
                        "rsconfig constant lookup requested for \"$token\", but no constants are loaded. " +
                            "Load constants first (e.g. ConstantProvider.load(...)) before decode."
                    )
                }

                if (table !in tableTypes) {
                    append(normalized, quoteStart, quoteEnd + 1)
                    cursor = quoteEnd + 1
                    continue
                }

                val mapped = ConstantProvider.getMappingOrNull(token)
                    ?: error("rsconfig constant mapping '$token' not found in table '$table'")
                append(mapped)
                cursor = quoteEnd + 1
            }
        }
    }

    private fun hasQuotedTokenWithDot(input: String): Boolean {
        var cursor = 0
        while (cursor < input.length) {
            val quoteStart = input.indexOf('"', cursor)
            if (quoteStart < 0) return false
            val quoteEnd = findStringEnd(input, quoteStart + 1)
            if (quoteEnd < 0) return false
            if (input.substring(quoteStart + 1, quoteEnd).contains('.')) return true
            cursor = quoteEnd + 1
        }
        return false
    }

    private fun findStringEnd(input: String, start: Int): Int {
        var escaped = false
        var index = start
        while (index < input.length) {
            val ch = input[index]
            if (escaped) {
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                return index
            }
            index++
        }
        return -1
    }

}
