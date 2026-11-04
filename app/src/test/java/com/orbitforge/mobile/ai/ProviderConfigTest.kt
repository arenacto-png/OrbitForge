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

    @Test
    fun buildsAnthropicMessagesEndpointFromHostOrV1Base() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            ProviderConfig(
                "https://api.anthropic.com",
                "claude-demo",
                ProviderApiFormat.ANTHROPIC_MESSAGES
            ).requestUrl()
        )
        assertEquals(
            "https://gateway.example/v1/messages",
            ProviderConfig(
                "https://gateway.example/v1",
                "claude-demo",
                ProviderApiFormat.ANTHROPIC_MESSAGES
            ).requestUrl()
        )
    }

    @Test
    fun doesNotDuplicateAnthropicMessagesPath() {
        assertEquals(
            "https://api.anthropic.com/v1/messages",
            ProviderConfig(
                "https://api.anthropic.com/v1/messages",
                "claude-demo",
                ProviderApiFormat.ANTHROPIC_MESSAGES
            ).requestUrl()
        )
    }

    @Test
    fun unknownStoredFormatFallsBackToOpenAiCompatible() {
        assertEquals(
            ProviderApiFormat.OPENAI_COMPATIBLE,
            ProviderApiFormat.fromStorage("future-provider-format")
        )
    }
}
