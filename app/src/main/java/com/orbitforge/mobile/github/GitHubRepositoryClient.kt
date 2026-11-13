package com.orbitforge.mobile.github

import com.orbitforge.mobile.agent.WorkspaceSensitivePathPolicy
import com.orbitforge.mobile.diagnostics.DiagnosticLog
import com.orbitforge.mobile.network.BoundedResponseBodyReader
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

data class GitHubRepository(
    val fullName: String,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val description: String
)

class GitHubRepositoryClient {
    private val http = GitHubHttpClientPolicy.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    suspend fun listRepositories(token: String): List<GitHubRepository> {
        require(token.isNotBlank()) { "Сначала сохрани GitHub token в настройках." }
        val body = execute(
            Request.Builder()
                .url("https://api.github.com/user/repos?sort=updated&per_page=30")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get().build()
        )
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            GitHubRepository(
                fullName = item.optString("full_name"),
                htmlUrl = item.optString("html_url"),
                isPrivate = item.optBoolean("private", true),
                description = item.optString("description")
            )
        }
    }

    suspend fun createRepository(
        token: String,
        name: String,
        description: String,
        privateRepo: Boolean
    ): GitHubRepository {
        require(token.isNotBlank()) { "Сначала сохрани GitHub token в настройках." }
        require(name.matches(Regex("[A-Za-z0-9_.-]{1,100}"))) {
            "Название репозитория может содержать латиницу, цифры, точку, дефис и подчёркивание."
        }
        val payload = JSONObject()
            .put("name", name)
            .put("description", description.take(300))
            .put("private", privateRepo)
            .put("auto_init", true)
            .toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val body = execute(
            Request.Builder()
                .url("https://api.github.com/user/repos")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(payload).build()
        )
        val item = JSONObject(body)
        return GitHubRepository(
            fullName = item.optString("full_name"),
            htmlUrl = item.optString("html_url"),
            isPrivate = item.optBoolean("private", true),
            description = item.optString("description")
        )
    }

    /**
     * Builds a read-only GitHub sync plan. Only added/modified text files are included;
     * unchanged files are omitted and remote-only files are never deleted.
     */
    suspend fun prepareWorkspaceSync(token: String, fullName: String, workspace: File): WorkspaceSyncPlan {
        require(token.isNotBlank()) { "Сначала сохрани GitHub token." }
        require(fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Укажи репозиторий в формате owner/name."
        }
        require(workspace.isDirectory) { "Рабочая папка не найдена." }

        val local = collectWorkspaceCandidates(workspace)
        val repository = getJson(token, "https://api.github.com/repos/" + fullName)
        val branch = repository.optString("default_branch").ifBlank { "main" }
        val branchState = loadBranchState(token, fullName, branch)
        val treeJson = getJson(
            token,
            "https://api.github.com/repos/" + fullName + "/git/trees/" + branchState.treeSha + "?recursive=1"
        )
        require(!treeJson.optBoolean("truncated")) {
            "Удалённое дерево слишком большое для безопасного полного сравнения. Синхронизация остановлена."
        }
        val entries = treeJson.optJSONArray("tree") ?: JSONArray()
        require(entries.length() <= MAX_REMOTE_TREE_ENTRIES) {
            "В удалённом репозитории слишком много объектов для полного предпросмотра."
        }

        val remoteBlobShas = linkedMapOf<String, String>()
        val remoteModes = linkedMapOf<String, String>()
        val remoteSizes = linkedMapOf<String, Long>()
        val unsupportedRemotePaths = linkedSetOf<String>()
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val path = entry.optString("path")
            if (path.isBlank()) continue
            val mode = entry.optString("mode")
            val type = entry.optString("type")
            if (type == "blob" && (mode == "100644" || mode == "100755")) {
                val sha = entry.optString("sha")
                require(sha.matches(Regex("[0-9a-f]{40}"))) {
                    "GitHub вернул некорректный SHA для файла " + path
                }
                require(remoteBlobShas.put(path, sha) == null) {
                    "Удалённое дерево содержит повторяющийся путь: " + path
                }
                remoteModes[path] = mode
                remoteSizes[path] = entry.optLong("size", -1L)
            } else {
                unsupportedRemotePaths += path
            }
        }

        val modifiedCandidates = local.candidates.filter { candidate ->
            val remoteSha = remoteBlobShas[candidate.path]
            remoteSha != null && remoteSha != candidate.blobSha
        }
        require(modifiedCandidates.size <= WorkspaceSyncPlanner.MAX_REMOTE_DIFF_FILES) {
            "Слишком много изменённых удалённых файлов для одного предпросмотра (лимит " +
                WorkspaceSyncPlanner.MAX_REMOTE_DIFF_FILES + "). Раздели синхронизацию на меньшие части."
        }
        val remoteContents = linkedMapOf<String, String>()
        var remoteDiffBytes = 0L
        for (candidate in modifiedCandidates) {
            val remoteSha = remoteBlobShas[candidate.path] ?: continue
            val remoteSize = remoteSizes[candidate.path] ?: -1L
            require(remoteSize in 0..MAX_FILE_BYTES) {
                "Удалённый файл " + candidate.path + " превышает лимит 200 КБ; безопасный diff не построен."
            }
            remoteDiffBytes += remoteSize
            require(remoteDiffBytes <= WorkspaceSyncPlanner.MAX_REMOTE_DIFF_BYTES) {
                "Удалённые версии изменённых файлов превышают общий лимит diff 2 МБ."
            }
            val blob = getJson(
                token,
                "https://api.github.com/repos/" + fullName + "/git/blobs/" + remoteSha
            )
            require(blob.optString("encoding") == "base64") {
                "GitHub вернул неподдерживаемое кодирование для " + candidate.path
            }
            val encoded = blob.optString("content")
            val bytes = try {
                Base64.getMimeDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw IOException("Не удалось декодировать удалённый файл " + candidate.path + ".")
            }
            require(bytes.size.toLong() == remoteSize && bytes.size.toLong() <= MAX_FILE_BYTES) {
                "Размер удалённого файла изменился во время предпросмотра: " + candidate.path
            }
            require(GitBlobHash.sha1(bytes) == remoteSha) {
                "Контрольная сумма удалённого файла не совпала: " + candidate.path
            }
            val oldContent = String(bytes, Charsets.UTF_8)
            require(oldContent.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                "Удалённый файл " + candidate.path + " не является корректным UTF-8 текстом; перезапись заблокирована."
            }
            require(!looksLikeSecret(oldContent)) {
                "Удалённый файл " + candidate.path + " похож на содержащий секреты; перезапись заблокирована."
            }
            remoteContents[candidate.path] = oldContent
        }

        return WorkspaceSyncPlanner.create(
            repositoryFullName = fullName,
            branch = branch,
            expectedHeadSha = branchState.headSha,
            baseTreeSha = branchState.treeSha,
            candidates = local.candidates,
            remoteBlobShas = remoteBlobShas,
            remoteModes = remoteModes,
            remoteContents = remoteContents,
            unsupportedRemotePaths = unsupportedRemotePaths,
            skippedFiles = local.skippedFiles
        )
    }

    /**
     * Commits exactly the reviewed plan. Both the remote branch head and every local file hash
     * are rechecked immediately before creating the tree; the branch ref is updated non-force.
     */
    suspend fun commitWorkspaceSync(
        token: String,
        plan: WorkspaceSyncPlan,
        workspace: File
    ): WorkspaceSyncResult {
        require(token.isNotBlank()) { "Сначала сохрани GitHub token." }
        require(plan.changes.isNotEmpty()) { "Нет изменений для commit." }
        require(workspace.isDirectory) { "Рабочая папка не найдена." }

        val branchState = loadBranchState(token, plan.repositoryFullName, plan.branch)
        require(branchState.headSha == plan.expectedHeadSha && branchState.treeSha == plan.baseTreeSha) {
            "Удалённая ветка изменилась после предпросмотра. Обнови diff и повтори подтверждение."
        }

        val root = workspace.canonicalFile
        val treeElements = JSONArray()
        var totalBytes = 0L
        for (change in plan.changes) {
            val relativePath = WorkspaceSyncPlanner.validateRelativePath(change.path)
            require(!isIgnoredPath(relativePath)) { "Файл исключён из синхронизации: " + relativePath }
            val target = File(root, relativePath)
            require(!Files.isSymbolicLink(target.toPath())) { "Символические ссылки не отправляются: " + relativePath }
            val canonical = target.canonicalFile
            require(canonical.path.startsWith(root.path + File.separator) && canonical.isFile) {
                "Файл исчез или вышел за пределы рабочей папки: " + relativePath
            }
            require(canonical.length() <= MAX_FILE_BYTES) { "Файл превышает лимит 200 КБ: " + relativePath }
            val bytes = canonical.readBytes()
            require(bytes.size.toLong() <= MAX_FILE_BYTES && bytes.none { it == 0.toByte() }) {
                "Файл стал слишком большим или бинарным после предпросмотра: " + relativePath
            }
            val content = String(bytes, Charsets.UTF_8)
            require(content.toByteArray(Charsets.UTF_8).contentEquals(bytes)) {
                "Файл перестал быть корректным UTF-8 текстом: " + relativePath
            }
            require(!looksLikeSecret(content)) { "Файл стал похож на содержащий секреты: " + relativePath }
            require(content == change.content && GitBlobHash.sha1(bytes) == change.blobSha) {
                "Локальный файл изменился после предпросмотра: " + relativePath
            }
            require(change.mode == "100644" || change.mode == "100755") {
                "Недопустимый Git mode для файла: " + relativePath
            }
            totalBytes += bytes.size
            require(totalBytes <= MAX_TOTAL_BYTES) { "Общий размер commit превышает 2 МБ." }
            treeElements.put(
                JSONObject()
                    .put("path", relativePath)
                    .put("mode", change.mode)
                    .put("type", "blob")
                    .put("content", content)
            )
        }

        val treeResult = postJson(
            token,
            "https://api.github.com/repos/" + plan.repositoryFullName + "/git/trees",
            JSONObject().put("base_tree", plan.baseTreeSha).put("tree", treeElements)
        )
        val treeSha = treeResult.optString("sha")
        require(treeSha.matches(Regex("[0-9a-f]{40}"))) { "GitHub не вернул корректный SHA дерева." }

        val commitResult = postJson(
            token,
            "https://api.github.com/repos/" + plan.repositoryFullName + "/git/commits",
            JSONObject()
                .put("message", "Sync OrbitForge workspace")
                .put("tree", treeSha)
                .put("parents", JSONArray().put(plan.expectedHeadSha))
        )
        val commitSha = commitResult.optString("sha")
        require(commitSha.matches(Regex("[0-9a-f]{40}"))) { "GitHub не вернул корректный SHA commit." }

        val updateBody = JSONObject().put("sha", commitSha).put("force", false)
        execute(
            apiRequest(
                token,
                "https://api.github.com/repos/" + plan.repositoryFullName + "/git/refs/heads/" + encodePathSegment(plan.branch),
                "PATCH",
                updateBody
            )
        )
        return WorkspaceSyncResult(
            addedFiles = plan.addedFiles,
            modifiedFiles = plan.modifiedFiles,
            unchangedFiles = plan.unchangedFiles,
            skippedFiles = plan.skippedFiles,
            branch = plan.branch,
            commitSha = commitSha
        )
    }

    private data class BranchState(val headSha: String, val treeSha: String)
    private data class LocalWorkspaceSnapshot(
        val candidates: List<WorkspaceSyncCandidate>,
        val skippedFiles: Int
    )

    private suspend fun loadBranchState(token: String, fullName: String, branch: String): BranchState {
        val branchInfo = getJson(
            token,
            "https://api.github.com/repos/" + fullName + "/branches/" + encodePathSegment(branch)
        )
        val commit = branchInfo.optJSONObject("commit")
        val headSha = commit?.optString("sha").orEmpty()
        val treeSha = commit?.optJSONObject("commit")?.optJSONObject("tree")?.optString("sha").orEmpty()
        require(headSha.matches(Regex("[0-9a-f]{40}")) && treeSha.matches(Regex("[0-9a-f]{40}"))) {
            "Не удалось определить актуальные commit и tree удалённой ветки."
        }
        return BranchState(headSha, treeSha)
    }

    private fun collectWorkspaceCandidates(workspace: File): LocalWorkspaceSnapshot {
        val root = workspace.canonicalFile
        var skipped = 0
        val files = workspace.walkTopDown()
            .onEnter { directory ->
                val canonical = runCatching { directory.canonicalFile }.getOrNull()
                if (canonical == null || (canonical != root && !canonical.path.startsWith(root.path + File.separator))) {
                    skipped++
                    false
                } else if (canonical != root) {
                    val relativeDirectory = root.toPath().relativize(canonical.toPath()).toString()
                    if (isIgnoredPath(relativeDirectory)) {
                        skipped++
                        false
                    } else true
                } else true
            }
            .filter { it.isFile }
            .take(MAX_FILES + 1)
            .toList()
        require(files.size <= MAX_FILES) { "В рабочей папке больше 200 подходящих файлов. Раздели проект на части." }

        val candidates = mutableListOf<WorkspaceSyncCandidate>()
        var totalBytes = 0L
        for (file in files) {
            if (Files.isSymbolicLink(file.toPath())) {
                skipped++
                continue
            }
            val canonical = runCatching { file.canonicalFile }.getOrNull()
            if (canonical == null || !canonical.path.startsWith(root.path + File.separator)) {
                skipped++
                continue
            }
            val relative = root.toPath().relativize(canonical.toPath()).toString().replace(File.separatorChar, '/')
            if (isIgnoredPath(relative)) {
                skipped++
                continue
            }
            if (canonical.length() > MAX_FILE_BYTES) {
                skipped++
                continue
            }
            val bytes = try {
                canonical.readBytes()
            } catch (_: Exception) {
                skipped++
                continue
            }
            if (bytes.size.toLong() > MAX_FILE_BYTES || bytes.any { it == 0.toByte() }) {
                skipped++
                continue
            }
            val content = String(bytes, Charsets.UTF_8)
            if (!content.toByteArray(Charsets.UTF_8).contentEquals(bytes) || looksLikeSecret(content)) {
                skipped++
                continue
            }
            if (totalBytes + bytes.size > MAX_TOTAL_BYTES) {
                skipped++
                continue
            }
            totalBytes += bytes.size
            candidates += WorkspaceSyncCandidate(relative, content, GitBlobHash.sha1(bytes))
        }
        return LocalWorkspaceSnapshot(candidates, skipped)
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun apiRequest(token: String, url: String, method: String, body: JSONObject? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
        val requestBody = body?.toString()?.toRequestBody("application/json; charset=utf-8".toMediaType())
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requireNotNull(requestBody))
            "PATCH" -> builder.patch(requireNotNull(requestBody))
            else -> throw IllegalArgumentException("Unsupported GitHub API method.")
        }
        return builder.build()
    }

    private suspend fun getJson(token: String, url: String): JSONObject =
        JSONObject(execute(apiRequest(token, url, "GET")))

    private suspend fun postJson(token: String, url: String, body: JSONObject): JSONObject =
        JSONObject(execute(apiRequest(token, url, "POST", body)))

    private fun isIgnoredPath(path: String): Boolean {
        val normalized = path.replace(File.separatorChar, '/')
        val lower = normalized.lowercase()
        val name = lower.substringAfterLast('/')
        val segments = lower.split('/')
        val blockedDirectories = setOf(".git", ".gradle", "build", ".idea", ".ssh", ".cxx", "node_modules", ".venv", "__pycache__", "target", "dist")
        val blockedExtensions = setOf(".apk", ".aab", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".crt", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".zip", ".jar", ".class", ".dex", ".so", ".mp4", ".mp3", ".ttf", ".otf", ".woff", ".woff2", ".db")
        return WorkspaceSensitivePathPolicy.isSensitive(normalized) ||
            segments.any { it in blockedDirectories } ||
            name in setOf("local.properties", "secrets.properties", "credentials.json", "service-account.json", "id_rsa", "id_ed25519") ||
            name.startsWith(".env") || blockedExtensions.any { name.endsWith(it) }
    }

    private fun looksLikeSecret(content: String): Boolean {
        val patterns = listOf(
            Regex("(?i)(api[_-]?key|access[_-]?token|github[_-]?token|client[_-]?secret|password)\\s*[:=]\\s*[\\\"']?[^\\s\\\"']{12,}"),
            Regex("(?i)sk-[A-Za-z0-9_-]{12,}"),
            Regex("(?i)gh[pousr]_[A-Za-z0-9]{20,}"),
            Regex("(?i)github_pat_[A-Za-z0-9_]{20,}"),
            Regex("AIza[0-9A-Za-z_-]{30,}"),
            Regex("-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
            Regex("(?i)AKIA[0-9A-Z]{16}"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{16,}")
        )
        return patterns.any { it.containsMatchIn(content) }
    }

    companion object {
        private const val MAX_FILE_BYTES = 200 * 1024L
        private const val MAX_TOTAL_BYTES = 2 * 1024 * 1024L
        private const val MAX_FILES = 200
        private const val MAX_REMOTE_TREE_ENTRIES = 5_000
        private const val MAX_GITHUB_RESPONSE_BYTES = 4 * 1024 * 1024
    }

    private suspend fun execute(request: Request): String =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (GitHubHttpClientPolicy.isRedirectStatus(response.code)) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(GitHubHttpClientPolicy.redirectFailure(response.code))
                                }
                                return@use
                            }
                            val body = response.body?.byteStream()?.use { BoundedResponseBodyReader.read(it, MAX_GITHUB_RESPONSE_BYTES) }.orEmpty()
                            if (!response.isSuccessful) {
                                val safeBody = DiagnosticLog.redact(body).take(300)
                                val message = when (response.code) {
                                    401 -> "GitHub отклонил токен (401). Проверь токен."
                                    403 -> "GitHub запретил действие (403). Проверь права токена и лимиты."
                                    404 -> "GitHub API вернул 404."
                                    409 -> "GitHub отклонил обновление ветки (409). Возможно, она изменилась после предпросмотра. Обнови diff."
                                    422 -> "GitHub не принял commit (422). Обнови предпросмотр и проверь права токена."
                                    else -> "GitHub API HTTP ${response.code}: ${safeBody}"
                                }
                                if (continuation.isActive) continuation.resumeWithException(IOException(message))
                            } else if (continuation.isActive) {
                                continuation.resumeWith(Result.success(body))
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }
}
