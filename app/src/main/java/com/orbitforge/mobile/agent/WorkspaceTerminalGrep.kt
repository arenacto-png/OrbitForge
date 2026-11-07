package com.orbitforge.mobile.agent

/**
 * Read-only terminal grep that always receives content through the agent-safe reader.
 * The caller must pass WorkspaceTools.readFileForAgent, never the unrestricted editor reader.
 */
internal object WorkspaceTerminalGrep {
    const val MAX_MATCHES = 100
    const val MAX_OUTPUT_CHARS = 20_000

    private const val NO_MATCHES = "Совпадений не найдено."
    private const val TRUNCATION_MARKER = "… вывод сокращён …"

    fun search(
        needle: String,
        relativePath: String,
        readAgentSafeFile: (String) -> String
    ): String {
        require(needle.isNotBlank()) { "Поисковый текст не должен быть пустым." }
        val content = readAgentSafeFile(relativePath)
        return format(content, needle)
    }

    fun format(
        content: String,
        needle: String,
        maxMatches: Int = MAX_MATCHES,
        maxOutputChars: Int = MAX_OUTPUT_CHARS
    ): String {
        require(needle.isNotBlank()) { "Поисковый текст не должен быть пустым." }
        require(maxMatches > 0) { "Лимит совпадений должен быть положительным." }
        require(maxOutputChars > 0) { "Лимит вывода должен быть положительным." }

        val matches = ArrayList<String>(minOf(maxMatches, 16))
        var truncatedByMatchCount = false
        for ((index, line) in content.lineSequence().withIndex()) {
            if (!line.contains(needle, ignoreCase = true)) continue
            if (matches.size >= maxMatches) {
                truncatedByMatchCount = true
                break
            }
            matches += "${index + 1}: $line"
        }

        if (matches.isEmpty()) return NO_MATCHES
        val output = matches.joinToString("\n")
        return if (truncatedByMatchCount || output.length > maxOutputChars) {
            withTruncationMarker(output, maxOutputChars)
        } else {
            output
        }
    }

    private fun withTruncationMarker(value: String, maxChars: Int): String {
        val suffix = "\n$TRUNCATION_MARKER"
        val boundedSuffix = if (suffix.length <= maxChars) suffix else TRUNCATION_MARKER.take(maxChars)
        val contentLength = (maxChars - boundedSuffix.length).coerceAtLeast(0)
        return value.take(contentLength) + boundedSuffix
    }
}
