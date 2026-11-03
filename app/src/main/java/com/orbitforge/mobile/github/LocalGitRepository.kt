package com.orbitforge.mobile.github

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class LocalGitStatusEntry(
    val path: String,
    val indexStatus: String,
    val workTreeStatus: String,
    val conflicted: Boolean
) {
    val shortStatus: String get() = indexStatus + workTreeStatus
}

data class LocalGitStatus(
    val branch: String,
    val entries: List<LocalGitStatusEntry>
) {
    val isClean: Boolean get() = entries.isEmpty()
}

class LocalGitCommitPlan internal constructor(
    val branch: String,
    val paths: List<String>,
    val diff: String,
    internal val expectedBlobShas: Map<String, String?>,
    internal val fingerprint: String
)

data class LocalGitCommitResult(
    val commitSha: String,
    val branch: String,
    val changedFiles: Int
)

/**
 * Local Git operations are deliberately limited to the app-private workspace root.
 * No parent repository discovery, remote operations, shell commands, or force updates are used.
 */
class LocalGitRepository {
    fun isInitialized(root: File): Boolean {
        val canonical = runCatching { root.canonicalFile }.getOrNull() ?: return false
        val gitDir = File(canonical, ".git")
        if (!gitDir.isDirectory || Files.isSymbolicLink(gitDir.toPath())) return false
        return runCatching { withGit(canonical) { true } }.getOrDefault(false)
    }

    fun initialize(root: File): String = withRootLock(root) {
        val canonical = canonicalRoot(root)
        val gitDir = File(canonical, ".git")
        require(!Files.isSymbolicLink(gitDir.toPath())) {
            "Папка .git не может быть символической ссылкой."
        }
        if (gitDir.exists()) {
            require(isInitialized(canonical)) {
                "В рабочей папке уже существует некорректная или неподдерживаемая папка .git."
            }
            return@withRootLock withGit(canonical) { it.repository.branch }
        }
        Git.init()
            .setDirectory(canonical)
            .setInitialBranch("main")
            .call()
            .use { it.repository.branch }
    }

    fun status(root: File): LocalGitStatus = withRootLock(root) {
        withGit(root) { git ->
            val raw = git.status().call()
            val snapshot = toSnapshot(git, raw)
            require(snapshot.entries.size <= MAX_STATUS_PATHS) {
                "Слишком много локальных изменений для безопасного отображения (лимит " + MAX_STATUS_PATHS + ")."
            }
            snapshot
        }
    }

    /** Creates a bounded, read-only diff plan. The plan must be revalidated before committing. */
    fun prepareCommit(root: File): LocalGitCommitPlan = withRootLock(root) {
        withGit(root) { git -> prepareCommitUnlocked(git) }
    }

    /**
     * Stages all reviewed workspace changes and creates a local commit after rechecking both
     * the working tree and the staged blobs. It never pushes or deletes workspace files.
     */
    fun commitReviewed(
        root: File,
        plan: LocalGitCommitPlan,
        message: String,
        authorName: String,
        authorEmail: String
    ): LocalGitCommitResult = withRootLock(root) {
        withGit(root) { git ->
            validateCommitMetadata(message, authorName, authorEmail)
            require(plan.paths.isNotEmpty()) { "Нет изменений для commit." }

            val currentPlan = prepareCommitUnlocked(git)
            require(currentPlan.fingerprint == plan.fingerprint &&
                currentPlan.paths == plan.paths &&
                currentPlan.diff == plan.diff
            ) {
                "Рабочая папка или Git index изменились после предпросмотра. Построй diff заново."
            }

            val pathsToAdd = plan.paths.filter { plan.expectedBlobShas[it] != null }
            if (pathsToAdd.isNotEmpty()) {
                val add = git.add()
                pathsToAdd.forEach { add.addFilepattern(it) }
                add.call()
            }
            val pathsToUpdate = plan.paths.filter { plan.expectedBlobShas[it] == null }
            if (pathsToUpdate.isNotEmpty()) {
                val update = git.add().setUpdate(true)
                pathsToUpdate.forEach { update.addFilepattern(it) }
                update.call()
            }

            val stagedStatus = git.status().call()
            val stagedSnapshot = toSnapshot(git, stagedStatus)
            require(stagedSnapshot.entries.map { it.path }.toSet() == plan.paths.toSet()) {
                "Набор файлов изменился во время staging. Commit остановлен; проверь Git status."
            }

            val repository = git.repository
            val index = repository.readDirCache()
            for (path in plan.paths) {
                val expectedSha = plan.expectedBlobShas[path]
                val indexEntry = index.getEntry(path)
                if (expectedSha == null) {
                    require(indexEntry == null) {
                        "Файл " + path + " должен быть удалён из index, но всё ещё присутствует."
                    }
                    continue
                }

                require(indexEntry != null && indexEntry.stage == 0) {
                    "Не удалось проверить staged-файл " + path + "."
                }
                val stagedBytes = readIndexEntry(repository, indexEntry)
                require(GitBlobHash.sha1(stagedBytes) == expectedSha) {
                    "Содержимое staged-файла изменилось во время staging: " + path
                }
                val workingFile = resolvePath(canonicalRoot(root), path)
                require(workingFile.isFile) {
                    "Файл исчез во время staging: " + path
                }
                val workingBytes = readWorkingFile(workingFile)
                require(GitBlobHash.sha1(workingBytes) == expectedSha) {
                    "Файл изменился во время staging: " + path
                }
            }

            val commit = git.commit()
                .setMessage(message.trim())
                .setAuthor(authorName.trim(), authorEmail.trim())
                .setCommitter(authorName.trim(), authorEmail.trim())
                .call()
            LocalGitCommitResult(
                commitSha = commit.name,
                branch = repository.branch,
                changedFiles = plan.paths.size
            )
        }
    }

