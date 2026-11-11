package com.orbitforge.mobile.ai

import com.orbitforge.mobile.agent.AgentToolCallParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderApiAdapterTest {
    @Test
    fun openAiCompatibleRequestKeepsBearerAuthAndChatCompletionsShape() {
        val spec = ProviderApiAdapter.chatRequest(
            ProviderConfig("https://example.com/v1", "demo-model"),
            "test-key",
            listOf(ApiMessage("user", "hello")),
            stream = true
        )

        assertEquals("https://example.com/v1/chat/completions", spec.url)
        assertEquals("Bearer test-key", spec.headers["Authorization"])
        assertTrue(spec.body.getBoolean("stream"))
        assertEquals("hello", spec.body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun anthropicChatRequestSeparatesSystemAndUsesAnthropicHeaders() {
        val spec = ProviderApiAdapter.chatRequest(
            ProviderConfig(
                "https://api.anthropic.com",
                "claude-demo",
                ProviderApiFormat.ANTHROPIC_MESSAGES
            ),
            "anthropic-test-key",
            listOf(ApiMessage("system", "Be concise."), ApiMessage("user", "hello")),
            stream = true
        )

        assertEquals("https://api.anthropic.com/v1/messages", spec.url)
        assertEquals("anthropic-test-key", spec.headers["x-api-key"])
        assertEquals(ProviderApiAdapter.ANTHROPIC_VERSION, spec.headers["anthropic-version"])
        assertFalse(spec.headers.containsKey("Authorization"))
        assertEquals("Be concise.", spec.body.getString("system"))
        assertEquals(ProviderApiAdapter.ANTHROPIC_MAX_TOKENS, spec.body.getInt("max_tokens"))
        assertEquals("hello", spec.body.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test
    fun anthropicAgentRequestTranslatesFunctionSchemaAndToolResultHistory() {
        val history = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "Work safely."))
            .put(JSONObject().put("role", "user").put("content", "List files."))
            .put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", JSONObject.NULL)
                    .put(
                        "tool_calls",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "call-1")
                                .put("type", "function")
                                .put(
                                    "function",
                                    JSONObject().put("name", "list_files").put("arguments", "{}")
                                )
                        )
                    )
            )
            .put(
                JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", "call-1")
                    .put("content", "FILE README.md")
            )
        val tools = JSONArray().put(
            JSONObject().put("type", "function").put(
                "function",
                JSONObject()
                    .put("name", "list_files")
                    .put("description", "List workspace files.")
                    .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()))
            )
        )

        val spec = ProviderApiAdapter.agentRequest(
            ProviderConfig("https://api.anthropic.com/v1", "claude-demo", ProviderApiFormat.ANTHROPIC_MESSAGES),
            "anthropic-test-key",
            history,
            tools
        )

        assertEquals("https://api.anthropic.com/v1/messages", spec.url)
        assertEquals("Work safely.", spec.body.getString("system"))
        assertEquals("auto", spec.body.getJSONObject("tool_choice").getString("type"))
        val messages = spec.body.getJSONArray("messages")
        assertEquals(3, messages.length())
        val assistantBlocks = messages.getJSONObject(1).getJSONArray("content")
        assertEquals("tool_use", assistantBlocks.getJSONObject(0).getString("type"))
        assertEquals("list_files", assistantBlocks.getJSONObject(0).getString("name"))
        val resultBlocks = messages.getJSONObject(2).getJSONArray("content")
        assertEquals("tool_result", resultBlocks.getJSONObject(0).getString("type"))
        assertEquals("call-1", resultBlocks.getJSONObject(0).getString("tool_use_id"))
        assertEquals("list_files", spec.body.getJSONArray("tools").getJSONObject(0).getString("name"))
        assertTrue(spec.body.getJSONArray("tools").getJSONObject(0).has("input_schema"))
    }

    @Test
    fun normalizesAnthropicToolUseForStrictAgentToolParser() {
        val response = JSONObject()
            .put(
                "content",
                JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "I will inspect the workspace."))
                    .put(
                        JSONObject()
                            .put("type", "tool_use")
                            .put("id", "toolu_1")
                            .put("name", "list_files")
                            .put("input", JSONObject().put("path", ""))
                    )
            )
            .put("stop_reason", "tool_use")

        val normalized = ProviderApiAdapter.normalizeAgentResponse(
            ProviderApiFormat.ANTHROPIC_MESSAGES,
            response
        )
        val message = normalized.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        assertEquals("I will inspect the workspace.", message.getString("content"))
        val parsed = AgentToolCallParser.parse(message, setOf("list_files"))
        assertEquals(1, parsed.size)
        assertEquals("toolu_1", parsed.single().id)
        assertEquals("", parsed.single().arguments.getString("path"))
    }

    @Test
    fun openAiFinishReasonDoesNotStopBeforeTrailingUsageChunk() {
        val finish = JSONObject()
            .put("choices", JSONArray().put(JSONObject().put("finish_reason", "stop")))
        val usage = JSONObject().put("usage", JSONObject().put("total_tokens", 7))

        assertFalse(ProviderApiAdapter.isStreamFinished(ProviderApiFormat.OPENAI_COMPATIBLE, finish))
        assertFalse(ProviderApiAdapter.isStreamFinished(ProviderApiFormat.OPENAI_COMPATIBLE, usage))
    }

    @Test
    fun parsesAnthropicTextDeltaAndRecognizesMessageStop() {
        val delta = JSONObject()
            .put("type", "content_block_delta")
            .put("delta", JSONObject().put("type", "text_delta").put("text", "hello"))
        assertEquals("hello", ProviderApiAdapter.parseStreamText(ProviderApiFormat.ANTHROPIC_MESSAGES, delta))
        assertTrue(
            ProviderApiAdapter.isStreamFinished(
                ProviderApiFormat.ANTHROPIC_MESSAGES,
                JSONObject().put("type", "message_stop")
            )
        )
    }

    @Test
    fun rejectsAnthropicToolInputThatIsNotAnObject() {
        val response = JSONObject()
            .put(
                "content",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", "toolu_1")
                        .put("name", "list_files")
                        .put("input", JSONArray())
                )
            )
            .put("stop_reason", "tool_use")
        try {
            ProviderApiAdapter.normalizeAgentResponse(ProviderApiFormat.ANTHROPIC_MESSAGES, response)
            throw AssertionError("Expected malformed Anthropic tool input to be rejected")
        } catch (_: java.io.IOException) {
            // Expected: tool arguments must be objects before they reach the shared validator.
        }
    }
}
