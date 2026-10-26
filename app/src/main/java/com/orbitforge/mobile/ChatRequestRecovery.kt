package com.orbitforge.mobile

internal data class ChatMessage(val role: String, val text: String)

/**
 * Converts an in-flight assistant placeholder into a durable cancellation result.
 * This is deliberately independent of Compose so its recovery behavior can be unit-tested.
 */
internal object ChatRequestRecovery {
    const val CANCELLED_MESSAGE = "Запрос отменён пользователем."
    private const val PARTIAL_RESPONSE_NOTE = "[Ответ неполный: запрос отменён пользователем.]"

    fun markCancelled(
        history: MutableList<ChatMessage>,
        assistantIndex: Int,
        agentMode: Boolean
    ): Boolean {
        if (assistantIndex !in history.indices) return false

        val previous = history[assistantIndex]
        val text = if (!agentMode && previous.text.isNotBlank()) {
            "${previous.text}\n\n$PARTIAL_RESPONSE_NOTE"
        } else {
            CANCELLED_MESSAGE
        }
        history[assistantIndex] = ChatMessage("assistant", text)
        return true
    }
}
