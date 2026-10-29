package com.orbitforge.mobile.network

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads an untrusted HTTP response without allocating or parsing an unbounded body.
 * One extra byte is probed to distinguish an exact-limit body from an oversized one.
 */
internal object BoundedResponseBodyReader {
    const val DEFAULT_MAX_BYTES = 2 * 1024 * 1024

    fun read(input: InputStream, maxBytes: Int = DEFAULT_MAX_BYTES): String {
        require(maxBytes > 0) { "Лимит HTTP-ответа должен быть положительным." }

        val output = ByteArrayOutputStream(minOf(maxBytes, 8 * 1024))
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val remaining = maxBytes - total
            val requested = minOf(buffer.size.toLong(), remaining.toLong() + 1L).toInt()
            val count = input.read(buffer, 0, requested)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                if (total >= maxBytes) throw tooLarge(maxBytes)
                output.write(next)
                total++
                continue
            }
            if (count > remaining) throw tooLarge(maxBytes)
            output.write(buffer, 0, count)
            total += count
        }
        return String(output.toByteArray(), Charsets.UTF_8)
    }

    private fun tooLarge(maxBytes: Int) =
        IOException("HTTP-ответ превышает лимит $maxBytes байт.")
}
