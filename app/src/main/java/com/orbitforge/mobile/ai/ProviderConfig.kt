package com.orbitforge.mobile.ai

enum class ProviderApiFormat(val storageValue: String, val label: String) {
    OPENAI_COMPATIBLE("openai_compatible", "OpenAI-compatible Chat Completions"),
    ANTHROPIC_MESSAGES("anthropic_messages", "Anthropic Messages");

    companion object {
        fun fromStorage(value: String?): ProviderApiFormat =
            values().firstOrNull { it.storageValue == value } ?: OPENAI_COMPATIBLE
    }
}

data class ProviderConfig(
    val baseUrl: String,
    val model: String,
    val apiFormat: ProviderApiFormat = ProviderApiFormat.OPENAI_COMPATIBLE
) {
    /** Returns the endpoint for the selected API format. */
    fun requestUrl(): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://")) { "Для API требуется HTTPS." }
        require(!trimmed.contains('?') && !trimmed.contains('#')) {
            "Не добавляй ключи или параметры авторизации в URL; используй поле API-ключа."
        }
        require(!trimmed.substringAfter("://").substringBefore('/').contains('@')) {
            "Не указывай логин или пароль в URL; используй отдельные поля авторизации."
        }
        return when (apiFormat) {
            ProviderApiFormat.OPENAI_COMPATIBLE -> when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/v1/chat/completions"
            }
            ProviderApiFormat.ANTHROPIC_MESSAGES -> when {
                trimmed.endsWith("/v1/messages") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/messages"
                else -> "$trimmed/v1/messages"
            }
        }
    }

    /** Kept for existing call sites and tests; now resolves the configured API format. */
    fun chatCompletionsUrl(): String = requestUrl()
}
