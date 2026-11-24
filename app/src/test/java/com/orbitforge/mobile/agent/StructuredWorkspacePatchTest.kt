package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StructuredWorkspacePatchTest {
    @Test
    fun appliesMultipleExactEditsInOrder() {
        val result = StructuredWorkspacePatch.apply(
            "fun greet() = \"old\"\nfun count() = 1\n",
            listOf(StructuredTextEdit("\"old\"", "\"hello\""), StructuredTextEdit("count() = 1", "count() = 2"))
        )
        assertEquals("fun greet() = \"hello\"\nfun count() = 2\n", result)
    }

    @Test
    fun appliesUnicodeTextExactly() {
        assertEquals("Привет, OrbitForge!", StructuredWorkspacePatch.apply(
            "Привет, мир!", listOf(StructuredTextEdit("мир", "OrbitForge"))
        ))
    }

    @Test
    fun rejectsMissingSearchFragment() {
        expectIllegalArgument { StructuredWorkspacePatch.apply("hello", listOf(StructuredTextEdit("missing", "new"))) }
    }

    @Test
    fun rejectsRepeatedAndOverlappingMatches() {
        expectIllegalArgument { StructuredWorkspacePatch.apply("same same", listOf(StructuredTextEdit("same", "new"))) }
        expectIllegalArgument { StructuredWorkspacePatch.apply("aaa", listOf(StructuredTextEdit("aa", "b"))) }
    }

    @Test
    fun rejectsEmptySearchAndNoOpPatch() {
        expectIllegalArgument { StructuredWorkspacePatch.apply("hello", listOf(StructuredTextEdit("", "x"))) }
        expectIllegalArgument { StructuredWorkspacePatch.apply("hello", listOf(StructuredTextEdit("hello", "hello"))) }
    }

    @Test
    fun rejectsOversizedResultBeforeWriting() {
        expectIllegalArgument {
            StructuredWorkspacePatch.apply("a|b|c", listOf(
                StructuredTextEdit("a", "x".repeat(4_000)),
                StructuredTextEdit("b", "y".repeat(4_000)),
                StructuredTextEdit("c", "z".repeat(4_000))
            ))
        }
    }

    @Test
    fun rejectsOversizedUtf8InputEvenWhenCharacterCountFits() {
        val original = "я".repeat(StructuredWorkspacePatch.MAX_FILE_BYTES / 2 + 1)
        assertTrue(original.length < StructuredWorkspacePatch.MAX_FILE_BYTES)
        expectIllegalArgument { StructuredWorkspacePatch.apply(original, listOf(StructuredTextEdit("я", "Ю"))) }
    }

    @Test
    fun rejectsOversizedUtf8Result() {
        val original = "a".repeat(StructuredWorkspacePatch.MAX_FILE_BYTES - "needle".length) + "needle"
        assertEquals(StructuredWorkspacePatch.MAX_FILE_BYTES, original.toByteArray(Charsets.UTF_8).size)
        expectIllegalArgument { StructuredWorkspacePatch.apply(original, listOf(StructuredTextEdit("needle", "я".repeat(4)))) }
    }

    @Test
    fun rejectsOversizedEditFragments() {
        expectIllegalArgument { StructuredWorkspacePatch.apply("needle", listOf(StructuredTextEdit("n".repeat(4_001), "x"))) }
        expectIllegalArgument { StructuredWorkspacePatch.apply("needle", listOf(StructuredTextEdit("needle", "x".repeat(4_001)))) }
    }

    @Test
    fun rollbackRestoresAppliedContentSkipsOriginalAndRejectsInterveningEdits() {
        assertTrue(StructuredWorkspacePatch.shouldRestoreOriginal("patched", "original", "patched"))
        assertFalse(StructuredWorkspacePatch.shouldRestoreOriginal("original", "original", "patched"))
        expectIllegalArgument {
            StructuredWorkspacePatch.shouldRestoreOriginal("user edit", "original", "patched")
        }
    }

    @Test
    fun rejectsTooManyEditsForOneFile() {
        val edits = (1..9).map { StructuredTextEdit("token-$it", "value-$it") }
        expectIllegalArgument { StructuredWorkspacePatch.apply((1..9).joinToString(" ") { "token-$it" }, edits) }
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected patch validation to reject this input")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
