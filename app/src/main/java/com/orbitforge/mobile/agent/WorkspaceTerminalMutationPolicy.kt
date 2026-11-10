package com.orbitforge.mobile.agent

import java.io.File

/**
 * Applies the shared sensitive-path policy to agent-requested terminal mutations.
 * User approval does not authorize creating entries in credential locations or through aliases
 * that resolve to sensitive/out-of-workspace targets.
 */
internal object WorkspaceTerminalMutationPolicy {
    fun createDirectory(rootDirectory: File, relativePath: String, target: File): File {
        val safeTarget = validateTarget(rootDirectory, relativePath, target)
        require(safeTarget.mkdirs() || safeTarget.isDirectory) { "Не удалось создать папку." }
        return safeTarget
    }

    fun createEmptyFile(rootDirectory: File, relativePath: String, target: File): File {
        val safeTarget = validateTarget(rootDirectory, relativePath, target)
        val parent = safeTarget.parentFile
        require(parent != null && (parent.mkdirs() || parent.isDirectory)) {
            "Не удалось создать родительскую папку."
        }
        if (!safeTarget.exists()) {
            require(safeTarget.createNewFile()) { "Не удалось создать файл." }
        }
        require(safeTarget.isFile) { "Путь существует, но это не файл." }
        return safeTarget
    }

    private fun validateTarget(rootDirectory: File, relativePath: String, target: File): File {
        require(relativePath.isNotBlank()) { "Путь не может быть пустым." }
        val root = rootDirectory.canonicalFile
        val canonicalTarget = target.canonicalFile
        require(isWithin(root, canonicalTarget)) { "Путь выходит за пределы рабочей папки." }
        require(
            !WorkspaceSensitivePathPolicy.isSensitive(relativePath) &&
                !WorkspaceSensitivePathPolicy.isSensitiveTarget(root, canonicalTarget)
        ) {
            "Создание файлов и папок в секретных путях через терминал запрещено."
        }
        return canonicalTarget
    }

    private fun isWithin(root: File, target: File): Boolean =
        target == root || target.path.startsWith(root.path + File.separator)
}
