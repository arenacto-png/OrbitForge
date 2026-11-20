package com.orbitforge.mobile.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/**
 * Performs bounded structural checks on remote MCP input schemas before they are sent to a model.
 * This is deliberately not a complete JSON Schema validator.
 */
internal object McpToolInputSchemaValidator {
    const val MAX_SCHEMA_NODES = 1_024
    const val MAX_COLLECTION_ITEMS = 256

    private val allowedTypes = setOf("array", "boolean", "integer", "null", "number", "object", "string")

    fun isValid(input: JSONObject): Boolean {
        if (input.opt("type") != "object") return false

        val pending = ArrayDeque<JSONObject>()
        pending.addLast(input)
        var visitedNodes = 0

        while (pending.isNotEmpty()) {
            val schema = pending.removeLast()
            visitedNodes += 1
            if (visitedNodes > MAX_SCHEMA_NODES) return false

            if (schema.has("type") && !isValidType(schema.opt("type"))) return false
            if (schema.has("description") && schema.opt("description") !is String) return false

            for (key in listOf("properties", "patternProperties", "definitions", "\$defs", "dependentSchemas")) {
                if (schema.has(key) && !enqueueSchemaMap(schema, key, pending)) return false
            }

            if (schema.has("required") && !isValidStringArray(schema.opt("required"))) return false
            if (schema.has("dependentRequired") && !isValidStringArrayMap(schema.opt("dependentRequired"))) return false

            if (schema.has("enum")) {
                val values = schema.optJSONArray("enum") ?: return false
                if (values.length() !in 1..MAX_COLLECTION_ITEMS) return false
            }

            if (schema.has("items") && !enqueueSchemaOrTuple(schema.opt("items"), pending)) return false
            if (schema.has("prefixItems") && !enqueueSchemaArray(schema.opt("prefixItems"), pending, allowEmpty = true)) return false

            for (key in listOf("additionalProperties", "unevaluatedProperties", "unevaluatedItems")) {
                if (schema.has(key) && !enqueueSchemaValue(schema.opt(key), pending)) return false
            }

            for (key in listOf("allOf", "anyOf", "oneOf")) {
                if (schema.has(key) && !enqueueSchemaArray(schema.opt(key), pending, allowEmpty = false)) return false
            }

            for (key in listOf("not", "if", "then", "else", "contains", "propertyNames")) {
                if (schema.has(key) && !enqueueSchemaValue(schema.opt(key), pending)) return false
            }
        }

        return true
    }

    private fun isValidType(value: Any?): Boolean = when (value) {
        is String -> value in allowedTypes
        is JSONArray -> {
            if (value.length() !in 1..allowedTypes.size) {
                false
            } else {
                val seen = mutableSetOf<String>()
                (0 until value.length()).all { index ->
                    val type = value.opt(index) as? String
                    type != null && type in allowedTypes && seen.add(type)
                }
            }
        }
        else -> false
    }

    private fun isValidStringArray(value: Any?): Boolean {
        val array = value as? JSONArray ?: return false
        if (array.length() > MAX_COLLECTION_ITEMS) return false
        val seen = mutableSetOf<String>()
        return (0 until array.length()).all { index ->
            val entry = array.opt(index) as? String
            entry != null && entry.isNotBlank() && entry.length <= 128 && seen.add(entry)
        }
    }

    private fun isValidStringArrayMap(value: Any?): Boolean {
        val map = value as? JSONObject ?: return false
        if (map.length() > MAX_COLLECTION_ITEMS) return false
        val keys = map.keys()
        while (keys.hasNext()) {
            if (!isValidStringArray(map.opt(keys.next()))) return false
        }
        return true
    }

    private fun enqueueSchemaMap(
        parent: JSONObject,
        key: String,
        pending: ArrayDeque<JSONObject>
    ): Boolean {
        val map = parent.optJSONObject(key) ?: return false
        if (map.length() > MAX_COLLECTION_ITEMS) return false
        val keys = map.keys()
        while (keys.hasNext()) {
            if (!enqueueSchemaValue(map.opt(keys.next()), pending)) return false
        }
        return true
    }

    private fun enqueueSchemaOrTuple(value: Any?, pending: ArrayDeque<JSONObject>): Boolean =
        when (value) {
            is JSONArray -> enqueueSchemaArray(value, pending, allowEmpty = true)
            else -> enqueueSchemaValue(value, pending)
        }

    private fun enqueueSchemaArray(
        value: Any?,
        pending: ArrayDeque<JSONObject>,
        allowEmpty: Boolean
    ): Boolean {
        val array = value as? JSONArray ?: return false
        if (array.length() > MAX_COLLECTION_ITEMS || (!allowEmpty && array.length() == 0)) return false
        for (index in 0 until array.length()) {
            if (!enqueueSchemaValue(array.opt(index), pending)) return false
        }
        return true
    }

    private fun enqueueSchemaValue(value: Any?, pending: ArrayDeque<JSONObject>): Boolean =
        when (value) {
            is JSONObject -> {
                pending.addLast(value)
                true
            }
            is Boolean -> true
            else -> false
        }
}
