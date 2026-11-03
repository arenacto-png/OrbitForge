package com.orbitforge.mobile.agent

/**
 * Parses the limited workspace-terminal command line without invoking a shell.
 *
 * Quotes group arguments and may be concatenated with unquoted text. Shell control
 * characters are rejected only outside quotes; inside quotes they are ordinary data.
 * There is intentionally no escape, expansion, substitution, or comment syntax.
 */
internal object TerminalCommandParser {
    const val MAX_COMMAND_CHARS = 1_000

    fun parse(commandLine: String): List<String> {
        require(commandLine.length <= MAX_COMMAND_CHARS) {
            "Команда слишком длинная (лимит 1000 символов)."
        }
        require(!commandLine.contains('\n') && !commandLine.contains('\r')) {
            "Переносы строк в команде не поддерживаются."
        }
        val input = commandLine.trim()
        require(input.isNotBlank()) { "Введи команду. Напиши help для списка команд." }

        val result = mutableListOf<String>()
        val token = StringBuilder()
        var tokenStarted = false
        var quote: Char? = null
        var index = 0

        while (index < input.length) {
            val char = input[index]
            require(char != '\n' && char != '\r') {
                "Переносы строк в команде не поддерживаются."
            }

            val activeQuote = quote
            if (activeQuote != null) {
                if (char == activeQuote) {
                    quote = null
                } else {
                    token.append(char)
                }
                tokenStarted = true
                index++
                continue
            }

            when {
                char.isWhitespace() -> {
                    if (tokenStarted) {
                        result += token.toString()
                        token.setLength(0)
                        tokenStarted = false
                    }
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    tokenStarted = true
                }
                char in SHELL_CONTROL_CHARACTERS -> {
                    throw IllegalArgumentException(
                        "Shell-операторы не поддерживаются. Команды выполняются по отдельности внутри рабочей папки."
                    )
                }
                char == '$' && input.getOrNull(index + 1) == '(' -> {
                    throw IllegalArgumentException(
                        "Подстановка команд не поддерживается. Произвольный shell не запускается."
                    )
                }
                char == '\\' -> {
                    throw IllegalArgumentException(
                        "Экранирование через обратную косую черту не поддерживается; используй кавычки для аргументов."
                    )
                }
                else -> token.append(char)
            }
            index++
        }

        require(quote == null) { "Незакрытая кавычка в команде." }
        if (tokenStarted) result += token.toString()
        return result
    }

    fun isMutating(commandLine: String): Boolean =
        runCatching {
            parse(commandLine).firstOrNull()?.lowercase() in MUTATING_COMMANDS
        }.getOrDefault(false)

    private val MUTATING_COMMANDS = setOf("mkdir", "touch")
    private val SHELL_CONTROL_CHARACTERS = setOf(';', '&', '|', '<', '>', '\u0060')
}
