package com.orbitforge.mobile.mcp

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpResponseBodyReaderTest {
    @Test
    fun readsUtf8BodyWithinLimit() {
        val payload = """{"message":"Привет, OrbitForge"}"""

        assertEquals(payload, McpResponseBodyReader.read(ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8))))
    }

    @Test
    fun acceptsBodyExactlyAtLimit() {
        val payload = "x".repeat(32)

        assertEquals(payload, McpResponseBodyReader.read(ByteArrayInputStream(payload.toByteArray()), maxBytes = 32))
    }

    @Test
    fun rejectsBodyOneByteOverLimitInsteadOfTruncating() {
        val payload = "x".repeat(33)

        try {
            McpResponseBodyReader.read(ByteArrayInputStream(payload.toByteArray()), maxBytes = 32)
            throw AssertionError("Expected an oversized MCP response to be rejected")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("32"))
        }
    }

    @Test
    fun rejectsNonPositiveLimit() {
        try {
            McpResponseBodyReader.read(ByteArrayInputStream(byteArrayOf(1)), maxBytes = 0)
            throw AssertionError("Expected a non-positive response limit to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
