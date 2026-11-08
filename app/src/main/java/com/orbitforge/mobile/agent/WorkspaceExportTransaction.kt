package com.orbitforge.mobile.agent

import java.io.IOException
import java.io.OutputStream
import java.util.UUID

internal interface WorkspaceExportDirectory {
    fun findFile(name: String): WorkspaceExportDocument?
    fun createFile(mimeType: String, name: String): WorkspaceExportDocument?
}

internal interface WorkspaceExportDocument {
    val isFile: Boolean
    val isDirectory: Boolean
    fun delete(): Boolean
    fun renameTo(displayName: String): Boolean
    fun openOutputStream(): OutputStream?
}

internal data class WorkspaceExportOutcome(
    val copied: Boolean,
    val bytes: Long = 0L,
    val replacedExisting: Boolean = false,
    val warning: String? = null
)

/**
 * Stages a complete SAF export before replacing a destination document.
 *
 * Existing content is moved to a unique backup name only after the staged copy has closed
 * successfully. If promotion fails, restoration is attempted; if restoration cannot be
 * confirmed, the backup is deliberately retained instead of deleting the user's old data.
 */
internal object WorkspaceExportTransaction {
    fun write(
        parent: WorkspaceExportDirectory,
        fileName: String,
        mimeType: String,
        writeContent: (OutputStream) -> Long
    ): WorkspaceExportOutcome {
        val existing = parent.findFile(fileName)
        if (existing != null && !existing.isFile) {
            return WorkspaceExportOutcome(
                copied = false,
                warning = "The destination name is not a regular file; the export was skipped."
            )
        }

        val temporaryName = uniqueName(parent, ".orbitforge-export-")
            ?: return WorkspaceExportOutcome(false, warning = "Could not reserve a temporary export name.")
        val temporary = parent.createFile(mimeType, temporaryName)
            ?: return WorkspaceExportOutcome(false, warning = "Could not create a temporary export document.")

        val bytes = try {
            val output = temporary.openOutputStream() ?: throw IOException("Could not open temporary export.")
            output.use { writeContent(it) }
        } catch (_: Exception) {
            val removed = runCatching { temporary.delete() }.getOrDefault(false)
            return WorkspaceExportOutcome(
                copied = false,
                warning = if (removed) {
                    "The staged export failed; the existing destination was left untouched."
                } else {
                    "The staged export failed; a temporary document may remain. Existing content was left untouched."
                }
            )
        }

        if (existing == null) {
            val promoted = runCatching { temporary.renameTo(fileName) }.getOrDefault(false)
            if (promoted) return WorkspaceExportOutcome(copied = true, bytes = bytes)
            val removed = runCatching { temporary.delete() }.getOrDefault(false)
            return WorkspaceExportOutcome(
                copied = false,
                warning = if (removed) {
                    "The destination provider does not support safe rename; the file was skipped."
                } else {
                    "Safe rename is unsupported; a temporary document may remain."
                }
            )
        }

        val backupName = uniqueName(parent, ".orbitforge-backup-")
        if (backupName == null) {
            runCatching { temporary.delete() }
            return WorkspaceExportOutcome(false, warning = "Could not reserve a backup name; existing content was preserved.")
        }

        val backupCreated = runCatching { existing.renameTo(backupName) }.getOrDefault(false)
        if (!backupCreated) {
            runCatching { temporary.delete() }
            return WorkspaceExportOutcome(false, warning = "Could not stage the previous destination; it was left untouched.")
        }

        val promoted = runCatching { temporary.renameTo(fileName) }.getOrDefault(false)
        if (promoted) {
            val backup = parent.findFile(backupName)
            val backupRemoved = backup?.let { runCatching { it.delete() }.getOrDefault(false) } ?: true
            return WorkspaceExportOutcome(
                copied = true,
                bytes = bytes,
                replacedExisting = true,
                warning = if (backupRemoved) null else {
                    "The new file was exported, but the previous version remains as a backup document."
                }
            )
        }

        val temporaryRemoved = runCatching { temporary.delete() }.getOrDefault(false)
        val backup = parent.findFile(backupName) ?: existing
        val restored = runCatching { backup.renameTo(fileName) }.getOrDefault(false)
        return WorkspaceExportOutcome(
            copied = false,
            warning = when {
                restored && temporaryRemoved ->
                    "Replacement failed; the previous destination was restored."
                restored ->
                    "The previous destination was restored; a temporary export document may remain."
                else ->
                    "Replacement failed; the previous destination is preserved under backup name " + backupName + "."
            }
        )
    }

    private fun uniqueName(parent: WorkspaceExportDirectory, prefix: String): String? {
        repeat(8) {
            val candidate = prefix + UUID.randomUUID().toString()
            if (parent.findFile(candidate) == null) return candidate
        }
        return null
    }
}
