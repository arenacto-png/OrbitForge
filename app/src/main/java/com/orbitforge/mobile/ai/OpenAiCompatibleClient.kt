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
import org.json.JSONObject
import com.orbitforge.mobile.network.BoundedResponseBodyReader
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
        val spec = try {
            ProviderApiAdapter.chatRequest(config, apiKey, messages, stream = true)
        } catch (e: Exception) {
            close(IllegalArgumentException("Некорректная конфигурация API: " + e.message))
            return@callbackFlow
        }
        val request = try {
            Request.Builder()
                .url(spec.url)
                .apply { spec.headers.forEach { (name, value) -> header(name, value) } }
                .post(spec.body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
        } catch (e: Exception) {
            close(IllegalArgumentException("Некорректный адрес API: " + e.message))
            return@callbackFlow
        }

        val trace = ProviderRequestTrace.create()
        DiagnosticLog.add(context, "INFO", "ProviderRequest", trace.event("chat", config.apiFormat, "started"))
        val call = http.newCall(request)
        val job = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    DiagnosticLog.add(
                        context,
                        if (response.isSuccessful) "INFO" else "ERROR",
                        "ProviderRequest",
                        trace.event(
                            "chat",
                            config.apiFormat,
                            if (response.isSuccessful) "headers_received" else "http_error",
                            statusCode = response.code,
                            durationMillis = trace.elapsedMillis()
                        )
                    )
                    if (!response.isSuccessful) {
                        val body = try {
                            response.body?.byteStream()?.use {
                                BoundedResponseBodyReader.read(it, MAX_ERROR_BODY_BYTES)
                            }.orEmpty()
                        } catch (_: IOException) {
                            DiagnosticLog.add(
                                context,
                                "WARN",
                                "ApiClient",
                                "request_id=${trace.id} HTTP " + response.code + ": response error body omitted (read limit or I/O failure)"
                            )
                            ""
                        }
                        val safeBody = DiagnosticLog.redact(body).take(1200)
                        DiagnosticLog.add(
                            context,
                            "ERROR",
                            "ApiClient",
                            "request_id=${trace.id} HTTP " + response.code + ": " + safeBody.ifBlank { "<empty or omitted>" }
                        )
                        throw ProviderHttpException(
                            response.code,
                            "${ProviderErrorMessages.forHttpStatus(response.code, safeBody)} [request_id=${trace.id}]"
                        )
                    }
                    val source = response.body?.source()
                        ?: throw IOException("API вернул пустой ответ.")
                    var observedUsage: ProviderUsage? = null
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val event = JSONObject(data)
                            ProviderUsageParser.parse(event)?.let { usage ->
                                observedUsage = observedUsage?.merge(usage) ?: usage
                            }
                            if (ProviderApiAdapter.isStreamFinished(config.apiFormat, event)) break
                            val content = ProviderApiAdapter.parseStreamText(config.apiFormat, event)
                            if (!content.isNullOrEmpty()) trySend(content)
                        } catch (e: IOException) {
                            throw e
                        } catch (e: Exception) {
                            DiagnosticLog.add(
                                context,
                                "WARN",
                                "ApiClient",
                                "request_id=${trace.id} Не удалось разобрать SSE-событие: " + e.javaClass.simpleName
                            )
                        }
                    }
                    observedUsage?.let { usage ->
                        DiagnosticLog.add(
                            context,
                            "INFO",
                            "ProviderUsage",
                            "request_id=${trace.id} ${usage.diagnosticSummary()}"
                        )
                    }
                    DiagnosticLog.add(
                        context,
                        "INFO",
                        "ProviderRequest",
                        trace.event(
                            "chat",
                            config.apiFormat,
                            "completed",
                            statusCode = response.code,
                            durationMillis = trace.elapsedMillis()
                        )
                    )
                }
                close()
            } catch (e: CancellationException) {
                DiagnosticLog.add(
                    context,
                    "INFO",
                    "ProviderRequest",
                    trace.event("chat", config.apiFormat, "cancelled", durationMillis = trace.elapsedMillis())
                )
                throw e
            } catch (e: Exception) {
                val isNetworkFailure = e is IOException && ProviderErrorMessages.isNetworkFailure(e)
                val safeMessage = if (isNetworkFailure) {
                    ProviderErrorMessages.forNetworkFailure(e as IOException)
                } else {
                    DiagnosticLog.redact(e.message.orEmpty()).take(300).ifBlank { "provider response error" }
                }
                DiagnosticLog.add(
                    context,
                    "ERROR",
                    "ProviderRequest",
                    trace.event(
                        "chat",
                        config.apiFormat,
                        "failed",
                        durationMillis = trace.elapsedMillis(),
                        errorType = e.javaClass.simpleName
                    )
                )
                DiagnosticLog.add(
                    context,
                    "ERROR",
                    "ApiClient",
                    "request_id=${trace.id} " +
                        (if (isNetworkFailure) "Network failure ${e.javaClass.simpleName}" else "${e.javaClass.simpleName}: $safeMessage")
                )
                close(
                    if (isNetworkFailure) IOException("$safeMessage [request_id=${trace.id}]", e) else e
                )
            }
        }

        awaitClose {
            call.cancel()
            job.cancel()
        }
    }

    private companion object {
        const val MAX_ERROR_BODY_BYTES = 64 * 1024
    }
}
