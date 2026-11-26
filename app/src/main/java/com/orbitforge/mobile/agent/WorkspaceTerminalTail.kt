package com.orbitforge.mobile.agent

/** Strict argument parsing and bounded output for the workspace terminal's read-only tail command. */
internal object WorkspaceTerminalTail {
    const val DEFAULT_LINE_COUNT = 20
    const val MAX_LINE_COUNT = 200
    const val MAX_OUTPUT_CHARS = 20_000

    private const val TRUNCATION_MARKER = "… вывод сокращён …"

    data class Request(val path: String, val lineCount: Int)

    fun parseArguments(params: List<String>): Request {
        require(params.size in 1..2) { "Использование: tail <файл> [строки]" }
        val path = params[0]
        require(path.isNotBlank()) { "Путь к файлу не может быть пустым." }
        val lineCount = params.getOrNull(1)?.let { raw ->
            val parsed = raw.toIntOrNull()
                ?: throw IllegalArgumentException("Количество строк должно быть целым числом.")
            require(parsed in 1..MAX_LINE_COUNT) {
                "Количество строк должно быть от 1 до $MAX_LINE_COUNT."
            }
            parsed
        } ?: DEFAULT_LINE_COUNT
        return Request(path, lineCount)
    }

    fun format(content: String, lineCount: Int, maxOutputChars: Int = MAX_OUTPUT_CHARS): String {
        require(lineCount in 1..MAX_LINE_COUNT) {
            "Количество строк должно быть от 1 до $MAX_LINE_COUNT."
        }
        require(maxOutputChars > 0) { "Лимит вывода должен быть положительным." }
        val output = splitLines(content).takeLast(lineCount).joinToString("\n")
        if (output.length <= maxOutputChars) return output

        val prefix = if (maxOutputChars > TRUNCATION_MARKER.length) {
            "$TRUNCATION_MARKER\n"
        } else {
            "…"
        }
        val tailChars = (maxOutputChars - prefix.length).coerceAtLeast(0)
        return prefix.take(maxOutputChars) + output.takeLast(tailChars)
    }

    private fun splitLines(content: String): List<String> {
        if (content.isEmpty()) return emptyList()
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val body = if (normalized.endsWith('\n')) normalized.dropLast(1) else normalized
        val lines = mutableListOf<String>()
        var start = 0
        for (index in body.indices) {
            if (body[index] == '\n') {
                lines += body.substring(start, index)
                start = index + 1
            }
        }
        lines += body.substring(start)
        return lines
    }
}
