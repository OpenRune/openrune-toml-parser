package dev.openrune.toml.rsconfig

/**
 * Handles RuneScape-specific TOML block and table normalization.
 */
internal object BlockParser {
    private enum class HeaderKind { STANDARD, ARRAY }

    private data class HeaderLine(
        val kind: HeaderKind,
        val header: String,
        val indentation: String,
        val suffix: String
    )

    private data class ParsedInput(
        val lines: List<String>,
        val headers: List<HeaderLine?>
    )

    /**
     * Rewrites repeated standard headers and array children into array-table syntax.
     */
    fun normalizeRuneScapeTableArrays(input: String): String {
        val normalizedInput = normalizeRuneScapeRootHeaders(
            input.removePrefix("\uFEFF").replace("\r\n", "\n")
        )
        val parsed = parseAllLines(normalizedInput)

        val standardHeaderCounts = mutableMapOf<String, Int>()
        val arrayTableHeaders = mutableSetOf<String>()
        parsed.headers.forEach { parsedHeader ->
            when (parsedHeader?.kind) {
                HeaderKind.STANDARD -> standardHeaderCounts[parsedHeader.header] = standardHeaderCounts.getOrDefault(parsedHeader.header, 0) + 1
                HeaderKind.ARRAY -> arrayTableHeaders += parsedHeader.header
                null -> Unit
            }
        }

        return buildString(normalizedInput.length + 16) {
            parsed.lines.indices.forEach { index ->
                val line = parsed.lines[index]
                val parsedHeader = parsed.headers[index]
                if (parsedHeader?.kind != HeaderKind.STANDARD) {
                    append(line)
                    if (index < parsed.lines.lastIndex) append('\n')
                    return@forEach
                }
                val indentation = parsedHeader.indentation
                val header = parsedHeader.header
                val suffix = parsedHeader.suffix
            val parent = header.substringBeforeLast('.', missingDelimiterValue = "")

            val isRepeatedHeader = standardHeaderCounts.getOrDefault(header, 0) > 1
            val isChildOfArrayTable = parent.isNotEmpty() && parent in arrayTableHeaders

            if (isRepeatedHeader || isChildOfArrayTable) {
                arrayTableHeaders += header
                    append("$indentation[[$header]]$suffix")
            } else {
                    append(line)
                }
                if (index < parsed.lines.lastIndex) append('\n')
            }
        }
    }

    /**
     * Normalizes only the first root array block for single-value decode paths.
     */
    fun normalizeRuneScapeTablesByBlock(input: String): String {
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n")
        val firstRoot = findFirstTopLevelArrayRoot(normalized)
            ?: return normalizeRuneScapeTableArrays(normalized)

        val blocks = extractRootArrayBlocks(normalized, listOf(firstRoot))
        if (blocks.isEmpty()) return normalizeRuneScapeTableArrays(normalized)

        val rewritten = buildString {
            append("[$firstRoot]\n")
            append(blocks.first().second.trim())
            append('\n')
        }
        return normalizeRuneScapeTableArrays(rewritten)
    }

    fun extractRootArrayBlocks(input: String, rootKeys: List<String>): List<Pair<String, String>> {
        if (rootKeys.isEmpty()) return emptyList()
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n")
        val keyLookup = rootKeys.associateBy { it.lowercase() }

        val blocks = mutableListOf<Pair<String, String>>()
        var activeKey: String? = null
        var activeBody = StringBuilder()

        fun flushBlock() {
            val key = activeKey ?: return
            val body = activeBody.toString().trim()
            if (body.isNotBlank()) {
                blocks += key to body
            }
        }

        normalized.lineSequence().forEach { line ->
            val parsedHeader = parseHeaderLine(line)
            val incomingKey = if (parsedHeader?.kind == HeaderKind.ARRAY) {
                keyLookup[parsedHeader.header.lowercase()]
            } else {
                null
            }

            if (incomingKey != null) {
                flushBlock()
                activeKey = incomingKey
                activeBody = StringBuilder()
            } else if (activeKey != null) {
                activeBody.append(line).append('\n')
            }
        }
        flushBlock()
        return blocks
    }

