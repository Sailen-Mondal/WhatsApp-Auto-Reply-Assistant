package com.whatsappautoreply.data.database.dao

import androidx.room.*
import com.whatsappautoreply.data.database.entity.SettingsEntity

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE `key` = :key")
    suspend fun getSetting(key: String): SettingsEntity?

    @Query("SELECT * FROM settings WHERE `key` = :key")
    fun getSettingFlow(key: String): kotlinx.coroutines.flow.Flow<SettingsEntity?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingsEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)
}

