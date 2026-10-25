package com.orbitforge.mobile.agent

import android.content.Context

/**
 * A deliberately limited, app-private workspace command runner.
 * It does not invoke /system/bin/sh and does not execute arbitrary shell syntax.
 */
class WorkspaceTerminal(context: Context) {
    private val tools = WorkspaceTools(context.applicationContext)
    private val root = tools.root

    fun execute(commandLine: String): String {
        val input = commandLine.trim()
        require(input.isNotBlank()) { "Введи команду. Напиши help для списка команд." }
        require(input.length <= 1000) { "Команда слишком длинная (лимит 1000 символов)." }
        WorkspaceTerminalPolicy.validateSyntax(input)
        val args = WorkspaceTerminalPolicy.tokenize(input)
        val command = args.firstOrNull()?.lowercase().orEmpty()
        val params = args.drop(1)

        return when (command) {
            "help" -> HELP
            "pwd" -> root.absolutePath
            "ls" -> {
                val entries = tools.listFiles(params.firstOrNull().orEmpty())
                if (entries.isEmpty()) "(папка пуста)" else entries.joinToString("\n") {
                    "${if (it.isDirectory) "DIR " else "FILE"} ${it.path}${if (it.isDirectory) "/" else " (${it.sizeBytes} B)"}"
                }
            }
            "cat" -> {
                require(params.size == 1) { "Использование: cat <путь>" }
                tools.readFileForAgent(params[0]).take(MAX_OUTPUT_CHARS)
            }
            "head" -> {
                require(params.isNotEmpty()) { "Использование: head <путь> [строки]" }
                val count = params.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 200) ?: 20
                tools.readFileForAgent(params[0]).lineSequence().take(count).joinToString("\n")
            }
            "grep" -> {
                require(params.size >= 2) { "Использование: grep <текст> <путь>" }
                val needle = params[0]
                tools.readFileForAgent(params[1]).lineSequence()
                    .mapIndexedNotNull { index, line -> if (line.contains(needle, ignoreCase = true)) "${index + 1}: $line" else null }
                    .take(100).joinToString("\n").ifBlank { "Совпадений не найдено." }
            }
            "wc" -> {
                require(params.size == 1) { "Использование: wc <путь>" }
                val content = tools.readFileForAgent(params[0])
                "Строк: ${content.lineSequence().count()}, символов: ${content.length}, байт UTF-8: ${content.toByteArray(Charsets.UTF_8).size}"
            }
            "find" -> {
                val start = tools.validatePath(params.firstOrNull().orEmpty())
                require(start.isDirectory) { "Путь не является папкой." }
                start.walkTopDown().take(201).map { it.relativeTo(root).path.ifBlank { "." } }
                    .joinToString("\n").let {
                        if (it.length > MAX_OUTPUT_CHARS) it.take(MAX_OUTPUT_CHARS) + "\n… вывод сокращён …" else it
                    }
            }
            "mkdir" -> {
                require(params.size == 1) { "Использование: mkdir <путь>" }
                val directory = tools.validatePath(params[0])
                require(directory.mkdirs() || directory.isDirectory) { "Не удалось создать папку." }
                "Создана папка: ${params[0]}"
            }
            "touch" -> {
                require(params.size == 1) { "Использование: touch <путь>" }
                val file = tools.validatePath(params[0])
                file.parentFile?.mkdirs()
                if (!file.exists()) require(file.createNewFile()) { "Не удалось создать файл." }
                require(file.isFile) { "Путь существует, но это не файл." }
                "Файл готов: ${params[0]}"
            }
            "echo" -> params.joinToString(" ")
            "clear" -> "__CLEAR__"
            else -> "Команда '$command' не поддерживается. Напиши help. Произвольный shell не запускается."
        }
    }

    fun isMutating(commandLine: String): Boolean = WorkspaceTerminalPolicy.isMutating(commandLine)

    companion object {
        private const val MAX_OUTPUT_CHARS = 20_000
        private val HELP = listOf(
            "Доступные команды OrbitForge (без системного shell):",
            "pwd — путь рабочей папки",
            "ls [папка] — список файлов",
            "find [папка] — дерево путей",
            "cat <файл> — прочитать файл",
            "head <файл> [N] — первые N строк",
            "grep <текст> <файл> — поиск текста",
            "wc <файл> — строки/символы/байты",
            "mkdir <папка> — создать папку",
            "touch <файл> — создать пустой файл",
            "echo <текст> — вывести текст",
            "clear — очистить вывод",
            "help — эта справка",
            "",
            "Все пути ограничены внутренней рабочей папкой приложения."
        ).joinToString("\n")
    }
}
