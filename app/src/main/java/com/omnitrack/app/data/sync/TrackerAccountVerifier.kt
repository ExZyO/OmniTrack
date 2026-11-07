package com.omnitrack.app.data.sync

import com.omnitrack.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class VerificationState {
    data object Idle : VerificationState()
    data object Checking : VerificationState()
    data class Verified(val username: String) : VerificationState()
    data class Failed(val message: String) : VerificationState()
}

object TrackerAccountVerifier {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

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

    // Default Fallback Credentials
    private const val DEFAULT_TRAKT_CLIENT_ID = "VqwWoA1jPRnMBEtn7fEV3Zlx4XeXYRNaipC7pAaWL4o"
    private const val DEFAULT_TRAKT_CLIENT_SECRET = "fZnobYDga_lRC-jatEiu88saM15CsmuLvfXCbj9WoPs"
    private const val DEFAULT_MAL_CLIENT_ID = "3342466cfb744b714350df413002f5ef"
    private const val DEFAULT_SIMKL_CLIENT_ID = "c9c006c049d34a887a0f744b29f09658e3dce579cf5db98665db4d96e35d036a"
    private const val DEFAULT_SIMKL_CLIENT_SECRET = "50fb0db6ae35c6564a1b4484ffd1d9abe79d139e358bc3417d03fcb68f540f24"

    val TRAKT_CLIENT_ID: String
        get() = customTraktClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TRAKT_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_TRAKT_CLIENT_ID

    val TRAKT_CLIENT_SECRET: String
        get() = customTraktClientSecret?.takeIf { it.isNotBlank() }
            ?: BuildConfig.TRAKT_CLIENT_SECRET.takeIf { it.isNotBlank() }
            ?: DEFAULT_TRAKT_CLIENT_SECRET

    val MAL_CLIENT_ID: String
        get() = customMalClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.MAL_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_MAL_CLIENT_ID

    val SIMKL_CLIENT_ID: String
        get() = customSimklClientId?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SIMKL_CLIENT_ID.takeIf { it.isNotBlank() }
            ?: DEFAULT_SIMKL_CLIENT_ID

    val SIMKL_CLIENT_SECRET: String
        get() = customSimklClientSecret?.takeIf { it.isNotBlank() } ?: ""

    // Bangumi Client Credentials
    const val BANGUMI_CLIENT_ID = "bgm31586666817a5d03b"
    const val BANGUMI_CLIENT_SECRET = "3dad27cf8edcac6f55361d7971d219e1"

    // Comick Client ID
    const val COMICK_CLIENT_ID = "SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH"

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
                .post(query.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
        }
    }

    suspend fun verifyMal(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.myanimelist.net/v2/users/@me")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
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
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
        }
    }

    suspend fun verifySimkl(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.simkl.com/users/settings")
                .header("Authorization", "Bearer $token")
                .header("simkl-api-key", SIMKL_CLIENT_ID)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
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
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
        }
    }

    suspend fun verifyVndb(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            // VNDB Kana authinfo endpoint strictly requires HTTP GET
            val request = Request.Builder()
                .url("https://api.vndb.org/kana/authinfo")
                .header("Authorization", "Token $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
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
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
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
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val respObj = parsed["response"]?.jsonObject
                val success = respObj?.get("success")?.jsonPrimitive?.content?.toIntOrNull()
                if (success == 1) {
                    respObj["steamid"]?.jsonPrimitive?.content
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun verifySteam(apiKey: String, steamId64: String): VerificationState = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || steamId64.isBlank()) return@withContext VerificationState.Idle
        try {
            val resolvedId = resolveSteamId(steamId64, apiKey) ?: steamId64.trim()
            val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=$apiKey&steamids=$resolvedId"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
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
            VerificationState.Failed(e.message ?: "Network error")
        }
    }

    suspend fun verifyTmdb(apiKey: String): VerificationState = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext VerificationState.Idle
        try {
            val url = "https://api.themoviedb.org/3/authentication?api_key=$apiKey"
            val request = Request.Builder().url(url).get().build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext VerificationState.Failed("HTTP ${response.code}")
                val bodyStr = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(bodyStr).jsonObject
                val success = parsed["success"]?.jsonPrimitive?.booleanOrNull ?: false
                if (success) {
                    VerificationState.Verified("TMDb API Key Active")
                } else {
                    VerificationState.Failed("Invalid TMDb key")
                }
            }
        } catch (e: Exception) {
            VerificationState.Failed(e.message ?: "Network error")
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
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pollTraktDeviceToken(
        deviceCode: String,
        customClientId: String? = null,
        customClientSecret: String? = null
    ): String? = withContext(Dispatchers.IO) {
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
                    obj["access_token"]?.jsonPrimitive?.content
                } else {
                    null
                }
            }
        } catch (_: Exception) {
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
            val modernPayload = """{"client_id":"$SIMKL_CLIENT_ID"}"""
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
        } catch (_: Exception) {}

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
        } catch (_: Exception) {
            null
        }
    }

    suspend fun pollSimklPinToken(userCode: String, deviceCode: String? = null): String? = withContext(Dispatchers.IO) {
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
                        val token = obj["access_token"]?.jsonPrimitive?.content
                        if (!token.isNullOrBlank()) return@withContext token
                    }
                }
            } catch (_: Exception) {}
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
                        obj["access_token"]?.jsonPrimitive?.content
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
