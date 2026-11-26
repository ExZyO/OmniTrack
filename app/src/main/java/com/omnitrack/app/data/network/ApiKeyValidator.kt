package com.omnitrack.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

sealed class ApiKeyValidationState {
    data object Idle : ApiKeyValidationState()
    data object Testing : ApiKeyValidationState()
    data class Valid(val detail: String = "Verified") : ApiKeyValidationState()
    data class Invalid(val reason: String) : ApiKeyValidationState()
}

object ApiKeyValidator {

    private val client: OkHttpClient by lazy {
        NetworkClient.sharedClient.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun validateGemini(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter a Gemini API key first")
        runCatching {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=${URLEncoder.encode(key, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid("Gemini key active")
                    400, 401, 403 -> ApiKeyValidationState.Invalid("Invalid Gemini API key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid("Unexpected HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateOpenRouter(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter an OpenRouter key first")
        runCatching {
            val req = Request.Builder()
                .url("https://openrouter.ai/api/v1/auth/key")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid("OpenRouter key active")
                    401, 403 -> ApiKeyValidationState.Invalid("Invalid OpenRouter key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid("Unexpected HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateTmdb(apiKeyOrToken: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKeyOrToken.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter a TMDb key or token first")
        runCatching {
            val isBearer = key.length > 48 || key.contains('.')
            val req = if (isBearer) {
                Request.Builder()
                    .url("https://api.themoviedb.org/3/authentication")
                    .header("Authorization", "Bearer $key")
                    .get()
                    .build()
            } else {
                Request.Builder()
                    .url("https://api.themoviedb.org/3/authentication?api_key=${URLEncoder.encode(key, "UTF-8")}")
                    .get()
                    .build()
            }
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid(if (isBearer) "TMDb Read Token active" else "TMDb API key active")
                    401, 403 -> ApiKeyValidationState.Invalid("Invalid TMDb key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid("Unexpected HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateFanart(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter a Fanart.tv API key first")
        runCatching {
            val url = "https://webservice.fanart.tv/v3/movies/550?api_key=${URLEncoder.encode(key, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid("Fanart.tv key active")
                    401, 403 -> ApiKeyValidationState.Invalid("Invalid Fanart.tv key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid("Unexpected HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateSteamGridDb(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter a SteamGridDB key first")
        runCatching {
            val req = Request.Builder()
                .url("https://www.steamgriddb.com/api/v2/games/id/1")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid("SteamGridDB key active")
                    401, 403 -> ApiKeyValidationState.Invalid("Invalid SteamGridDB key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid("Unexpected HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateIgdb(clientId: String, clientSecret: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val id = clientId.trim()
        val secret = clientSecret.trim()
        if (id.isEmpty() || secret.isEmpty()) {
            return@withContext ApiKeyValidationState.Invalid("Enter both Client ID and Secret")
        }
        runCatching {
            val body = FormBody.Builder()
                .add("client_id", id)
                .add("client_secret", secret)
                .add("grant_type", "client_credentials")
                .build()
            val req = Request.Builder()
                .url("https://id.twitch.tv/oauth2/token")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && raw.contains("access_token")) {
                    ApiKeyValidationState.Valid("Twitch/IGDB pair active")
                } else if (resp.code in listOf(400, 401, 403)) {
                    ApiKeyValidationState.Invalid("Invalid Twitch ID or Secret")
                } else {
                    ApiKeyValidationState.Invalid("Twitch OAuth HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateMdblist(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter an MDBList API key first")
        runCatching {
            val url = "https://api.mdblist.com/user?apikey=${URLEncoder.encode(key, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (resp.isSuccessful && !raw.contains("Invalid API key", ignoreCase = true) && !raw.contains("\"error\"", ignoreCase = true)) {
                    ApiKeyValidationState.Valid("MDBList key active")
                } else if (resp.code in listOf(401, 403) || raw.contains("Invalid API key", ignoreCase = true)) {
                    ApiKeyValidationState.Invalid("Invalid MDBList API key")
                } else {
                    ApiKeyValidationState.Invalid("MDBList HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateOmdb(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter an OMDb API key first")
        runCatching {
            val url = "https://www.omdbapi.com/?i=tt0133093&apikey=${URLEncoder.encode(key, "UTF-8")}"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
                val responseFlag = obj?.get("Response")?.jsonPrimitive?.contentOrNull
                val errMsg = obj?.get("Error")?.jsonPrimitive?.contentOrNull
                when {
                    resp.isSuccessful && responseFlag.equals("True", ignoreCase = true) ->
                        ApiKeyValidationState.Valid("OMDb key active")
                    resp.code in listOf(401, 403) || errMsg?.contains("Invalid API key", ignoreCase = true) == true ->
                        ApiKeyValidationState.Invalid(errMsg ?: "Invalid OMDb key (HTTP ${resp.code})")
                    else -> ApiKeyValidationState.Invalid(errMsg ?: "OMDb HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateComicVine(apiKey: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isEmpty()) return@withContext ApiKeyValidationState.Invalid("Enter a Comic Vine key first")
        runCatching {
            val url = "https://comicvine.gamespot.com/api/types/?api_key=${URLEncoder.encode(key, "UTF-8")}&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val statusCode = runCatching {
                    json.parseToJsonElement(raw).jsonObject["status_code"]?.jsonPrimitive?.intOrNull
                }.getOrNull()
                when {
                    resp.isSuccessful && statusCode == 1 -> ApiKeyValidationState.Valid("Comic Vine key active")
                    statusCode == 100 || resp.code in listOf(401, 403) ->
                        ApiKeyValidationState.Invalid("Invalid Comic Vine key")
                    else -> ApiKeyValidationState.Invalid("Comic Vine HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }

    suspend fun validateMetron(username: String, password: String): ApiKeyValidationState = withContext(Dispatchers.IO) {
        val user = username.trim()
        val pass = password.trim()
        if (user.isEmpty() || pass.isEmpty()) {
            return@withContext ApiKeyValidationState.Invalid("Enter both username and password")
        }
        runCatching {
            val req = Request.Builder()
                .url("https://metron.cloud/api/publisher/?page=1")
                .header("Authorization", Credentials.basic(user, pass))
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                when (resp.code) {
                    in 200..299 -> ApiKeyValidationState.Valid("Metron account active")
                    401, 403 -> ApiKeyValidationState.Invalid("Invalid Metron login")
                    else -> ApiKeyValidationState.Invalid("Metron HTTP ${resp.code}")
                }
            }
        }.getOrElse { ApiKeyValidationState.Invalid("Network error: ${it.message ?: "failed"}") }
    }
}
