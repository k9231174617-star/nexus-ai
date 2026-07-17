package com.nexus.agent.core.trading

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletManager @Inject constructor(
    private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "nexus_wallet_secure",
            masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isConfigured(): Boolean = prefs.getString("private_key", null)?.isNotBlank() == true

    fun configure(config: WalletConfig) {
        require(config.privateKey.isNotBlank()) { "Private key cannot be empty" }
        prefs.edit()
            .putString("private_key", config.privateKey)
            .putString("rpc_url", config.rpcUrl)
            .putString("preferred_dex", config.preferredDex)
            .apply()
    }

    fun getAddress(): String = "0x${prefs.getString("private_key", "")?.takeLast(20) ?: "..."}"
    fun clear() { prefs.edit().clear().apply() }
}
