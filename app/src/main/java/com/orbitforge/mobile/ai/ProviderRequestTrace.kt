package com.orbitforge.mobile.ai

import java.util.UUID

/** Local-only correlation metadata for a single provider HTTP request. */
internal data class ProviderRequestTrace(
    val id: String,
    private val startedAtNanos: Long
) {
    fun elapsedMillis(nowNanos: Long = System.nanoTime()): Long =
        ((nowNanos - startedAtNanos).coerceAtLeast(0L) / NANOS_PER_MILLI).coerceAtLeast(0L)

    fun event(
        operation: String,
        format: ProviderApiFormat,
        phase: String,
        statusCode: Int? = null,
        durationMillis: Long? = null,
        errorType: String? = null
    ): String = buildList {
        add("request_id=$id")
        add("operation=$operation")
        add("format=${format.name}")
        add("phase=$phase")
        statusCode?.let { add("http_status=$it") }
        durationMillis?.let { add("duration_ms=${it.coerceAtLeast(0L)}") }
        errorType?.let { add("error_type=$it") }
    }.joinToString(" ")

    companion object {
        fun create(): ProviderRequestTrace = ProviderRequestTrace(UUID.randomUUID().toString(), System.nanoTime())

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
