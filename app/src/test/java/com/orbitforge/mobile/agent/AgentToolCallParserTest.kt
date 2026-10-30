package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgentToolCallParserTest {
    private val allowedTools = setOf("read_file", "search_text")

    @Test
    fun parsesEveryCallAndPreservesArguments() {
        val parsed = AgentToolCallParser.parse(
            message(
                call("call-1", "read_file", """{"path":"README.md"}"""),
                call("call-2", "search_text", """{"query":"TODO"}""")
            ),
            allowedTools
        )

        assertEquals(2, parsed.size)
        assertEquals("call-1", parsed[0].id)
        assertEquals("read_file", parsed[0].name)
        assertEquals("README.md", parsed[0].arguments.getString("path"))
        assertEquals("TODO", parsed[1].arguments.getString("query"))
    }

    @Test
    fun absentNullAndEmptyToolCallsMeanNoCalls() {
        assertTrue(AgentToolCallParser.parse(JSONObject(), allowedTools).isEmpty())
        assertTrue(
            AgentToolCallParser.parse(JSONObject().put("tool_calls", JSONObject.NULL), allowedTools).isEmpty()
        )
        assertTrue(
            AgentToolCallParser.parse(JSONObject().put("tool_calls", JSONArray()), allowedTools).isEmpty()
        )
    }

    @Test
    fun rejectsWrongToolCallsContainerAndNonObjectEntries() {
        expectRejected { AgentToolCallParser.parse(JSONObject().put("tool_calls", "not-an-array"), allowedTools) }
        expectRejected {
            AgentToolCallParser.parse(
                JSONObject().put("tool_calls", JSONArray().put("not-an-object")),
                allowedTools
            )
        }
    }

    @Test
    fun rejectsMissingOrInvalidCallIdentityAndFunction() {
        expectRejected { AgentToolCallParser.parse(message(JSONObject().put("function", function("read_file", "{}"))), allowedTools) }
        expectRejected { AgentToolCallParser.parse(message(JSONObject().put("id", "call-1")), allowedTools) }
        expectRejected {
            AgentToolCallParser.parse(
                message(JSONObject().put("id", "call-1").put("function", JSONObject())),
                allowedTools
            )
        }
        expectRejected {
            AgentToolCallParser.parse(
                message(JSONObject().put("id", " ").put("function", function("read_file", "{}"))),
                allowedTools
            )
        }
    }

    @Test
    fun rejectsDuplicateIdsUnknownNamesAndInvalidNames() {
        expectRejected {
            AgentToolCallParser.parse(
                message(call("same", "read_file", "{}"), call("same", "search_text", "{}")),
                allowedTools
            )
        }
        expectRejected {
            AgentToolCallParser.parse(message(call("call-1", "delete_everything", "{}")), allowedTools)
        }
        expectRejected {
            AgentToolCallParser.parse(message(call("call-1", "read file", "{}")), allowedTools)
        }
    }

    @Test
    fun rejectsMalformedNonObjectAndOversizedArguments() {
        expectRejected {
            AgentToolCallParser.parse(message(call("call-1", "read_file", "{broken")), allowedTools)
        }
        expectRejected {
            AgentToolCallParser.parse(message(call("call-1", "read_file", """["path"]""")), allowedTools)
        }
        expectRejected {
            AgentToolCallParser.parse(
                message(call("call-1", "read_file", """{"value":"${"x".repeat(AgentToolCallParser.MAX_ARGUMENT_CHARS)}"}""")),
                allowedTools
            )
        }
    }

    @Test
    fun rejectsBatchesAboveThePerRoundLimitBeforeReturningAnyCall() {
        val calls = (1..AgentToolCallParser.MAX_TOOL_CALLS_PER_ROUND + 1).map {
            call("call-$it", "read_file", "{}")
        }
        expectRejected { AgentToolCallParser.parse(message(*calls.toTypedArray()), allowedTools) }
    }

    private fun message(vararg calls: JSONObject): JSONObject =
        JSONObject().put("tool_calls", JSONArray().apply { calls.forEach(::put) })

    private fun call(id: String, name: String, arguments: String): JSONObject =
        JSONObject().put("id", id).put("type", "function").put("function", function(name, arguments))

    private fun function(name: String, arguments: String): JSONObject =
        JSONObject().put("name", name).put("arguments", arguments)

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected malformed or unsafe tool-call input to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
