package com.omnitrack.app.data.sync

import com.omnitrack.app.BuildConfig
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

data class OAuthTokenPair(
    val accessToken: String,
    val refreshToken: String? = null
)

sealed class VerificationState {
    data object Idle : VerificationState()
    data object Checking : VerificationState()
    data class Verified(val username: String) : VerificationState()
    data class Failed(val message: String) : VerificationState()
}

object TrackerAccountVerifier {

    private val client = com.omnitrack.app.data.network.NetworkClient.sharedClient

    private val json = Json { ignoreUnknownKeys = true }

    // Custom Override Credentials from private local DataStore
    @Volatile
    var customTraktClientId: String? = null
    @Volatile
    var customTraktClientSecret: String? = null
    @Volatile
    var customMalClientId: String? = null
    @Volatile
    var customSimklClientId: String? = null
    @Volatile
    var customSimklClientSecret: String? = null

    // Default Fallback Public Client IDs (non-secret public OAuth identifiers)
    private const val DEFAULT_ANILIST_CLIENT_ID = "50688"
    private const val DEFAULT_TRAKT_CLIENT_ID = "VqwWoA1jPRnMBEtn7fEV3Zlx4XeXYRNaipC7pAaWL4o"
    private const val DEFAULT_SIMKL_CLIENT_ID = "c9c006c049d34a887a0f744b29f09658e3dce579cf5db98665db4d96e35d036a"
    private const val DEFAULT_COMICK_CLIENT_ID = "SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH"

    @Volatile
    var pendingTraktVerifier: String? = null

    @Volatile
    var pendingOAuthState: String? = null

    fun generateOAuthState(): String {
        val bytes = ByteArray(24)
        java.security.SecureRandom().nextBytes(bytes)
        return android.util.Base64.encodeToString(
            bytes,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
        )
    }

    fun generatePkceVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generatePkceS256Challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    val ANILIST_CLIENT_ID: String
        get() = BuildConfig.ANILIST_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_ANILIST_CLIENT_ID

    val TRAKT_CLIENT_ID: String
        get() = customTraktClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TRAKT_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_TRAKT_CLIENT_ID

    val TRAKT_CLIENT_SECRET: String
        get() = when {
            !customTraktClientId.isNullOrBlank() -> customTraktClientSecret?.takeIf { it.isNotBlank() } ?: ""
            else -> BuildConfig.TRAKT_CLIENT_SECRET.takeIf { it.isNotBlank() } ?: ""
        }

    val MAL_CLIENT_ID: String
        get() = customMalClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.MAL_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: ""

    val SIMKL_CLIENT_ID: String
        get() = customSimklClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_SIMKL_CLIENT_ID

    val SIMKL_CLIENT_SECRET: String
        get() = customSimklClientSecret?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SIMKL_CLIENT_SECRET.takeIf { it.isNotBlank() }
            ?: ""

    private val refreshMutex = Mutex()

    // Bangumi Client Credentials (injected via BuildConfig / CI Secrets)
    val BANGUMI_CLIENT_ID: String
        get() = BuildConfig.BANGUMI_CLIENT_ID.takeIf { it.isNotBlank() } ?: ""

    val BANGUMI_CLIENT_SECRET: String
        get() = BuildConfig.BANGUMI_CLIENT_SECRET.takeIf { it.isNotBlank() } ?: ""

    // Comick Client ID (custom CI secret if set, else Mihon public PKCE client ID since Comick has no self-serve OAuth portal)
    val COMICK_CLIENT_ID: String
        get() = BuildConfig.COMICK_CLIENT_ID.takeIf { it.isNotBlank() } ?: DEFAULT_COMICK_CLIENT_ID

    val COMICK_REDIRECT_URI: String
        get() = if (BuildConfig.COMICK_CLIENT_ID.isNotBlank()) "omnitrack://auth/comick" else "app.mihon://comick-auth"

    @Volatile
    var pendingMalVerifier: String? = null

    @Volatile
    var pendingComickVerifier: String? = null

