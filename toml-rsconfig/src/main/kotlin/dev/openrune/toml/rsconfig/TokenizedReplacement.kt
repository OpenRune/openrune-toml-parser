package dev.openrune.toml.rsconfig

import dev.openrune.toml.TomlMapper
import dev.openrune.toml.configuration.getExtensionSetting

/**
 * Handles `%token%` replacement before TOML parsing.
 *
 * Behavior:
 * - Local tokens come from `[[tokenizedReplacement]]` blocks.
 * - Global tokens come from mapper DSL (`enabledTokenizedReplacement(...)`) and must be referenced as `%global.name%`.
 * - Tokens must be referenced inside quoted strings (`"%token%"`).
 */
internal object TokenizedReplacement {
    private data class ParsedTokenBlocks(
        val hasBlocks: Boolean,
        val tokens: Map<String, String>
    )

    private data class Placeholder(
        val key: String,
        val replacementStart: Int,
        val replacementEndExclusive: Int,
        val quoted: Boolean
    )

    /**
     * Applies all tokenized replacements for the current mapper and input content.
     */
    fun apply(mapper: TomlMapper, input: String): String {
        val options = mapper.getExtensionSetting(RS_CONFIG_OPTIONS_KEY) as? RsConfigOptions
        val enabled = options?.tokenizedReplacementEnabled == true
        if (!enabled) return input

        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n")
        if ('%' !in normalized) return normalized

        val globalTokens = options?.tokenizedReplacements.orEmpty()
        val parsed = parseEntriesWithMetadata(normalized)
        val localTokens = parsed.tokens
        val reservedLocalKeys = localTokens.keys.filter { it.startsWith("global.") }
        if (reservedLocalKeys.isNotEmpty()) {
            error(
                "rsconfig local [[tokenizedReplacement]] cannot define reserved global token key(s): " +
                    reservedLocalKeys.sorted().joinToString(", ") +
                    ". Define them in enabledTokenizedReplacement(...) instead."
            )
        }

        fun resolveToken(token: String): String? {
            val normalizedToken = token.lowercase()
            return if (normalizedToken.startsWith("global.")) {
                globalTokens[normalizedToken]
            } else {
                localTokens[normalizedToken]
            }
        }

        val hasLocalReplacementBlocks = parsed.hasBlocks
        val placeholders = scanPlaceholders(normalized)

        if (globalTokens.isEmpty() && localTokens.isEmpty()) {
            val unresolved = placeholders.asSequence()
                .map { it.key }
                .distinct()
                .toList()
            if (unresolved.isNotEmpty()) {
                error(buildUnresolvedTokenMessage(unresolved, hasLocalReplacementBlocks, localTokens.keys, globalTokens.keys))
            }
            return normalized
        }

        val unquotedPlaceholders = placeholders.asSequence()
            .filterNot { it.quoted }
            .map { it.key }
            .distinct()
            .toList()
        if (unquotedPlaceholders.isNotEmpty()) {
            error(
                "rsconfig token placeholders must be quoted string values. " +
                    "Use \"%token%\" (with quotes). Found unquoted token(s): ${unquotedPlaceholders.joinToString(", ")}"
            )
        }

        val updated = buildString(normalized.length + 16) {
            var cursor = 0
            placeholders.forEach { placeholder ->
                if (placeholder.replacementStart < cursor) return@forEach
                append(normalized, cursor, placeholder.replacementStart)
                val replacement = resolveToken(placeholder.key)
                if (replacement == null) {
                    append(normalized, placeholder.replacementStart, placeholder.replacementEndExclusive)
                } else if (placeholder.quoted) {
                    if (replacement.isTomlLiteral()) {
                        append(replacement)
                    } else {
                        append('"')
                        append(replacement.escapeTomlBasicString())
                        append('"')
                    }
                } else {
                    append(replacement)
                }
                cursor = placeholder.replacementEndExclusive
            }
            append(normalized, cursor, normalized.length)
        }

        val unresolved = scanPlaceholders(updated).asSequence()
            .map { it.key }
            .distinct()
            .toList()
        if (unresolved.isNotEmpty()) {
            error(buildUnresolvedTokenMessage(unresolved, hasLocalReplacementBlocks, localTokens.keys, globalTokens.keys))
        }

        return updated
    }

    /**
     * Parses all `[[tokenizedReplacement]]` blocks and returns token -> value mappings.
     */
    fun parseEntries(input: String): Map<String, String> =
        parseEntriesWithMetadata(input).tokens

