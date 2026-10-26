package com.orbitforge.mobile

/**
 * A safe, clickable workspace search result. The path is resolved against the app-private workspace
 * file list rather than guessed from colons that may also appear in filenames or matched text.
 */
internal data class WorkspaceSearchHit(
    val path: String,
    val lineNumber: Int?,
    val preview: String
)

internal object WorkspaceSearchResultParser {
    fun parse(line: String, knownPaths: Collection<String>): WorkspaceSearchHit? {
        val path = knownPaths.asSequence()
            .filter { it.isNotBlank() && line.startsWith("$it:") }
            .maxByOrNull { it.length }
            ?: return null

        val suffix = line.substring(path.length + 1)
        val separator = suffix.indexOf(':')
        if (separator <= 0) return null

        val lineNumber = suffix.substring(0, separator).toIntOrNull() ?: return null
        if (lineNumber <= 0) return null

        return WorkspaceSearchHit(
            path = path,
            lineNumber = lineNumber,
            preview = suffix.substring(separator + 1).trimStart()
        )
    }
}

/** Path-name filtering for user-facing search; content reads use WorkspaceTools policy too. */
internal object WorkspaceSearchPathPolicy {
    fun isSensitive(path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        val name = normalized.substringAfterLast('/')
        return name.startsWith(".env") || name in setOf(
            "local.properties", "secrets.properties", "credentials.json", "id_rsa", "id_ed25519"
        ) || name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pfx") || name.endsWith(".pem") || name.endsWith(".key") ||
            normalized.split('/').any { it == ".ssh" }
    }
}
