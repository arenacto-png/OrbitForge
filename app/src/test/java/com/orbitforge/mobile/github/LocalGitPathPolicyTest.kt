package com.orbitforge.mobile.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocalGitPathPolicyTest {
    @Test
    fun acceptsOrdinaryRelativeGitPaths() {
        assertEquals("src/main/kotlin/Main.kt", LocalGitPathPolicy.validate("src/main/kotlin/Main.kt"))
        assertEquals("README.md", LocalGitPathPolicy.validate("README.md"))
    }

    @Test
    fun rejectsGitPathspecGlobAndMagicCharacters() {
        val unsafePaths = listOf(
            "wild*.txt",
            "question?.txt",
            "range[0].txt",
            "closing].txt",
            "magic:(glob)**"
        )

        unsafePaths.forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                LocalGitPathPolicy.validate(path)
            }
        }
    }

    @Test
    fun preservesTraversalAndAbsolutePathRejection() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalGitPathPolicy.validate("../outside.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalGitPathPolicy.validate("/absolute.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalGitPathPolicy.validate("src\\outside.txt")
        }
    }
}