    private fun parseEntriesWithMetadata(input: String): ParsedTokenBlocks {
        val map = mutableMapOf<String, String>()
        var inTokenBlock = false
        var hasBlocks = false

        input.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach

            if (line.startsWith("[[") && line.endsWith("]]")) {
                val header = line.removePrefix("[[").removeSuffix("]]").trim()
                inTokenBlock = header.equals("tokenizedReplacement", ignoreCase = true)
                if (inTokenBlock) hasBlocks = true
                return@forEach
            }

            if (!inTokenBlock || line.startsWith("[")) return@forEach
            val assignment = parseAssignment(line) ?: return@forEach
            map[assignment.first.lowercase()] = assignment.second
        }
        return ParsedTokenBlocks(hasBlocks = hasBlocks, tokens = map)
    }

    private fun parseAssignment(line: String): Pair<String, String>? {
        val delimiterIndex = line.indexOf('=')
        if (delimiterIndex <= 0) return null
        val rawKey = line.substring(0, delimiterIndex).trim().trim('"')
        if (rawKey.isEmpty()) return null

        val rawValue = line.substring(delimiterIndex + 1).trim()
        if (rawValue.isEmpty()) return null

        val value = if (rawValue.startsWith('"')) {
            parseQuotedValue(rawValue) ?: return null
        } else {
            rawValue.substringBefore('#').trim()
        }
        return rawKey to value
    }

    private fun parseQuotedValue(rawValue: String): String? {
        val builder = StringBuilder(rawValue.length)
        var escaped = false
        var i = 1
        while (i < rawValue.length) {
            val ch = rawValue[i]
            if (escaped) {
                builder.append(ch)
                escaped = false
                i++
                continue
            }
            if (ch == '\\') {
                escaped = true
                i++
                continue
            }
            if (ch == '"') {
                return builder.toString()
            }
            builder.append(ch)
            i++
        }
        return null
    }

    private fun scanPlaceholders(input: String): List<Placeholder> {
        val placeholders = mutableListOf<Placeholder>()
        var i = 0
        while (i < input.length) {
            if (input[i] != '%') {
                i++
                continue
            }

            val end = input.indexOf('%', startIndex = i + 1)
            if (end <= i + 1) {
                i++
                continue
            }

            val token = input.substring(i + 1, end)
            if (!isValidToken(token)) {
                i++
                continue
            }

            val quoted = i > 0 && end + 1 < input.length && input[i - 1] == '"' && input[end + 1] == '"'
            val replacementStart = if (quoted) i - 1 else i
            val replacementEndExclusive = if (quoted) end + 2 else end + 1
            placeholders += Placeholder(
                key = token.lowercase(),
                replacementStart = replacementStart,
                replacementEndExclusive = replacementEndExclusive,
                quoted = quoted
            )
            i = end + 1
        }
        return placeholders
    }

    private fun isValidToken(token: String): Boolean {
        if (token.isEmpty()) return false
        token.forEach { ch ->
            if (!(ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-')) {
                return false
            }
        }
        return true
    }

    private fun String.isTomlLiteral(): Boolean {
        val candidate = trim()
        if (candidate.equals("true", ignoreCase = true) || candidate.equals("false", ignoreCase = true)) {
            return true
        }
        val compact = candidate.replace("_", "")
        return compact.toLongOrNull() != null || compact.toDoubleOrNull() != null
    }

    private fun String.escapeTomlBasicString(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    fun toGlobalTokenKey(key: String): String =
        if (key.startsWith("global.")) key else "global.$key"

    private fun buildUnresolvedTokenMessage(
        unresolvedTokens: List<String>,
        hasLocalReplacementBlocks: Boolean,
        localTokenKeys: Set<String>,
        globalTokenKeys: Set<String>
    ): String {
        val unresolved = unresolvedTokens.distinct().sorted()
        val unresolvedGlobal = unresolved.filter { it.startsWith("global.") }
        val unresolvedLocal = unresolved.filterNot { it.startsWith("global.") }
        val lines = mutableListOf<String>()

        lines += "rsconfig unresolved tokenized replacement(s): ${unresolved.joinToString(", ")}."

        if (unresolvedGlobal.isNotEmpty()) {
            val knownGlobal = globalTokenKeys.sorted()
            val available = if (knownGlobal.isEmpty()) "<none>" else knownGlobal.joinToString(", ")
            lines +=
                "Add missing global token(s) via rsconfig { enabledTokenizedReplacement(mapOf(\"key\" to \"value\")) } " +
                    "and reference them as \"%global.token%\". currentAvailable=[$available]"
        }

        if (unresolvedLocal.isNotEmpty()) {
            val knownLocal = localTokenKeys.sorted()
            val available = if (knownLocal.isEmpty()) "<none>" else knownLocal.joinToString(", ")
            lines +=
                "Add missing local token(s) to [[tokenizedReplacement]] and reference them as \"%token%\". " +
                    "currentAvailable=[$available]"
            if (!hasLocalReplacementBlocks) {
                lines += "No [[tokenizedReplacement]] block found in this TOML. Example:"
                lines += "[[tokenizedReplacement]] my_token = \"value\""
            }
        }

        return lines.joinToString("\n")
    }
}
