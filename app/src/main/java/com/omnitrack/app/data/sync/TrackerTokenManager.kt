package com.omnitrack.app.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class TrackerTokens(
    val anilistToken: String? = null,
    val malToken: String? = null,
    val malRefreshToken: String? = null,
    val traktToken: String? = null,
    val traktRefreshToken: String? = null,
    val simklToken: String? = null,
    val simklRefreshToken: String? = null,
    val comickToken: String? = null,
    val comickRefreshToken: String? = null,
    val vndbToken: String? = null,
    val steamApiKey: String? = null,
    val steamId64: String? = null,
    val bangumiToken: String? = null,
    val bangumiRefreshToken: String? = null,
    val tmdbToken: String? = null,
    val tmdbSessionId: String? = null,
    val tmdbAccountId: String? = null,
    val igdbToken: String? = null,
    val hardcoverToken: String? = null
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
        val MAL_REFRESH_TOKEN = stringPreferencesKey("token_mal_refresh")
        val TRAKT_TOKEN = stringPreferencesKey("token_trakt")
        val TRAKT_REFRESH_TOKEN = stringPreferencesKey("token_trakt_refresh")
        val SIMKL_TOKEN = stringPreferencesKey("token_simkl")
        val SIMKL_REFRESH_TOKEN = stringPreferencesKey("token_simkl_refresh")
        val COMICK_TOKEN = stringPreferencesKey("token_comick")
        val COMICK_REFRESH_TOKEN = stringPreferencesKey("token_comick_refresh")
        val VNDB_TOKEN = stringPreferencesKey("token_vndb")
        val STEAM_API_KEY = stringPreferencesKey("token_steam_api")
        val STEAM_ID_64 = stringPreferencesKey("token_steam_id_64")
        val BANGUMI_TOKEN = stringPreferencesKey("token_bangumi")
        val BANGUMI_REFRESH_TOKEN = stringPreferencesKey("token_bangumi_refresh")
        val TMDB_TOKEN = stringPreferencesKey("token_tmdb")
        val TMDB_SESSION_ID = stringPreferencesKey("token_tmdb_session")
        val TMDB_ACCOUNT_ID = stringPreferencesKey("token_tmdb_account")
        val TMDB_PENDING_REQUEST_TOKEN = stringPreferencesKey("tmdb_pending_req_token")
        val IGDB_TOKEN = stringPreferencesKey("token_igdb")
        val HARDCOVER_TOKEN = stringPreferencesKey("token_hardcover")
        val COMICK_VERIFIER = stringPreferencesKey("pkce_comick_verifier")
        val MAL_VERIFIER = stringPreferencesKey("pkce_mal_verifier")
        val TRAKT_VERIFIER = stringPreferencesKey("pkce_trakt_verifier")
        val OAUTH_STATE = stringPreferencesKey("pkce_oauth_state")
        val FANFIC_COOKIE_AO3 = stringPreferencesKey("fanfic_cookie_ao3")
        val FANFIC_COOKIE_SB = stringPreferencesKey("fanfic_cookie_sb")
        val FANFIC_COOKIE_SV = stringPreferencesKey("fanfic_cookie_sv")
        val FANFIC_COOKIE_QQ = stringPreferencesKey("fanfic_cookie_qq")
        val FANFIC_COOKIE_FFN = stringPreferencesKey("fanfic_cookie_ffn")
        val FANFIC_AO3_USERNAME = stringPreferencesKey("fanfic_ao3_username")
    }

    val tokens: Flow<TrackerTokens> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            TrackerTokens(
                anilistToken = prefs[Keys.ANILIST_TOKEN]?.let { decrypt(it) },
                malToken = prefs[Keys.MAL_TOKEN]?.let { decrypt(it) },
                malRefreshToken = prefs[Keys.MAL_REFRESH_TOKEN]?.let { decrypt(it) },
                traktToken = prefs[Keys.TRAKT_TOKEN]?.let { decrypt(it) },
                traktRefreshToken = prefs[Keys.TRAKT_REFRESH_TOKEN]?.let { decrypt(it) },
                simklToken = prefs[Keys.SIMKL_TOKEN]?.let { decrypt(it) },
                simklRefreshToken = prefs[Keys.SIMKL_REFRESH_TOKEN]?.let { decrypt(it) },
                comickToken = prefs[Keys.COMICK_TOKEN]?.let { decrypt(it) },
                comickRefreshToken = prefs[Keys.COMICK_REFRESH_TOKEN]?.let { decrypt(it) },
                vndbToken = prefs[Keys.VNDB_TOKEN]?.let { decrypt(it) },
                steamApiKey = prefs[Keys.STEAM_API_KEY]?.let { decrypt(it) },
                steamId64 = prefs[Keys.STEAM_ID_64]?.let { decrypt(it) },
                bangumiToken = prefs[Keys.BANGUMI_TOKEN]?.let { decrypt(it) },
                bangumiRefreshToken = prefs[Keys.BANGUMI_REFRESH_TOKEN]?.let { decrypt(it) },
                tmdbToken = prefs[Keys.TMDB_TOKEN]?.let { decrypt(it) },
                tmdbSessionId = prefs[Keys.TMDB_SESSION_ID]?.let { decrypt(it) },
                tmdbAccountId = prefs[Keys.TMDB_ACCOUNT_ID]?.let { decrypt(it) },
                igdbToken = prefs[Keys.IGDB_TOKEN]?.let { decrypt(it) },
                hardcoverToken = prefs[Keys.HARDCOVER_TOKEN]?.let { decrypt(it) }
            )
        }

    suspend fun saveAnilistToken(token: String?) = saveToken(Keys.ANILIST_TOKEN, token)

    suspend fun saveMalToken(token: String?) {
        saveToken(Keys.MAL_TOKEN, token)
        if (token.isNullOrBlank()) saveToken(Keys.MAL_REFRESH_TOKEN, null)
    }

    suspend fun saveMalTokens(accessToken: String?, refreshToken: String?) {
        saveToken(Keys.MAL_TOKEN, accessToken)
        if (accessToken.isNullOrBlank()) {
            saveToken(Keys.MAL_REFRESH_TOKEN, null)
        } else if (!refreshToken.isNullOrBlank()) {
            saveToken(Keys.MAL_REFRESH_TOKEN, refreshToken)
        }
    }

    suspend fun saveTraktToken(token: String?) {
        saveToken(Keys.TRAKT_TOKEN, token)
        if (token.isNullOrBlank()) saveToken(Keys.TRAKT_REFRESH_TOKEN, null)
    }

    suspend fun saveTraktTokens(accessToken: String?, refreshToken: String?) {
        saveToken(Keys.TRAKT_TOKEN, accessToken)
        if (accessToken.isNullOrBlank()) {
            saveToken(Keys.TRAKT_REFRESH_TOKEN, null)
        } else if (!refreshToken.isNullOrBlank()) {
            saveToken(Keys.TRAKT_REFRESH_TOKEN, refreshToken)
        }
    }

    suspend fun saveSimklToken(token: String?) {
        saveToken(Keys.SIMKL_TOKEN, token)
        if (token.isNullOrBlank()) saveToken(Keys.SIMKL_REFRESH_TOKEN, null)
    }

    suspend fun saveSimklTokens(accessToken: String?, refreshToken: String?) {
        saveToken(Keys.SIMKL_TOKEN, accessToken)
        if (accessToken.isNullOrBlank()) {
            saveToken(Keys.SIMKL_REFRESH_TOKEN, null)
        } else if (!refreshToken.isNullOrBlank()) {
            saveToken(Keys.SIMKL_REFRESH_TOKEN, refreshToken)
        }
    }

    suspend fun saveComickToken(token: String?) {
        saveToken(Keys.COMICK_TOKEN, token)
        if (token.isNullOrBlank()) saveToken(Keys.COMICK_REFRESH_TOKEN, null)
    }

    suspend fun saveComickTokens(accessToken: String?, refreshToken: String?) {
        saveToken(Keys.COMICK_TOKEN, accessToken)
        if (accessToken.isNullOrBlank()) {
            saveToken(Keys.COMICK_REFRESH_TOKEN, null)
        } else if (!refreshToken.isNullOrBlank()) {
            saveToken(Keys.COMICK_REFRESH_TOKEN, refreshToken)
        }
    }

    suspend fun saveVndbToken(token: String?) = saveToken(Keys.VNDB_TOKEN, token)
    suspend fun saveSteamApiKey(key: String?) = saveToken(Keys.STEAM_API_KEY, key)
    suspend fun saveSteamId64(steamId: String?) = saveToken(Keys.STEAM_ID_64, steamId)

    suspend fun saveBangumiToken(token: String?) {
        saveToken(Keys.BANGUMI_TOKEN, token)
        if (token.isNullOrBlank()) saveToken(Keys.BANGUMI_REFRESH_TOKEN, null)
    }

    suspend fun saveBangumiTokens(accessToken: String?, refreshToken: String?) {
        saveToken(Keys.BANGUMI_TOKEN, accessToken)
        if (accessToken.isNullOrBlank()) {
            saveToken(Keys.BANGUMI_REFRESH_TOKEN, null)
        } else if (!refreshToken.isNullOrBlank()) {
            saveToken(Keys.BANGUMI_REFRESH_TOKEN, refreshToken)
        }
    }

    suspend fun saveTmdbToken(token: String?) {
        saveToken(Keys.TMDB_TOKEN, token)
        if (token.isNullOrBlank()) {
            saveToken(Keys.TMDB_SESSION_ID, null)
            saveToken(Keys.TMDB_ACCOUNT_ID, null)
        }
    }

    suspend fun saveTmdbSession(sessionId: String?, accountId: String? = null) {
        saveToken(Keys.TMDB_SESSION_ID, sessionId)
        saveToken(Keys.TMDB_ACCOUNT_ID, accountId)
    }

    suspend fun savePendingTmdbRequestToken(requestToken: String?) =
        saveToken(Keys.TMDB_PENDING_REQUEST_TOKEN, requestToken)

    suspend fun getPendingTmdbRequestToken(): String? =
        context.dataStore.data.firstOrNull()?.get(Keys.TMDB_PENDING_REQUEST_TOKEN)?.let { decrypt(it) }

    suspend fun saveIgdbToken(token: String?) = saveToken(Keys.IGDB_TOKEN, token)
    suspend fun saveHardcoverToken(token: String?) {
        cachedHardcoverToken = token?.trim().orEmpty()
        saveToken(Keys.HARDCOVER_TOKEN, token)
    }

    suspend fun clearTrackerToken(serviceKey: String) {
        when (serviceKey.uppercase()) {
            "ANILIST" -> saveAnilistToken(null)
            "MAL" -> saveMalTokens(null, null)
            "TRAKT" -> saveTraktTokens(null, null)
            "SIMKL" -> saveSimklTokens(null, null)
            "BANGUMI" -> saveBangumiTokens(null, null)
            "COMICK" -> saveComickTokens(null, null)
            "VNDB" -> saveVndbToken(null)
            "TMDB" -> saveTmdbToken(null)
            "HARDCOVER" -> saveHardcoverToken(null)
            "IGDB" -> saveIgdbToken(null)
            "STEAM" -> {
                saveSteamApiKey(null)
                saveSteamId64(null)
            }
        }
    }
    suspend fun saveComickVerifier(verifier: String?) = saveToken(Keys.COMICK_VERIFIER, verifier)
    suspend fun getComickVerifier(): String? = context.dataStore.data.firstOrNull()?.get(Keys.COMICK_VERIFIER)?.let { decrypt(it) }
    suspend fun saveMalVerifier(verifier: String?) = saveToken(Keys.MAL_VERIFIER, verifier)
    suspend fun getMalVerifier(): String? = context.dataStore.data.firstOrNull()?.get(Keys.MAL_VERIFIER)?.let { decrypt(it) }
    suspend fun saveTraktVerifier(verifier: String?) = saveToken(Keys.TRAKT_VERIFIER, verifier)
    suspend fun getTraktVerifier(): String? = context.dataStore.data.firstOrNull()?.get(Keys.TRAKT_VERIFIER)?.let { decrypt(it) }
    suspend fun saveOAuthState(state: String?) = saveToken(Keys.OAUTH_STATE, state)
    suspend fun getOAuthState(): String? = context.dataStore.data.firstOrNull()?.get(Keys.OAUTH_STATE)?.let { decrypt(it) }

    private fun fanficKeyFor(site: FanficSite): Preferences.Key<String>? = when (site) {
        FanficSite.AO3 -> Keys.FANFIC_COOKIE_AO3
        FanficSite.SB -> Keys.FANFIC_COOKIE_SB
        FanficSite.SV -> Keys.FANFIC_COOKIE_SV
        FanficSite.QQ -> Keys.FANFIC_COOKIE_QQ
        FanficSite.FFN -> Keys.FANFIC_COOKIE_FFN
        FanficSite.OTHER -> null
    }

    val fanficConnectedSites: Flow<Set<String>> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            buildSet {
                if (!prefs[Keys.FANFIC_COOKIE_AO3].isNullOrBlank()) add(FanficSite.AO3.code)
                if (!prefs[Keys.FANFIC_COOKIE_SB].isNullOrBlank()) add(FanficSite.SB.code)
                if (!prefs[Keys.FANFIC_COOKIE_SV].isNullOrBlank()) add(FanficSite.SV.code)
                if (!prefs[Keys.FANFIC_COOKIE_QQ].isNullOrBlank()) add(FanficSite.QQ.code)
                if (!prefs[Keys.FANFIC_COOKIE_FFN].isNullOrBlank()) add(FanficSite.FFN.code)
            }
        }

    suspend fun saveFanficCookie(site: FanficSite, cookie: String?) {
        val key = fanficKeyFor(site) ?: return
        saveToken(key, cookie?.trim()?.takeIf { it.isNotBlank() })
    }

    suspend fun getFanficCookie(site: FanficSite): String? {
        val key = fanficKeyFor(site) ?: return null
        return context.dataStore.data.firstOrNull()?.get(key)?.let { decrypt(it) }
    }

    suspend fun saveAo3Username(username: String?) {
        val clean = username?.trim().orEmpty()
        context.dataStore.edit { prefs ->
            if (clean.isBlank()) prefs.remove(Keys.FANFIC_AO3_USERNAME)
            else prefs[Keys.FANFIC_AO3_USERNAME] = clean
        }
    }

    suspend fun getAo3Username(): String? =
        context.dataStore.data.firstOrNull()?.get(Keys.FANFIC_AO3_USERNAME)

    /**
     * One-time transparent upgrade: re-encrypts any legacy v1 token entries under the
     * hardware-backed Android Keystore master key without forcing sign-out.
     */
    suspend fun migrateLegacyTokensToKeystore() {
        val snapshot = context.dataStore.data.first().asMap()
        val toUpgrade = mutableMapOf<Preferences.Key<String>, String>()
        for ((prefKey, rawVal) in snapshot) {
            if (!isEncryptedTokenKey(prefKey.name)) continue
            val strVal = rawVal as? String ?: continue
            if (strVal.isNotBlank() && !strVal.startsWith(KEYSTORE_PREFIX)) {
                val plain = decryptLegacy(strVal) ?: continue
                @Suppress("UNCHECKED_CAST")
                toUpgrade[prefKey as Preferences.Key<String>] = encrypt(plain)
            }
        }
        if (toUpgrade.isNotEmpty()) {
            context.dataStore.edit { prefs ->
                toUpgrade.forEach { (k, v) -> prefs[k] = v }
            }
        }
    }

    private suspend fun saveToken(key: Preferences.Key<String>, token: String?) {
        context.dataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = encrypt(token)
            }
        }
    }

    companion object {
        @Volatile
        var cachedHardcoverToken: String = ""

        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "omnitrack_token_master_v2"
        private const val KEYSTORE_PREFIX = "v2:"
        private const val PASSWORD_PREFIX = "pw1:"

        private const val LEGACY_PBKDF2_ITERATIONS = 10000
        private const val BACKUP_PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH = 256
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val BACKUP_SALT_LENGTH = 16

        // Legacy salt/passphrase kept strictly read-only for migrating older tokens/backups
        private val LEGACY_PORTABLE_SALT = byteArrayOf(
            0x4f, 0x6d, 0x6e, 0x69, 0x54, 0x72, 0x61, 0x63,
            0x6b, 0x5f, 0x50, 0x6f, 0x72, 0x74, 0x61, 0x62
        )
        private const val LEGACY_PORTABLE_PASSPHRASE = "OmniTrack_Universal_Master_Vault_Key_2026"

        private val legacySecretKey: SecretKeySpec by lazy {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(LEGACY_PORTABLE_PASSPHRASE.toCharArray(), LEGACY_PORTABLE_SALT, LEGACY_PBKDF2_ITERATIONS, KEY_LENGTH)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        }

        @Synchronized
        private fun getOrCreateKeystoreKey(): SecretKey {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return existing

            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH)
                .setRandomizedEncryptionRequired(true)
                .build()
            keyGen.init(spec)
            return keyGen.generateKey()
        }

        fun isEncryptedTokenKey(keyName: String): Boolean =
            keyName.startsWith("token_") ||
                keyName.startsWith("pkce_") ||
                keyName.startsWith("tmdb_pending_") ||
                keyName.startsWith("fanfic_cookie_")

        fun encrypt(plainText: String): String {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
                val iv = cipher.iv
                val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

                val combined = ByteArray(iv.size + cipherText.size)
                System.arraycopy(iv, 0, combined, 0, iv.size)
                System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

                KEYSTORE_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (e: Exception) {
                throw IllegalStateException("Keystore token encryption failed", e)
            }
        }

        fun decrypt(stored: String): String? {
            if (stored.isBlank()) return null
            if (stored.startsWith(KEYSTORE_PREFIX)) {
                return try {
                    val combined = Base64.decode(stored.removePrefix(KEYSTORE_PREFIX), Base64.NO_WRAP)
                    if (combined.size <= GCM_IV_LENGTH) return null
                    val iv = ByteArray(GCM_IV_LENGTH)
                    val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
                    System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
                    System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
                    String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
            return decryptLegacy(stored)
        }

        fun decryptLegacy(cipherTextBase64: String): String? {
            return try {
                val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
                if (combined.size < GCM_IV_LENGTH) return null

                val iv = ByteArray(GCM_IV_LENGTH)
                val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)
                System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
                System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, legacySecretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val decrypted = cipher.doFinal(cipherText)
                String(decrypted, StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }

        private fun derivePasswordKey(password: CharArray, salt: ByteArray): SecretKeySpec {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password, salt, BACKUP_PBKDF2_ITERATIONS, KEY_LENGTH)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        }

        fun encryptWithPassword(plainText: String, password: CharArray): String {
            val rng = SecureRandom()
            val salt = ByteArray(BACKUP_SALT_LENGTH).also { rng.nextBytes(it) }
            val iv = ByteArray(GCM_IV_LENGTH).also { rng.nextBytes(it) }
            val key = derivePasswordKey(password, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))

            val combined = ByteArray(salt.size + iv.size + cipherText.size)
            System.arraycopy(salt, 0, combined, 0, salt.size)
            System.arraycopy(iv, 0, combined, salt.size, iv.size)
            System.arraycopy(cipherText, 0, combined, salt.size + iv.size, cipherText.size)

            return PASSWORD_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decryptWithPassword(blob: String, password: CharArray): String? {
            if (!blob.startsWith(PASSWORD_PREFIX)) return null
            return try {
                val combined = Base64.decode(blob.removePrefix(PASSWORD_PREFIX), Base64.NO_WRAP)
                val headerSize = BACKUP_SALT_LENGTH + GCM_IV_LENGTH
                if (combined.size <= headerSize) return null

                val salt = combined.copyOfRange(0, BACKUP_SALT_LENGTH)
                val iv = combined.copyOfRange(BACKUP_SALT_LENGTH, headerSize)
                val cipherText = combined.copyOfRange(headerSize, combined.size)

                val key = derivePasswordKey(password, salt)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }
}
