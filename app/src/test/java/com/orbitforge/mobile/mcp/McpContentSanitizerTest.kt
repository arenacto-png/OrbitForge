package com.orbitforge.mobile.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpContentSanitizerTest {
    @Test
    fun redactsCredentialFieldsAndAuthorizationHeadersFromToolOutput() {
        val input = """{"access_token":"mcp-access-secret","model":"build"}\nAuthorization: Bearer mcp-bearer-secret-value"""
        val safe = McpContentSanitizer.toolOutput(input)

        assertFalse(safe.contains("mcp-access-secret"))
        assertFalse(safe.contains("mcp-bearer-secret-value"))
        assertTrue(safe.contains("build"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun redactsCookieHeaderIncludingAttributesFromToolOutput() {
        val safe = McpContentSanitizer.toolOutput("Set-Cookie: session=mcp-cookie-secret; Path=/; HttpOnly")

        assertFalse(safe.contains("mcp-cookie-secret"))
        assertFalse(safe.contains("Path=/"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun capsToolOutputAfterRedaction() {
        val safe = McpContentSanitizer.toolOutput("x".repeat(McpContentSanitizer.MAX_TOOL_OUTPUT_CHARS + 500))

        assertEquals(McpContentSanitizer.MAX_TOOL_OUTPUT_CHARS, safe.length)
    }

    @Test
    fun redactsAndNormalizesBoundedRemoteErrorDetails() {
        val safe = McpContentSanitizer.errorDetail(
            "request failed\nAuthorization: Bearer mcp-error-secret-value\nretry later",
            "no details"
        )

        assertFalse(safe.contains("mcp-error-secret-value"))
        assertFalse(safe.contains("\n"))
        assertTrue(safe.contains("request failed"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test
    fun capsErrorDetailsAndUsesFallbackForBlankInput() {
        val safe = McpContentSanitizer.errorDetail("e".repeat(500), "no details")

        assertEquals(McpContentSanitizer.MAX_ERROR_DETAIL_CHARS, safe.length)
        assertEquals("no details", McpContentSanitizer.errorDetail("  ", "no details"))
    }
}