    private fun prepareCommitUnlocked(git: Git): LocalGitCommitPlan {
        val rawStatus = git.status().call()
        val snapshot = toSnapshot(git, rawStatus)
        require(snapshot.entries.size <= MAX_COMMIT_PATHS) {
            "Слишком много изменений для одного локального commit (лимит " + MAX_COMMIT_PATHS + ")."
        }
        require(snapshot.entries.none { it.conflicted }) {
            "В Git есть конфликтующие файлы. Разреши конфликты перед commit."
        }

        val root = canonicalRoot(git.repository.workTree)
        val paths = snapshot.entries.map { validatePath(it.path) }.sorted()
        val expectedShas = linkedMapOf<String, String?>()
        val statusByPath = snapshot.entries.associateBy { it.path }
        var totalBytes = 0L

        for (path in paths) {
            val target = resolvePath(root, path)
            if (!target.exists()) {
                val state = statusByPath[path]
                require(state != null && (state.indexStatus == "D" || state.workTreeStatus == "D")) {
                    "Файл исчез во время подготовки diff: " + path
                }
                expectedShas[path] = null
                continue
            }
            require(!isSensitivePath(path)) {
                "Commit заблокирован: чувствительный путь " + path + ". Убери его из Git index или исключи из проекта."
            }
            require(target.isFile) { "Изменённый путь не является обычным файлом: " + path }
            val bytes = readWorkingFile(target)
            totalBytes += bytes.size
            require(totalBytes <= MAX_TOTAL_BYTES) {
                "Изменённые файлы превышают общий лимит локального commit 2 МБ."
            }
            val content = decodeText(bytes, path)
            require(!looksLikeSecretContent(content)) {
                "Commit заблокирован: содержимое " + path + " похоже на секрет или токен."
            }
            expectedShas[path] = GitBlobHash.sha1(bytes)
        }

        val diff = buildDiff(git, rawStatus, paths)
        val fingerprint = fingerprint(snapshot, expectedShas, diff)
        return LocalGitCommitPlan(
            branch = snapshot.branch,
            paths = paths,
            diff = diff,
            expectedBlobShas = expectedShas.toMap(),
            fingerprint = fingerprint
        )
    }

    private fun readIndexEntry(repository: Repository, entry: DirCacheEntry): ByteArray {
        require(entry.fileMode != FileMode.SYMLINK) {
            "Символические ссылки не поддерживаются локальным Git preview."
        }
        require(entry.fileMode == FileMode.REGULAR_FILE || entry.fileMode == FileMode.EXECUTABLE_FILE) {
            "Неподдерживаемый Git mode в index."
        }
        val loader = repository.open(entry.objectId)
        require(loader.size <= MAX_FILE_BYTES) {
            "Staged-файл превышает лимит 200 КБ."
        }
        return loader.getBytes()
    }

