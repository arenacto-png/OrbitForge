package com.orbitforge.mobile.mcp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolResultFormatterTest {
    @Test
    fun formatsStructuredObjectWhenTextContentIsAbsent() {
        val output = McpToolResultFormatter.format(
            JSONObject().put("structuredContent", JSONObject().put("count", 3).put("ready", true))
        )
        assertTrue(output.contains("\"count\":3"))
        assertTrue(output.contains("\"ready\":true"))
        assertTrue(output.startsWith("Structured result (JSON):"))
    }

    @Test
    fun includesBothTextAndStructuredContent() {
        val output = McpToolResultFormatter.format(
            JSONObject()
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Build complete")))
                .put("structuredContent", JSONObject().put("exitCode", 0))
        )
        assertTrue(output.contains("Build complete"))
        assertTrue(output.contains("\"exitCode\":0"))
    }

    @Test
    fun serializesScalarStructuredContentAsJson() {
        val output = McpToolResultFormatter.format(JSONObject().put("structuredContent", "ready"))
        assertTrue(output.endsWith("\"ready\""))
    }

    @Test
    fun redactsSecretsInsideStructuredContent() {
        val output = McpToolResultFormatter.format(
            JSONObject().put("structuredContent", JSONObject()
                .put("access_token", "mcp-structured-secret")
                .put("status", "ok"))
        )
        assertFalse(output.contains("mcp-structured-secret"))
        assertTrue(output.contains("[REDACTED]"))
        assertTrue(output.contains("\"status\":\"ok\""))
    }

    @Test
    fun omitsImageAudioPayloadsAndResourceLinkUris() {
        val output = McpToolResultFormatter.format(
            JSONObject().put("content", JSONArray()
                .put(JSONObject().put("type", "image").put("data", "private-image-base64"))
                .put(JSONObject().put("type", "audio").put("data", "private-audio-base64"))
                .put(JSONObject().put("type", "resource_link")
                    .put("name", "private report")
                    .put("uri", "https://example.test/report?access_token=private-uri-secret")))
        )
        assertTrue(output.contains("[Image payload omitted]"))
        assertTrue(output.contains("[Audio payload omitted]"))
        assertTrue(output.contains("private report"))
        assertFalse(output.contains("private-image-base64"))
        assertFalse(output.contains("private-audio-base64"))
        assertFalse(output.contains("example.test"))
        assertFalse(output.contains("private-uri-secret"))
    }

    @Test
    fun includesEmbeddedResourceTextButNeverItsUri() {
        val output = McpToolResultFormatter.format(
            JSONObject().put("content", JSONArray().put(
                JSONObject().put("type", "resource").put("resource",
                    JSONObject().put("uri", "file:///private/credentials.json")
                        .put("text", "Build output is ready")))
            )
        )
        assertTrue(output.contains("Build output is ready"))
        assertFalse(output.contains("credentials.json"))
        assertFalse(output.contains("file:///"))
    }

    @Test
    fun appliesOneBoundAcrossCombinedTextAndStructuredOutput() {
        val output = McpToolResultFormatter.format(
            JSONObject()
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "x".repeat(15_000))))
                .put("structuredContent", JSONObject().put("payload", "y".repeat(15_000)))
        )
        assertEquals(McpContentSanitizer.MAX_TOOL_OUTPUT_CHARS, output.length)
    }

    @Test
    fun prefixesToolErrorsAndRejectsMalformedResultFlags() {
        val output = McpToolResultFormatter.format(
            JSONObject().put("isError", true)
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "not found")))
        )
        assertTrue(output.startsWith("MCP tool reported an error:"))
        try {
            McpToolResultFormatter.format(JSONObject().put("isError", "true"))
            throw AssertionError("Expected malformed isError flag to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
