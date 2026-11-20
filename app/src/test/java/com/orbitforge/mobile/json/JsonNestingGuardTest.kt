package com.orbitforge.mobile.json

import org.junit.Assert.fail
import org.junit.Test

class JsonNestingGuardTest {
    @Test
    fun acceptsJsonAtTheMaximumNestingDepth() {
        val nested = "[".repeat(JsonNestingGuard.MAX_NESTING_DEPTH - 1) +
            "0" +
            "]".repeat(JsonNestingGuard.MAX_NESTING_DEPTH - 1)
        JsonNestingGuard.requireWithinLimit("{\"nested\":$nested}")
    }

    @Test
    fun rejectsJsonBeyondTheMaximumNestingDepth() {
        val nested = "[".repeat(JsonNestingGuard.MAX_NESTING_DEPTH + 1) +
            "0" +
            "]".repeat(JsonNestingGuard.MAX_NESTING_DEPTH + 1)
        expectRejected(nested)
    }

    @Test
    fun ignoresBracketsAndBracesInsideEscapedStrings() {
        JsonNestingGuard.requireWithinLimit(
            "{\"text\":\"escaped quote: \\\" and brackets [ ] { }\",\"nested\":[1]}"
        )
    }

    @Test
    fun rejectsMismatchedAndUnclosedContainers() {
        expectRejected("{\"value\":[1}}")
        expectRejected("{\"value\":[1]")
    }

    @Test
    fun rejectsUnterminatedStrings() {
        expectRejected("{\"value\":\"unterminated}")
    }

    private fun expectRejected(json: String) {
        try {
            JsonNestingGuard.requireWithinLimit(json)
            fail("Expected JSON nesting guard to reject malformed or over-deep input")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
