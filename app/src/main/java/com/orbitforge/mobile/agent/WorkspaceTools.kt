package com.orbitforge.mobile.agent

import android.content.Context
import java.io.File

data class WorkspaceEntry(val path: String, val isDirectory: Boolean, val sizeBytes: Long)

class WorkspaceTools(context: Context) {
    val root: File = File(context.filesDir, "orbitforge-workspace").apply { mkdirs() }
    private val pathPolicy = WorkspacePathPolicy(root)

    fun listFiles(relativePath: String = ""): List<WorkspaceEntry> {
        val directory = resolve(relativePath)
        require(directory.isDirectory) { "Путь не является папкой." }
        return directory.listFiles().orEmpty()
            .filter { file ->
                runCatching {
                    val canonical = file.canonicalFile
                    canonical == root.canonicalFile || canonical.path.startsWith(root.canonicalPath + File.separator)
                }.getOrDefault(false)
            }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            .take(200)
            .map { file ->
                WorkspaceEntry(
                    path = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/'),
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L
                )
            }
    }

    fun readFile(relativePath: String): String {
        val file = resolve(relativePath)
        require(file.isFile) { "Файл не найден." }
        require(file.length() <= 512 * 1024) { "Файл больше лимита чтения 512 КБ." }
        return file.readText(Charsets.UTF_8)
    }

    /** Bounded file reader for model context; the user editor can still read the full file. */
    fun readFileForAgent(relativePath: String): String {
        require(!isSensitivePath(relativePath)) { "Чтение секретных файлов через инструменты агента запрещено." }
        val content = readFile(relativePath)
        require(!looksLikeSecretContent(content)) { "Файл похож на содержащий секреты; доступ агента заблокирован." }
        return if (content.length <= 40_000) content
        else content.take(40_000) + "\n\n[Файл сокращён для контекста агента; полная версия доступна в редакторе.]"
    }

    fun writeFile(relativePath: String, content: String): String {
        require(!isSensitivePath(relativePath)) { "Запись в секретные файлы через агента запрещена." }
        require(!looksLikeSecretContent(content)) { "Содержимое похоже на секрет или токен; запись агентом заблокирована." }
        val file = resolve(relativePath)
        require(content.toByteArray(Charsets.UTF_8).size <= 10 * 1024) { "Файл больше лимита записи 10 КБ. Разбей его на несколько небольших файлов." }
        file.parentFile?.mkdirs()
        AtomicWorkspaceFileWriter.write(file, content)
        return "Записано ${content.toByteArray(Charsets.UTF_8).size} байт в $relativePath"
    }

    /** User-initiated editor save; still workspace-confined, but allows larger files than agent tool writes. */
    fun writeFileFromUser(relativePath: String, content: String): String {
        val file = resolve(relativePath)
        require(content.toByteArray(Charsets.UTF_8).size <= 512 * 1024) { "Файл больше лимита редактора 512 КБ." }
        file.parentFile?.mkdirs()
        AtomicWorkspaceFileWriter.write(file, content)
        return "Сохранено ${content.toByteArray(Charsets.UTF_8).size} байт в $relativePath"
    }


    fun searchText(query: String, relativePath: String = ""): String {
        require(query.isNotBlank()) { "Укажи текст для поиска." }
        require(query.length <= 200) { "Поисковый запрос слишком длинный." }
        require(!isSensitivePath(relativePath)) { "Поиск по секретным файлам через инструменты агента запрещён." }
        val target = resolve(relativePath)
        require(target.exists()) { "Путь не найден." }
        val candidates = if (target.isFile) sequenceOf(target) else target.walkTopDown().filter { it.isFile }
        val matches = mutableListOf<String>()
        var inspected = 0
        for (file in candidates) {
            if (inspected >= 200 || matches.size >= 100) break
            val relativePath = root.toPath().relativize(file.toPath()).toString()
            if (isSensitivePath(relativePath)) continue
            val canonical = file.canonicalFile
            if (canonical != root.canonicalFile && !canonical.path.startsWith(root.canonicalPath + File.separator)) continue
            if (file.length() > 512 * 1024) continue
            inspected++
            val fileContent = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { continue }
            if (looksLikeSecretContent(fileContent)) continue
            val lines = fileContent.lineSequence().toList()
            lines.forEachIndexed { index, line ->
                if (matches.size < 100 && line.contains(query, ignoreCase = true)) {
                    val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                    matches.add("$relative:${index + 1}: ${line.take(240)}")
                }
            }
        }
        return if (matches.isEmpty()) "Совпадений не найдено (проверено файлов: $inspected)." else matches.joinToString("\n")
    }

    private fun looksLikeSecretContent(content: String): Boolean {
        val patterns = listOf(
            Regex("(?i)(api[_-]?key|access[_-]?token|github[_-]?token|client[_-]?secret|password)\\s*[:=]\\s*[\"']?[A-Za-z0-9_./+=-]{20,}"),
            Regex("(?i)(sk-[A-Za-z0-9_-]{12,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})"),
            Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{20,}")
        )
        return patterns.any { it.containsMatchIn(content) }
    }

    private fun isSensitivePath(relativePath: String): Boolean {
        val normalized = relativePath.replace('\\', '/').lowercase()
        val name = normalized.substringAfterLast('/')
        return name.startsWith(".env") || name in setOf(
            "local.properties", "secrets.properties", "credentials.json", "id_rsa", "id_ed25519"
        ) || name.endsWith(".jks") || name.endsWith(".keystore") || name.endsWith(".p12") ||
            name.endsWith(".pfx") || name.endsWith(".pem") || name.endsWith(".key") ||
            normalized.split('/').any { it == ".ssh" }
    }

    fun rootPath(): String = root.absolutePath

    /** Validates a relative path and returns its canonical in-workspace target. */
    fun validatePath(relativePath: String): File = resolve(relativePath)

    private fun resolve(relativePath: String): File = pathPolicy.resolve(relativePath)
}
