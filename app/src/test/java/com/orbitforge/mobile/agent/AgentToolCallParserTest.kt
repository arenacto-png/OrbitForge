package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolCallParserTest {
    private fun call(
        id: String = "call_1",
        name: String = "read_file",
        arguments: String = "{\"path\":\"README.md\"}"
    ) = JSONObject()
        .put("id", id)
        .put("type", "function")
        .put("function", JSONObject().put("name", name).put("arguments", arguments))

    @Test
    fun parsesValidFunctionCall() {
        val parsed = AgentToolCallParser.parse(JSONArray().put(call()))

        assertEquals(1, parsed.size)
        assertEquals("call_1", parsed.single().id)
        assertEquals("read_file", parsed.single().name)
        assertEquals("README.md", parsed.single().arguments.getString("path"))
    }

    @Test
    fun emptyOrMissingToolCallsAreAllowed() {
        assertTrue(AgentToolCallParser.parse(null).isEmpty())
        assertTrue(AgentToolCallParser.parse(JSONArray()).isEmpty())
    }

    @Test
    fun rejectsMoreThanConfiguredCallLimit() {
        val calls = JSONArray().put(call("call_1")).put(call("call_2"))

        assertIllegalArgument { AgentToolCallParser.parse(calls, maxCalls = 1) }
    }

    @Test
    fun rejectsMalformedArgumentsInsteadOfReplacingThemWithEmptyObject() {
        assertIllegalArgument {
            AgentToolCallParser.parse(JSONArray().put(call(arguments = "{not-json")))
        }
    }

    @Test
    fun rejectsOversizedArguments() {
        val large = "{\"value\":\"" + "x".repeat(40) + "\"}"

        assertIllegalArgument {
            AgentToolCallParser.parse(JSONArray().put(call(arguments = large)), maxArgumentChars = 20)
        }
    }

    @Test
    fun rejectsMissingIdAndInvalidFunctionName() {
        assertIllegalArgument {
            AgentToolCallParser.parse(JSONArray().put(call(id = "")))
        }
        assertIllegalArgument {
            AgentToolCallParser.parse(JSONArray().put(call(name = "write file")))
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
