package com.orbitforge.mobile.mcp

import org.json.JSONObject
import java.io.BufferedReader
import java.io.StringReader

/**
 * Parses one JSON-RPC response from JSON or a bounded Streamable HTTP SSE body.
 * Notifications may be interleaved in SSE, but only the response for our request is accepted.
 */
internal object McpJsonRpcResponseParser {
    const val MAX_SSE_EVENTS = 1_000
    private const val MAX_SSE_EVENT_CHARS = 2 * 1024 * 1024

    fun parse(body: String, contentType: String, expectedId: Int): JSONObject {
        require(body.isNotBlank()) { "MCP response body is empty." }
        val isEventStream = contentType.contains("text/event-stream", ignoreCase = true)
        val payloads = if (isEventStream) readSseDataEvents(body) else listOf(body.trim())
        require(payloads.isNotEmpty()) { "MCP event stream contains no data events." }

        var matchedResponse: JSONObject? = null
        payloads.forEach { payload ->
            val message = try {
                JSONObject(payload)
            } catch (_: Exception) {
                throw IllegalArgumentException("MCP response event is not a JSON object.")
            }

            require(message.optString("jsonrpc") == "2.0") {
                "MCP response does not use JSON-RPC 2.0."
            }

            val rawId = message.opt("id")
            if (rawId == null || rawId == JSONObject.NULL) {
                val method = message.opt("method") as? String
                require(isEventStream && !method.isNullOrBlank()) {
                    "MCP response has no request identifier."
                }
                return@forEach
            }

            val numericId = rawId as? Number
                ?: throw IllegalArgumentException("MCP response identifier has an unsupported type.")
            val idAsLong = numericId.toLong()
            require(numericId.toDouble() == idAsLong.toDouble() && idAsLong == expectedId.toLong()) {
                "MCP response identifier does not match the request."
            }
            require(!message.has("method")) { "MCP response unexpectedly contains a method." }

            val hasResult = message.has("result")
            val hasError = message.has("error")
            require(hasResult.xor(hasError)) {
                "MCP response must contain exactly one of result or error."
            }

            if (hasError) {
                val error = message.optJSONObject("error")
                    ?: throw IllegalArgumentException("MCP JSON-RPC error must be an object.")
                val code = error.opt("code") as? Number
                    ?: throw IllegalArgumentException("MCP JSON-RPC error code must be numeric.")
                require(code.toDouble() == code.toLong().toDouble()) {
                    "MCP JSON-RPC error code must be an integer."
                }
                require(error.opt("message") is String) {
                    "MCP JSON-RPC error message must be a string."
                }
            } else {
                require(message.optJSONObject("result") != null) {
                    "MCP method result must be a JSON object."
                }
            }

            require(matchedResponse == null) { "MCP event stream contains duplicate responses." }
            matchedResponse = message
        }

        return matchedResponse
            ?: throw IllegalArgumentException("MCP event stream has no response for this request.")
    }

    private fun readSseDataEvents(body: String): List<String> {
        val events = ArrayList<String>()
        val data = StringBuilder()
        val reader = BufferedReader(StringReader(body))
        var firstLine = true

        fun dispatch() {
            if (data.isEmpty()) return
            require(events.size < MAX_SSE_EVENTS) {
                "MCP event stream exceeds the $MAX_SSE_EVENTS event limit."
            }
            events += data.toString()
            data.setLength(0)
        }

        while (true) {
            var line = reader.readLine() ?: break
            if (firstLine) {
                line = line.removePrefix("\uFEFF")
                firstLine = false
            }
            if (line.isEmpty()) {
                dispatch()
                continue
            }
            if (line.startsWith(":")) continue

            val separator = line.indexOf(':')
            val field = if (separator < 0) line else line.substring(0, separator)
            if (field != "data") continue

            var value = if (separator < 0) "" else line.substring(separator + 1)
            if (value.startsWith(" ")) value = value.substring(1)
            if (data.isNotEmpty()) data.append('\n')
            data.append(value)
            require(data.length <= MAX_SSE_EVENT_CHARS) {
                "MCP SSE event exceeds the response size limit."
            }
        }
        dispatch()
        return events
    }
}
