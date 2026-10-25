package com.orbitforge.mobile.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderErrorMessagesTest {
    @Test
    fun explainsAuthenticationFailuresWithoutExposingCredentials() {
        val message = ProviderErrorMessages.forHttpStatus(401, "Authorization: Bearer should-not-be-shown")
        assertTrue(message.contains("ключ"))
        assertTrue(message.contains("401"))
        assertFalse(message.contains("should-not-be-shown"))
    }

    @Test
    fun distinguishesCommonProviderFailures() {
        assertTrue(ProviderErrorMessages.forHttpStatus(404).contains("URL"))
        assertTrue(ProviderErrorMessages.forHttpStatus(429).contains("квоту"))
        assertTrue(ProviderErrorMessages.forHttpStatus(503).contains("стороне провайдера"))
        assertTrue(ProviderErrorMessages.forHttpStatus(413).contains("слишком большой"))
    }

    @Test
    fun includesOnlyBoundedDetailsForRequestAndEndpointErrors() {
        val body = "invalid model"
        assertTrue(ProviderErrorMessages.forHttpStatus(400, body).contains(body))
        assertFalse(ProviderErrorMessages.forHttpStatus(400, "x".repeat(500)).contains("x".repeat(251)))
        assertFalse(ProviderErrorMessages.forHttpStatus(503, "internal server detail").contains("internal server detail"))
    }
}
