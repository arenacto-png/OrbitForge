package com.orbitforge.mobile.ai

import com.orbitforge.mobile.diagnostics.DiagnosticLog
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal class ProviderHttpException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/** Provider-neutral, bounded guidance for common HTTP and network failures. */
internal object ProviderErrorMessages {
    fun forHttpStatus(code: Int, responseBody: String = ""): String {
        val guidance = when (code) {
            400 -> "Запрос отклонён (HTTP $code). Проверь ID модели, формат запроса и поддержку выбранных функций."
            401, 403 -> "API отклонил ключ или доступ (HTTP $code). Проверь ключ, права и доступность модели."
            404 -> "Endpoint или модель не найдены (HTTP 404). Проверь базовый URL и ID модели."
            408 -> "Провайдер не успел обработать запрос (HTTP 408). Повтори попытку позже."
            413 -> "Запрос слишком большой (HTTP 413). Сократи историю или содержимое файлов."
            422 -> "Провайдер не смог обработать запрос (HTTP 422). Проверь параметры и формат выбранной модели."
            429 -> "Лимит запросов или средств исчерпан (HTTP 429). Проверь квоту и ограничения провайдера."
            in 500..599 -> "Ошибка на стороне провайдера (HTTP $code). Повтори попытку позже и проверь статус сервиса."
            else -> "Ошибка API HTTP $code."
        }
        val detail = DiagnosticLog.redact(responseBody).trim().take(MAX_DETAIL_CHARS)
        return if (code in DETAIL_STATUSES && detail.isNotBlank()) "$guidance Детали: $detail" else guidance
    }

    fun isNetworkFailure(error: IOException): Boolean =
        error is SocketTimeoutException ||
            error is UnknownHostException ||
            error is SSLException ||
            error is SocketException

    fun forNetworkFailure(error: IOException): String = when (error) {
        is SocketTimeoutException ->
            "Время ожидания ответа провайдера истекло. Проверь соединение и повтори попытку."
        is UnknownHostException ->
            "Не удалось найти сервер провайдера. Проверь базовый URL и интернет-соединение."
        is SSLException ->
            "Не удалось установить защищённое HTTPS-соединение с провайдером. Проверь URL и сертификат сервера."
        is SocketException ->
            "Не удалось подключиться к провайдеру. Проверь интернет-соединение и доступность сервера."
        else ->
            "Сетевая ошибка при обращении к провайдеру (${error.javaClass.simpleName}). Проверь диагностику и повтори попытку."
    }

    private const val MAX_DETAIL_CHARS = 250
    private val DETAIL_STATUSES = setOf(400, 404, 422)
}
