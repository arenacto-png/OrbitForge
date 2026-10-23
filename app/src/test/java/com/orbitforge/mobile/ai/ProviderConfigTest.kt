package com.orbitforge.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderConfigTest {
    @Test
    fun appendsChatCompletionsToV1BaseUrl() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderConfig("https://example.com/v1", "demo-model").chatCompletionsUrl()
        )
    }

    @Test
    fun doesNotDuplicateChatCompletionsPath() {
        assertEquals(
            "https://example.com/v1/chat/completions",
            ProviderConfig("https://example.com/v1/chat/completions", "demo-model").chatCompletionsUrl()
        )
    }
}
