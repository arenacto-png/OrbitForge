package com.orbitforge.mobile.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHttpClientPolicyTest {
    @Test
    fun disablesAutomaticRedirectsAndHttpsDowngrades() {
        val client = ProviderHttpClientPolicy.newBuilder().build()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun explainsThatRedirectResponsesRequireTheFinalEndpoint() {
        val message = ProviderHttpClientPolicy.redirectFailure(307).message.orEmpty()

        assertTrue(message.contains("307"))
        assertTrue(message.contains("перенаправления"))
        assertTrue(message.contains("конечный URL API"))
    }

    @Test
    fun rejectsNonRedirectStatusCodesInRedirectFailureHelper() {
        try {
            ProviderHttpClientPolicy.redirectFailure(200)
            throw AssertionError("Expected a non-redirect status to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
