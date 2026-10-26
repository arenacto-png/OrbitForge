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
    fun labelsNewFile() {
        assertTrue(WorkspaceDiff.preview("", "hello", isNewFile = true).contains("[новый файл]"))
    }

    @Test
    fun boundsLongDiffPreviews() {
        val oldContent = (1..200).joinToString("\n") { "old-line-$it" }
        val newContent = (1..200).joinToString("\n") { "new-line-$it" }

        val preview = WorkspaceDiff.preview(oldContent, newContent, maxLines = 12, isNewFile = false)

        assertTrue(preview.lines().size <= 13)
        assertTrue(preview.length <= 6_100)
        assertTrue(preview.contains("предпросмотр сокращён"))
    }
}
