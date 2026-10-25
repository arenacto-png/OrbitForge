package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceTerminalPolicyTest {
    @Test
    fun recognizesOnlyAllowlistedMutatingCommands() {
        assertTrue(WorkspaceTerminalPolicy.isMutating("mkdir src"))
        assertTrue(WorkspaceTerminalPolicy.isMutating("TOUCH \"hello world.txt\""))
        assertFalse(WorkspaceTerminalPolicy.isMutating("cat README.md"))
        assertFalse(WorkspaceTerminalPolicy.isMutating("echo mkdir"))
    }

    @Test
    fun tokenizesQuotedArgumentsWithoutBreakingSpaces() {
        assertEquals(listOf("cat", "folder name/file.txt"), WorkspaceTerminalPolicy.tokenize("cat \"folder name/file.txt\""))
        assertEquals(listOf("grep", "two words", "README.md"), WorkspaceTerminalPolicy.tokenize("grep 'two words' README.md"))
    }

    @Test
    fun rejectsShellOperatorsAndCommandSubstitution() {
        listOf("ls; cat README.md", "ls && pwd", "cat $(whoami)", "echo x > out.txt", "cat a | grep x").forEach { input ->
            val rejected = runCatching { WorkspaceTerminalPolicy.validateSyntax(input) }.isFailure
            assertTrue("Expected rejection for: $input", rejected)
        }
    }

    @Test
    fun acceptsOrdinaryArgumentsAndPaths() {
        WorkspaceTerminalPolicy.validateSyntax("grep 'hello world' src/Main.kt")
    }
}
