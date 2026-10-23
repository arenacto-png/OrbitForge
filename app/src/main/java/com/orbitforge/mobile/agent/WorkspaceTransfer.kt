package com.orbitforge.mobile.agent

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class TransferResult(val copied: Int, val skipped: Int, val bytes: Long)

class WorkspaceTransfer(private val context: Context) {
    private val root = File(context.filesDir, "orbitforge-workspace").apply { mkdirs() }

    fun importFrom(treeUri: Uri): TransferResult {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Не удалось открыть выбранную папку.")
        require(tree.isDirectory) { "Выбранный объект не является папкой." }
        var copied = 0
        var skipped = 0
        var totalBytes = 0L
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        tree.listFiles().take(MAX_FILES + 1).forEach { child ->
            val name = safeName(child.name)
            if (name == null || shouldSkip(name)) skipped++ else queue.add(child to name)
        }

        var visitedEntries = 0
        while (queue.isNotEmpty() && visitedEntries < MAX_FILES) {
            val (document, relative) = queue.removeFirst()
            visitedEntries++
            if (document.isDirectory) {
                val directory = safeTarget(relative)
                directory.mkdirs()
                document.listFiles().forEach { child ->
                    val name = safeName(child.name)
                    val childPath = if (name == null) null else "$relative/$name"
                    if (childPath == null || shouldSkip(childPath)) skipped++ else queue.add(child to childPath)
                }
                continue
            }
            if (!document.isFile || document.length > MAX_FILE_BYTES) { skipped++; continue }
            val target = safeTarget(relative)
            if (target.exists()) { skipped++; continue }
            target.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(document.uri)
            if (input == null) { skipped++; continue }
            try {
                val bytes = copyBounded(input, target.outputStream(), MAX_TOTAL_BYTES - totalBytes)
                totalBytes += bytes
                if (looksLikeSecretFile(target)) {
                    target.delete()
                    totalBytes -= bytes
                    skipped++
                } else {
                    copied++
                }
            } catch (e: Exception) {
                target.delete()
                skipped++
                if (totalBytes >= MAX_TOTAL_BYTES) break
            }
        }
        skipped += queue.size
        return TransferResult(copied, skipped, totalBytes)
    }

    fun exportTo(treeUri: Uri): TransferResult {
        val tree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalArgumentException("Не удалось открыть выбранную папку.")
        require(tree.isDirectory) { "Выбранный объект не является папкой." }
        val candidates = root.walkTopDown()
            .filter { it.isFile }
            .filterNot { shouldSkip(it.relativeTo(root).path) }
            .take(MAX_FILES + 1)
            .toList()
        require(candidates.size <= MAX_FILES) { "Слишком много файлов для экспорта (лимит $MAX_FILES)." }

        var copied = 0
        var skipped = 0
        var totalBytes = 0L
        for (file in candidates) {
            if (file.length() > MAX_FILE_BYTES || totalBytes + file.length() > MAX_TOTAL_BYTES) {
                skipped++
                continue
            }
            if (looksLikeSecretFile(file)) {
                skipped++
                continue
            }
            val relative = file.relativeTo(root).path.replace(File.separatorChar, '/')
            val parts = relative.split('/')
            var parent = tree
            var failed = false
            for (part in parts.dropLast(1)) {
                val existing = parent.findFile(part)
                val nextParent = when {
                    existing != null && existing.isDirectory -> existing
                    existing == null -> parent.createDirectory(part)
                    else -> null
                }
                if (nextParent == null) {
                    failed = true
                    break
                }
                parent = nextParent
            }
            if (failed) { skipped++; continue }
            val filename = parts.last()
            val existing = parent.findFile(filename)
            if (existing?.isDirectory == true) { skipped++; continue }
            if (existing != null && !existing.delete()) { skipped++; continue }
            val target = parent.createFile("application/octet-stream", filename)
            if (target == null) { skipped++; continue }
            val output = context.contentResolver.openOutputStream(target.uri, "wt")
            if (output == null) { target.delete(); skipped++; continue }
            try {
                val bytes = copyBounded(file.inputStream(), output, MAX_TOTAL_BYTES - totalBytes)
                totalBytes += bytes
                copied++
            } catch (_: Exception) {
                target.delete()
                skipped++
            }
        }
        return TransferResult(copied, skipped, totalBytes)
    }

    private fun safeTarget(relative: String): File {
        val target = File(root, relative).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator)) { "Путь выходит за пределы рабочей папки." }
        return target
    }

    private fun safeName(value: String?): String? {
        if (value.isNullOrBlank() || value == "." || value == ".." || value.contains('/') || value.contains('\\') || value.contains('\u0000')) return null
        return value.takeIf { it.length <= 180 }
    }

    private fun shouldSkip(path: String): Boolean {
        val normalized = path.replace(File.separatorChar, '/')
        val lower = normalized.lowercase()
        val name = lower.substringAfterLast('/')
        return lower.split('/').any { it in setOf(".git", ".gradle", "build", ".idea", ".cxx", ".ssh") } ||
            name in setOf("local.properties", "secrets.properties", ".env", ".env.local", "credentials.json", "service-account.json", "id_rsa", "id_ed25519") ||
            name.endsWith(".apk") || name.endsWith(".aab") || name.endsWith(".jks") ||
            name.endsWith(".keystore") || name.endsWith(".p12") || name.endsWith(".pfx") ||
            name.endsWith(".pem") || name.endsWith(".key") || name.endsWith(".crt")
    }

    private fun looksLikeSecretFile(file: File): Boolean {
        val textExtensions = setOf("kt", "kts", "java", "xml", "json", "yml", "yaml", "toml", "properties", "gradle", "md", "txt", "js", "ts", "py", "sh", "conf", "ini", "env")
        if (file.extension.lowercase() !in textExtensions && file.name !in setOf("Dockerfile", "Makefile", ".gitignore", ".editorconfig")) return false
        val content = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return true
        if (content.contains('\u0000') || content.contains('\uFFFD')) return true
        val patterns = listOf(
            Regex("(?i)(api[_-]?key|github[_-]?token|access[_-]?token|client[_-]?secret|password)\\s*[:=]\\s*[\"']?[^\\s\"']{12,}"),
            Regex("(?i)(sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|Bearer\\s+[A-Za-z0-9._~+/=-]{16,})")
        )
        return patterns.any { it.containsMatchIn(content) }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, allowedBytes: Long): Long {
        input.use { source ->
            output.use { sink ->
                val buffer = ByteArray(8 * 1024)
                var total = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= allowedBytes) { "Достигнут лимит передачи 5 МиБ." }
                    sink.write(buffer, 0, count)
                }
                sink.flush()
                return total
            }
        }
    }

    companion object {
        private const val MAX_FILES = 200
        private const val MAX_FILE_BYTES = 512 * 1024L
        private const val MAX_TOTAL_BYTES = 5 * 1024 * 1024L
    }
}
