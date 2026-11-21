package com.orbitforge.mobile.json

import java.util.ArrayDeque

/**
 * Rejects excessively nested or structurally unbalanced JSON before a recursive JSON parser sees it.
 * This is a lightweight depth guard, not a replacement for full JSON syntax validation.
 */
internal object JsonNestingGuard {
    const val MAX_NESTING_DEPTH = 64

    fun requireWithinLimit(json: String) {
        val expectedClosers = ArrayDeque<Char>()
        var inString = false
        var escaped = false

        json.forEachIndexed { index, character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> inString = false
                }
                return@forEachIndexed
            }

            when (character) {
                '"' -> inString = true
                '{' -> pushCloser(expectedClosers, '}', index)
                '[' -> pushCloser(expectedClosers, ']', index)
                '}', ']' -> {
                    require(expectedClosers.isNotEmpty() && expectedClosers.peekLast() == character) {
                        "JSON container delimiters are mismatched at index $index."
                    }
                    expectedClosers.removeLast()
                }
            }
        }

        require(!inString) { "JSON contains an unterminated string." }
        require(expectedClosers.isEmpty()) { "JSON contains an unterminated container." }
    }

    private fun pushCloser(expectedClosers: ArrayDeque<Char>, closer: Char, index: Int) {
        expectedClosers.addLast(closer)
        require(expectedClosers.size <= MAX_NESTING_DEPTH) {
            "JSON nesting exceeds the maximum depth of $MAX_NESTING_DEPTH at index $index."
        }
    }
}
