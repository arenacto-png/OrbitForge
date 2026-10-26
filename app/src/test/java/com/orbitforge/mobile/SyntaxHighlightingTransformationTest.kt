package com.orbitforge.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightingTransformationTest {
    @Test
    fun highlightsKeywordsStringsNumbersAndCommentsWithoutChangingText() {
        val source = "val count = 42 // note\nval label = \"hello\""
        val transformed = SyntaxHighlightingTransformation.forFile("Main.kt").filter(AnnotatedString(source))
        val spans = transformed.text.spanStyles

        assertEquals(source, transformed.text.text)
        assertTrue(spans.any { it.start == 0 && it.end == 3 && it.item.color == Color(0xFFC792EA) })
        assertTrue(spans.any { it.start == 12 && it.end == 14 && it.item.color == Color(0xFFF78C6C) })
        val stringStart = source.indexOf("\"hello\"")
        assertTrue(spans.any { it.start == stringStart && it.end == stringStart + 7 && it.item.color == Color(0xFFC3E88D) })
        val commentStart = source.indexOf("// note")
        assertTrue(spans.any { it.start == commentStart && it.end == source.length - source.substringAfter("// note").length && it.item.color == Color(0xFF8B93A7) })
    }

    @Test
    fun jsonKeysUsePropertyColor() {
        val source = "{\"model\": \"demo\", \"count\": 3}"
        val transformed = SyntaxHighlightingTransformation.forFile("settings.json").filter(AnnotatedString(source))
        val keyStart = source.indexOf("\"model\"")

        assertEquals(source, transformed.text.text)
        assertTrue(transformed.text.spanStyles.any {
            it.start == keyStart && it.end == keyStart + 7 && it.item.color == Color(0xFF82AAFF)
        })
    }

    @Test
    fun unsupportedFilesRemainPlainText() {
        val source = "val count = 42"
        val transformed = SyntaxHighlightingTransformation.forFile("notes.txt").filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        assertTrue(transformed.text.spanStyles.isEmpty())
    }

    @Test
    fun largeFilesFallBackToPlainText() {
        val source = "val x = 1\n".repeat(10_001)
        val transformed = SyntaxHighlightingTransformation.forFile("Large.kt").filter(AnnotatedString(source))

        assertEquals(source, transformed.text.text)
        assertTrue(transformed.text.spanStyles.isEmpty())
    }
}
