package com.orbitforge.mobile.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubHttpClientPolicyTest {
    @Test
    fun disablesAutomaticRedirectsAndHttpsDowngrades() {
        val client = GitHubHttpClientPolicy.newBuilder().build()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun recognizesRedirectStatusCodesButNotNotModified() {
        listOf(300, 301, 302, 303, 307, 308).forEach { statusCode ->
            assertTrue(GitHubHttpClientPolicy.isRedirectStatus(statusCode))
        }
        assertFalse(GitHubHttpClientPolicy.isRedirectStatus(304))
    }

    @Test
    fun explainsThatRepositoryAddressMustBeUpdatedExplicitly() {
        val message = GitHubHttpClientPolicy.redirectFailure(301).message.orEmpty()

        assertTrue(message.contains("301"))
        assertTrue(message.contains("рабочей папки"))
        assertTrue(message.contains("owner/name"))
    }
}
