package com.orbitforge.mobile.github

import com.orbitforge.mobile.agent.WorkspaceDiff
import com.orbitforge.mobile.agent.WorkspaceSensitivePathPolicy
import java.security.MessageDigest

enum class WorkspaceSyncChangeKind { ADDED, MODIFIED }

internal data class WorkspaceSyncCandidate(
    val path: String,
    val content: String,
    val blobSha: String
)

data class WorkspaceSyncChange(
    val path: String,
    val kind: WorkspaceSyncChangeKind,
    val content: String,
    val blobSha: String,
    val mode: String,
    val preview: String
)

data class WorkspaceSyncPlan(
    val repositoryFullName: String,
    val branch: String,
    val expectedHeadSha: String,
    val baseTreeSha: String,
    val changes: List<WorkspaceSyncChange>,
    val unchangedFiles: Int,
    val skippedFiles: Int,
    val preview: String
) {
    val addedFiles: Int get() = changes.count { it.kind == WorkspaceSyncChangeKind.ADDED }
    val modifiedFiles: Int get() = changes.count { it.kind == WorkspaceSyncChangeKind.MODIFIED }
}

data class WorkspaceSyncResult(
    val addedFiles: Int,
    val modifiedFiles: Int,
    val unchangedFiles: Int,
    val skippedFiles: Int,
    val branch: String,
    val commitSha: String
)

internal object GitBlobHash {
    fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            value.toString(16).padStart(2, '0')
        }
    }

    fun sha1(content: String): String = sha1(content.toByteArray(Charsets.UTF_8))
}

internal object WorkspaceSyncPlanner {
    const val MAX_PREVIEW_CHARS = 12_000
    const val MAX_REMOTE_DIFF_FILES = 40
    const val MAX_REMOTE_DIFF_BYTES = 2 * 1024 * 1024
    private const val MAX_FILE_PREVIEW_LINES = 40

    fun validateRelativePath(path: String): String {
        require(path.isNotBlank()) { "Путь файла не может быть пустым." }
        require(path.length <= 240) { "Путь файла слишком длинный." }
        require(!path.startsWith("/") && !path.contains('\\')) { "Разрешены только относительные пути с разделителем '/'." }
        require(path.none { it.code < 0x20 || it.code == 0x7f }) { "Путь содержит управляющие символы." }
        val segments = path.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." }) { "Путь содержит недопустимый сегмент." }
        return path
    }

    fun create(
        repositoryFullName: String,
        branch: String,
        expectedHeadSha: String,
        baseTreeSha: String,
        candidates: List<WorkspaceSyncCandidate>,
        remoteBlobShas: Map<String, String>,
        remoteModes: Map<String, String>,
        remoteContents: Map<String, String>,
        unsupportedRemotePaths: Set<String>,
        skippedFiles: Int
    ): WorkspaceSyncPlan {
        require(repositoryFullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Некорректное имя GitHub-репозитория."
        }
        require(branch.isNotBlank() && branch.length <= 255 && branch.none { it.code < 0x20 || it.code == 0x7f }) {
            "Некорректное имя ветки GitHub."
        }
        require(expectedHeadSha.matches(Regex("[0-9a-f]{40}"))) { "Некорректный SHA последнего commit." }
        require(baseTreeSha.matches(Regex("[0-9a-f]{40}"))) { "Некорректный SHA дерева Git." }
        require(skippedFiles >= 0) { "Количество пропущенных файлов не может быть отрицательным." }
        require(candidates.size <= 200) { "Превышен лимит 200 файлов для синхронизации." }
        require(remoteContents.size <= MAX_REMOTE_DIFF_FILES) {
            "Слишком много удалённых файлов для полного diff."
        }
        val remoteDiffBytes = remoteContents.values.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() }
        require(remoteDiffBytes <= MAX_REMOTE_DIFF_BYTES) {
            "Удалённые версии изменённых файлов превышают общий лимит diff 2 МБ."
        }

        val seen = mutableSetOf<String>()
        val changes = mutableListOf<WorkspaceSyncChange>()
        var unchanged = 0

        for (candidate in candidates.sortedBy { it.path }) {
            val path = validateRelativePath(candidate.path)
            require(!WorkspaceSensitivePathPolicy.isSensitive(path)) {
                "Синхронизация чувствительного пути запрещена: " + path
            }
            require(seen.add(path)) { "Путь файла повторяется в плане синхронизации: " + path }
            require(GitBlobHash.sha1(candidate.content) == candidate.blobSha) {
                "Содержимое файла изменилось до построения плана: " + path
            }
            require(!unsupportedRemotePaths.contains(path)) {
                "Удалённый путь " + path + " является ссылкой, подмодулем или неподдерживаемым объектом; перезапись заблокирована."
            }

            val remoteSha = remoteBlobShas[path]
            if (remoteSha == candidate.blobSha) {
                unchanged++
                continue
            }

            val kind = if (remoteSha == null) WorkspaceSyncChangeKind.ADDED else WorkspaceSyncChangeKind.MODIFIED
            val mode = if (kind == WorkspaceSyncChangeKind.ADDED) {
                "100644"
            } else {
                require(remoteSha!!.matches(Regex("[0-9a-f]{40}"))) { "Некорректный SHA удалённого файла: " + path }
                val remoteMode = remoteModes[path]
                require(remoteMode == "100644" || remoteMode == "100755") {
                    "Неподдерживаемый режим удалённого файла: " + path
                }
                remoteMode
            }

            val diff = if (kind == WorkspaceSyncChangeKind.ADDED) {
                WorkspaceDiff.preview("", candidate.content, maxLines = MAX_FILE_PREVIEW_LINES, isNewFile = true)
            } else {
                val oldContent = remoteContents[path]
                    ?: throw IllegalArgumentException("Не удалось безопасно получить diff удалённого файла: " + path)
                WorkspaceDiff.preview(oldContent, candidate.content, maxLines = MAX_FILE_PREVIEW_LINES, isNewFile = false)
            }
            require(!diff.contains("предпросмотр сокращён")) {
                "Diff файла " + path + " слишком велик для полного предпросмотра. Разбей синхронизацию на меньшие части."
            }
            changes += WorkspaceSyncChange(path, kind, candidate.content, candidate.blobSha, mode, diff)
        }

        val preview = if (changes.isEmpty()) {
            "Нет изменений для отправки."
        } else {
            changes.joinToString("\n\n") { change ->
                (if (change.kind == WorkspaceSyncChangeKind.ADDED) "Добавление: " else "Изменение: ") +
                    change.path + "\n" + change.preview
            }
        }
        require(preview.length <= MAX_PREVIEW_CHARS) {
            "Общий diff превышает лимит предпросмотра 12 000 символов. Разбей синхронизацию на меньшие части."
        }

        return WorkspaceSyncPlan(
            repositoryFullName = repositoryFullName,
            branch = branch,
            expectedHeadSha = expectedHeadSha,
            baseTreeSha = baseTreeSha,
            changes = changes.toList(),
            unchangedFiles = unchanged,
            skippedFiles = skippedFiles,
            preview = preview
        )
    }
}
