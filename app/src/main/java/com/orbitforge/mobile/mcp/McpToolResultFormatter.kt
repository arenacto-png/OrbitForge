package com.orbitforge.mobile.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts untrusted MCP tool result blocks into bounded text for the model.
 * Binary media and resource-link URIs are deliberately not forwarded.
 */
internal object McpToolResultFormatter {
    private const val NO_SUPPORTED_CONTENT = "(MCP tool returned no supported content)"
    private const val IMAGE_OMITTED = "[Image payload omitted]"
    private const val AUDIO_OMITTED = "[Audio payload omitted]"

    fun format(result: JSONObject): String {
        val isError = when {
            !result.has("isError") -> false
            result.opt("isError") is Boolean -> result.optBoolean("isError")
            else -> throw IllegalArgumentException("MCP tool isError flag must be a boolean.")
        }
        val sections = mutableListOf<String>()
        if (result.has("content")) {
            val content = result.opt("content") as? JSONArray
                ?: throw IllegalArgumentException("MCP tool content must be an array.")
            for (index in 0 until content.length()) {
                val block = content.optJSONObject(index)
                    ?: throw IllegalArgumentException("MCP tool content block must be an object.")
                val type = block.opt("type") as? String
                    ?: throw IllegalArgumentException("MCP tool content block type must be a string.")
                when (type) {
                    "text" -> {
                        val text = block.opt("text") as? String
                            ?: throw IllegalArgumentException("MCP text content must contain a string.")
                        if (text.isNotBlank()) sections += text
                    }
                    "resource" -> {
                        val resource = block.optJSONObject("resource")
                            ?: throw IllegalArgumentException("MCP embedded resource must be an object.")
                        val text = resource.opt("text") as? String
                        if (!text.isNullOrBlank()) sections += "Embedded resource text:\n$text"
                    }
                    "resource_link" -> {
                        val name = (block.opt("name") as? String).orEmpty().take(120)
                        val description = (block.opt("description") as? String).orEmpty().take(500)
                        val label = listOf(name, description).filter { it.isNotBlank() }.joinToString(": ")
                        if (label.isNotBlank()) sections += "Resource link (URI not forwarded): $label"
                    }
                    "image" -> sections += IMAGE_OMITTED
                    "audio" -> sections += AUDIO_OMITTED
                    else -> sections += "[Unsupported MCP content block omitted]"
                }
            }
        }
        if (result.has("structuredContent")) {
            sections += "Structured result (JSON):\n${serializeJsonValue(result.opt("structuredContent"))}"
        }

        val content = sections.joinToString("\n\n").ifBlank { NO_SUPPORTED_CONTENT }
        val output = if (isError) "MCP tool reported an error:\n$content" else content
        return McpContentSanitizer.toolOutput(output)
    }

    private fun serializeJsonValue(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject, is JSONArray -> value.toString()
        is String -> JSONObject.quote(value)
        is Number, is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
}
