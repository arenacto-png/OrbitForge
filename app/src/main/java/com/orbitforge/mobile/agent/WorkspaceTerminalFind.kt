package com.orbitforge.mobile.agent

import java.io.File

/**
 * Bounded, agent-safe path listing for the limited workspace terminal.
 * Sensitive directories are pruned before traversal and each yielded entry is checked again.
 */
internal object WorkspaceTerminalFind {
    const val MAX_RESULTS = 200
    const val MAX_SCANNED_ENTRIES = 1_000
    const val MAX_OUTPUT_CHARS = 20_000

    private const val TRUNCATION_MARKER = "… вывод сокращён …"

    fun list(
        rootDirectory: File,
        startDirectory: File,
        maxResults: Int = MAX_RESULTS,
        maxScannedEntries: Int = MAX_SCANNED_ENTRIES,
        maxOutputChars: Int = MAX_OUTPUT_CHARS
    ): String {
        require(maxResults > 0) { "Лимит результатов должен быть положительным." }
        require(maxScannedEntries > 0) { "Лимит обхода должен быть положительным." }
        require(maxOutputChars > 0) { "Лимит вывода должен быть положительным." }

        val root = rootDirectory.canonicalFile
        val start = startDirectory.canonicalFile
        require(isWithin(root, start)) { "Путь выходит за пределы рабочей папки." }
        require(!isExcluded(root, start)) { "Обход секретных путей через инструменты агента запрещён." }
        require(start.isDirectory) { "Путь не является папкой." }

        val paths = ArrayList<String>(minOf(maxResults, 16))
        var outputLength = 0
        var scanned = 0
        var truncated = false

        val walk = start.walkTopDown().onEnter { directory -> !isExcluded(root, directory) }
        for (file in walk) {
            if (scanned >= maxScannedEntries) {
                truncated = true
                break
            }
            scanned++

            if (isExcluded(root, file)) continue
            if (paths.size >= maxResults) {
                truncated = true
                break
            }

            val path = relative(root, file).ifBlank { "." }
            val addedLength = path.length + if (paths.isEmpty()) 0 else 1
            if (outputLength + addedLength > maxOutputChars) {
                truncated = true
                break
            }

            paths += path
            outputLength += addedLength
        }

        val output = paths.joinToString("\n")
        return if (truncated) withTruncationMarker(output, maxOutputChars) else output
    }

    private fun isExcluded(root: File, file: File): Boolean {
        val relative = relative(root, file)
        return WorkspaceSensitivePathPolicy.isSensitive(relative) ||
            WorkspaceSensitivePathPolicy.isSensitiveTarget(root, file)
    }

    private fun relative(root: File, file: File): String =
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    private fun isWithin(root: File, target: File): Boolean =
        target == root || target.path.startsWith(root.path + File.separator)

    private fun withTruncationMarker(value: String, maxChars: Int): String {
        if (maxChars <= TRUNCATION_MARKER.length) return TRUNCATION_MARKER.take(maxChars)
        val suffix = "\n$TRUNCATION_MARKER"
        return value.take((maxChars - suffix.length).coerceAtLeast(0)) + suffix
    }
}
