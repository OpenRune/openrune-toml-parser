package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.configuration.getExtensionSetting
import dev.openrune.definition.constants.ConstantProvider

/**
 * Handles quoted constant replacement before TOML parsing.
 *
 * Example:
 * - `"obj.shark"` -> `385`
 *
 * Inside each top-level **array-table** segment (from one `[[header]]` line until the next
 * `[[...]]` line), if a line assigns `id = "table.name"` with a resolvable constant token and the
 * block does not already define `debugName`, a line `debugName = "<rscmName>"` is inserted
 * immediately after that `id` line. `rscmName` is the token text after the first `.` (e.g.
 * `varbit.merlin_teleports_selected_category` -> `merlin_teleports_selected_category`). Constant
 * replacement then runs on the whole document as usual (including the `id` value).
 */
internal object ConstantReplacement {

    /** RuneScape constant keys are `table.name`; prose with a period must not match this. */
    private val constantTablePrefix = Regex("^[a-zA-Z][a-zA-Z0-9_]*$")

    private val idEqualsQuoted = Regex("^\\s*id\\s*=\\s*\"", RegexOption.IGNORE_CASE)

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

        if (!constantProviderEnabled) {
            return replaceConstantsLegacy(normalized, tableTypes, constantProviderEnabled = false)
        }

        if (tableTypes.isEmpty()) {
            return replaceConstantsLegacy(normalized, tableTypes, constantProviderEnabled = true)
        }

        val withDebugName = injectDebugNameAfterIdInArrayTableBlocks(normalized, tableTypes)
        return replaceConstantsLegacy(withDebugName, tableTypes, constantProviderEnabled = true)
    }

    /**
     * For each segment between consecutive `[[...]]` lines, insert `debugName` after the first
     * `id = "constant"` line when appropriate.
     */
    private fun injectDebugNameAfterIdInArrayTableBlocks(normalized: String, tableTypes: Set<String>): String {
        val lines = normalized.split('\n')
        val arrayHeaderIndices = lines.indices.filter { isArrayTableHeaderLine(lines[it]) }
        if (arrayHeaderIndices.isEmpty()) return normalized

        val out = ArrayList<String>(lines.size + 2)
        if (arrayHeaderIndices.first() > 0) {
            out.addAll(lines.subList(0, arrayHeaderIndices.first()))
        }
        for (i in arrayHeaderIndices.indices) {
            val from = arrayHeaderIndices[i]
            val toExclusive = if (i + 1 < arrayHeaderIndices.size) arrayHeaderIndices[i + 1] else lines.size
            val block = lines.subList(from, toExclusive)
            out.addAll(transformArrayTableBlockForDebugName(block, tableTypes))
        }
        return out.joinToString("\n")
    }

    private fun transformArrayTableBlockForDebugName(block: List<String>, tableTypes: Set<String>): List<String> {
        if (block.isEmpty()) return block
        val hasDebugName = block.any { line ->
            val t = line.trimStart()
            !t.startsWith("#") && isDebugNameAssignmentLine(line)
        }
        if (hasDebugName) return block

        val idLineIndex = block.indexOfFirst { line ->
            val token = quotedRhsTokenAfterIdEquals(line) ?: return@indexOfFirst false
            isResolvableConstantToken(token, tableTypes)
        }
        if (idLineIndex < 0) return block

        val idLine = block[idLineIndex]
        val token = quotedRhsTokenAfterIdEquals(idLine)!!
        val suffix = token.substringAfter('.')
        val indent = idLine.takeWhile { it.isWhitespace() }
        val debugLine = buildString {
            append(indent)
            append("debugName = \"")
            append(escapeTomlBasicString(suffix))
            append('"')
        }
        return buildList(block.size + 1) {
            addAll(block.subList(0, idLineIndex + 1))
            add(debugLine)
            addAll(block.subList(idLineIndex + 1, block.size))
        }
    }

    private fun isResolvableConstantToken(token: String, tableTypes: Set<String>): Boolean {
        val tablePrefix = token.substringBefore('.', missingDelimiterValue = "").trim()
        val table = tablePrefix.lowercase()
        val looksLikeConstant =
            '.' in token && constantTablePrefix.matches(tablePrefix)
        if (!looksLikeConstant || table !in tableTypes) return false
        return ConstantProvider.getMappingOrNull(token) != null
    }

    private fun quotedRhsTokenAfterIdEquals(line: String): String? {
        val match = idEqualsQuoted.find(line) ?: return null
        val openQuoteIndex = match.range.last
        val closeQuote = findStringEnd(line, openQuoteIndex + 1)
        if (closeQuote < 0) return null
        return line.substring(openQuoteIndex + 1, closeQuote)
    }

    private fun isArrayTableHeaderLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("[[") && t.contains("]]")
    }

    private fun isDebugNameAssignmentLine(line: String): Boolean {
        val trimmed = line.trimStart()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return false
        val eq = trimmed.indexOf('=')
        if (eq <= 0) return false
        val rawKey = trimmed.substring(0, eq).trimEnd()
        val key = when {
            rawKey.length >= 2 && rawKey.startsWith('"') && rawKey.endsWith('"') ->
                rawKey.substring(1, rawKey.lastIndex)
            else -> rawKey
        }
        return key.equals("debugName", ignoreCase = true)
    }

    /**
     * Original whole-document pass (no debugName injection). Used when the provider is disabled
     * (error path), when no constant types are loaded (error path), or after debugName injection.
     */
    private fun replaceConstantsLegacy(
        normalized: String,
        tableTypes: Set<String>,
        constantProviderEnabled: Boolean,
    ): String =
        buildString(normalized.length) {
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
                            "Enable it via tomlMapper { rsconfig { enableConstantProvider() } }.",
                    )
                }

                if (tableTypes.isEmpty()) {
                    error(
                        "rsconfig constant lookup requested for \"$token\", but no constants are loaded. " +
                            "Load constants first (e.g. ConstantProvider.load(...)) before decode.",
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

    private fun escapeTomlBasicString(value: String): String =
        buildString(value.length + 4) {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\u0000' -> append("\\u0000")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
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
