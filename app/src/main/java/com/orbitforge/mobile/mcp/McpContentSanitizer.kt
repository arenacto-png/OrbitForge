package com.orbitforge.mobile.mcp

import com.orbitforge.mobile.diagnostics.DiagnosticLog

/**
 * Sanitizes untrusted MCP text before it is added to the provider conversation.
 * Redaction is defense-in-depth; it does not make remote content trusted.
 */
internal object McpContentSanitizer {
    const val MAX_TOOL_OUTPUT_CHARS = 20_000
    const val MAX_ERROR_DETAIL_CHARS = 300

    private val lineBreaks = Regex("[\\r\\n\\t]+")
    private val repeatedWhitespace = Regex("\\s{2,}")

    fun toolOutput(input: String): String =
        DiagnosticLog.redact(input).take(MAX_TOOL_OUTPUT_CHARS)

    fun errorDetail(input: String?, fallback: String): String {
        val safe = DiagnosticLog.redact(input.orEmpty())
            .replace(lineBreaks, " ")
            .replace(repeatedWhitespace, " ")
            .trim()
            .take(MAX_ERROR_DETAIL_CHARS)
        return safe.ifBlank { fallback }
    }
}
