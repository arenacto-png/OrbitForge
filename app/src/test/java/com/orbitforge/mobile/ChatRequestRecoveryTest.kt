package com.orbitforge.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRequestRecoveryTest {
    @Test
    fun cancellationReplacesEmptyAssistantPlaceholder() {
        val history = mutableListOf(
            ChatMessage("user", "Объясни проект"),
            ChatMessage("assistant", "")
        )

        assertTrue(ChatRequestRecovery.markCancelled(history, 1, agentMode = false))

        assertEquals("Объясни проект", history[0].text)
        assertEquals(ChatRequestRecovery.CANCELLED_MESSAGE, history[1].text)
        assertEquals("assistant", history[1].role)
    }

    @Test
    fun cancellationPreservesPartialChatResponseAndMarksItIncomplete() {
        val history = mutableListOf(
            ChatMessage("user", "Расскажи"),
            ChatMessage("assistant", "Первый фрагмент ответа")
        )

        assertTrue(ChatRequestRecovery.markCancelled(history, 1, agentMode = false))

        assertTrue(history[1].text.startsWith("Первый фрагмент ответа"))
        assertTrue(history[1].text.contains("Ответ неполный"))
        assertTrue(history[1].text.contains("Запрос отменён пользователем"))
    }

    @Test
    fun cancellationReplacesAgentProgressText() {
        val history = mutableListOf(
            ChatMessage("user", "Исправь код"),
            ChatMessage("assistant", "⏳ Инструмент: read_file")
        )

        assertTrue(ChatRequestRecovery.markCancelled(history, 1, agentMode = true))

        assertEquals(ChatRequestRecovery.CANCELLED_MESSAGE, history[1].text)
    }

    @Test
    fun invalidAssistantIndexLeavesHistoryUntouched() {
        val history = mutableListOf(
            ChatMessage("user", "Задача"),
            ChatMessage("assistant", "Текущий ответ")
        )
        val before = history.toList()

        assertFalse(ChatRequestRecovery.markCancelled(history, 8, agentMode = false))

        assertEquals(before, history)
    }
}
