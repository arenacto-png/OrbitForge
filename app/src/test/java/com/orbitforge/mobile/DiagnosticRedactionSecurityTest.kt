package com.orbitforge.mobile.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionSecurityTest {
    @Test
    fun redactsGithubClassicToken() {
        val token = "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        val result = DiagnosticLog.redact("github_token=$token")
        assertFalse(result.contains(token))
        assertTrue(result.contains("REDACTED"))
    }

    @Test
    fun redactsBearerAndApiKey() {
        val result = DiagnosticLog.redact("Authorization: Bearer abcdefghijklmnop api_key=sk-abcdefghijklmnop")
        assertFalse(result.contains("abcdefghijklmnop"))
    }
}
