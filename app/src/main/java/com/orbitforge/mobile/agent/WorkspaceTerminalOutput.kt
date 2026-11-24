package com.orbitforge.mobile.agent

/** Keeps terminal output bounded while telling the caller when content was cut. */
internal object WorkspaceTerminalOutput {
    const val MAX_OUTPUT_CHARS = 20_000
    private const val TRUNCATION_MARKER = "… вывод сокращён …"

    fun format(content: String, maxOutputChars: Int = MAX_OUTPUT_CHARS): String {
        require(maxOutputChars > 0) { "Лимит вывода должен быть положительным." }
        if (content.length <= maxOutputChars) return content

        val suffix = if (maxOutputChars > TRUNCATION_MARKER.length) {
            "\n$TRUNCATION_MARKER"
        } else {
            TRUNCATION_MARKER.take(maxOutputChars)
        }
        val prefixLength = (maxOutputChars - suffix.length).coerceAtLeast(0)
        return content.take(prefixLength) + suffix
    }
}
