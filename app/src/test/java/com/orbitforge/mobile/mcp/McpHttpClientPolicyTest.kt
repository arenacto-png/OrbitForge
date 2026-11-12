package com.orbitforge.mobile.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class McpHttpClientPolicyTest {
    @Test
    fun refusesAutomaticRedirectsAndHttpsDowngrades() {
        val client = McpHttpClientPolicy.createClient()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun preservesBoundedMcpTimeouts() {
        val client = McpHttpClientPolicy.createClient()

        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(45_000, client.readTimeoutMillis)
        assertEquals(50_000, client.callTimeoutMillis)
    }
}
