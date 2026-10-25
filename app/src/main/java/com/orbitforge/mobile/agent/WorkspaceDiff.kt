package com.orbitforge.mobile.agent

object WorkspaceDiff {
    /** Combines per-file previews into one bounded approval screen. */
    fun combineBatchPreview(previews: List<Pair<String, String>>, maxChars: Int = 16_000): String {
        require(previews.isNotEmpty() && previews.size <= 8) { "Пакет должен содержать от 1 до 8 файлов." }
        val combined = previews.joinToString("\n\n") { (path, preview) -> "### $path\n$preview" }
        require(combined.length <= maxChars) { "Общий предпросмотр слишком велик. Разбей изменения на несколько пакетов." }
        return combined
    }

    /**
     * Small bounded preview, not a complete unified diff. Shows common prefix/suffix as context
     * and marks changed middle lines with '-' and '+'.
     */
    fun preview(oldContent: String, newContent: String, maxLines: Int = 80, isNewFile: Boolean = oldContent.isBlank()): String {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        if (isNewFile) {
            val shown = newLines.take((maxLines - 1).coerceAtLeast(0)).joinToString("\n") { "+ $it" }
            return ("[новый файл]\n" + shown + if (newLines.size > maxLines - 1) "\n… предпросмотр сокращён …" else "").take(6000)
        }
        if (oldContent == newContent) return "[нет изменений]"
        var prefix = 0
        while (prefix < oldLines.size && prefix < newLines.size && oldLines[prefix] == newLines[prefix]) prefix++

        var oldSuffix = oldLines.size - 1
        var newSuffix = newLines.size - 1
        while (oldSuffix >= prefix && newSuffix >= prefix && oldLines[oldSuffix] == newLines[newSuffix]) {
            oldSuffix--
            newSuffix--
        }

        val lines = mutableListOf<String>()
        lines += "[изменение файла]"
        oldLines.take(prefix).takeLast(3).forEach { lines += "  $it" }
        oldLines.subList(prefix, oldSuffix + 1).forEach { lines += "- $it" }
        newLines.subList(prefix, newSuffix + 1).forEach { lines += "+ $it" }
        newLines.drop(newSuffix + 1).take(3).forEach { lines += "  $it" }

        val bounded = lines.take(maxLines)
        return bounded.joinToString("\n").take(6000).let {
            if (lines.size > bounded.size || lines.joinToString("\n").length > 6000) it + "\n… предпросмотр сокращён …" else it
        }
    }
}
