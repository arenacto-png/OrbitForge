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
        assertTrue(message.contains("перенаправлением"))
        assertTrue(message.contains("конечный URL API"))
    }

    @Test
    fun rejectsNonRedirectStatusCodesInRedirectFailureHelper() {
        listOf(200, 304, 404).forEach { statusCode ->
            try {
                ProviderHttpClientPolicy.redirectFailure(statusCode)
                throw AssertionError("Expected status $statusCode to be rejected")
            } catch (_: IllegalArgumentException) {
                // Expected.
            }
            assertFalse(ProviderHttpClientPolicy.isRedirectStatus(statusCode))
        }
    }

    @Test
    fun recognizesRedirectStatusCodesHandledByOkHttp() {
        listOf(300, 301, 302, 303, 307, 308).forEach { statusCode ->
            assertTrue(ProviderHttpClientPolicy.isRedirectStatus(statusCode))
        }
    }
}
