package com.orbitforge.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProviderConfigSecurityTest {
    @Test
    fun rejectsPlainHttpEndpoints() {
        try {
            ProviderConfig("http://example.com/v1", "demo-model").chatCompletionsUrl()
            fail("Expected HTTP endpoint to be rejected")
        } catch (_: IllegalArgumentException) {
            // API credentials must only be sent over HTTPS.
        }
    }

    @Test
    fun rejectsCredentialsInUrl() {
        try {
            ProviderConfig("https://example.com/v1?api_key=secret", "demo-model").chatCompletionsUrl()
            fail("Credentials in the URL must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: credentials belong in protected fields, not URLs.
        }
    }

    @Test
    fun rejectsCredentialsInAnthropicUrlToo() {
        try {
            ProviderConfig(
                "https://user:pass@api.anthropic.com",
                "claude-demo",
                ProviderApiFormat.ANTHROPIC_MESSAGES
            ).requestUrl()
            fail("Credentials in Anthropic URLs must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: credentials belong in protected fields, not URLs.
        }
    }

    @Test
    fun appendsV1ForHostOnlyBaseUrl() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderConfig("https://example.com", "demo-model").chatCompletionsUrl()
        )
    }
}
