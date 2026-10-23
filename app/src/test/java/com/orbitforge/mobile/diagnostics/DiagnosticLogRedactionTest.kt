package com.orbitforge.mobile.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLogRedactionTest {
    @Test
    fun redactsBearerToken() {
        val result = DiagnosticLog.redact("Authorization: Bearer abcdefghijklmnop")
        assertFalse(result.contains("abcdefghijklmnop"))
        assertTrue(result.contains("[REDACTED]"))
    }

    @Test
    fun redactsAccessTokensAndPasswords() {
        val result = DiagnosticLog.redact("access_token=private-value password=private-password")
        assertFalse(result.contains("private-value"))
        assertFalse(result.contains("private-password"))
    }

    @Test
    fun redactsApiKeyLikeStrings() {
        val result = DiagnosticLog.redact("api_key=sk-abcdefghijklmnop")
        assertFalse(result.contains("sk-abcdefghijklmnop"))
    }
}
