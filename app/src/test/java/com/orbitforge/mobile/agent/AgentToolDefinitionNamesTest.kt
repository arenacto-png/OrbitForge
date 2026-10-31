package com.orbitforge.mobile.agent

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AgentToolDefinitionNamesTest {
    @Test
    fun derivesAllowlistFromLocalAndRemoteFunctionDefinitions() {
        val definitions = JSONArray()
            .put(definition("read_file"))
            .put(definition("mcp_example_0_search"))

        assertEquals(
            linkedSetOf("read_file", "mcp_example_0_search"),
            AgentToolDefinitionNames.from(definitions)
        )
    }

    @Test
    fun rejectsMalformedOrNonFunctionDefinitions() {
        expectRejected { AgentToolDefinitionNames.from(JSONArray().put("not-an-object")) }
        expectRejected {
            AgentToolDefinitionNames.from(
                JSONArray().put(JSONObject().put("type", "custom").put("function", JSONObject().put("name", "read_file")))
            )
        }
        expectRejected {
            AgentToolDefinitionNames.from(
                JSONArray().put(JSONObject().put("type", "function"))
            )
        }
    }

    @Test
    fun rejectsInvalidAndDuplicateNames() {
        expectRejected { AgentToolDefinitionNames.from(JSONArray().put(definition("read file"))) }
        expectRejected {
            AgentToolDefinitionNames.from(
                JSONArray().put(definition("read_file")).put(definition("read_file"))
            )
        }
    }

    private fun definition(name: String): JSONObject =
        JSONObject()
            .put("type", "function")
            .put("function", JSONObject().put("name", name))

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid tool definitions to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