    suspend fun verifyAnilist(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val query = """{"query":"query { Viewer { name } }"}"""
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(query.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=ANILIST http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val viewer = parsed["data"]?.jsonObject?.get("Viewer")?.jsonObject
                val name = viewer?.get("name")?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) {
                    VerificationState.Verified(name)
                } else {
                    VerificationState.Failed("No profile returned")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=ANILIST ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifyMal(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.myanimelist.net/v2/users/@me")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=MAL http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val name = parsed["name"]?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) {
                    VerificationState.Verified(name)
                } else {
                    VerificationState.Failed("Invalid MAL response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=MAL ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifyTrakt(token: String, customClientId: String? = null): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val clientId = customClientId?.takeIf { it.isNotBlank() } ?: TRAKT_CLIENT_ID
            val request = Request.Builder()
                .url("https://api.trakt.tv/users/me")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=TRAKT http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val username = parsed["username"]?.jsonPrimitive?.content
                    ?: parsed["name"]?.jsonPrimitive?.content
                if (!username.isNullOrBlank()) {
                    VerificationState.Verified(username)
                } else {
                    VerificationState.Failed("Invalid Trakt response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=TRAKT ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifySimkl(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.simkl.com/users/settings")
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", SIMKL_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=SIMKL http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val user = parsed["user"]?.jsonObject
                val name = user?.get("name")?.jsonPrimitive?.content
                if (!name.isNullOrBlank()) {
                    VerificationState.Verified(name)
                } else {
                    VerificationState.Failed("Invalid SIMKL response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=SIMKL ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifyBangumi(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/me")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=BANGUMI http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val nickname = parsed["nickname"]?.jsonPrimitive?.content
                val username = parsed["username"]?.jsonPrimitive?.content
                val display = nickname ?: username
                if (!display.isNullOrBlank()) {
                    VerificationState.Verified(display)
                } else {
                    VerificationState.Failed("Invalid Bangumi response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=BANGUMI ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifyVndb(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            // VNDB Kana authinfo endpoint strictly requires HTTP GET
            val request = Request.Builder()
                .url("https://api.vndb.org/kana/authinfo")
                .header("Authorization", "Token $token")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=VNDB http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val username = parsed["username"]?.jsonPrimitive?.content
                if (!username.isNullOrBlank()) {
                    VerificationState.Verified(username)
                } else {
                    VerificationState.Failed("Invalid VNDB response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=VNDB ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun verifyComick(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.comick.dev/integrations/v1/me")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=COMICK http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val username = parsed["username"]?.jsonPrimitive?.content
                    ?: parsed["id"]?.jsonPrimitive?.content
                if (!username.isNullOrBlank()) {
                    VerificationState.Verified(username)
                } else {
                    VerificationState.Failed("Invalid Comick response")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=COMICK ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun resolveSteamId(rawInput: String, apiKey: String): String? = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.all { it.isDigit() } && trimmed.length == 17) return@withContext trimmed

        val vanity = when {
            trimmed.contains("/id/") -> trimmed.substringAfter("/id/").trimEnd('/')
            trimmed.contains("/profiles/") -> trimmed.substringAfter("/profiles/").trimEnd('/')
            else -> trimmed
        }
        if (vanity.all { it.isDigit() } && vanity.length == 17) return@withContext vanity

        try {
            val url = "https://api.steampowered.com/ISteamUser/ResolveVanityURL/v0001/?key=$apiKey&vanityurl=$vanity"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=STEAM op=resolve http=${response.code}")
                    return@withContext null
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val respObj = parsed["response"]?.jsonObject
                val success = respObj?.get("success")?.jsonPrimitive?.content?.toIntOrNull()
                if (success == 1) {
                    respObj["steamid"]?.jsonPrimitive?.content
                } else null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun verifySteam(apiKey: String, steamId64: String): VerificationState = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || steamId64.isBlank()) return@withContext VerificationState.Idle
        try {
            val resolvedId = resolveSteamId(steamId64, apiKey) ?: steamId64.trim()
            val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=$apiKey&steamids=$resolvedId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=STEAM http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val players = parsed["response"]?.jsonObject?.get("players")?.jsonArray
                val player = players?.firstOrNull()?.jsonObject
                val personaName = player?.get("personaname")?.jsonPrimitive?.content
                if (!personaName.isNullOrBlank()) {
                    VerificationState.Verified(personaName)
                } else {
                    VerificationState.Failed("No player found for SteamID/Username")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=STEAM ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    fun isTmdbV4Jwt(token: String): Boolean {
        val clean = token.trim()
        return clean.count { it == '.' } >= 2 || clean.length > 64
    }

    suspend fun verifyTmdb(apiKey: String, sessionId: String? = null): VerificationState = withContext(Dispatchers.IO) {
        val clean = apiKey.trim()
        if (clean.isBlank()) return@withContext VerificationState.Idle
        try {
            val isJwt = isTmdbV4Jwt(clean)
            val sid = sessionId?.trim()?.takeIf { it.isNotBlank() }
            if (sid != null) {
                val encSid = URLEncoder.encode(sid, "UTF-8")
                val url = if (isJwt) {
                    "https://api.themoviedb.org/3/account?session_id=$encSid"
                } else {
                    "https://api.themoviedb.org/3/account?api_key=${URLEncoder.encode(clean, "UTF-8")}&session_id=$encSid"
                }
                val reqBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                if (isJwt) reqBuilder.header("Authorization", "Bearer $clean")
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        AppLogger.w("TrackerVerify", "service=TMDB op=account http=${resp.code}")
                        return@withContext if (resp.code == 401 || resp.code == 403) {
                            VerificationState.Failed("TMDb session expired — tap Sign in to TMDb")
                        } else {
                            VerificationState.Failed("HTTP ${resp.code}")
                        }
                    }
                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val username = obj["username"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    return@withContext VerificationState.Verified(
                        if (username != null) "$username · Write sync active" else "Write sync active"
                    )
                }
            }

            val url = if (isJwt) {
                "https://api.themoviedb.org/3/authentication"
            } else {
                "https://api.themoviedb.org/3/authentication?api_key=${URLEncoder.encode(clean, "UTF-8")}"
            }
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
            if (isJwt) requestBuilder.header("Authorization", "Bearer $clean")

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=TMDB http=${response.code}")
                    return@withContext VerificationState.Failed("HTTP ${response.code}")
                }
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val success = parsed["success"]?.jsonPrimitive?.booleanOrNull ?: false
                if (success) {
                    VerificationState.Verified("Read-only API key (sign in for write sync)")
                } else {
                    VerificationState.Failed("Invalid TMDb key")
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=TMDB ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Network error (${e.javaClass.simpleName})")
        }
    }

    suspend fun createTmdbRequestToken(apiKeyOrBearer: String): String? = withContext(Dispatchers.IO) {
        val clean = apiKeyOrBearer.trim()
        if (clean.isBlank()) return@withContext null
        try {
            val isJwt = isTmdbV4Jwt(clean)
            val url = if (isJwt) {
                "https://api.themoviedb.org/3/authentication/token/new"
            } else {
                "https://api.themoviedb.org/3/authentication/token/new?api_key=${URLEncoder.encode(clean, "UTF-8")}"
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
            if (isJwt) reqBuilder.header("Authorization", "Bearer $clean")
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=TMDB op=reqToken http=${resp.code}")
                    return@withContext null
                }
                val parsed = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                if (parsed["success"]?.jsonPrimitive?.booleanOrNull == true) {
                    parsed["request_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=TMDB op=reqToken ex=${e.javaClass.simpleName}")
            null
        }
    }

    suspend fun exchangeTmdbSession(
        apiKeyOrBearer: String,
        requestToken: String
    ): Pair<String, Long?>? = withContext(Dispatchers.IO) {
        val clean = apiKeyOrBearer.trim()
        val reqTok = requestToken.trim()
        if (clean.isBlank() || reqTok.isBlank()) return@withContext null
        try {
            val isJwt = isTmdbV4Jwt(clean)
            val url = if (isJwt) {
                "https://api.themoviedb.org/3/authentication/session/new"
            } else {
                "https://api.themoviedb.org/3/authentication/session/new?api_key=${URLEncoder.encode(clean, "UTF-8")}"
            }
            val payload = """{"request_token":"$reqTok"}"""
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(payload.toRequestBody("application/json".toMediaType()))
            if (isJwt) reqBuilder.header("Authorization", "Bearer $clean")

            val sessionId = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.w("TrackerVerify", "service=TMDB op=session http=${resp.code}")
                    return@withContext null
                }
                val parsed = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                parsed["session_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            } ?: return@withContext null

            val accountUrl = if (isJwt) {
                "https://api.themoviedb.org/3/account?session_id=${URLEncoder.encode(sessionId, "UTF-8")}"
            } else {
                "https://api.themoviedb.org/3/account?api_key=${URLEncoder.encode(clean, "UTF-8")}&session_id=${URLEncoder.encode(sessionId, "UTF-8")}"
            }
            val accReqBuilder = Request.Builder()
                .url(accountUrl)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
            if (isJwt) accReqBuilder.header("Authorization", "Bearer $clean")
            val accountId = runCatching {
                client.newCall(accReqBuilder.build()).execute().use { accResp ->
                    if (!accResp.isSuccessful) return@use null
                    val accObj = json.parseToJsonElement(accResp.body?.string().orEmpty()).jsonObject
                    accObj["id"]?.jsonPrimitive?.longOrNull
                }
            }.getOrNull()

            sessionId to accountId
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=TMDB op=session ex=${e.javaClass.simpleName}")
            null
        }
    }

    // Trakt Device Code Request
    data class TraktDeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int
    )

    suspend fun requestTraktDeviceCode(customClientId: String? = null): TraktDeviceCode? = withContext(Dispatchers.IO) {
        try {
            val clientId = customClientId?.takeIf { it.isNotBlank() } ?: TRAKT_CLIENT_ID
            val payload = """{"client_id":"$clientId"}"""
            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/device/code")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId)
                .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val deviceCode = obj["device_code"]?.jsonPrimitive?.content ?: return@withContext null
                val userCode = obj["user_code"]?.jsonPrimitive?.content ?: return@withContext null
                val verificationUrl = obj["verification_url"]?.jsonPrimitive?.content ?: "https://trakt.tv/activate"
                val interval = obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
                TraktDeviceCode(deviceCode, userCode, verificationUrl, interval)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun pollTraktDeviceToken(
        deviceCode: String,
        customClientId: String? = null,
        customClientSecret: String? = null
    ): OAuthTokenPair? = withContext(Dispatchers.IO) {
        try {
            val clientId = customClientId?.takeIf { it.isNotBlank() } ?: TRAKT_CLIENT_ID
            val clientSecret = customClientSecret?.takeIf { it.isNotBlank() } ?: TRAKT_CLIENT_SECRET
            val payload = if (clientSecret.isNotBlank()) {
                """{"code":"$deviceCode","client_id":"$clientId","client_secret":"$clientSecret"}"""
            } else {
                """{"code":"$deviceCode","client_id":"$clientId"}"""
            }
            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/device/token")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", clientId)
                .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val body = response.body?.string().orEmpty()
                    val obj = json.parseToJsonElement(body).jsonObject
                    val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@use null
                    val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    OAuthTokenPair(access, refresh)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    // SIMKL PIN Code Request & Poll
    data class SimklPinCode(
        val userCode: String,
        val verificationUrl: String,
        val expiresIn: Int,
        val intervalSeconds: Int,
        val deviceCode: String? = null
    )

    suspend fun requestSimklPinCode(): SimklPinCode? = withContext(Dispatchers.IO) {
        // 1. Modern SIMKL OAuth 2.0 Device Flow (Required for modern apps: POST /oauth2/device)
        try {
            val modernUrl = "https://api.simkl.com/oauth2/device"
            val modernPayload = """{"client_id":"$SIMKL_CLIENT_ID","scope":"media:read media:write"}"""
            val modernRequest = Request.Builder()
                .url(modernUrl)
                .header("Content-Type", "application/json")
                .header("simkl-api-key", SIMKL_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .post(modernPayload.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(modernRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val obj = json.parseToJsonElement(body).jsonObject
                    val userCode = obj["user_code"]?.jsonPrimitive?.content ?: return@withContext null
                    val deviceCode = obj["device_code"]?.jsonPrimitive?.content
                    val verificationUrl = obj["verification_uri_complete"]?.jsonPrimitive?.content
                        ?: obj["verification_uri"]?.jsonPrimitive?.content
                        ?: "https://simkl.com/pin"
                    val interval = obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
                    val expires = obj["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 900
                    return@withContext SimklPinCode(userCode, verificationUrl, expires, interval, deviceCode)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }

        // 2. Legacy SIMKL PIN Flow fallback (GET /oauth/pin)
        try {
            val url = "https://api.simkl.com/oauth/pin?client_id=$SIMKL_CLIENT_ID"
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("simkl-api-key", SIMKL_CLIENT_ID)
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val obj = json.parseToJsonElement(body).jsonObject
                val userCode = obj["user_code"]?.jsonPrimitive?.content ?: return@withContext null
                val verificationUrl = obj["verification_url"]?.jsonPrimitive?.content ?: "https://simkl.com/pin"
                val interval = obj["interval"]?.jsonPrimitive?.content?.toIntOrNull() ?: 5
                val expires = obj["expires_in"]?.jsonPrimitive?.content?.toIntOrNull() ?: 900
                SimklPinCode(userCode, verificationUrl, expires, interval, null)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun pollSimklPinToken(userCode: String, deviceCode: String? = null): OAuthTokenPair? = withContext(Dispatchers.IO) {
        if (!deviceCode.isNullOrBlank()) {
            try {
                val tokenUrl = "https://api.simkl.com/oauth2/token"
                val tokenPayload = """{"client_id":"$SIMKL_CLIENT_ID","device_code":"$deviceCode","grant_type":"urn:ietf:params:oauth:grant-type:device_code"}"""
                val tokenRequest = Request.Builder()
                    .url(tokenUrl)
                    .header("Content-Type", "application/json")
                    .header("simkl-api-key", SIMKL_CLIENT_ID)
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .post(tokenPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(tokenRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val obj = json.parseToJsonElement(body).jsonObject
                        val token = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        if (!token.isNullOrBlank()) return@withContext OAuthTokenPair(token, refresh)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // Legacy fallback
        try {
            val url = "https://api.simkl.com/oauth/pin/$userCode?client_id=$SIMKL_CLIENT_ID"
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val obj = json.parseToJsonElement(body).jsonObject
                    if (obj["result"]?.jsonPrimitive?.content == "OK") {
                        val token = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                            ?: return@use null
                        val refresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        OAuthTokenPair(token, refresh)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Exchanges a stored OAuth2 refresh_token for a new access_token (and saves any rotated
     * refresh_token) across MAL, Trakt, SIMKL, Bangumi, and Comick.
     */
    suspend fun refreshAccessToken(
        serviceKey: String,
        tokenManager: TrackerTokenManager
    ): String? = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val key = serviceKey.uppercase()
            val tokens = tokenManager.tokens.firstOrNull() ?: return@withLock null
            try {
                when (key) {
                    "MAL" -> {
                        val rt = tokens.malRefreshToken?.trim().orEmpty()
                        if (rt.isBlank()) return@withLock null
                        val form = FormBody.Builder()
                            .add("grant_type", "refresh_token")
                            .add("client_id", MAL_CLIENT_ID)
                            .add("refresh_token", rt)
                            .build()
                        val req = Request.Builder()
                            .url("https://myanimelist.net/v1/oauth2/token")
                            .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                            .post(form)
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                AppLogger.w("TrackerVerify", "service=MAL op=refresh http=${resp.code}")
                                return@withLock null
                            }
                            val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                                ?: return@withLock null
                            val nextRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: rt
                            tokenManager.saveMalTokens(access, nextRefresh)
                            access
                        }
                    }

                    "TRAKT" -> {
                        val rt = tokens.traktRefreshToken?.trim().orEmpty()
                        if (rt.isBlank()) return@withLock null
                        val secret = TRAKT_CLIENT_SECRET
                        val payload = if (secret.isNotBlank()) {
                            """{"refresh_token":"$rt","client_id":"$TRAKT_CLIENT_ID","client_secret":"$secret","redirect_uri":"omnitrack://auth/trakt","grant_type":"refresh_token"}"""
                        } else {
                            """{"refresh_token":"$rt","client_id":"$TRAKT_CLIENT_ID","redirect_uri":"omnitrack://auth/trakt","grant_type":"refresh_token"}"""
                        }
                        val req = Request.Builder()
                            .url("https://api.trakt.tv/oauth/token")
                            .header("Content-Type", "application/json")
                            .header("trakt-api-version", "2")
                            .header("trakt-api-key", TRAKT_CLIENT_ID)
                            .header("User-Agent", "OmniTrack/1.0 (Android)")
                            .post(payload.toRequestBody("application/json".toMediaType()))
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                AppLogger.w("TrackerVerify", "service=TRAKT op=refresh http=${resp.code}")
                                return@withLock null
                            }
                            val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                                ?: return@withLock null
                            val nextRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: rt
                            tokenManager.saveTraktTokens(access, nextRefresh)
                            access
                        }
                    }

                    "SIMKL" -> {
                        val rt = tokens.simklRefreshToken?.trim().orEmpty()
                        if (rt.isBlank()) return@withLock null
                        val endpoints = listOf(
                            "https://api.simkl.com/oauth2/token",
                            "https://api.simkl.com/oauth/token"
                        )
                        for (url in endpoints) {
                            val payload = """{"grant_type":"refresh_token","client_id":"$SIMKL_CLIENT_ID","client_secret":"$SIMKL_CLIENT_SECRET","refresh_token":"$rt"}"""
                            val req = Request.Builder()
                                .url(url)
                                .header("Content-Type", "application/json")
                                .header("simkl-api-key", SIMKL_CLIENT_ID)
                                .header("User-Agent", "OmniTrack/1.0 (Android)")
                                .post(payload.toRequestBody("application/json".toMediaType()))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                                    val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                                    if (access != null) {
                                        val nextRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: rt
                                        tokenManager.saveSimklTokens(access, nextRefresh)
                                        return@withLock access
                                    }
                                }
                            }
                        }
                        AppLogger.w("TrackerVerify", "service=SIMKL op=refresh failed")
                        null
                    }

                    "BANGUMI" -> {
                        val rt = tokens.bangumiRefreshToken?.trim().orEmpty()
                        if (rt.isBlank()) return@withLock null
                        val form = FormBody.Builder()
                            .add("grant_type", "refresh_token")
                            .add("client_id", BANGUMI_CLIENT_ID)
                            .add("client_secret", BANGUMI_CLIENT_SECRET)
                            .add("redirect_uri", "omnitrack://auth/bangumi")
                            .add("refresh_token", rt)
                            .build()
                        val req = Request.Builder()
                            .url("https://bgm.tv/oauth/access_token")
                            .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                            .post(form)
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                AppLogger.w("TrackerVerify", "service=BANGUMI op=refresh http=${resp.code}")
                                return@withLock null
                            }
                            val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                                ?: return@withLock null
                            val nextRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: rt
                            tokenManager.saveBangumiTokens(access, nextRefresh)
                            access
                        }
                    }

                    "COMICK" -> {
                        val rt = tokens.comickRefreshToken?.trim().orEmpty()
                        if (rt.isBlank()) return@withLock null
                        val form = FormBody.Builder()
                            .add("grant_type", "refresh_token")
                            .add("client_id", COMICK_CLIENT_ID)
                            .add("refresh_token", rt)
                            .add("resource", "https://api.comick.dev/integrations/v1")
                            .build()
                        val req = Request.Builder()
                            .url("https://comick.dev/api/auth/oauth2/token")
                            .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                            .post(form)
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) {
                                AppLogger.w("TrackerVerify", "service=COMICK op=refresh http=${resp.code}")
                                return@withLock null
                            }
                            val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                                ?: return@withLock null
                            val nextRefresh = obj["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: rt
                            tokenManager.saveComickTokens(access, nextRefresh)
                            access
                        }
                    }

                    else -> null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLogger.w("TrackerVerify", "service=$key op=refresh ex=${e.javaClass.simpleName}")
                null
            }
        }
    }

    /**
     * Verifies a service account and transparently refreshes expired OAuth2 access tokens
     * when a refresh_token is stored, returning clear reconnect guidance if expired.
     */
    suspend fun verifyWithAutoRefresh(
        serviceKey: String,
        tokenManager: TrackerTokenManager,
        customTraktClientId: String? = null
    ): VerificationState {
        val key = serviceKey.uppercase()
        val t = tokenManager.tokens.firstOrNull() ?: return VerificationState.Idle

        suspend fun runVerify(tok: TrackerTokens): VerificationState = when (key) {
            "ANILIST" -> tok.anilistToken?.takeIf { it.isNotBlank() }?.let { verifyAnilist(it) } ?: VerificationState.Idle
            "MAL" -> tok.malToken?.takeIf { it.isNotBlank() }?.let { verifyMal(it) } ?: VerificationState.Idle
            "TRAKT" -> tok.traktToken?.takeIf { it.isNotBlank() }?.let { verifyTrakt(it, customTraktClientId) } ?: VerificationState.Idle
            "SIMKL" -> tok.simklToken?.takeIf { it.isNotBlank() }?.let { verifySimkl(it) } ?: VerificationState.Idle
            "BANGUMI" -> tok.bangumiToken?.takeIf { it.isNotBlank() }?.let { verifyBangumi(it) } ?: VerificationState.Idle
            "COMICK" -> tok.comickToken?.takeIf { it.isNotBlank() }?.let { verifyComick(it) } ?: VerificationState.Idle
            "VNDB" -> tok.vndbToken?.takeIf { it.isNotBlank() }?.let { verifyVndb(it) } ?: VerificationState.Idle
            "HARDCOVER" -> tok.hardcoverToken?.takeIf { it.isNotBlank() }?.let { verifyHardcover(it) } ?: VerificationState.Idle
            "TMDB" -> tok.tmdbToken?.takeIf { it.isNotBlank() }?.let { verifyTmdb(it, tok.tmdbSessionId) } ?: VerificationState.Idle
            "STEAM" -> if (!tok.steamApiKey.isNullOrBlank() && !tok.steamId64.isNullOrBlank()) {
                verifySteam(tok.steamApiKey, tok.steamId64)
            } else VerificationState.Idle
            else -> VerificationState.Idle
        }

        val initial = runVerify(t)
        if (initial is VerificationState.Failed && (initial.message.contains("401") || initial.message.contains("403"))) {
            val refreshed = refreshAccessToken(key, tokenManager)
            if (!refreshed.isNullOrBlank()) {
                val nextTokens = tokenManager.tokens.firstOrNull() ?: return initial
                return runVerify(nextTokens)
            }
            if (key in setOf("ANILIST", "MAL", "TRAKT", "SIMKL", "BANGUMI", "COMICK")) {
                return VerificationState.Failed("Session expired — tap Reconnect")
            }
        }
        return initial
    }

    suspend fun verifyHardcover(token: String): VerificationState = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isBlank()) return@withContext VerificationState.Idle
        try {
            val hcClient = com.omnitrack.app.data.enrichment.HardcoverApiClient(client)
            val handle = hcClient.verifyToken(clean)
            if (!handle.isNullOrBlank()) {
                VerificationState.Verified("@$handle")
            } else {
                VerificationState.Failed(hcClient.lastError ?: "Invalid or expired Hardcover token")
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("TrackerVerify", "service=HARDCOVER ex=${e.javaClass.simpleName}")
            VerificationState.Failed("Hardcover network error (${e.javaClass.simpleName})")
        }
    }
}
