package com.orbitforge.mobile.agent

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads UTF-8 files without trusting a pre-read file length check. */
internal object WorkspaceBoundedFileReader {
    const val MAX_BYTES = 512 * 1024
    private const val BUFFER_SIZE = 8 * 1024
    private const val TOO_LARGE_MESSAGE = "Файл больше лимита чтения 512 КБ."

    fun readUtf8(input: InputStream, maxBytes: Int = MAX_BYTES): String {
        require(maxBytes in 0..MAX_BYTES) { "Лимит чтения должен быть от 0 до $MAX_BYTES байт." }
        val output = ByteArrayOutputStream(minOf(maxBytes, BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0

        while (true) {
            val remaining = maxBytes - total
            val count = input.read(buffer, 0, minOf(buffer.size, remaining + 1))
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                require(total < maxBytes) { TOO_LARGE_MESSAGE }
                output.write(single)
                total++
            } else {
                require(count <= remaining) { TOO_LARGE_MESSAGE }
                output.write(buffer, 0, count)
                total += count
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
