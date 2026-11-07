package com.omnitrack.app.data.sync

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnitrack.app.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class TrackerTokens(
    val anilistToken: String? = null,
    val malToken: String? = null,
    val traktToken: String? = null,
    val simklToken: String? = null,
    val comickToken: String? = null,
    val vndbToken: String? = null,
    val steamApiKey: String? = null
)

/**
 * Portable AES-256-GCM Token Manager.
 * Uses PBKDF2 with a deterministic application-level salt so that tokens can be
 * exported and restored across different devices, clean installs, and backups
 * without being trapped inside device-locked hardware Secure Elements.
 */
class TrackerTokenManager(private val context: Context) {

    private object Keys {
        val ANILIST_TOKEN = stringPreferencesKey("token_anilist")
        val MAL_TOKEN = stringPreferencesKey("token_mal")
        val TRAKT_TOKEN = stringPreferencesKey("token_trakt")
        val SIMKL_TOKEN = stringPreferencesKey("token_simkl")
        val COMICK_TOKEN = stringPreferencesKey("token_comick")
        val VNDB_TOKEN = stringPreferencesKey("token_vndb")
        val STEAM_API_KEY = stringPreferencesKey("token_steam_api")
    }

    val tokens: Flow<TrackerTokens> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            TrackerTokens(
                anilistToken = prefs[Keys.ANILIST_TOKEN]?.let { decrypt(it) },
                malToken = prefs[Keys.MAL_TOKEN]?.let { decrypt(it) },
                traktToken = prefs[Keys.TRAKT_TOKEN]?.let { decrypt(it) },
                simklToken = prefs[Keys.SIMKL_TOKEN]?.let { decrypt(it) },
                comickToken = prefs[Keys.COMICK_TOKEN]?.let { decrypt(it) },
                vndbToken = prefs[Keys.VNDB_TOKEN]?.let { decrypt(it) },
                steamApiKey = prefs[Keys.STEAM_API_KEY]?.let { decrypt(it) }
            )
        }

    suspend fun saveAnilistToken(token: String?) = saveToken(Keys.ANILIST_TOKEN, token)
    suspend fun saveMalToken(token: String?) = saveToken(Keys.MAL_TOKEN, token)
    suspend fun saveTraktToken(token: String?) = saveToken(Keys.TRAKT_TOKEN, token)
    suspend fun saveSimklToken(token: String?) = saveToken(Keys.SIMKL_TOKEN, token)
    suspend fun saveComickToken(token: String?) = saveToken(Keys.COMICK_TOKEN, token)
    suspend fun saveVndbToken(token: String?) = saveToken(Keys.VNDB_TOKEN, token)
    suspend fun saveSteamApiKey(key: String?) = saveToken(Keys.STEAM_API_KEY, key)

    private suspend fun saveToken(key: androidx.datastore.preferences.core.Preferences.Key<String>, token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = encrypt(token)
            }
        }
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12

        // Fixed app portable salt for cross-device .omnibk compatibility
        private val PORTABLE_SALT = byteArrayOf(
            0x4f, 0x6d, 0x6e, 0x69, 0x54, 0x72, 0x61, 0x63,
            0x6b, 0x5f, 0x50, 0x6f, 0x72, 0x74, 0x61, 0x62
        )
        private const val PORTABLE_PASSPHRASE = "OmniTrack_Universal_Master_Vault_Key_2026"

        private val secretKey: SecretKeySpec by lazy {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(PORTABLE_PASSPHRASE.toCharArray(), PORTABLE_SALT, PBKDF2_ITERATIONS, KEY_LENGTH)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        }

        fun encrypt(plainText: String): String {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val iv = ByteArray(GCM_IV_LENGTH)
                SecureRandom().nextBytes(iv)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

                val combined = ByteArray(iv.size + cipherText.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

                Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (_: Exception) {
                plainText
            }
        }

        fun decrypt(cipherTextBase64: String): String? {
            return try {
                val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
                if (combined.size < GCM_IV_LENGTH) return null

                val iv = ByteArray(GCM_IV_LENGTH)
                val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val decrypted = cipher.doFinal(cipherText)
                String(decrypted, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
