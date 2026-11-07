package com.orbitforge.mobile.agent

import java.io.File

/**
 * Shared sensitive-path checks for agent reads, writes, and workspace search.
 * The canonical-target check is necessary because a harmless-looking in-root symlink can point
 * at a sensitive file such as .env or local.properties.
 */
internal object WorkspaceSensitivePathPolicy {
    fun isSensitive(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').lowercase()
        val name = normalized.substringAfterLast('/')
        return name.startsWith(".env") || name in setOf(
            "local.properties", "secrets.properties", "credentials.json", "id_rsa", "id_ed25519"
        ) || name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pfx") || name.endsWith(".pem") || name.endsWith(".key") ||
            normalized.split('/').any { it == ".ssh" }
    }

    /** Returns true for sensitive canonical targets and fails closed on canonicalization errors/outside paths. */
    fun isSensitiveTarget(rootDirectory: File, target: File): Boolean = runCatching {
        val canonicalRoot = rootDirectory.canonicalFile
        val canonicalTarget = target.canonicalFile
        if (canonicalTarget != canonicalRoot &&
            !canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)
        ) {
            return@runCatching true
        }
        val relative = canonicalRoot.toPath().relativize(canonicalTarget.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        isSensitive(relative)
    }.getOrDefault(true)
}
