package com.orbitforge.mobile.ai

import okhttp3.OkHttpClient
import java.io.IOException

/**
 * Provider requests contain conversation history and may carry API credentials.
 * Never let a provider-controlled redirect choose a second request destination.
 */
internal object ProviderHttpClientPolicy {
    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)

    fun redirectFailure(statusCode: Int): IOException {
        require(statusCode in 300..399) { "HTTP status is not a redirect." }
        return IOException(
            "Провайдер вернул HTTP $statusCode с перенаправлением. " +
                "Автоматические перенаправления отключены, чтобы не пересылать данные диалога " +
                "на адрес, выбранный сервером. Укажи конечный URL API напрямую."
        )
    }
}
