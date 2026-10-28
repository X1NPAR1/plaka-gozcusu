package com.plakagozcusu.core.datastore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's AI API key encrypted with a non-exportable AES-GCM key in the Android Keystore.
 * Only the ciphertext lives in DataStore (which is excluded from backups). The key is never logged.
 */
@Singleton
class SecretStore
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) {
        val hasAiKey = store.data.map { !it[KEY_AI].isNullOrEmpty() }

        suspend fun setAiKey(value: String?) {
            store.edit { p ->
                val v = value?.trim()
                if (v.isNullOrEmpty()) p.remove(KEY_AI) else p[KEY_AI] = encrypt(v)
            }
        }

        /** Null when not set or when the Keystore key was lost (e.g. device restore): user re-enters it. */
        suspend fun aiKey(): String? = store.data.first()[KEY_AI]?.let { runCatching { decrypt(it) }.getOrNull() }

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            gen.init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return gen.generateKey()
        }

        private fun encrypt(plain: String): String {
            val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val out = c.iv + c.doFinal(plain.toByteArray())
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        private fun decrypt(encoded: String): String {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            return String(c.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }

        private companion object {
            const val ANDROID_KEYSTORE = "AndroidKeyStore"
            const val ALIAS = "plakagozcusu.ai.key"
            const val TRANSFORMATION = "AES/GCM/NoPadding"
            const val IV_BYTES = 12
            const val TAG_BITS = 128
            val KEY_AI = stringPreferencesKey("ai_api_key_enc")
        }
    }
