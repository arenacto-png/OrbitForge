package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject

internal data class ParsedAgentToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject
)

/**
 * Validates the complete provider tool-call batch before any tool can execute.
 * Invalid or unknown calls fail closed instead of being silently converted to empty arguments.
 */
internal object AgentToolCallParser {
    const val MAX_TOOL_CALLS_PER_ROUND = 8
    const val MAX_ARGUMENT_CHARS = 20_000
    private const val MAX_CALL_ID_CHARS = 256
    private const val MAX_TOOL_NAME_CHARS = 128
    private val toolNamePattern = Regex("[A-Za-z0-9_-]{1,$MAX_TOOL_NAME_CHARS}")

    fun parse(message: JSONObject, allowedToolNames: Set<String>): List<ParsedAgentToolCall> {
        val rawCalls = message.opt("tool_calls")
        if (rawCalls == null || rawCalls == JSONObject.NULL) return emptyList()
        require(rawCalls is JSONArray) { "Поле tool_calls должно быть массивом." }
        if (rawCalls.length() == 0) return emptyList()
        require(rawCalls.length() <= MAX_TOOL_CALLS_PER_ROUND) {
            "Модель запросила слишком много инструментов за один шаг (лимит $MAX_TOOL_CALLS_PER_ROUND)."
        }

        val seenIds = mutableSetOf<String>()
        return buildList(rawCalls.length()) {
            for (index in 0 until rawCalls.length()) {
                val call = rawCalls.optJSONObject(index)
                    ?: throw IllegalArgumentException("Вызов инструмента #${index + 1} должен быть объектом.")

                val id = call.opt("id") as? String
                require(!id.isNullOrBlank() && id.length <= MAX_CALL_ID_CHARS &&
                    id.none { it.code < 0x20 || it.code == 0x7f }) {
                    "У вызова инструмента #${index + 1} отсутствует корректный идентификатор."
                }
                require(seenIds.add(id!!)) { "Ответ модели содержит повторяющийся идентификатор вызова инструмента." }

                val function = call.optJSONObject("function")
                    ?: throw IllegalArgumentException("У вызова инструмента #${index + 1} отсутствует объект function.")
                val name = function.opt("name") as? String
                require(!name.isNullOrBlank() && toolNamePattern.matches(name)) {
                    "У вызова инструмента #${index + 1} некорректное имя."
                }
                require(name in allowedToolNames) {
                    "Модель запросила неизвестный инструмент: $name."
                }

                val rawArguments = function.opt("arguments") as? String
                    ?: throw IllegalArgumentException("Аргументы инструмента $name должны быть JSON-строкой.")
                require(rawArguments.length <= MAX_ARGUMENT_CHARS) {
                    "Аргументы инструмента превышают лимит $MAX_ARGUMENT_CHARS символов."
                }
                val arguments = try {
                    JSONObject(rawArguments)
                } catch (_: Exception) {
                    throw IllegalArgumentException("Аргументы инструмента $name содержат некорректный JSON-объект.")
                }

                add(ParsedAgentToolCall(id, name, arguments))
            }
        }
    }
}
