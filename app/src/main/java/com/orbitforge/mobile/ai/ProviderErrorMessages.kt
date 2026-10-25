package com.orbitforge.mobile.ai

/**
 * Maps common HTTP failures from OpenAI-compatible providers to actionable, provider-neutral text.
 * The response body must be redacted before being supplied here.
 */
internal object ProviderErrorMessages {
    fun forHttpStatus(code: Int, safeBody: String = ""): String {
        val guidance = when (code) {
            400 -> "Запрос отклонён (HTTP 400). Проверь ID модели, формат запроса и поддержку выбранных функций."
            401, 403 -> "API отклонил ключ или доступ (HTTP $code). Проверь ключ, права и доступность модели."
            404 -> "Endpoint или модель не найдены (HTTP 404). Проверь базовый URL и ID модели."
            408 -> "Провайдер не успел обработать запрос (HTTP 408). Повтори попытку позже."
            413 -> "Запрос слишком большой (HTTP 413). Сократи историю или содержимое файлов."
            429 -> "Лимит запросов или средств исчерпан (HTTP 429). Проверь квоту и ограничения провайдера."
            in 500..599 -> "Ошибка на стороне провайдера (HTTP $code). Повтори попытку позже и проверь статус сервиса."
            else -> "Ошибка API HTTP $code."
        }
        val detail = safeBody.trim().take(250)
        return if (code in setOf(400, 404) && detail.isNotBlank()) "$guidance Детали: $detail" else guidance
    }
}
