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
        val args = TerminalCommandParser.parse(commandLine)
        val command = args.firstOrNull()?.lowercase().orEmpty()
        val params = args.drop(1)

        return when (command) {
            "help" -> HELP
            "pwd" -> root.absolutePath
            "ls" -> {
                require(params.size <= 1) { "Использование: ls [папка]" }
                val entries = tools.listFilesForAgent(params.firstOrNull().orEmpty())
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
                require(params.size == 2) { "Использование: grep <текст> <путь>" }
                WorkspaceTerminalGrep.search(params[0], params[1], tools::readFileForAgent)
            }
            "wc" -> {
                require(params.size == 1) { "Использование: wc <путь>" }
                val content = tools.readFileForAgent(params[0])
                "Строк: ${content.lineSequence().count()}, символов: ${content.length}, байт UTF-8: ${content.toByteArray(Charsets.UTF_8).size}"
            }
            "find" -> {
                require(params.size <= 1) { "Использование: find [папка]" }
                val start = tools.validatePath(params.firstOrNull().orEmpty())
                require(start.isDirectory) { "Путь не является папкой." }
                WorkspaceTerminalFind.list(root, start)
            }
            "mkdir" -> {
                require(params.size == 1) { "Использование: mkdir <путь>" }
                val directory = WorkspaceTerminalMutationPolicy.createDirectory(
                    root,
                    params[0],
                    tools.validatePath(params[0])
                )
                "Создана папка: ${params[0]}"
            }
            "touch" -> {
                require(params.size == 1) { "Использование: touch <путь>" }
                val file = WorkspaceTerminalMutationPolicy.createEmptyFile(
                    root,
                    params[0],
                    tools.validatePath(params[0])
                )
                "Файл готов: ${params[0]}"
            }
            "echo" -> params.joinToString(" ")
            "clear" -> "__CLEAR__"
            else -> "Команда '$command' не поддерживается. Напиши help. Произвольный shell не запускается."
        }
    }

    fun isMutating(commandLine: String): Boolean = TerminalCommandParser.isMutating(commandLine)

    companion object {
        private const val MAX_OUTPUT_CHARS = 20_000
        private val HELP = listOf(
            "Доступные команды OrbitForge (без системного shell):",
            "pwd — путь рабочей папки",
            "ls [папка] — список безопасных для агента файлов",
            "find [папка] — ограниченное дерево безопасных путей",
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
