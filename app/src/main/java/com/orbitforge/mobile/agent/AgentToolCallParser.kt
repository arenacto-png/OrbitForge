package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Strictly validates the untrusted tool-call envelope returned by a model before dispatch.
 * Malformed arguments fail the agent step instead of being silently replaced with an empty object.
 */
data class AgentToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

object AgentToolCallParser {
    const val DEFAULT_MAX_CALLS = 8
    const val DEFAULT_MAX_ARGUMENT_CHARS = 20_000

    fun parse(
        toolCalls: JSONArray?,
        maxCalls: Int = DEFAULT_MAX_CALLS,
        maxArgumentChars: Int = DEFAULT_MAX_ARGUMENT_CHARS
    ): List<AgentToolCall> {
        if (toolCalls == null || toolCalls.length() == 0) return emptyList()
        require(maxCalls > 0) { "Лимит вызовов инструментов должен быть положительным." }
        require(maxArgumentChars > 0) { "Лимит аргументов должен быть положительным." }
        require(toolCalls.length() <= maxCalls) {
            "Модель запросила слишком много инструментов за один шаг (лимит $maxCalls)."
        }

        return (0 until toolCalls.length()).map { index ->
            val call = toolCalls.optJSONObject(index)
                ?: throw IllegalArgumentException("Вызов инструмента #${index + 1} имеет неверный формат.")
            val id = call.optString("id").trim()
            require(id.isNotEmpty() && id.length <= 200) {
                "Вызов инструмента #${index + 1} содержит некорректный идентификатор."
            }
            val function = call.optJSONObject("function")
                ?: throw IllegalArgumentException("Вызов инструмента '$id' не содержит function.")
            val name = function.optString("name").trim()
            require(name.matches(Regex("[A-Za-z0-9_.:-]{1,128}"))) {
                "Вызов инструмента '$id' содержит некорректное имя."
            }
            val rawArguments = function.opt("arguments")
            require(rawArguments is String) {
                "Аргументы инструмента '$name' должны быть JSON-строкой."
            }
            require(rawArguments.length <= maxArgumentChars) {
                "Аргументы инструмента '$name' превышают лимит."
            }
            val arguments = try {
                JSONObject(rawArguments)
            } catch (_: Exception) {
                throw IllegalArgumentException("Аргументы инструмента '$name' содержат некорректный JSON.")
            }
            AgentToolCall(id = id, name = name, arguments = arguments)
        }
    }
}
