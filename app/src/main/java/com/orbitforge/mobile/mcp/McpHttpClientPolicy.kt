package com.orbitforge.mobile.mcp

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * MCP requests carry a bearer token and invoke tools on a user-configured endpoint.
 * Do not follow server-controlled redirects: they can change the request destination
 * or downgrade an HTTPS request before the user has approved that destination.
 */
internal object McpHttpClientPolicy {
    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(50, TimeUnit.SECONDS)
        .build()
}
