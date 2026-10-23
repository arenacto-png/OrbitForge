package com.orbitforge.mobile.agent

import java.io.File

/**
 * Pure path boundary policy shared by tools and tests. Canonicalization prevents traversal and
 * existing symlink escapes outside the app-private workspace.
 */
class WorkspacePathPolicy(rootDirectory: File) {
    val root: File = rootDirectory.canonicalFile

    fun resolve(relativePath: String): File {
        require(relativePath.length <= 400) { "Слишком длинный путь." }
        require(!relativePath.startsWith("/") && !relativePath.startsWith("\\"))
        require(!relativePath.contains('\u0000'))
        val candidate = File(root, relativePath).canonicalFile
        require(candidate == root || candidate.path.startsWith(root.path + File.separator)) {
            "Путь выходит за пределы рабочей папки."
        }
        return candidate
    }
}
