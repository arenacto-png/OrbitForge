package com.orbitforge.mobile.ai

import org.json.JSONObject

internal data class ProviderUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?
) {
    fun diagnosticSummary(): String = listOfNotNull(
        promptTokens?.let { "prompt_tokens=$it" },
        completionTokens?.let { "completion_tokens=$it" },
        totalTokens?.let { "total_tokens=$it" }
    ).joinToString(" ")

    fun merge(other: ProviderUsage): ProviderUsage = ProviderUsage(
        promptTokens = other.promptTokens ?: promptTokens,
        completionTokens = other.completionTokens ?: completionTokens,
        totalTokens = other.totalTokens ?: totalTokens
    )
}

/** Parses common OpenAI-compatible and Anthropic usage counters without failing on malformed telemetry. */
internal object ProviderUsageParser {
    fun parse(response: JSONObject): ProviderUsage? {
        val usage = response.optJSONObject("usage")
            ?: response.optJSONObject("message")?.optJSONObject("usage")
            ?: return null
        val prompt = readNonNegativeInt(usage, "prompt_tokens", "input_tokens")
        val completion = readNonNegativeInt(usage, "completion_tokens", "output_tokens")
        val total = readNonNegativeInt(usage, "total_tokens")
        if (prompt == null && completion == null && total == null) return null
        return ProviderUsage(prompt, completion, total)
    }

    private fun readNonNegativeInt(json: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            val value = json.opt(key) ?: continue
            val parsed = value.toString().toLongOrNull() ?: continue
            if (parsed in 0..Int.MAX_VALUE.toLong()) return parsed.toInt()
        }
        return null
    }
}
