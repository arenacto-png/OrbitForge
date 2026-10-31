package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the parser allowlist from the exact function definitions sent to the provider.
 * This keeps newly added local or remote tools in sync with response validation.
 */
internal object AgentToolDefinitionNames {
    private const val MAX_TOOL_NAME_CHARS = 128
    private val toolNamePattern = Regex("[A-Za-z0-9_-]{1,$MAX_TOOL_NAME_CHARS}")

    fun from(definitions: JSONArray): Set<String> {
        require(definitions.length() > 0) { "Список инструментов не должен быть пустым." }
        val names = linkedSetOf<String>()
        for (index in 0 until definitions.length()) {
            val definition = definitions.optJSONObject(index)
                ?: throw IllegalArgumentException("Определение инструмента #${index + 1} должно быть объектом.")
            require(definition.opt("type") == "function") {
                "Определение инструмента #${index + 1} должно иметь тип function."
            }
            val function = definition.optJSONObject("function")
                ?: throw IllegalArgumentException("У определения инструмента #${index + 1} отсутствует function.")
            val name = function.opt("name") as? String
            require(name != null && toolNamePattern.matches(name)) {
                "У определения инструмента #${index + 1} некорректное имя."
            }
            require(names.add(name!!)) { "Список инструментов содержит повторяющееся имя." }
        }
        return names
    }
}