    /**
     * Splits the file on every top-level array-table header `[[name]]` where [name] has no `.`.
     * Nested headers like `[[parent.child]]` stay inside the current block body.
     */
    fun extractAllTopLevelArrayBlocks(input: String): List<Pair<String, String>> {
        val normalized = input.removePrefix("\uFEFF").replace("\r\n", "\n")
        val blocks = mutableListOf<Pair<String, String>>()
        var activeKey: String? = null
        var activeBody = StringBuilder()

        fun flushBlock() {
            val key = activeKey ?: return
            val body = activeBody.toString().trim()
            if (body.isNotBlank()) {
                blocks += key to body
            }
        }

        normalized.lineSequence().forEach { line ->
            val parsedHeader = parseHeaderLine(line)
            val isTopLevelArray =
                parsedHeader?.kind == HeaderKind.ARRAY && '.' !in parsedHeader.header
            if (isTopLevelArray) {
                flushBlock()
                activeKey = parsedHeader!!.header.lowercase()
                activeBody = StringBuilder()
            } else if (activeKey != null) {
                activeBody.append(line).append('\n')
            }
        }
        flushBlock()
        return blocks
    }

    fun buildRootWrappedBlock(rootKey: String, blockBody: String): String =
        buildString {
            append("[$rootKey]\n")
            append(blockBody.trim())
            append('\n')
        }

    private fun normalizeRuneScapeRootHeaders(input: String): String {
        val parsed = parseAllLines(input)
        val topLevelArrayHeaderCounts = mutableMapOf<String, Int>()
        parsed.headers.forEach { parsedHeader ->
            if (parsedHeader?.kind == HeaderKind.ARRAY && '.' !in parsedHeader.header) {
                topLevelArrayHeaderCounts[parsedHeader.header] = topLevelArrayHeaderCounts.getOrDefault(parsedHeader.header, 0) + 1
            }
        }

        return buildString(input.length + 8) {
            parsed.lines.indices.forEach { index ->
                val line = parsed.lines[index]
                val parsedHeader = parsed.headers[index]
                if (parsedHeader?.kind != HeaderKind.ARRAY) {
                    append(line)
                    if (index < parsed.lines.lastIndex) append('\n')
                    return@forEach
                }
                val indentation = parsedHeader.indentation
                val header = parsedHeader.header
                val suffix = parsedHeader.suffix

            if ('.' !in header && topLevelArrayHeaderCounts[header] == 1) {
                    append("$indentation[$header]$suffix")
            } else {
                    append(line)
                }
                if (index < parsed.lines.lastIndex) append('\n')
            }
        }
    }

    private fun parseAllLines(input: String): ParsedInput {
        val lines = input.split('\n')
        val headers = ArrayList<HeaderLine?>(lines.size)
        lines.forEach { headers += parseHeaderLine(it) }
        return ParsedInput(lines, headers)
    }

    private fun findFirstTopLevelArrayRoot(input: String): String? {
        input.lineSequence().forEach { line ->
            val parsedHeader = parseHeaderLine(line) ?: return@forEach
            if (parsedHeader.kind == HeaderKind.ARRAY && '.' !in parsedHeader.header) {
                return parsedHeader.header
            }
        }
        return null
    }

    private fun parseHeaderLine(line: String): HeaderLine? {
        var index = 0
        while (index < line.length && line[index].isWhitespace()) index++
        if (index >= line.length || line[index] != '[') return null
        val indentation = line.substring(0, index)

        if (index + 1 < line.length && line[index + 1] == '[') {
            val closing = line.indexOf("]]", startIndex = index + 2)
            if (closing < 0) return null
            val header = line.substring(index + 2, closing).trim()
            if (header.isEmpty() || '[' in header || ']' in header) return null
            val suffix = line.substring(closing + 2)
            if (!isValidSuffix(suffix)) return null
            return HeaderLine(kind = HeaderKind.ARRAY, header = header, indentation = indentation, suffix = suffix)
        }

        val closing = line.indexOf(']', startIndex = index + 1)
        if (closing < 0) return null
        val header = line.substring(index + 1, closing).trim()
        if (header.isEmpty() || '[' in header || ']' in header) return null
        val suffix = line.substring(closing + 1)
        if (!isValidSuffix(suffix)) return null
        return HeaderLine(kind = HeaderKind.STANDARD, header = header, indentation = indentation, suffix = suffix)
    }

    private fun isValidSuffix(suffix: String): Boolean {
        val trimmed = suffix.trimStart()
        return trimmed.isEmpty() || trimmed.startsWith("#")
    }

}
