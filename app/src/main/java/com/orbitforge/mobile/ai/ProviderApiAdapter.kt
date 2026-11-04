package com.orbitforge.mobile.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal data class ProviderRequestSpec(
    val url: String,
    val headers: Map<String, String>,
    val body: JSONObject
)

/**
 * Converts the app's internal OpenAI-shaped messages/tool calls to each provider's wire format.
 * API credentials are only returned as request headers and are never included in URLs or bodies.
 */
internal object ProviderApiAdapter {
    const val ANTHROPIC_VERSION = "2023-06-01"
    const val ANTHROPIC_MAX_TOKENS = 4096

    fun chatRequest(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ApiMessage>,
        stream: Boolean
    ): ProviderRequestSpec {
        val body = when (config.apiFormat) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> JSONObject()
                .put("model", config.model)
                .put("stream", stream)
                .put("messages", JSONArray().apply {
                    messages.forEach { message ->
                        put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                })
            ProviderApiFormat.ANTHROPIC_MESSAGES -> anthropicChatBody(config, messages, stream)
        }
        return requestSpec(config, apiKey, body)
    }

    fun agentRequest(
        config: ProviderConfig,
        apiKey: String,
        history: JSONArray,
        availableTools: JSONArray
    ): ProviderRequestSpec {
        val body = when (config.apiFormat) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> JSONObject()
                .put("model", config.model)
                .put("messages", history)
                .put("tools", availableTools)
                .put("tool_choice", "auto")
                .put("stream", false)
            ProviderApiFormat.ANTHROPIC_MESSAGES -> anthropicAgentBody(config, history, availableTools)
        }
        return requestSpec(config, apiKey, body)
    }

    fun parseStreamText(format: ProviderApiFormat, event: JSONObject): String? {
        return when (format) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> {
                val choices = event.optJSONArray("choices") ?: return null
                val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return null
                (delta.opt("content") as? String)?.takeIf { it.isNotEmpty() }
            }
            ProviderApiFormat.ANTHROPIC_MESSAGES -> {
                if (event.optString("type") == "error") {
                    throw IOException("Провайдер вернул ошибку потокового ответа.")
                }
                val delta = event.optJSONObject("delta")
                if (event.optString("type") == "content_block_delta" &&
                    delta?.optString("type") == "text_delta"
                ) {
                    delta.optString("text").takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        }
    }

    fun isStreamFinished(format: ProviderApiFormat, event: JSONObject): Boolean =
        when (format) {
            ProviderApiFormat.OPENAI_COMPATIBLE ->
                event.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason") == "stop"
            ProviderApiFormat.ANTHROPIC_MESSAGES -> event.optString("type") == "message_stop"
        }

    fun normalizeAgentResponse(format: ProviderApiFormat, response: JSONObject): JSONObject =
        when (format) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> response
            ProviderApiFormat.ANTHROPIC_MESSAGES -> normalizeAnthropicAgentResponse(response)
        }

    private fun requestSpec(
        config: ProviderConfig,
        apiKey: String,
        body: JSONObject
    ): ProviderRequestSpec {
        val headers = when (config.apiFormat) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )
            ProviderApiFormat.ANTHROPIC_MESSAGES -> mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
                "Content-Type" to "application/json"
            )
        }
        return ProviderRequestSpec(config.requestUrl(), headers, body)
    }

    private fun anthropicChatBody(
        config: ProviderConfig,
        messages: List<ApiMessage>,
        stream: Boolean
    ): JSONObject {
        val system = messages.filter { it.role == "system" }
            .map { it.content }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        val converted = JSONArray()
        messages.filter { it.role != "system" }.forEach { message ->
            require(message.role == "user" || message.role == "assistant") {
                "Anthropic chat поддерживает только роли user и assistant в истории."
            }
            converted.put(JSONObject().put("role", message.role).put("content", message.content))
        }
        require(converted.length() > 0) { "Для Anthropic требуется хотя бы одно сообщение user или assistant." }
        return JSONObject()
            .put("model", config.model)
            .put("max_tokens", ANTHROPIC_MAX_TOKENS)
            .put("messages", converted)
            .put("stream", stream)
            .apply { if (system.isNotBlank()) put("system", system) }
    }

    private fun anthropicAgentBody(
        config: ProviderConfig,
        history: JSONArray,
        availableTools: JSONArray
    ): JSONObject {
        val systemParts = mutableListOf<String>()
        val messages = JSONArray()
        for (index in 0 until history.length()) {
            val message = history.optJSONObject(index)
                ?: throw IOException("История агента содержит некорректное сообщение.")
            when (message.optString("role")) {
                "system" -> {
                    val content = message.opt("content") as? String ?: ""
                    if (content.isNotBlank()) systemParts += content
                }
                "user" -> {
                    val content = message.opt("content") as? String ?: ""
                    messages.put(JSONObject().put("role", "user").put("content", content))
                }
                "assistant" -> appendAnthropicAssistantMessage(messages, message)
                "tool" -> appendAnthropicToolResult(messages, message)
                else -> throw IOException("История агента содержит неподдерживаемую роль.")
            }
        }
        require(messages.length() > 0) { "Для Anthropic требуется хотя бы одно сообщение." }

        val body = JSONObject()
            .put("model", config.model)
            .put("max_tokens", ANTHROPIC_MAX_TOKENS)
            .put("messages", messages)
            .put("stream", false)
        val system = systemParts.joinToString("\n\n")
        if (system.isNotBlank()) body.put("system", system)

        if (availableTools.length() > 0) {
            body.put("tools", anthropicTools(availableTools))
            body.put("tool_choice", JSONObject().put("type", "auto"))
        }
        return body
    }

    private fun appendAnthropicAssistantMessage(messages: JSONArray, message: JSONObject) {
        val text = message.opt("content") as? String ?: ""
        val calls = message.optJSONArray("tool_calls")
        if (calls == null || calls.length() == 0) {
            messages.put(JSONObject().put("role", "assistant").put("content", text))
            return
        }

        val blocks = JSONArray()
        if (text.isNotBlank()) blocks.put(JSONObject().put("type", "text").put("text", text))
        for (index in 0 until calls.length()) {
            val call = calls.optJSONObject(index)
                ?: throw IOException("История агента содержит некорректный вызов инструмента.")
            require(call.optString("type") == "function") {
                "История агента содержит вызов инструмента неизвестного типа."
            }
            val function = call.optJSONObject("function")
                ?: throw IOException("Вызов инструмента не содержит function.")
            val id = call.optString("id")
            val name = function.optString("name")
            require(id.isNotBlank() && name.isNotBlank()) {
                "Вызов инструмента не содержит ID или имя."
            }
            val rawArguments = function.opt("arguments")
            val input = when (rawArguments) {
                is JSONObject -> rawArguments
                is String -> try {
                    JSONObject(rawArguments)
                } catch (_: Exception) {
                    throw IOException("Аргументы инструмента в истории не являются JSON-объектом.")
                }
                else -> throw IOException("Аргументы инструмента в истории должны быть JSON-объектом.")
            }
            blocks.put(
                JSONObject()
                    .put("type", "tool_use")
                    .put("id", id)
                    .put("name", name)
                    .put("input", input)
            )
        }
        messages.put(JSONObject().put("role", "assistant").put("content", blocks))
    }

    private fun appendAnthropicToolResult(messages: JSONArray, message: JSONObject) {
        val toolCallId = message.optString("tool_call_id")
        require(toolCallId.isNotBlank()) { "Результат инструмента не содержит tool_call_id." }
        val rawContent = message.opt("content")
        val resultText = if (rawContent == null || rawContent === JSONObject.NULL) "" else rawContent.toString()
        val resultBlock = JSONObject()
            .put("type", "tool_result")
            .put("tool_use_id", toolCallId)
            .put("content", resultText)

        val last = messages.optJSONObject(messages.length() - 1)
        if (last?.optString("role") == "user") {
            val existing = last.opt("content")
            val blocks = when (existing) {
                is JSONArray -> existing
                is String -> JSONArray().put(JSONObject().put("type", "text").put("text", existing))
                else -> JSONArray()
            }
            blocks.put(resultBlock)
            last.put("content", blocks)
        } else {
            messages.put(JSONObject().put("role", "user").put("content", JSONArray().put(resultBlock)))
        }
    }

    private fun anthropicTools(openAiTools: JSONArray): JSONArray = JSONArray().apply {
        for (index in 0 until openAiTools.length()) {
            val tool = openAiTools.optJSONObject(index)
                ?: throw IOException("Определение инструмента должно быть объектом.")
            val function = tool.optJSONObject("function")
                ?: throw IOException("Определение инструмента не содержит function.")
            val name = function.optString("name")
            require(name.isNotBlank()) { "Определение инструмента не содержит имени." }
            val inputSchema = function.optJSONObject("parameters")
                ?: throw IOException("Определение инструмента не содержит JSON Schema parameters.")
            put(
                JSONObject()
                    .put("name", name)
                    .put("description", function.optString("description"))
                    .put("input_schema", inputSchema)
            )
        }
    }

    private fun normalizeAnthropicAgentResponse(response: JSONObject): JSONObject {
        val content = response.optJSONArray("content")
            ?: throw IOException("Ответ Anthropic не содержит content.")
        val text = StringBuilder()
        val calls = JSONArray()
        for (index in 0 until content.length()) {
            val block = content.optJSONObject(index)
                ?: throw IOException("Ответ Anthropic содержит некорректный content block.")
            when (block.optString("type")) {
                "text" -> text.append(block.optString("text"))
                "tool_use" -> {
                    val id = block.optString("id")
                    val name = block.optString("name")
                    val input = block.optJSONObject("input")
                        ?: throw IOException("Вызов инструмента Anthropic должен содержать input-объект.")
                    require(id.isNotBlank() && name.isNotBlank()) {
                        "Вызов инструмента Anthropic не содержит ID или имени."
                    }
                    calls.put(
                        JSONObject()
                            .put("id", id)
                            .put("type", "function")
                            .put(
                                "function",
                                JSONObject().put("name", name).put("arguments", input.toString())
                            )
                    )
                }
                "thinking", "redacted_thinking" -> Unit
                else -> throw IOException("Ответ Anthropic содержит неподдерживаемый content block.")
            }
        }
        if (response.optString("stop_reason") == "tool_use" && calls.length() == 0) {
            throw IOException("Anthropic сообщил tool_use, но не вернул вызов инструмента.")
        }
        val message = JSONObject()
            .put("role", "assistant")
            .put("content", if (text.isNotEmpty()) text.toString() else JSONObject.NULL)
        if (calls.length() > 0) message.put("tool_calls", calls)
        return JSONObject().put("choices", JSONArray().put(JSONObject().put("message", message)))
    }
}
