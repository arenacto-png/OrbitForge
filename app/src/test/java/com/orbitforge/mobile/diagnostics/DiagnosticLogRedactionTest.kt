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
    @Test
    fun redactsQuotedJsonCredentialValuesAndPreservesNonSecretFields() {
        val input = """{"access_token":"json-access-secret","password":"a password with spaces","x-api-key":"json-api-secret","model":"gpt-4"}"""
        val result = DiagnosticLog.redact(input)

        assertFalse(result.contains("json-access-secret"))
        assertFalse(result.contains("a password with spaces"))
        assertFalse(result.contains("json-api-secret"))
        assertTrue(result.contains("\"model\":\"gpt-4\""))
    }

    @Test
    fun redactsEscapedQuotesInsideJsonSecrets() {
        val result = DiagnosticLog.redact("""{"client_secret":"part-one\"part-two"}""")

        assertFalse(result.contains("part-one"))
        assertFalse(result.contains("part-two"))
    }

    @Test
    fun redactsJsonAuthorizationAndBasicAuthorizationHeaders() {
        val json = DiagnosticLog.redact("""{"authorization":"Bearer json-bearer-secret"}""")
        val header = DiagnosticLog.redact("Authorization: Basic username:password-secret")

        assertFalse(json.contains("json-bearer-secret"))
        assertFalse(header.contains("username:password-secret"))
        assertTrue(header.contains("[REDACTED]"))
    }

    @Test
    fun redactsSingleQuotedAndCookieAssignments() {
        val result = DiagnosticLog.redact("password='single quote secret' cookie=session-cookie-secret")

        assertFalse(result.contains("single quote secret"))
        assertFalse(result.contains("session-cookie-secret"))
    }

    @Test
    fun redactsEntireCookieHeadersIncludingAttributes() {
        val result = DiagnosticLog.redact("Set-Cookie: session=cookie-header-secret; Path=/; HttpOnly")

        assertFalse(result.contains("cookie-header-secret"))
        assertFalse(result.contains("Path=/"))
        assertTrue(result.contains("[REDACTED]"))
    }

}