    private fun buildDiff(git: Git, status: org.eclipse.jgit.api.Status, paths: List<String>): String {
        if (paths.isEmpty()) return "Нет локальных изменений."
        require(paths.size <= MAX_DIFF_FILES) {
            "Diff превышает лимит " + MAX_DIFF_FILES + " файлов. Раздели изменения на несколько commit."
        }

        val output = BoundedByteArrayOutputStream(MAX_DIFF_BYTES)
        val repository = git.repository
        val head = repository.resolve(Constants.HEAD)
        val staged = if (head == null) {
            val scanner = DiffFormatter(ByteArrayOutputStream())
            try {
                scanner.setRepository(repository)
                scanner.scan(EmptyTreeIterator(), DirCacheIterator(repository.readDirCache()))
            } finally {
                scanner.close()
            }
        } else {
            git.diff().setCached(true).call()
        }
        if (staged.isNotEmpty()) {
            DiffFormatter(output).use { formatter ->
                formatter.setRepository(repository)
                formatter.setContext(3)
                formatter.format(staged)
            }
        }

        val unstaged = if (head == null) {
            val scanner = DiffFormatter(ByteArrayOutputStream())
            try {
                scanner.setRepository(repository)
                scanner.scan(
                    DirCacheIterator(repository.readDirCache()),
                    FileTreeIterator(repository)
                ).filterNot { status.untracked.contains(it.newPath) }
            } finally {
                scanner.close()
            }
        } else {
            git.diff().call()
        }
        if (unstaged.isNotEmpty()) {
            DiffFormatter(output).use { formatter ->
                formatter.setRepository(repository)
                formatter.setContext(3)
                formatter.format(unstaged)
            }
        }

        for (path in status.untracked.sorted()) {
            val file = resolvePath(canonicalRoot(git.repository.workTree), path)
            require(file.isFile) { "Untracked-путь не является обычным файлом: " + path }
            val bytes = readWorkingFile(file)
            val content = decodeText(bytes, path)
            writeUtf8(output, "diff --git a/" + path + " b/" + path + "\n")
            writeUtf8(output, "new file mode 100644\n--- /dev/null\n+++ b/" + path + "\n")
            val lines = content.split('\n')
            val actualLines = if (content.isEmpty()) emptyList()
                else if (content.endsWith("\n")) lines.dropLast(1) else lines
            writeUtf8(output, "@@ -0,0 +1," + actualLines.size + " @@\n")
            for (line in actualLines) writeUtf8(output, "+" + line + "\n")
            if (content.isNotEmpty() && !content.endsWith("\n")) {
                writeUtf8(output, "\\ No newline at end of file\n")
            }
        }

        val result = String(output.toByteArray(), Charsets.UTF_8)
        require(result.length <= MAX_DIFF_CHARS) {
            "Diff превышает лимит предпросмотра " + MAX_DIFF_CHARS + " символов."
        }
        return result.ifBlank { "Нет локальных изменений." }
    }

    private fun toSnapshot(git: Git, status: org.eclipse.jgit.api.Status): LocalGitStatus {
        val staged = linkedMapOf<String, String>()
        val workTree = linkedMapOf<String, String>()
        val conflicts = status.conflicting

        status.added.forEach { staged[it] = "A" }
        status.changed.forEach { staged[it] = "M" }
        status.removed.forEach { staged[it] = "D" }
        status.modified.forEach { workTree[it] = "M" }
        status.missing.forEach { workTree[it] = "D" }
        status.untracked.forEach { workTree[it] = "?" }
        conflicts.forEach {
            staged[it] = "U"
            workTree[it] = "U"
        }

        val all = (staged.keys + workTree.keys + conflicts).toSortedSet()
        val entries = all.map { path ->
            LocalGitStatusEntry(
                path = path,
                indexStatus = staged[path] ?: " ",
                workTreeStatus = workTree[path] ?: " ",
                conflicted = conflicts.contains(path)
            )
        }
        return LocalGitStatus(git.repository.branch.ifBlank { "HEAD" }, entries)
    }

    private fun fingerprint(
        status: LocalGitStatus,
        expectedShas: Map<String, String?>,
        diff: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        add(status.branch)
        for (entry in status.entries) {
            add(entry.path)
            add(entry.indexStatus)
            add(entry.workTreeStatus)
            add(entry.conflicted.toString())
        }
        for (path in expectedShas.keys.sorted()) {
            add(path)
            add(expectedShas[path] ?: "<missing>")
        }
        add(diff)
        return digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    private fun validateCommitMetadata(message: String, authorName: String, authorEmail: String) {
        val trimmedMessage = message.trim()
        require(trimmedMessage.isNotBlank() && trimmedMessage.length <= MAX_COMMIT_MESSAGE_CHARS) {
            "Укажи сообщение commit длиной от 1 до " + MAX_COMMIT_MESSAGE_CHARS + " символов."
        }
        require(trimmedMessage.none { it.code < 0x20 || it.code == 0x7f }) {
            "Сообщение commit должно быть одной строкой без управляющих символов."
        }
        val name = authorName.trim()
        require(name.isNotBlank() && name.length <= 100 && name.none { it.code < 0x20 || it.code == 0x7f || it == '<' || it == '>' }) {
            "Укажи корректное имя автора (до 100 символов)."
        }
        val email = authorEmail.trim()
        require(email.length <= 254 && email.matches(Regex("""[^@\s<>]+@[^@\s<>]+"""))) {
            "Укажи корректный email автора."
        }
    }

    private fun canonicalRoot(root: File): File {
        val canonical = root.canonicalFile
        require(canonical.isDirectory) { "Рабочая папка не найдена." }
        return canonical
    }

    private fun resolvePath(root: File, relativePath: String): File {
        val path = validatePath(relativePath)
        var current = root
        for (segment in path.split('/')) {
            current = File(current, segment)
            require(!Files.isSymbolicLink(current.toPath())) {
                "Символические ссылки запрещены для локального Git: " + path
            }
        }
        val canonical = current.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) {
            "Git-путь выходит за пределы рабочей папки: " + path
        }
        return canonical
    }

