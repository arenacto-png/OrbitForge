package com.orbitforge.mobile.github

import com.orbitforge.mobile.diagnostics.DiagnosticLog
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

data class SyncResult(val uploadedFiles: Int, val skippedFiles: Int, val branch: String, val commitSha: String)

data class GitHubRepository(
    val fullName: String,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val description: String
)

class GitHubRepositoryClient {
    private val http = OkHttpClient.Builder()
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
     * Uploads eligible workspace text files as a single Git tree + commit.
     * Secret-like files and oversized files are skipped. The caller must obtain user confirmation first.
     */
    suspend fun pushWorkspace(token: String, fullName: String, workspace: File): SyncResult {
        require(token.isNotBlank()) { "Сначала сохрани GitHub token." }
        require(fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
            "Укажи репозиторий в формате owner/name."
        }
        require(workspace.isDirectory) { "Рабочая папка не найдена." }

        val canonicalRoot = workspace.canonicalFile.path
        val candidates = workspace.walkTopDown()
            .filter { it.isFile }
            .filter { file -> runCatching { file.canonicalPath.startsWith(canonicalRoot + File.separator) }.getOrDefault(false) }
            .filterNot { isIgnoredPath(it.relativeTo(workspace).path) }
            .take(201)
            .toList()
        require(candidates.size <= 200) { "Слишком много файлов (лимит 200). Раздели проект на части." }

        val tree = JSONArray()
        var uploaded = 0
        var skipped = 0
        var totalBytes = 0L
        for (file in candidates) {
            if (file.length() > MAX_FILE_BYTES) { skipped++; continue }
            val rawBytes = try { file.readBytes() } catch (_: Exception) { skipped++; continue }
            if (rawBytes.any { it == 0.toByte() }) { skipped++; continue }
            val content = String(rawBytes, Charsets.UTF_8)
            if (!content.toByteArray(Charsets.UTF_8).contentEquals(rawBytes)) { skipped++; continue }
            if (looksLikeSecret(content)) { skipped++; continue }
            val bytes = rawBytes.size
            if (totalBytes + bytes > MAX_TOTAL_BYTES) { skipped++; continue }
            val relative = file.relativeTo(workspace).path.replace(File.separatorChar, '/')
            tree.put(JSONObject()
                .put("path", relative)
                .put("mode", "100644")
                .put("type", "blob")
                .put("content", content))
            uploaded++
            totalBytes += bytes
        }
        require(uploaded > 0) { "Нет подходящих файлов для отправки. Секреты, бинарные или слишком большие файлы пропускаются." }

        val repoInfo = JSONObject(execute(
            Request.Builder()
                .url("https://api.github.com/repos/$fullName")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get().build()
        ))
        val branch = repoInfo.optString("default_branch").ifBlank { "main" }
        val encodedBranch = URLEncoder.encode(branch, "UTF-8").replace("+", "%20")
        val branchInfo = JSONObject(execute(
            Request.Builder()
                .url("https://api.github.com/repos/$fullName/branches/$encodedBranch")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .get().build()
        ))
        val branchCommit = branchInfo.optJSONObject("commit")
        val parentSha = branchCommit?.optString("sha").orEmpty()
        val baseTreeSha = branchCommit?.optJSONObject("commit")?.optJSONObject("tree")?.optString("sha").orEmpty()
        require(parentSha.isNotBlank()) { "Не удалось определить последний commit репозитория." }
        require(baseTreeSha.isNotBlank()) { "Не удалось определить базовое дерево репозитория." }

        val treeBody = JSONObject().put("base_tree", baseTreeSha).put("tree", tree)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val treeResult = JSONObject(execute(
            Request.Builder()
                .url("https://api.github.com/repos/$fullName/git/trees")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(treeBody).build()
        ))
        val treeSha = treeResult.optString("sha")
        require(treeSha.isNotBlank()) { "GitHub не вернул SHA дерева." }

        val commitBody = JSONObject()
            .put("message", "Sync OrbitForge workspace")
            .put("tree", treeSha)
            .put("parents", JSONArray().put(parentSha))
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val commitResult = JSONObject(execute(
            Request.Builder()
                .url("https://api.github.com/repos/$fullName/git/commits")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .post(commitBody).build()
        ))
        val commitSha = commitResult.optString("sha")
        require(commitSha.isNotBlank()) { "GitHub не вернул SHA commit." }

        val updateBody = JSONObject().put("sha", commitSha).put("force", false)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        execute(
            Request.Builder()
                .url("https://api.github.com/repos/$fullName/git/refs/heads/$encodedBranch")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .patch(updateBody).build()
        )
        return SyncResult(uploaded, skipped, branch, commitSha)
    }

    private fun isIgnoredPath(path: String): Boolean {
        val normalized = path.replace(File.separatorChar, '/')
        val lower = normalized.lowercase()
        val name = lower.substringAfterLast('/')
        val segments = lower.split('/')
        val blockedDirectories = setOf(".git", ".gradle", "build", ".idea", ".ssh", ".cxx", "node_modules", ".venv", "__pycache__", "target", "dist")
        val blockedExtensions = setOf(".apk", ".aab", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key", ".crt", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".pdf", ".zip", ".jar", ".class", ".dex", ".so", ".mp4", ".mp3", ".ttf", ".otf", ".woff", ".woff2", ".db")
        return segments.any { it in blockedDirectories } ||
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
                            val body = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                val message = when (response.code) {
                                    401 -> "GitHub отклонил токен (401). Проверь токен."
                                    403 -> "GitHub запретил действие (403). Проверь права токена и лимиты."
                                    404 -> "GitHub API вернул 404."
                                    422 -> "GitHub не принял данные. Возможно, репозиторий с таким именем уже существует."
                                    else -> "GitHub API HTTP ${response.code}: ${body.take(300)}"
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
