package com.orbitforge.mobile.github

import okhttp3.OkHttpClient
import java.io.IOException

/**
 * GitHub requests can carry a personal access token and workspace file contents.
 * Do not let a server-controlled redirect choose another destination for those requests.
 */
internal object GitHubHttpClientPolicy {
    private val redirectStatusCodes = setOf(300, 301, 302, 303, 307, 308)

    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)

    fun isRedirectStatus(statusCode: Int): Boolean = statusCode in redirectStatusCodes

    fun redirectFailure(statusCode: Int): IOException {
        require(isRedirectStatus(statusCode)) { "HTTP status is not a redirect." }
        return IOException(
            "GitHub API вернул HTTP $statusCode с перенаправлением. " +
                "Автоматические перенаправления отключены, чтобы не пересылать содержимое рабочей папки " +
                "на адрес, выбранный сервером. Проверь owner/name репозитория: он мог быть переименован или перемещён."
        )
    }
}