    private fun validatePath(path: String): String {
        require(path.isNotBlank() && path.length <= 400) { "Некорректный Git-путь." }
        require(!path.startsWith("/") && !path.contains('\\') && !path.contains('\u0000')) {
            "Разрешены только относительные Git-пути."
        }
        require(path.none { it.code < 0x20 || it.code == 0x7f }) {
            "Git-путь содержит управляющие символы."
        }
        val segments = path.split('/')
        require(segments.all { it.isNotBlank() && it != "." && it != ".." && !it.equals(".git", true) }) {
            "Git-путь содержит недопустимый сегмент."
        }
        return path
    }

    private fun readWorkingFile(file: File): ByteArray {
        require(!Files.isSymbolicLink(file.toPath())) { "Символические ссылки не читаются." }
        require(file.isFile) { "Путь не является обычным файлом." }
        require(file.length() <= MAX_FILE_BYTES) { "Файл превышает лимит 200 КБ." }
        val bytes = file.readBytes()
        require(bytes.size <= MAX_FILE_BYTES) { "Файл вырос и превысил лимит 200 КБ." }
        return bytes
    }

    private fun decodeText(bytes: ByteArray, path: String): String {
        require(bytes.none { it == 0.toByte() }) { "Бинарный файл не поддерживается в diff/commit: " + path }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("Файл не является корректным UTF-8 текстом: " + path)
        }
    }

    private fun isSensitivePath(path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        val name = normalized.substringAfterLast('/')
        return name.startsWith(".env") || name in setOf(
            "local.properties", "secrets.properties", "credentials.json", "id_rsa", "id_ed25519",
            ".netrc", ".npmrc", ".git-credentials"
        ) || name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pfx") || name.endsWith(".pem") || name.endsWith(".key") ||
            normalized.split('/').any { it == ".ssh" || it == ".aws" }
    }

    private fun looksLikeSecretContent(content: String): Boolean {
        val patterns = listOf(
            Regex("""(?i)(api[_-]?key|access[_-]?token|github[_-]?token|client[_-]?secret|password)\s*[:=]\s*["']?[A-Za-z0-9_./+=-]{20,}"""),
            Regex("(?i)(sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"),
            Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]{20,}""")
        )
        return patterns.any { it.containsMatchIn(content) }
    }

    private fun writeUtf8(output: ByteArrayOutputStream, value: String) {
        output.write(value.toByteArray(Charsets.UTF_8))
    }

    private fun <T> withGit(root: File, action: (Git) -> T): T {
        val canonical = canonicalRoot(root)
        val gitDir = File(canonical, ".git")
        require(!Files.isSymbolicLink(gitDir.toPath()) && gitDir.isDirectory) {
            "Локальный Git не инициализирован в рабочей папке."
        }
        val repository = FileRepositoryBuilder()
            .setGitDir(gitDir)
            .setWorkTree(canonical)
            .build()
        return Git(repository).use(action)
    }

    private fun <T> withRootLock(root: File, action: () -> T): T {
        val key = root.canonicalPath
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }
        return lock.withLock(action)
    }

    private class BoundedByteArrayOutputStream(private val limit: Int) : ByteArrayOutputStream() {
        override fun write(value: Int) {
            if (count >= limit) throw IOException("Diff превысил лимит размера.")
            super.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length < 0 || count.toLong() + length > limit) {
                throw IOException("Diff превысил лимит размера.")
            }
            super.write(bytes, offset, length)
        }
    }

    companion object {
        const val MAX_STATUS_PATHS = 500
        const val MAX_COMMIT_PATHS = 200
        const val MAX_DIFF_FILES = 30
        const val MAX_FILE_BYTES = 200 * 1024
        const val MAX_TOTAL_BYTES = 2 * 1024 * 1024
        const val MAX_DIFF_BYTES = 200 * 1024
        const val MAX_DIFF_CHARS = 80_000
        const val MAX_COMMIT_MESSAGE_CHARS = 200

        private val locks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
