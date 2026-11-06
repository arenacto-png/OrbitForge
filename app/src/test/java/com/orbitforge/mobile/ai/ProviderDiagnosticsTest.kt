package com.orbitforge.mobile.ai

import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDiagnosticsTest {
    @Test
    fun mapsCommonHttpStatusesToActionableGuidance() {
        assertTrue(ProviderErrorMessages.forHttpStatus(401).contains("ключ"))
        assertTrue(ProviderErrorMessages.forHttpStatus(404).contains("URL"))
        assertTrue(ProviderErrorMessages.forHttpStatus(413).contains("слишком большой"))
        assertTrue(ProviderErrorMessages.forHttpStatus(429).contains("квоту"))
        assertTrue(ProviderErrorMessages.forHttpStatus(503).contains("стороне провайдера"))
    }

    @Test
    fun includesOnlyBoundedRedactedDetailsForRequestErrors() {
        val secret = "should-not-be-shown"
        val body = """{"message":"invalid model","authorization":"Bearer $secret"}"""
        val message = ProviderErrorMessages.forHttpStatus(400, body)

        assertTrue(message.contains("invalid model"))
        assertFalse(message.contains(secret))
        assertFalse(ProviderErrorMessages.forHttpStatus(503, "internal detail").contains("internal detail"))
        assertFalse(ProviderErrorMessages.forHttpStatus(400, "x".repeat(500)).contains("x".repeat(251)))
    }

    @Test
    fun mapsNetworkFailuresWithoutRawMessages() {
        assertTrue(ProviderErrorMessages.forNetworkFailure(SocketTimeoutException("raw")).contains("ожидания"))
        assertTrue(ProviderErrorMessages.forNetworkFailure(UnknownHostException("raw")).contains("сервер"))
        assertTrue(ProviderErrorMessages.forNetworkFailure(SSLException("raw")).contains("HTTPS"))
        assertFalse(ProviderErrorMessages.forNetworkFailure(SocketTimeoutException("sensitive")).contains("sensitive"))
    }

    @Test
    fun onlyTransportIoExceptionsAreMappedAsNetworkFailures() {
        assertTrue(ProviderErrorMessages.isNetworkFailure(SocketTimeoutException("timeout")))
        assertTrue(ProviderErrorMessages.isNetworkFailure(SocketException("connection reset")))
        assertFalse(ProviderErrorMessages.isNetworkFailure(IOException("malformed SSE event")))
    }

    @Test
    fun parsesOpenAiCompatibleUsageAndDiagnosticSummary() {
        val usage = ProviderUsageParser.parse(
            JSONObject("""{"usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19}}""")
        )

        assertEquals(12, usage?.promptTokens)
        assertEquals(7, usage?.completionTokens)
        assertEquals(19, usage?.totalTokens)
        assertEquals("prompt_tokens=12 completion_tokens=7 total_tokens=19", usage?.diagnosticSummary())
    }

    @Test
    fun supportsInputOutputAliasesAndStringCounts() {
        val usage = ProviderUsageParser.parse(
            JSONObject("""{"usage":{"input_tokens":"4","output_tokens":3,"total_tokens":7}}""")
        )

        assertEquals(4, usage?.promptTokens)
        assertEquals(3, usage?.completionTokens)
        assertEquals(7, usage?.totalTokens)
    }

    @Test
    fun mergesPartialAnthropicStreamingUsage() {
        val input = ProviderUsageParser.parse(
            JSONObject("""{"message":{"usage":{"input_tokens":15,"output_tokens":0}}}""")
        )
        val output = ProviderUsageParser.parse(
            JSONObject("""{"usage":{"output_tokens":6}}""")
        )

        val merged = requireNotNull(input).merge(requireNotNull(output))
        assertEquals(15, merged.promptTokens)
        assertEquals(6, merged.completionTokens)
    }

    @Test
    fun ignoresMissingMalformedAndNegativeUsage() {
        assertNull(ProviderUsageParser.parse(JSONObject("""{"choices":[]}""")))
        assertNull(ProviderUsageParser.parse(JSONObject("""{"usage":{"prompt_tokens":-1,"completion_tokens":"bad"}}""")))
    }
}
