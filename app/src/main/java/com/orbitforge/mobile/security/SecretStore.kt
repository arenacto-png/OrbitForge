package com.orbitforge.mobile.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts user-supplied credentials with AES-GCM keys held by Android Keystore.
 * This is local-at-rest protection, not protection against a compromised/unlocked device.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("orbitforge_secrets", Context.MODE_PRIVATE)

    fun saveApiKey(value: String) = saveSecret("api_key", "orbitforge_api_key_v1", value)
    fun readApiKey(): String = readSecret("api_key", "orbitforge_api_key_v1")
    fun hasApiKey(): Boolean = hasSecret("api_key")

    fun saveGitHubToken(value: String) = saveSecret("github_token", "orbitforge_github_token_v1", value)
    fun readGitHubToken(): String = readSecret("github_token", "orbitforge_github_token_v1")
    fun hasGitHubToken(): Boolean = hasSecret("github_token")

    fun saveMcpToken(value: String) = saveSecret("mcp_token", "orbitforge_mcp_token_v1", value)
    fun readMcpToken(): String = readSecret("mcp_token", "orbitforge_mcp_token_v1")
    fun hasMcpToken(): Boolean = hasSecret("mcp_token")

    private fun saveSecret(slot: String, alias: String, value: String) {
        val cipherKey = "${slot}_cipher"
        val ivKey = "${slot}_iv"
        if (value.isBlank()) {
            prefs.edit().remove(cipherKey).remove(ivKey).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(cipherKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun readSecret(slot: String, alias: String): String {
        val encoded = prefs.getString("${slot}_cipher", null) ?: return ""
        val ivEncoded = prefs.getString("${slot}_iv", null) ?: return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(alias),
                GCMParameterSpec(128, Base64.decode(ivEncoded, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun hasSecret(slot: String): Boolean = prefs.contains("${slot}_cipher")

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
