package com.orbitforge.mobile.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDiffTest {
    @Test
    fun showsRemovedAndAddedLinesWithContext() {
        val preview = WorkspaceDiff.preview("first\nold line\nlast", "first\nnew line\nlast", isNewFile = false)
        assertTrue(preview.contains("- old line"))
        assertTrue(preview.contains("+ new line"))
        assertTrue(preview.contains("first"))
        assertFalse(preview.contains("old line") && preview.contains("+ old line"))
    }

    @Test
    fun marksUnchangedFile() {
        assertTrue(WorkspaceDiff.preview("same", "same", isNewFile = false).contains("[нет изменений]"))
    }

    @Test
    fun combinesBatchPreviewWithEachFilePath() {
        val preview = WorkspaceDiff.combineBatchPreview(listOf("a.kt" to "+ one", "b.kt" to "- old\\n+ new"))
        assertTrue(preview.contains("### a.kt"))
        assertTrue(preview.contains("### b.kt"))
        assertTrue(preview.contains("+ one"))
        assertTrue(preview.contains("- old"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBatchPreviewThatExceedsLimit() {
        WorkspaceDiff.combineBatchPreview(listOf("large.txt" to "0123456789"), maxChars = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyBatchPreview() {
        WorkspaceDiff.combineBatchPreview(emptyList())
    }

    @Test
    fun labelsNewFile() {
        assertTrue(WorkspaceDiff.preview("", "hello", isNewFile = true).contains("[новый файл]"))
    }
}
