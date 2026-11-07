package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceTerminalGrepTest {
    @Test
    fun searchUsesAgentSafeReaderForTheRequestedPath() {
        var requestedPath: String? = null
        val result = WorkspaceTerminalGrep.search("needle", "src/file.txt") { path ->
            requestedPath = path
            "first\nNeedle found\nlast needle"
        }

        assertEquals("src/file.txt", requestedPath)
        assertEquals("2: Needle found\n3: last needle", result)
    }

    @Test
    fun blankNeedleIsRejectedBeforeReadingAnyFile() {
        var readerCalled = false
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalGrep.search("  ", ".env") {
                readerCalled = true
                "must not be read"
            }
        }
        assertFalse(readerCalled)
    }

    @Test
    fun readerPolicyFailureIsPropagatedInsteadOfReturningFileContent() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalGrep.search("token", "secrets.properties") {
                throw IllegalArgumentException("agent read blocked")
            }
        }
        assertEquals("agent read blocked", failure.message)
    }

    @Test
    fun outputIsBoundedAndMarkedWhenMatchCountIsExceeded() {
        val content = (1..8).joinToString("\n") { "needle " + "x".repeat(40) }
        val result = WorkspaceTerminalGrep.format(
            content = content,
            needle = "needle",
            maxMatches = 3,
            maxOutputChars = 70
        )

        assertTrue(result.length <= 70)
        assertTrue(result.contains("сокращён"))
    }

    @Test
    fun singleLongMatchingLineIsBounded() {
        val result = WorkspaceTerminalGrep.format(
            content = "needle " + "x".repeat(500),
            needle = "needle",
            maxOutputChars = 32
        )

        assertTrue(result.length <= 32)
        assertTrue(result.contains("сокращён"))
    }

    @Test
    fun noMatchMessageRespectsTheConfiguredOutputLimit() {
        val result = WorkspaceTerminalGrep.format(
            content = "hello",
            needle = "needle",
            maxOutputChars = 5
        )
        assertEquals(5, result.length)
    }

    @Test
    fun noMatchesHaveAStableMessage() {
        assertEquals(
            "Совпадений не найдено.",
            WorkspaceTerminalGrep.format("hello\nworld", "needle")
        )
    }
}
