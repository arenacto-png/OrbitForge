package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceTerminalOutputTest {
    @Test
    fun leavesShortOutputUnchanged() {
        assertEquals("small file", WorkspaceTerminalOutput.format("small file"))
    }

    @Test
    fun leavesOutputAtExactLimitUnchanged() {
        val content = "x".repeat(WorkspaceTerminalOutput.MAX_OUTPUT_CHARS)
        assertEquals(content, WorkspaceTerminalOutput.format(content))
    }

    @Test
    fun truncatesOversizedOutputAndAddsMarkerWithinLimit() {
        val output = WorkspaceTerminalOutput.format("x".repeat(30_000))
        assertTrue(output.length <= WorkspaceTerminalOutput.MAX_OUTPUT_CHARS)
        assertTrue(output.contains("вывод сокращён"))
    }

    @Test
    fun handlesLimitsSmallerThanMarker() {
        assertEquals("…", WorkspaceTerminalOutput.format("long", 1))
        assertTrue(WorkspaceTerminalOutput.format("long", 3).length <= 3)
    }

    @Test
    fun rejectsNonPositiveOutputLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceTerminalOutput.format("content", 0)
        }
    }
}
