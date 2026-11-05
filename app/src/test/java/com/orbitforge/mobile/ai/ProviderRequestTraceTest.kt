package com.orbitforge.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRequestTraceTest {
    @Test
    fun generatedIdsAreUuidAndUnique() {
        val first = ProviderRequestTrace.create()
        val second = ProviderRequestTrace.create()

        assertTrue(first.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        assertNotEquals(first.id, second.id)
    }

    @Test
    fun eventIncludesOnlyBoundedRequestMetadata() {
        val trace = ProviderRequestTrace("trace-123", 1_000_000L)
        val event = trace.event(
            operation = "agent",
            format = ProviderApiFormat.ANTHROPIC_MESSAGES,
            phase = "completed",
            statusCode = 200,
            durationMillis = trace.elapsedMillis(251_000_000L)
        )

        assertEquals(
            "request_id=trace-123 operation=agent format=ANTHROPIC_MESSAGES phase=completed http_status=200 duration_ms=250",
            event
        )
        assertFalse(event.contains("api-key"))
        assertFalse(event.contains("prompt"))
    }

    @Test
    fun elapsedDurationNeverBecomesNegative() {
        val trace = ProviderRequestTrace("trace-123", 10_000_000L)

        assertEquals(0L, trace.elapsedMillis(9_000_000L))
    }

    @Test
    fun eventIncludesErrorClassWithoutMessage() {
        val trace = ProviderRequestTrace("trace-123", 0L)
        val event = trace.event(
            operation = "chat",
            format = ProviderApiFormat.OPENAI_COMPATIBLE,
            phase = "failed",
            errorType = "SocketTimeoutException"
        )

        assertTrue(event.contains("error_type=SocketTimeoutException"))
        assertFalse(event.contains("secret-value"))
    }
}
