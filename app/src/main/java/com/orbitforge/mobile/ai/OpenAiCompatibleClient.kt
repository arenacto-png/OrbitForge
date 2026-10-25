package com.orbitforge.mobile.ai

import android.content.Context
import com.orbitforge.mobile.diagnostics.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ApiMessage(val role: String, val content: String)

class OpenAiCompatibleClient(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun streamChat(
        config: ProviderConfig,
        apiKey: String,
        messages: List<ApiMessage>
    ): Flow<String> = callbackFlow {
        if (apiKey.isBlank()) {
            close(IllegalStateException("Сначала укажи API-ключ в настройках."))
            return@callbackFlow
        }
        if (config.model.isBlank()) {
            close(IllegalStateException("Укажи идентификатор модели в настройках."))
            return@callbackFlow
        }
        val requestBody = JSONObject().apply {
            put("model", config.model)
            put("stream", true)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().put("role", message.role).put("content", message.content))
                }
            })
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = try {
            Request.Builder()
                .url(config.chatCompletionsUrl())
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()
        } catch (e: Exception) {
            close(IllegalArgumentException("Некорректный адрес API: ${e.message}"))
            return@callbackFlow
        }

        val call = http.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty().take(1200)
                        val safeBody = DiagnosticLog.redact(body)
                        DiagnosticLog.add(context, "ERROR", "ApiClient", "HTTP ${response.code}: $safeBody")
                        throw IOException(ProviderErrorMessages.forHttpStatus(response.code, safeBody))
                    }
                    val source = response.body?.source()
                        ?: throw IOException("API вернул пустой ответ.")
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val root = JSONObject(data)
                            val choices = root.optJSONArray("choices") ?: continue
                            val delta = choices.optJSONObject(0)?.optJSONObject("delta")
                            val content = delta?.optString("content").orEmpty()
                            if (content.isNotEmpty() && content != "null") trySend(content)
                            val finish = choices.optJSONObject(0)?.optString("finish_reason")
                            if (finish == "stop") break
                        } catch (e: Exception) {
                            DiagnosticLog.add(context, "WARN", "ApiClient", "Не удалось разобрать SSE-событие: ${e.javaClass.simpleName}")
                        }
                    }
                }
                close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticLog.add(context, "ERROR", "ApiClient", "${e.javaClass.simpleName}: ${e.message ?: "network error"}")
                close(e)
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }
}
