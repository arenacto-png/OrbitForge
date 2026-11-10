package com.orbitforge.mobile.agent

import java.io.File

/**
 * Agent-facing directory listing. Sensitive path names and canonical aliases are omitted so
 * listing metadata cannot bypass the read policy used by the file-content tools.
 */
internal object WorkspaceAgentFileListing {
    const val MAX_ENTRIES = 200

    fun list(
        rootDirectory: File,
        directory: File,
        maxEntries: Int = MAX_ENTRIES
    ): List<WorkspaceEntry> {
        require(maxEntries > 0) { "Лимит файлов должен быть положительным." }

        val root = rootDirectory.canonicalFile
        val target = directory.canonicalFile
        require(isWithin(root, target)) { "Путь выходит за пределы рабочей папки." }
        val targetRelative = relative(root, target)
        require(
            !WorkspaceSensitivePathPolicy.isSensitive(targetRelative) &&
                !WorkspaceSensitivePathPolicy.isSensitiveTarget(root, target)
        ) { "Список секретных путей через инструменты агента запрещён." }
        require(target.isDirectory) { "Путь не является папкой." }

        return target.listFiles().orEmpty()
            .filter { entry ->
                val entryRelative = relative(root, entry)
                !WorkspaceSensitivePathPolicy.isSensitive(entryRelative) &&
                    !WorkspaceSensitivePathPolicy.isSensitiveTarget(root, entry)
            }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .take(maxEntries)
            .map { entry ->
                WorkspaceEntry(
                    path = relative(root, entry),
                    isDirectory = entry.isDirectory,
                    sizeBytes = if (entry.isFile) entry.length() else 0L
                )
            }
    }

    private fun relative(root: File, file: File): String =
        root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')

    private fun isWithin(root: File, target: File): Boolean =
        target == root || target.path.startsWith(root.path + File.separator)
}
