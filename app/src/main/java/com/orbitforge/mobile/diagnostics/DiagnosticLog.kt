package com.orbitforge.mobile.diagnostics

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight diagnostic log. Never pass raw API keys, Authorization headers, or full prompts here.
 * The redactor is defense-in-depth, not a substitute for avoiding secret logging.
 */
object DiagnosticLog {
    private const val PREFS = "orbitforge_diagnostics"
    private const val KEY_LINES = "lines"
    private const val MAX_LINES = 300

    @Synchronized
    fun add(context: Context, level: String, tag: String, message: String) {
        val safe = redact(message).replace('\n', ' ').take(700)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$stamp [$level] [$tag] $safe"
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY_LINES, "").orEmpty()
        val lines = (old.lines().filter { it.isNotBlank() } + line).takeLast(MAX_LINES)
        prefs.edit().putString(KEY_LINES, lines.joinToString("\n")).apply()
    }

    fun recordCrash(context: Context, throwable: Throwable) {
        add(context, "FATAL", "Crash", "Uncaught ${throwable.javaClass.name}: ${throwable.message ?: "no message"}")
        throwable.stackTrace.take(12).forEach { frame ->
            add(context, "FATAL", "Crash", "at $frame")
        }
        throwable.cause?.let { cause ->
            add(context, "FATAL", "Crash", "Caused by ${cause.javaClass.name}: ${cause.message ?: "no message"}")
            cause.stackTrace.take(6).forEach { frame -> add(context, "FATAL", "Crash", "at $frame") }
        }
    }

    fun read(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LINES, "")
            .orEmpty()
            .ifBlank { "Пока нет диагностических записей." }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LINES).apply()
        add(context, "INFO", "Diagnostics", "Журнал очищен")
    }

    fun redact(input: String): String {
        var result = input
        result = AUTHORIZATION_HEADER.replace(result, "$1[REDACTED]")
        result = DOUBLE_QUOTED_SECRET_ASSIGNMENT.replace(result, "$1\"[REDACTED]\"")
        result = SINGLE_QUOTED_SECRET_ASSIGNMENT.replace(result, "$1'[REDACTED]'")
        result = UNQUOTED_SECRET_ASSIGNMENT.replace(result, "$1[REDACTED]")

        val patterns = listOf(
            Regex("(?i)(sk-[A-Za-z0-9_-]{8,})") to "[REDACTED_API_KEY]",
            Regex("(?i)(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})") to "[REDACTED_GITHUB_TOKEN]",
            Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]{8,}") to "$1[REDACTED]"
        )
        patterns.forEach { (regex, replacement) -> result = regex.replace(result, replacement) }
        return result
    }

    private const val SECRET_ASSIGNMENT_KEY =
        """(?<![A-Za-z0-9])["']?(?:api[_-]?key|access[_-]?token|refresh[_-]?token|client[_-]?secret|private[_-]?key|authorization|token|secret|password|cookie)["']?\s*[:=]\s*"""

    private val AUTHORIZATION_HEADER =
        Regex("(?i)(authorization\\s*[:=]\\s*)[^\\r\\n]+")

    private val DOUBLE_QUOTED_SECRET_ASSIGNMENT =
        Regex("(?i)($SECRET_ASSIGNMENT_KEY)\"(?:\\\\.|[^\"\\\\])*\"")

    private val SINGLE_QUOTED_SECRET_ASSIGNMENT =
        Regex("(?i)($SECRET_ASSIGNMENT_KEY)'(?:\\\\.|[^'\\\\])*'")

    private val UNQUOTED_SECRET_ASSIGNMENT =
        Regex("(?i)($SECRET_ASSIGNMENT_KEY)(?![\"'])([^\\s,;{}\\]]+)")
}
