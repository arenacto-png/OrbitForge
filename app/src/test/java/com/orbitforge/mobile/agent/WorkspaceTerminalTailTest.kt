package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceTerminalTailTest {
    @Test
    fun defaultsToTwentyLines() {
        assertEquals(
            WorkspaceTerminalTail.Request("app.log", 20),
            WorkspaceTerminalTail.parseArguments(listOf("app.log"))
        )
    }

    @Test
    fun acceptsExplicitCountsAtBothBounds() {
        assertEquals(1, WorkspaceTerminalTail.parseArguments(listOf("app.log", "1")).lineCount)
        assertEquals(200, WorkspaceTerminalTail.parseArguments(listOf("app.log", "200")).lineCount)
    }

    @Test
    fun rejectsExtraArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalTail.parseArguments(listOf("app.log", "10", "extra"))
        }
    }

    @Test
    fun rejectsNonIntegerCounts() {
        listOf("abc", "2.5", "", " 2 ").forEach { count ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceTerminalTail.parseArguments(listOf("app.log", count))
            }
        }
    }

    @Test
    fun rejectsCountsOutsideOneToTwoHundred() {
        listOf("0", "-1", "201").forEach { count ->
            assertThrows(IllegalArgumentException::class.java) {
                WorkspaceTerminalTail.parseArguments(listOf("app.log", count))
            }
        }
    }

    @Test
    fun rejectsBlankPath() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalTail.parseArguments(listOf(""))
        }
    }

    @Test
    fun returnsLastRequestedLinesAndIgnoresSingleTrailingNewline() {
        assertEquals("third\nfourth", WorkspaceTerminalTail.format("first\nsecond\nthird\nfourth\n", 2))
        assertEquals("last", WorkspaceTerminalTail.format("first\nlast\n", 1))
    }

    @Test
    fun preservesTrailingBlankLines() {
        assertEquals("", WorkspaceTerminalTail.format("first\nlast\n\n", 1))
        assertEquals("last\n", WorkspaceTerminalTail.format("first\nlast\n\n", 2))
    }

    @Test
    fun handlesEmptyFilesAndCrLf() {
        assertEquals("", WorkspaceTerminalTail.format("", 20))
        assertEquals("second\nthird", WorkspaceTerminalTail.format("first\r\nsecond\r\nthird\r\n", 2))
    }

    @Test
    fun truncationKeepsEndOfOutputAndMarkerWithinLimit() {
        val output = WorkspaceTerminalTail.format(
            content = "abcdefghijklmnopqrstuvwxyz",
            lineCount = 1,
            maxOutputChars = 24
        )
        assertTrue(output.length <= 24)
        assertTrue(output.contains("сокращён"))
        assertTrue(output.endsWith("vwxyz"))
    }

    @Test
    fun rejectsInvalidOutputAndLineLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalTail.format("text", 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalTail.format("text", 1, 0)
        }
    }
}
