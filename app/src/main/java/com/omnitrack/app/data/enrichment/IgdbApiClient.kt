package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.ArtworkCandidate
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class IgdbGameArtworkResult(
    val igdbId: Long? = null,
    val name: String? = null,
    val coverUrl: String? = null,
    val artworks: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val rating: Float? = null
)

fun IgdbGameArtworkResult.toArtworkCandidates(): List<ArtworkCandidate> = buildList {
    val label = name?.takeIf { it.isNotBlank() } ?: "IGDB"
    if (!coverUrl.isNullOrBlank()) {
        add(
            ArtworkCandidate(
                url = coverUrl,
                sourceName = "IGDB",
                description = "$label (Official Cover)",
                isBanner = false
            )
        )
    }
    artworks.forEachIndexed { idx, url ->
        add(
            ArtworkCandidate(
                url = url,
                sourceName = "IGDB Artwork",
                description = "$label (Key Art ${idx + 1})",
                isBanner = true,
                isScreenshot = false
            )
        )
    }
    screenshots.forEachIndexed { idx, url ->
        add(
            ArtworkCandidate(
                url = url,
                sourceName = "IGDB Screenshot",
                description = "$label (Screenshot ${idx + 1})",
                isBanner = true,
                isScreenshot = true
            )
        )
    }
}

class IgdbApiClient(
    private val client: OkHttpClient = NetworkClient.getClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val plainMediaType = "text/plain; charset=utf-8".toMediaType()
    private val formMediaType = "application/x-www-form-urlencoded".toMediaType()

    companion object {
        private val tokenCache = ConcurrentHashMap<String, Pair<String, Long>>()
    }

    private fun getOrFetchAccessToken(clientId: String, clientSecret: String): String? {
        val cleanId = clientId.trim()
        val cleanSecret = clientSecret.trim()
        if (cleanId.isBlank() || cleanSecret.isBlank()) return null

        val cacheKey = "$cleanId:$cleanSecret"
        val nowMs = System.currentTimeMillis()
        tokenCache[cacheKey]?.let { (cachedToken, expiresAtMs) ->
            if (nowMs < expiresAtMs) return cachedToken
        }

        return try {
            val tokenUrl = "https://id.twitch.tv/oauth2/token" +
                "?client_id=${URLEncoder.encode(cleanId, "UTF-8")}" +
                "&client_secret=${URLEncoder.encode(cleanSecret, "UTF-8")}" +
                "&grant_type=client_credentials"
            val req = Request.Builder()
                .url(tokenUrl)
                .post("".toRequestBody(formMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    AppLogger.w("IgdbClient", "Twitch OAuth failed HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val obj = json.parseToJsonElement(body).jsonObject
                val token = obj["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
                val expiresInSec = obj["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
                val safeExpiryMs = nowMs + ((expiresInSec - 120L).coerceAtLeast(60L) * 1000L)
                tokenCache[cacheKey] = token to safeExpiryMs
                token
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "Twitch OAuth error: ${e.message}")
            null
        }
    }

    private fun queryIgdbGames(clientId: String, token: String, queryBody: String): IgdbGameArtworkResult? {
        val req = Request.Builder()
            .url("https://api.igdb.com/v4/games")
            .header("Client-ID", clientId.trim())
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .post(queryBody.toRequestBody(plainMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val arr = json.parseToJsonElement(body).jsonArray
            if (arr.isEmpty()) return null

            val chosen = arr.firstOrNull { el ->
                val o = el.jsonObject
                val hasArt = o["artworks"]?.takeIf { it !is JsonNull }?.jsonArray?.isNotEmpty() == true
                val hasCover = o["cover"] != null && o["cover"] !is JsonNull
                hasArt || hasCover
            }?.jsonObject ?: arr.first().jsonObject

            val igdbId = chosen["id"]?.jsonPrimitive?.longOrNull
            val gameName = chosen["name"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            val coverId = chosen["cover"]?.takeIf { it !is JsonNull }?.jsonObject
                ?.get("image_id")?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
            val coverUrl = coverId?.takeIf { it.isNotBlank() }?.let {
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/${it}.jpg"
            }

            val artworks = chosen["artworks"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { el ->
                val imgId = el.jsonObject["image_id"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                imgId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_1080p/${it}.jpg" }
            }.orEmpty()

            val screenshots = chosen["screenshots"]?.takeIf { it !is JsonNull }?.jsonArray?.mapNotNull { el ->
                val imgId = el.jsonObject["image_id"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                imgId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_1080p/${it}.jpg" }
            }.orEmpty()

            val rawRating = chosen["aggregated_rating"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.floatOrNull
                ?: chosen["rating"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.floatOrNull
            val normalizedScore = rawRating?.let { (it / 10f).coerceIn(0f, 10f) }

            return IgdbGameArtworkResult(
                igdbId = igdbId,
                name = gameName,
                coverUrl = coverUrl,
                artworks = artworks,
                screenshots = screenshots,
                rating = normalizedScore
            )
        }
    }

    suspend fun fetchGameArtwork(
        clientId: String,
        clientSecret: String,
        title: String,
        slug: String? = null
    ): IgdbGameArtworkResult? = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null
        try {
            val token = getOrFetchAccessToken(clientId, clientSecret) ?: return@withContext null
            val cleanSlug = slug?.replace("\"", "")?.trim()
            if (!cleanSlug.isNullOrBlank()) {
                val slugQuery = "fields id,name,slug,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id; where slug = \"$cleanSlug\"; limit 1;"
                val hit = queryIgdbGames(clientId, token, slugQuery)
                if (hit != null) return@withContext hit
            }
            val cleanTitle = title.replace("\"", "").trim()
            if (cleanTitle.isBlank()) return@withContext null
            val searchQuery = "fields id,name,slug,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id; search \"$cleanTitle\"; limit 3;"
            queryIgdbGames(clientId, token, searchQuery)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "fetchGameArtwork error for $title: ${e.message}")
            null
        }
    }
}
