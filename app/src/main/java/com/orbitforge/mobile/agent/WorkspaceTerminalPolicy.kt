package com.orbitforge.mobile.agent

/**
 * Pure parsing and safety rules for the limited workspace command runner.
 * This is deliberately not a shell parser and never executes arbitrary processes.
 */
internal object WorkspaceTerminalPolicy {
    private val forbiddenSyntax = listOf(";", "&&", "||", "|", ">", "<", "`", "$(", "\n", "\r")

    fun validateSyntax(input: String) {
        require(!forbiddenSyntax.any { input.contains(it) }) {
            "Shell-операторы не поддерживаются. Команды выполняются по отдельности внутри рабочей папки."
        }
    }

    fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        val tokenPattern = Regex("\"([^\"]*)\"|'([^']*)'|(\\S+)")
        tokenPattern.findAll(input).forEach { match ->
            result += match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
        }
        return result
    }

    fun isMutating(commandLine: String): Boolean =
        tokenize(commandLine.trim()).firstOrNull()?.lowercase() in setOf("mkdir", "touch")
}
