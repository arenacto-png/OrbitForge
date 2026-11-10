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
        val segments = normalized.split('/')
        val sensitiveDirectories = setOf(".git", ".ssh", ".aws", ".azure", ".gcloud", ".kube")
        val sensitiveNames = setOf(
            "local.properties", "secrets.properties", "credentials.json", "google-credentials.json",
            "service-account.json", "id_rsa", "id_ed25519", "id_ecdsa", "id_dsa",
            ".netrc", ".npmrc", ".pypirc", ".git-credentials"
        )
        // Treat .env-like path segments as sensitive too, not only a final file name:
        // otherwise a directory such as .env/ could expose descendants to agent tools.
        return segments.any { it.startsWith(".env") } ||
            name in sensitiveNames ||
            (name.startsWith("service-account") && name.endsWith(".json")) ||
            (name.startsWith("firebase-adminsdk") && name.endsWith(".json")) ||
            name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pfx") || name.endsWith(".pem") || name.endsWith(".key") ||
            name.endsWith(".tfstate") || name.endsWith(".tfstate.backup") ||
            name.endsWith(".tfvars") || name.endsWith(".tfvars.json") ||
            segments.any { it in sensitiveDirectories } ||
            (name == "config.json" && segments.dropLast(1).lastOrNull() == ".docker")
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
