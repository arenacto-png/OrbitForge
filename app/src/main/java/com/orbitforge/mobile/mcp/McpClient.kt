package com.orbitforge.mobile.mcp

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resumeWithException

data class McpTool(
    val name: String,
    val exposedName: String,
    val description: String,
    val inputSchema: JSONObject
)

class McpClient {
    private val http = McpHttpClientPolicy.createClient()

    suspend fun connect(endpoint: String, bearerToken: String = ""): McpSession {
        val normalizedEndpoint = endpoint.trim()
        require(normalizedEndpoint.startsWith("https://")) { "Для удалённого MCP требуется HTTPS." }
        require(!normalizedEndpoint.contains('?') && !normalizedEndpoint.contains('#') &&
            !normalizedEndpoint.substringAfter("://").substringBefore('/').contains('@')) {
            "Не помещай токены или логины в URL MCP; используй отдельное поле bearer token."
        }
        val session = McpSession(normalizedEndpoint, bearerToken, http)
        session.initialize()
        return session
    }
}

class McpSession internal constructor(
    private val endpoint: String,
    private val bearerToken: String,
    private val http: OkHttpClient
) {
    private var sessionId: String? = null
    private var nextId = 1
    var tools: List<McpTool> = emptyList()
        private set

    suspend fun initialize() {
        val initResponse = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "OrbitForge").put("version", "0.5.0"))
        )
        if (initResponse.has("error")) throw IOException("MCP initialize error: ${initResponse.optJSONObject("error")?.optString("message")}")
        request("notifications/initialized", null, notification = true)
        val response = request("tools/list", JSONObject())
        if (response.has("error")) throw IOException("MCP tools/list error: ${response.optJSONObject("error")?.optString("message")}")
        val result = response.optJSONObject("result") ?: JSONObject()
        val list = result.optJSONArray("tools") ?: JSONArray()
        tools = (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name")
            if (name.isBlank()) return@mapNotNull null
            val inputSchema = item.optJSONObject("inputSchema") ?: JSONObject().put("type", "object").put("properties", JSONObject())
            if (inputSchema.toString().length > MAX_SCHEMA_CHARS) return@mapNotNull null
            val safePrefix = endpoint.substringAfter("://").substringBefore('/').lowercase()
                .replace(Regex("[^a-z0-9_]"), "_").take(16)
            val safeName = name.replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
            McpTool(
                name = name,
                exposedName = "mcp_${safePrefix}_${index}_$safeName",
                description = item.optString("description").take(500),
                inputSchema = inputSchema
            )
        }.take(50)
    }

    suspend fun callTool(name: String, arguments: JSONObject): String {
        val response = request(
            "tools/call",
            JSONObject().put("name", name).put("arguments", arguments)
        )
        if (response.has("error")) {
            throw IOException("MCP tool error: ${response.optJSONObject("error")?.optString("message")}")
        }
        val result = response.optJSONObject("result") ?: JSONObject()
        val content = result.optJSONArray("content") ?: JSONArray()
        val output = (0 until content.length()).mapNotNull { index ->
            val item = content.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("type") == "text") item.optString("text") else null
        }.joinToString("\n")
        val bounded = output.take(MAX_RESULT_CHARS)
        return if (result.optBoolean("isError")) "MCP tool reported an error:\n$bounded" else bounded.ifBlank { "(MCP tool returned no text content)" }
    }

    private suspend fun request(method: String, params: JSONObject?, notification: Boolean = false): JSONObject {
        val id = if (notification) null else nextId++
        val payload = JSONObject().put("jsonrpc", "2.0").put("method", method)
        if (id != null) payload.put("id", id)
        if (params != null) payload.put("params", params)
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json, text/event-stream")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        if (bearerToken.isNotBlank()) builder.header("Authorization", "Bearer $bearerToken")
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        val result = execute(builder.build())
        result.sessionId?.let { sessionId = it }
        if (result.code in 300..399) {
            throw IOException(
                "MCP endpoint returned HTTP ${result.code}; automatic redirects are disabled. " +
                    "Configure the final HTTPS endpoint directly."
            )
        }
        if (result.code !in 200..299 && !(notification && result.code == 202)) {
            throw IOException("MCP HTTP ${result.code}: ${result.body.take(300)}")
        }
        if (notification && result.body.isBlank()) return JSONObject()
        val jsonText = if (result.contentType.contains("text/event-stream", ignoreCase = true)) {
            result.body.lineSequence().firstOrNull { it.startsWith("data:") }?.removePrefix("data:")?.trim().orEmpty()
        } else result.body
        return try { JSONObject(jsonText) } catch (e: Exception) {
            throw IOException("MCP вернул неподдерживаемый ответ. Ожидался JSON или SSE data event.")
        }
    }

    private suspend fun execute(request: Request): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val result = HttpResult(
                                code = response.code,
                                body = response.body?.byteStream()?.use { McpResponseBodyReader.read(it) }.orEmpty(),
                                contentType = response.header("Content-Type").orEmpty(),
                                sessionId = response.header("Mcp-Session-Id")
                            )
                            if (continuation.isActive) continuation.resumeWith(Result.success(result))
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            })
        }

    private data class HttpResult(val code: Int, val body: String, val contentType: String, val sessionId: String?)

    companion object {
        private const val MAX_RESULT_CHARS = 20_000
        private const val MAX_SCHEMA_CHARS = 20_000
    }
}
