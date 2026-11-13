package com.orbitforge.mobile.ai

import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Provider requests contain conversation history and may carry API credentials.
 * Never let a provider-controlled redirect choose a second request destination.
 */
internal object ProviderHttpClientPolicy {
    private val redirectStatusCodes = setOf(300, 301, 302, 303, 307, 308)

    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)

    fun isRedirectStatus(statusCode: Int): Boolean = statusCode in redirectStatusCodes

    fun redirectFailure(statusCode: Int): IOException {
        require(isRedirectStatus(statusCode)) { "HTTP status is not a redirect." }
        return IOException(
            "Провайдер вернул HTTP $statusCode с перенаправлением. " +
                "Автоматические перенаправления отключены, чтобы не пересылать данные диалога " +
                "на адрес, выбранный сервером. Укажи конечный URL API напрямую."
        )
    }
}
