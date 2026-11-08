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
                warning = "Имя назначения занято объектом, который не является обычным файлом; экспорт пропущен."
            )
        }

        val temporaryName = uniqueName(parent, ".orbitforge-export-")
            ?: return WorkspaceExportOutcome(false, warning = "Не удалось зарезервировать временное имя экспорта.")
        val temporary = parent.createFile(mimeType, temporaryName)
            ?: return WorkspaceExportOutcome(false, warning = "Не удалось создать временный документ экспорта.")

        val bytes = try {
            val output = temporary.openOutputStream() ?: throw IOException("Не удалось открыть временный документ экспорта.")
            output.use { writeContent(it) }
        } catch (_: Exception) {
            val removed = runCatching { temporary.delete() }.getOrDefault(false)
            return WorkspaceExportOutcome(
                copied = false,
                warning = if (removed) {
                    "Не удалось подготовить экспорт; существующий файл не изменён."
                } else {
                    "Не удалось подготовить экспорт; временный документ может остаться. Существующий файл не изменён."
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
                    "Провайдер папки не поддерживает безопасное переименование; файл пропущен."
                } else {
                    "Безопасное переименование не поддерживается; временный документ может остаться."
                }
            )
        }

        val backupName = uniqueName(parent, ".orbitforge-backup-")
        if (backupName == null) {
            runCatching { temporary.delete() }
            return WorkspaceExportOutcome(false, warning = "Не удалось зарезервировать имя резервной копии; существующий файл сохранён.")
        }

        val backupCreated = runCatching { existing.renameTo(backupName) }.getOrDefault(false)
        if (!backupCreated) {
            runCatching { temporary.delete() }
            return WorkspaceExportOutcome(false, warning = "Не удалось переместить прежний файл в резервную копию; он не изменён.")
        }

        val promoted = runCatching { temporary.renameTo(fileName) }.getOrDefault(false)
        if (promoted) {
            val backup = parent.findFile(backupName)
            val backupRemoved = backup?.let { runCatching { it.delete() }.getOrDefault(false) } == true
            return WorkspaceExportOutcome(
                copied = true,
                bytes = bytes,
                replacedExisting = true,
                warning = if (backupRemoved) null else {
                    "Новый файл экспортирован, но удаление резервной копии не подтверждено; проверь папку назначения."
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
                    "Замена не удалась; прежний файл восстановлен."
                restored ->
                    "Прежний файл восстановлен; временный документ экспорта может остаться."
                else ->
                    "Замена не удалась; прежний файл сохранён под резервным именем " + backupName + "."
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
