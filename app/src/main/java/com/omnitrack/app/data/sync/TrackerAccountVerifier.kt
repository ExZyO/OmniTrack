package com.omnitrack.app.data.sync

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

    // Trakt Device Code and Client Constants from local verified config
    const val TRAKT_CLIENT_ID = "eyAYRNGOL9TopY6lw3agFsTWDZVfiqMG3tp0DMBQo2U"
    const val TRAKT_CLIENT_SECRET = "fZnobYDga_lRC-jatEiu88saM15CsmuLvfXCbj9WoPs"

    // SIMKL Client ID
    const val SIMKL_CLIENT_ID = "6eaf02a9b63b01eb1750cdecf0f05e7d0d8dd949d1eb6894716857947bb68c1a"

    // Bangumi Client ID
    const val BANGUMI_CLIENT_ID = "bgm31586666817a5d03b"

    // Comick Client ID
    const val COMICK_CLIENT_ID = "SGrUojDvbgqfhSCQbwfCjUPIccYMJdBH"

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

    suspend fun verifyTrakt(token: String): VerificationState = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext VerificationState.Idle
        try {
            val request = Request.Builder()
                .url("https://api.trakt.tv/users/me")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", TRAKT_CLIENT_ID)
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
                .url("https://api.comick.dev/integrations/v1/users/me")
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

    suspend fun verifySteam(apiKey: String, steamId64: String): VerificationState = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || steamId64.isBlank()) return@withContext VerificationState.Idle
        try {
            val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=$apiKey&steamids=$steamId64"
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
                    VerificationState.Failed("No player found for SteamID")
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

    suspend fun requestTraktDeviceCode(): TraktDeviceCode? = withContext(Dispatchers.IO) {
        try {
            val payload = """{"client_id":"$TRAKT_CLIENT_ID"}"""
            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/device/code")
                .header("Content-Type", "application/json")
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

    suspend fun pollTraktDeviceToken(deviceCode: String): String? = withContext(Dispatchers.IO) {
        try {
            val payload = """{"code":"$deviceCode","client_id":"$TRAKT_CLIENT_ID","client_secret":"$TRAKT_CLIENT_SECRET"}"""
            val request = Request.Builder()
                .url("https://api.trakt.tv/oauth/device/token")
                .header("Content-Type", "application/json")
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
}
