package com.nexus.agent.ui.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.nexus.agent.core.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure API Key Manager using Android Keystore + EncryptedSharedPreferences
 */
class APIKeyManager(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "api_keys_secure"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_api_key_master"
        private const val KEY_SIZE = 256
    }

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Store API key securely for a specific provider
     */
    suspend fun storeKey(providerId: String, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Validate key format before storing
            if (apiKey.length < 10) {
                return@withContext Result.failure(IllegalArgumentException("API key too short"))
            }

            encryptedPrefs.edit {
                putString("key_$providerId", apiKey)
                putLong("stored_at_${providerId}", System.currentTimeMillis())
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Retrieve decrypted API key for a provider
     */
    suspend fun getKey(providerId: String): String? = withContext(Dispatchers.IO) {
        encryptedPrefs.getString("key_$providerId", null)
    }

    /**
     * Check if a key exists for provider
     */
    fun hasKey(providerId: String): Boolean {
        return encryptedPrefs.contains("key_$providerId")
    }

    /**
     * Delete stored key for provider
     */
    suspend fun deleteKey(providerId: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit {
            remove("key_$providerId")
            remove("stored_at_${providerId}")
        }
    }

    /**
     * Get all stored key metadata (without exposing actual keys)
     */
    fun getStoredKeyMetadata(): List<KeyMetadata> {
        return encryptedPrefs.all.keys
            .filter { it.startsWith("stored_at_") }
            .map { key ->
                val providerId = key.removePrefix("stored_at_")
                KeyMetadata(
                    providerId = providerId,
                    storedAt = encryptedPrefs.getLong(key, 0),
                    hasKey = encryptedPrefs.contains("key_$providerId")
                )
            }
    }

    /**
     * Rotate/refresh a key
     */
    suspend fun rotateKey(providerId: String, newKey: String): Result<Unit> {
        deleteKey(providerId)
        return storeKey(providerId, newKey)
    }

    /**
     * Clear all stored keys (dangerous - use with confirmation)
     */
    suspend fun clearAllKeys() = withContext(Dispatchers.IO) {
        encryptedPrefs.edit { clear() }
    }

    /**
     * Validate key format for common providers
     */
    fun validateKeyFormat(providerId: String, key: String): ValidationResult {
        return when (providerId) {
            "openai" -> validateOpenAIKey(key)
            "anthropic" -> validateAnthropicKey(key)
            "google" -> validateGoogleKey(key)
            "groq" -> validateGroqKey(key)
            else -> ValidationResult(true, "No specific validation for this provider")
        }
    }

    private fun validateOpenAIKey(key: String): ValidationResult {
        return when {
            !key.startsWith("sk-") -> ValidationResult(false, "OpenAI keys must start with 'sk-'")
            key.length < 40 -> ValidationResult(false, "OpenAI key appears too short")
            else -> ValidationResult(true, "Valid OpenAI key format")
        }
    }

    private fun validateAnthropicKey(key: String): ValidationResult {
        return when {
            !key.startsWith("sk-ant-") -> ValidationResult(false, "Anthropic keys must start with 'sk-ant-'")
            key.length < 50 -> ValidationResult(false, "Anthropic key appears too short")
            else -> ValidationResult(true, "Valid Anthropic key format")
        }
    }

    private fun validateGoogleKey(key: String): ValidationResult {
        return when {
            key.length < 30 -> ValidationResult(false, "Google API key appears too short")
            else -> ValidationResult(true, "Valid Google key format")
        }
    }

    private fun validateGroqKey(key: String): ValidationResult {
        return when {
            !key.startsWith("gsk_") -> ValidationResult(false, "Groq keys must start with 'gsk_'")
            key.length < 40 -> ValidationResult(false, "Groq key appears too short")
            else -> ValidationResult(true, "Valid Groq key format")
        }
    }

    data class KeyMetadata(
        val providerId: String,
        val storedAt: Long,
        val hasKey: Boolean
    )

    data class ValidationResult(
        val isValid: Boolean,
        val message: String
    )
}
