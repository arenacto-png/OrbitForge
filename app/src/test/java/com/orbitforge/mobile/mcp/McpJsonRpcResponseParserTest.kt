package com.orbitforge.mobile.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpJsonRpcResponseParserTest {
    @Test
    fun parsesJsonResponseWithMatchingNumericId() {
        val parsed = McpJsonRpcResponseParser.parse(
            """{"jsonrpc":"2.0","id":7,"result":{"tools":[]}}""",
            "application/json",
            expectedId = 7
        )

        assertEquals(7, parsed.getInt("id"))
        assertTrue(parsed.getJSONObject("result").getJSONArray("tools").length() == 0)
    }

    @Test
    fun acceptsWellFormedJsonRpcErrorResponseForCallerToHandle() {
        val parsed = McpJsonRpcResponseParser.parse(
            """{"jsonrpc":"2.0","id":7,"error":{"code":-32601,"message":"Method not found"}}""",
            "application/json",
            expectedId = 7
        )

        assertEquals("Method not found", parsed.getJSONObject("error").getString("message"))
    }

    @Test
    fun parsesMultilineSseAndSkipsInterleavedServerNotifications() {
        val body = listOf(
            ": keepalive",
            "",
            """data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}""",
            "",
            "event: message",
            """data: {"jsonrpc":"2.0","id":7,"result":""",
            """data: {"tools":[]} }""",
            ""
        ).joinToString("\n")

        val parsed = McpJsonRpcResponseParser.parse(body, "text/event-stream", expectedId = 7)

        assertEquals(7, parsed.getInt("id"))
        assertTrue(parsed.getJSONObject("result").getJSONArray("tools").length() == 0)
    }

    @Test
    fun parsesSseEventWithoutTrailingBlankLine() {
        val parsed = McpJsonRpcResponseParser.parse(
            """data: {"jsonrpc":"2.0","id":7,"result":{"ok":true}}""",
            "text/event-stream",
            expectedId = 7
        )

        assertEquals(true, parsed.getJSONObject("result").getBoolean("ok"))
    }

    @Test
    fun ignoresUtf8BomBeforeFirstSseField() {
        val parsed = McpJsonRpcResponseParser.parse(
            "\uFEFFdata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}",
            "text/event-stream",
            expectedId = 7
        )

        assertTrue(parsed.getJSONObject("result").getBoolean("ok"))
    }

    @Test
    fun rejectsMismatchedResponseId() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":8,"result":{}}""",
                "application/json",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsWrongVersionMissingIdAndStringId() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"1.0","id":7,"result":{}}""",
                "application/json",
                expectedId = 7
            )
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","result":{}}""",
                "application/json",
                expectedId = 7
            )
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":"7","result":{}}""",
                "application/json",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsNonStringVersionAndExplicitNullId() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":2.0,"id":7,"result":{}}""",
                "application/json",
                expectedId = 7
            )
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """data: {"jsonrpc":"2.0","id":null,"method":"notifications/progress"}""",
                "text/event-stream",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsResponsesWithBothOrNeitherResultAndError() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":7,"result":{},"error":{"code":-1,"message":"bad"}}""",
                "application/json",
                expectedId = 7
            )
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":7}""",
                "application/json",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsMalformedErrorAndNonObjectResult() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":7,"error":{"code":"bad","message":"bad"}}""",
                "application/json",
                expectedId = 7
            )
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","id":7,"result":[]}""",
                "application/json",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsMalformedSseAndStreamsWithoutMatchingResponse() {
        expectRejected {
            McpJsonRpcResponseParser.parse("data: {broken", "text/event-stream", expectedId = 7)
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """data: {"jsonrpc":"2.0","method":"notifications/progress"}""",
                "text/event-stream",
                expectedId = 7
            )
        }
    }

    @Test
    fun rejectsTooManySseEvents() {
        val body = buildString {
            repeat(McpJsonRpcResponseParser.MAX_SSE_EVENTS + 1) { append("data: {}\n\n") }
        }
        expectRejected {
            McpJsonRpcResponseParser.parse(body, "text/event-stream", expectedId = 7)
        }
    }

    @Test
    fun acceptsWellFormedInterleavedNotificationsWithObjectAndArrayParams() {
        val body = listOf(
            """data: {"jsonrpc":"2.0","method":"notifications/progress","params":{"progress":1}}""",
            "",
            """data: {"jsonrpc":"2.0","method":"notifications/message","params":["ready"]}""",
            "",
            """data: {"jsonrpc":"2.0","id":7,"result":{"ok":true}}"""
        ).joinToString("\n")

        val parsed = McpJsonRpcResponseParser.parse(body, "text/event-stream", expectedId = 7)

        assertTrue(parsed.getJSONObject("result").getBoolean("ok"))
    }

    @Test
    fun rejectsNotificationsContainingResultOrErrorFields() {
        val invalidNotifications = listOf(
            """{"jsonrpc":"2.0","method":"notifications/progress","result":{}}""",
            """{"jsonrpc":"2.0","method":"notifications/progress","error":{"code":-1,"message":"bad"}}"""
        )
        invalidNotifications.forEach { notification ->
            expectRejected {
                McpJsonRpcResponseParser.parse(
                    "data: $notification",
                    "text/event-stream",
                    expectedId = 7
                )
            }
        }
    }

    @Test
    fun rejectsNotificationsWithScalarParamsOrBlankMethods() {
        val invalidNotifications = listOf(
            """{"jsonrpc":"2.0","method":"notifications/progress","params":"not-an-object-or-array"}""",
            """{"jsonrpc":"2.0","method":"   "}"""
        )
        invalidNotifications.forEach { notification ->
            expectRejected {
                McpJsonRpcResponseParser.parse(
                    "data: $notification",
                    "text/event-stream",
                    expectedId = 7
                )
            }
        }
    }

    @Test
    fun rejectsNotificationsOutsideAnEventStream() {
        expectRejected {
            McpJsonRpcResponseParser.parse(
                """{"jsonrpc":"2.0","method":"notifications/progress","params":{}}""",
                "application/json",
                expectedId = 7
            )
        }
    }

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected malformed JSON-RPC input to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
