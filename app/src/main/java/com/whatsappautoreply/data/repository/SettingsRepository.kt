package com.whatsappautoreply.data.repository

import com.whatsappautoreply.data.database.dao.SettingsDao
import com.whatsappautoreply.data.database.entity.SettingsEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import com.whatsappautoreply.util.CryptoManager

@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDao: SettingsDao
) {
    suspend fun getString(key: String): String? {
        val value = settingsDao.getSettingValue(key) ?: return null
        if (key == "api_key" && value.isNotBlank()) {
            // Attempt decryption. If it fails, maybe it's plain text from before encryption was added.
            val decryptedBytes = CryptoManager.decrypt(value)
            return if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                String(decryptedBytes, Charsets.UTF_8)
            } else {
                value // Return as-is if decryption fails (fallback for legacy plaintext)
            }
        }
        return value
    }

    fun getStringFlow(key: String): kotlinx.coroutines.flow.Flow<String?> {
        return settingsDao.getSettingFlow(key).map { entity ->
            val value = entity?.value
            if (key == "api_key" && !value.isNullOrBlank()) {
                val decryptedBytes = CryptoManager.decrypt(value)
                if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                    String(decryptedBytes, Charsets.UTF_8)
                } else {
                    value
                }
            } else {
                value
            }
        }
    }

    suspend fun setString(key: String, value: String) {
        val finalValue = if (key == "api_key" && value.isNotBlank()) {
            CryptoManager.encrypt(value.toByteArray(Charsets.UTF_8)) ?: value
        } else {
            value
        }
        settingsDao.insertSetting(SettingsEntity(key = key, value = finalValue))
    }

    suspend fun clear(key: String) {
        settingsDao.deleteSetting(key)
    }
}


