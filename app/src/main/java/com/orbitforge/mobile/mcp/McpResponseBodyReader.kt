package com.orbitforge.mobile.mcp

import com.orbitforge.mobile.network.BoundedResponseBodyReader
import java.io.InputStream

/** MCP-facing alias kept so MCP callers and their regression tests stay explicit. */
internal object McpResponseBodyReader {
    const val MAX_RESPONSE_BYTES = BoundedResponseBodyReader.DEFAULT_MAX_BYTES

    fun read(input: InputStream, maxBytes: Int = MAX_RESPONSE_BYTES): String =
        BoundedResponseBodyReader.read(input, maxBytes)
}
