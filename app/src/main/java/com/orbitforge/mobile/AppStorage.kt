package com.orbitforge.mobile

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Non-secret preferences and app-private workspace storage. API keys live in SecretStore. */
class AppStorage(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("orbitforge_settings", Context.MODE_PRIVATE)

    var endpoint: String
        get() = prefs.getString("endpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(value) { prefs.edit().putString("endpoint", value.trim().trimEnd('/')).apply() }
    var model: String
        get() = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        set(value) { prefs.edit().putString("model", value.trim()).apply() }

    var mcpEndpoint: String
        get() = prefs.getString("mcp_endpoint", "") ?: ""
        set(value) { prefs.edit().putString("mcp_endpoint", value.trim()).apply() }

    var projectTreeUri: String
        get() = prefs.getString("project_tree_uri", "") ?: ""
        set(value) { prefs.edit().putString("project_tree_uri", value).apply() }

    fun workspace(): File = File(appContext.filesDir, "orbitforge-workspace").apply { mkdirs() }

    fun loadMessages(): JSONArray = try {
        JSONArray(prefs.getString("chat_history", "[]") ?: "[]")
    } catch (_: Exception) { JSONArray() }

    fun saveMessages(messages: JSONArray) {
        val bounded = JSONArray()
        val start = (messages.length() - 100).coerceAtLeast(0)
        for (i in start until messages.length()) bounded.put(messages.getJSONObject(i))
        prefs.edit().putString("chat_history", bounded.toString()).apply()
    }
}
