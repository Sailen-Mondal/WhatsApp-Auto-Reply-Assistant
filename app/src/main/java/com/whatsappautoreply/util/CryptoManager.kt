package com.whatsappautoreply.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.whatsappautoreply.util.DebugLogger

object CryptoManager {
    private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
    private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
    private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
    private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    private const val KEY_ALIAS = "whatsapp_autoreply_api_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createSecretKey()
    }

    private fun createSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM, ANDROID_KEYSTORE)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(BLOCK_MODE)
            .setEncryptionPaddings(PADDING)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun encrypt(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(bytes)
            val ivAndEncryptedBytes = iv + encryptedBytes
            Base64.encodeToString(ivAndEncryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            DebugLogger.logError("CryptoManager", "Encryption failed", e)
            null
        }
    }

    fun decrypt(encryptedString: String): ByteArray? {
        if (encryptedString.isBlank()) return ByteArray(0)
        return try {
            val ivAndEncryptedBytes = Base64.decode(encryptedString, Base64.DEFAULT)
            // GCM uses a 12-byte IV
            val iv = ivAndEncryptedBytes.copyOfRange(0, 12)
            val encryptedBytes = ivAndEncryptedBytes.copyOfRange(12, ivAndEncryptedBytes.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            DebugLogger.logError("CryptoManager", "Decryption failed", e)
            null
        }
    }
}
