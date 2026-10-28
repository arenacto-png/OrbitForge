package com.orbitforge.mobile.mcp

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Reads untrusted remote MCP response bodies without allowing unbounded allocation.
 * The extra byte is only a probe to distinguish an exact-limit body from an oversized one.
 */
internal object McpResponseBodyReader {
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

    fun read(input: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String {
        require(maxBytes > 0) { "Лимит ответа MCP должен быть положительным." }

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
        IOException("Ответ MCP превышает лимит $maxBytes байт.")
}
