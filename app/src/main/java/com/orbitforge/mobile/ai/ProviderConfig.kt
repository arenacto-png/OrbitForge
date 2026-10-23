package com.orbitforge.mobile.ai

data class ProviderConfig(
    val baseUrl: String,
    val model: String
) {
    fun chatCompletionsUrl(): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://")) { "Для API требуется HTTPS." }
        require(!trimmed.contains('?') && !trimmed.contains('#')) {
            "Не добавляй ключи или параметры авторизации в URL; используй поле API-ключа."
        }
        require(!trimmed.substringAfter("://").substringBefore('/').contains('@')) {
            "Не указывай логин или пароль в URL; используй отдельные поля авторизации."
        }
        return if (trimmed.endsWith("/chat/completions")) trimmed
        else if (trimmed.endsWith("/v1")) "$trimmed/chat/completions"
        else "$trimmed/v1/chat/completions"
    }
}
