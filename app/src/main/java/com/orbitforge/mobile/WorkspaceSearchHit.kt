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
