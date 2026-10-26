package com.orbitforge.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkspaceSearchResultParserTest {
    @Test
    fun parsesNestedPathAndKeepsColonsInMatchedText() {
        val result = WorkspaceSearchResultParser.parse(
            "src/main/App.kt:42: val endpoint = \"https://host:8443/v1\"",
            listOf("src/main/App.kt", "README.md")
        )

        assertEquals(
            WorkspaceSearchHit("src/main/App.kt", 42, "val endpoint = \"https://host:8443/v1\""),
            result
        )
    }

    @Test
    fun prefersLongestKnownPathWhenFilenameContainsColon() {
        val result = WorkspaceSearchResultParser.parse(
            "config:prod.txt:7: token:123: value",
            listOf("config", "config:prod.txt")
        )

        assertEquals(WorkspaceSearchHit("config:prod.txt", 7, "token:123: value"), result)
    }

    @Test
    fun rejectsLinesThatDoNotBelongToKnownWorkspaceFiles() {
        assertNull(WorkspaceSearchResultParser.parse("outside.txt:1: secret", listOf("inside.txt")))
    }

    @Test
    fun rejectsMalformedLineNumbers() {
        assertNull(WorkspaceSearchResultParser.parse("README.md:not-a-line: text", listOf("README.md")))
        assertNull(WorkspaceSearchResultParser.parse("README.md:0: text", listOf("README.md")))
    }
}
