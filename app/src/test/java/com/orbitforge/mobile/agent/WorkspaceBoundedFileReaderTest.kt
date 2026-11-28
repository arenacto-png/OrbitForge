package com.orbitforge.mobile.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class WorkspaceBoundedFileReaderTest {
    @Test
    fun acceptsContentExactlyAtByteLimit() {
        val content = "abcd"
        assertEquals(content, WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(content.toByteArray()), 4))
    }

    @Test
    fun rejectsOneByteBeyondLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream("abcde".toByteArray()), 4)
        }
    }

    @Test
    fun zeroByteLimitAcceptsOnlyEmptyInput() {
        assertEquals("", WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(byteArrayOf()), 0))
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(byteArrayOf(1)), 0)
        }
    }

    @Test
    fun limitCountsUtf8BytesRatherThanCharacters() {
        val content = "я🙂"
        val bytes = content.toByteArray(Charsets.UTF_8)
        assertEquals(content, WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(bytes), bytes.size))
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(bytes), bytes.size - 1)
        }
    }

    @Test
    fun fallsBackToSingleByteReadWhenBulkReadReturnsZero() {
        val input = object : InputStream() {
            private val delegate = ByteArrayInputStream("abc".toByteArray())
            private var returnZeroOnce = true

            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZeroOnce) {
                    returnZeroOnce = false
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }
        assertEquals("abc", WorkspaceBoundedFileReader.readUtf8(input, 3))
    }

    @Test
    fun rejectsOverflowAfterReadingOnlyLimitPlusOneBytes() {
        val input = object : ByteArrayInputStream(ByteArray(100_000) { 'x'.code.toByte() }) {
            var bytesConsumed = 0

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val count = super.read(buffer, offset, length)
                if (count > 0) bytesConsumed += count
                return count
            }

            override fun read(): Int {
                val value = super.read()
                if (value >= 0) bytesConsumed++
                return value
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(input, 4)
        }
        assertEquals(5, input.bytesConsumed)
    }

    @Test
    fun rejectsLimitsOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(ByteArrayInputStream(byteArrayOf()), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceBoundedFileReader.readUtf8(
                ByteArrayInputStream(byteArrayOf()),
                WorkspaceBoundedFileReader.MAX_BYTES + 1
            )
        }
    }
}
