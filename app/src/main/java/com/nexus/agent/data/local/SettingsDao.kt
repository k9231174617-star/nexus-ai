package com.nexus.agent.data.local

import androidx.room.*

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val apiKey: String? = null,
    val endpoint: String? = null,
    val mainModel: String? = null,
    val codeModel: String? = null,
    val uniModel: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val streamEnabled: Boolean = true,
    val autoCliEnabled: Boolean = false,
    val rootModeEnabled: Boolean = false,
    val saveHistory: Boolean = true,
)

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsSync(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun clearSettings()
}