package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.ArtworkCandidate
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
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
                val obj = json.parseToJsonElement(body) as? JsonObject ?: return null
                val token = (obj["access_token"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
                val expiresInSec = (obj["expires_in"] as? JsonPrimitive)?.longOrNull ?: 3600L
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

    private fun queryIgdbGames(
        clientId: String,
        token: String,
        queryBody: String,
        preferredTitle: String? = null
    ): IgdbGameArtworkResult? {
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
            val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
            if (arr.isEmpty()) return null

            val objects = arr.mapNotNull { it as? JsonObject }
            if (objects.isEmpty()) return null

            fun JsonObject.hasVisuals(): Boolean {
                val hasArt = (this["artworks"] as? JsonArray)?.isNotEmpty() == true
                val hasCover = this["cover"] is JsonObject
                return hasArt || hasCover
            }

            val targetTitle = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            val chosen = objects.firstOrNull { o ->
                val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                targetTitle != null && name.equals(targetTitle, ignoreCase = true) && o.hasVisuals()
            } ?: objects.firstOrNull { o ->
                val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.trim()
                targetTitle != null && name.equals(targetTitle, ignoreCase = true)
            } ?: objects.firstOrNull { it.hasVisuals() }
                ?: objects.first()

            val igdbId = (chosen["id"] as? JsonPrimitive)?.longOrNull
            val gameName = (chosen["name"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
            val coverId = ((chosen["cover"] as? JsonObject)?.get("image_id") as? JsonPrimitive)
                ?.takeIf { it !is JsonNull }?.contentOrNull
            val coverUrl = coverId?.takeIf { it.isNotBlank() }?.let {
                "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/${it}.jpg"
            }

            val artworks = (chosen["artworks"] as? JsonArray)?.mapNotNull { el ->
                val imgId = ((el as? JsonObject)?.get("image_id") as? JsonPrimitive)
                    ?.takeIf { it !is JsonNull }?.contentOrNull
                imgId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_1080p/${it}.jpg" }
            }.orEmpty()

            val screenshots = (chosen["screenshots"] as? JsonArray)?.mapNotNull { el ->
                val imgId = ((el as? JsonObject)?.get("image_id") as? JsonPrimitive)
                    ?.takeIf { it !is JsonNull }?.contentOrNull
                imgId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_1080p/${it}.jpg" }
            }.orEmpty()

            val rawRating = (chosen["aggregated_rating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
                ?: (chosen["rating"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
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
                val hit = queryIgdbGames(clientId, token, slugQuery, preferredTitle = title)
                if (hit != null) return@withContext hit
            }
            val cleanTitle = title.replace("\"", "").trim()
            if (cleanTitle.isBlank()) return@withContext null
            val searchQuery = "fields id,name,slug,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id; search \"$cleanTitle\"; limit 5;"
            queryIgdbGames(clientId, token, searchQuery, preferredTitle = cleanTitle)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "fetchGameArtwork error for $title: ${e.message}")
            null
        }
    }
}
