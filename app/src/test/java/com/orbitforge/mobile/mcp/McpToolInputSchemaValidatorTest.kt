package com.orbitforge.mobile.mcp

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolInputSchemaValidatorTest {
    @Test
    fun acceptsObjectSchemaWithNestedPropertiesAndCompositions() {
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("path", JSONObject().put("type", "string").put("description", "Relative path"))
                .put("options", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("recursive", JSONObject().put("type", "boolean")))
                    .put("required", JSONArray().put("recursive"))
                    .put("additionalProperties", false)))
            .put("required", JSONArray().put("path"))
            .put("oneOf", JSONArray().put(JSONObject().put("type", "object")))

        assertTrue(McpToolInputSchemaValidator.isValid(schema))
    }

    @Test
    fun rejectsMissingOrNonObjectRootType() {
        assertFalse(McpToolInputSchemaValidator.isValid(JSONObject()))
        assertFalse(McpToolInputSchemaValidator.isValid(JSONObject().put("type", "array")))
        assertFalse(McpToolInputSchemaValidator.isValid(JSONObject().put("type", 7)))
    }

    @Test
    fun rejectsMalformedPropertiesAndRequiredLists() {
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("properties", "not-an-object")
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object")
                    .put("properties", JSONObject().put("path", "not-a-schema"))
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("required", "path")
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("required", JSONArray().put(1))
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("required", JSONArray().put("path").put("path"))
            )
        )
    }

    @Test
    fun rejectsMalformedItemsAndCompositionKeywords() {
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("items", "not-a-schema")
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("anyOf", JSONObject())
            )
        )
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("additionalProperties", "yes")
            )
        )
    }

    @Test
    fun acceptsBooleanSchemasAndTupleItems() {
        val schema = JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("unrestricted", true))
            .put("items", JSONArray().put(false).put(JSONObject().put("type", "string")))
            .put("additionalProperties", false)

        assertTrue(McpToolInputSchemaValidator.isValid(schema))
    }

    @Test
    fun rejectsOversizedPropertyMaps() {
        val properties = JSONObject()
        repeat(McpToolInputSchemaValidator.MAX_COLLECTION_ITEMS + 1) { index ->
            properties.put("field_" + index, JSONObject().put("type", "string"))
        }
        assertFalse(
            McpToolInputSchemaValidator.isValid(
                JSONObject().put("type", "object").put("properties", properties)
            )
        )
    }

    @Test
    fun rejectsSchemaGraphsAboveTheNodeBudget() {
        val root = JSONObject().put("type", "object")
        var current = root
        repeat(4) { depth ->
            val properties = JSONObject()
            repeat(McpToolInputSchemaValidator.MAX_COLLECTION_ITEMS - 1) { index ->
                properties.put("leaf_" + depth + "_" + index, JSONObject().put("type", "string"))
            }
            val nested = JSONObject().put("type", "object")
            properties.put("nested", nested)
            current.put("properties", properties)
            current = nested
        }

        assertFalse(McpToolInputSchemaValidator.isValid(root))
    }
}
