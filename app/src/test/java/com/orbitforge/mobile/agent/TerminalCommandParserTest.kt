package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TerminalCommandParserTest {
    @Test
    fun parsesOrdinaryUnquotedArguments() {
        assertEquals(listOf("touch", "file.txt"), TerminalCommandParser.parse("touch file.txt"))
        assertEquals(listOf("grep", "needle", "path.txt"), TerminalCommandParser.parse("grep needle path.txt"))
    }

    @Test
    fun parsesQuotedPathsAndArgumentsContainingSpaces() {
        assertEquals(
            listOf("grep", "needle with spaces", "src/Main File.kt"),
            TerminalCommandParser.parse("""grep "needle with spaces" 'src/Main File.kt'""")
        )
    }

    @Test
    fun supportsConcatenatedQuotedAndUnquotedSegments() {
        assertEquals(
            listOf("echo", "prefix-middle-suffix"),
            TerminalCommandParser.parse("""echo prefix"middle"suffix""")
        )
    }

    @Test
    fun preservesAnEmptyQuotedArgument() {
        assertEquals(listOf("echo", ""), TerminalCommandParser.parse("echo \"\""))
    }

    @Test
    fun treatsControlCharactersAsDataInsideQuotes() {
        assertEquals(
            listOf("echo", "a|b;$(literal)"),
            TerminalCommandParser.parse("echo \"a|b;$(literal)\"")
        )
    }

    @Test
    fun rejectsShellControlSyntaxOutsideQuotes() {
        val backtick = 96.toChar()
        listOf(
            "echo one;two",
            "echo one && two",
            "echo one || two",
            "echo one|two",
            "echo one > file",
            "echo one < file",
            "echo one & two",
            "echo " + backtick + "id" + backtick,
            "echo $(id)"
        ).forEach { command ->
            assertThrows(IllegalArgumentException::class.java) {
                TerminalCommandParser.parse(command)
            }
        }
    }

    @Test
    fun rejectsUnmatchedSingleAndDoubleQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("cat \"unfinished path")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("cat 'unfinished path")
        }
    }

    @Test
    fun rejectsBackslashEscapeSyntaxOutsideQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("echo one\\\\ two")
        }
    }

    @Test
    fun rejectsMultilineAndOversizedCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("echo first\necho second")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("echo hi\n")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("x".repeat(TerminalCommandParser.MAX_COMMAND_CHARS + 1))
        }
    }


    @Test
    fun mutationApprovalUsesTheSameParserAndFailsClosedOnMalformedInput() {
        assertEquals(true, TerminalCommandParser.isMutating("mkdir \"new folder\""))
        assertEquals(true, TerminalCommandParser.isMutating("touch file.txt"))
        assertEquals(false, TerminalCommandParser.isMutating("echo mkdir"))
        assertEquals(false, TerminalCommandParser.isMutating("mkdir folder; echo unsafe"))
        assertEquals(false, TerminalCommandParser.isMutating("mkdir \"unfinished"))
    }

    @Test
    fun rejectsBlankCommands() {
        assertThrows(IllegalArgumentException::class.java) {
            TerminalCommandParser.parse("   ")
        }
    }
}
