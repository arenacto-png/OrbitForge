package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTerminalHeadTest {
    @Test
    fun defaultsToTwentyLines() {
        assertEquals(
            WorkspaceTerminalHead.Request("README.md", 20),
            WorkspaceTerminalHead.parseArguments(listOf("README.md"))
        )
    }

    @Test
    fun acceptsExplicitLineCountAtBothBounds() {
        assertEquals(1, WorkspaceTerminalHead.parseArguments(listOf("file.txt", "1")).lineCount)
        assertEquals(200, WorkspaceTerminalHead.parseArguments(listOf("file.txt", "200")).lineCount)
    }

    @Test
    fun rejectsExtraArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalHead.parseArguments(listOf("file.txt", "10", "ignored"))
        }
    }

    @Test
    fun rejectsNonIntegerLineCount() {
        listOf("abc", "2.5", "", " 2 ").forEach { count ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceTerminalHead.parseArguments(listOf("file.txt", count))
            }
        }
    }

    @Test
    fun rejectsCountsOutsideOneToTwoHundredInsteadOfClamping() {
        listOf("0", "-1", "201").forEach { count ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceTerminalHead.parseArguments(listOf("file.txt", count))
            }
        }
    }

    @Test
    fun rejectsBlankPath() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalHead.parseArguments(listOf(""))
        }
    }

    @Test
    fun formatsOnlyRequestedLines() {
        assertEquals(
            "first\nsecond",
            WorkspaceTerminalHead.format("first\nsecond\nthird", 2)
        )
    }

    @Test
    fun truncatesOversizedOutputAndAddsMarker() {
        val output = WorkspaceTerminalHead.format(
            content = "x".repeat(100),
            lineCount = 1,
            maxOutputChars = 24
        )
        assertTrue(output.length <= 24)
        assertTrue(output.contains("сокращён"))
    }

    @Test
    fun rejectsInvalidFormatLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalHead.format("text", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalHead.format("text", 1, 0)
        }
    }
}
