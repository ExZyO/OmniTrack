package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.ArtworkCandidate
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.ExternalLinkItem
import com.omnitrack.app.data.model.GameHltbTimes
import com.omnitrack.app.data.model.GameMediaAsset
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import com.omnitrack.app.ui.screens.detail.components.NetworkProductionCompany
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

data class IgdbGameArtworkResult(
    val igdbId: Long? = null,
    val name: String? = null,
    val coverUrl: String? = null,
    val artworks: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val rating: Float? = null
)

data class IgdbFullGameDetails(
    val igdbId: Long,
    val name: String,
    val slug: String? = null,
    val summary: String? = null,
    val storyline: String? = null,
    val releaseYear: String? = null,
    val firstReleaseDateFormatted: String? = null,
    val userRating: Float? = null,
    val userRatingCount: Int? = null,
    val criticRating: Int? = null,
    val criticRatingCount: Int? = null,
    val coverUrl: String? = null,
    val artworks: List<String> = emptyList(),
    val screenshots: List<String> = emptyList(),
    val mediaGallery: List<GameMediaAsset> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val platforms: List<String> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList(),
    val perspectives: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val engine: String? = null,
    val synonyms: List<String> = emptyList(),
    val companies: List<NetworkProductionCompany> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val similarGames: List<RecommendationItem> = emptyList(),
    val expansionSeeds: List<Pair<String, Long?>> = emptyList(),
    val steamAppId: Long? = null,
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val ageRatings: List<String> = emptyList(),
    val hltb: GameHltbTimes = GameHltbTimes()
)

fun IgdbGameArtworkResult.toArtworkCandidates(): List<ArtworkCandidate> = buildList {
    val label = name?.takeIf { it.isNotBlank() } ?: "IGDB"
    if (!coverUrl.isNullOrBlank()) {
        add(
            ArtworkCandidate(
                url = coverUrl,
                sourceName = "IGDB",
                description = "$label Cover",
                isBanner = false
            )
        )
    }
    artworks.forEachIndexed { idx, url ->
        add(
            ArtworkCandidate(
                url = url,
                sourceName = "IGDB Artwork",
                description = "$label (Artwork ${idx + 1})",
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
        private val STEAM_APP_URL_REGEX = Regex("store\\.steampowered\\.com/app/(\\d+)")
    }

    private fun JsonElement?.str(): String? {
        if (this == null || this is JsonNull) return null
        val raw = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return raw.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray

    private fun JsonElement?.longVal(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonElement?.intVal(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
    private fun JsonElement?.floatVal(): Float? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
    private fun JsonElement?.boolVal(): Boolean? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

    private fun igdbCoverUrl(imageId: String?): String? =
        imageId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big_2x/${it}.jpg" }

    private fun igdb1080pUrl(imageId: String?): String? =
        imageId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_1080p/${it}.jpg" }

    private fun igdbLogoUrl(imageId: String?): String? =
        imageId?.takeIf { it.isNotBlank() }?.let { "https://images.igdb.com/igdb/image/upload/t_logo_med/${it}.png" }

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
                val obj = json.parseToJsonElement(body).obj() ?: return null
                val token = obj["access_token"].str() ?: return null
                val expiresInSec = obj["expires_in"].longVal() ?: 3600L
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

    private suspend fun postIgdb(
        endpoint: String,
        clientId: String,
        token: String,
        queryBody: String
    ): JsonArray? = NetworkPacer.withIgdbPacing {
        try {
            val req = Request.Builder()
                .url("https://api.igdb.com/v4/$endpoint")
                .header("Client-ID", clientId.trim())
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(queryBody.toRequestBody(plainMediaType))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withIgdbPacing null
                val body = resp.body?.string() ?: return@withIgdbPacing null
                json.parseToJsonElement(body).arr()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "IGDB $endpoint error: ${e.message}")
            null
        }
    }

    private fun extractSteamAppId(obj: JsonObject): Long? {
        obj["websites"].arr()?.forEach { wEl ->
            val u = wEl.obj()?.get("url").str() ?: return@forEach
            val m = STEAM_APP_URL_REGEX.find(u)
            val id = m?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (id != null && id > 0L) return id
        }
        obj["external_games"].arr()?.forEach { eEl ->
            val eo = eEl.obj() ?: return@forEach
            val src = eo["external_game_source"].intVal() ?: eo["category"].intVal()
            val u = eo["url"].str().orEmpty()
            if (src == 1 || "store.steampowered.com/app/" in u) {
                val uidNum = eo["uid"].str()?.toLongOrNull()
                if (uidNum != null && uidNum > 0L) return uidNum
                val regexNum = STEAM_APP_URL_REGEX.find(u)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (regexNum != null && regexNum > 0L) return regexNum
            }
        }
        return null
    }

    private fun formatEpochYear(epochSec: Long?): String? {
        if (epochSec == null || epochSec <= 0L) return null
        return try {
            SimpleDateFormat("yyyy", Locale.US).format(Date(epochSec * 1000L))
        } catch (_: Exception) {
            null
        }
    }

    private fun formatEpochFullDate(epochSec: Long?): String? {
        if (epochSec == null || epochSec <= 0L) return null
        return try {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochSec * 1000L))
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizePlatformDisplay(name: String?, abbr: String?): String? {
        val raw = (abbr ?: name)?.trim() ?: return null
        val low = (name ?: abbr).orEmpty().lowercase()
        return when {
            "pc (microsoft windows)" in low || low == "pc" || raw.equals("win", true) -> "PC"
            "playstation 5" in low || raw.equals("ps5", true) -> "PS5"
            "playstation 4" in low || raw.equals("ps4", true) -> "PS4"
            "playstation 3" in low || raw.equals("ps3", true) -> "PS3"
            "playstation vita" in low || "vita" in low -> "PS Vita"
            "playstation" in low -> raw
            "nintendo switch" in low || raw.equals("switch", true) -> "Switch"
            "xbox series" in low -> "Xbox Series X|S"
            "xbox one" in low || raw.equals("xone", true) -> "Xbox One"
            "xbox 360" in low -> "Xbox 360"
            "linux" in low -> "Linux"
            "mac" in low -> "macOS"
            "android" in low -> "Android"
            "ios" in low -> "iOS"
            else -> raw
        }
    }

    private fun classifyWebsite(url: String): String {
        val low = url.lowercase()
        return when {
            "store.steampowered.com" in low -> "Steam"
            "store.playstation.com" in low -> "PlayStation Store"
            "xbox.com" in low -> "Xbox Store"
            "nintendo.com" in low || "nintendo." in low -> "Nintendo eShop"
            "epicgames.com" in low -> "Epic Games"
            "gog.com" in low -> "GOG"
            "pcgamingwiki.com" in low -> "PCGamingWiki"
            "howlongtobeat.com" in low -> "HowLongToBeat"
            "wikipedia.org" in low -> "Wikipedia"
            "wiki.gg" in low || "fandom.com" in low -> "Wiki"
            "discord.gg" in low || "discord.com" in low -> "Discord"
            "reddit.com" in low -> "Reddit"
            "youtube.com" in low || "youtu.be" in low -> "YouTube"
            "twitch.tv" in low -> "Twitch"
            "twitter.com" in low || "x.com" in low -> "X / Twitter"
            else -> "Official Site"
        }
    }

    private fun parseSearchResults(arr: JsonArray): List<SearchResultItem> {
        return arr.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o["id"].longVal() ?: return@mapNotNull null
            val name = o["name"].str() ?: return@mapNotNull null
            val coverId = o["cover"].obj()?.get("image_id").str()
            val artId = o["artworks"].arr()?.firstOrNull().obj()?.get("image_id").str()
                ?: o["screenshots"].arr()?.firstOrNull().obj()?.get("image_id").str()
            val coverUrl = igdbCoverUrl(coverId) ?: igdb1080pUrl(artId) ?: return@mapNotNull null
            val bannerUrl = igdb1080pUrl(artId)
            val steamId = extractSteamAppId(o)
            val year = formatEpochYear(o["first_release_date"].longVal())
            val rawScore = o["aggregated_rating"].floatVal() ?: o["rating"].floatVal()
            val score = rawScore?.let { (it / 10f).coerceIn(0f, 10f) }
            val plats = o["platforms"].arr()?.mapNotNull { pEl ->
                val po = pEl.obj() ?: return@mapNotNull null
                normalizePlatformDisplay(po["name"].str(), po["abbreviation"].str())
            }.orEmpty().distinct().take(4).joinToString(", ").ifBlank { null }

            SearchResultItem(
                title = name,
                synopsis = o["summary"].str(),
                coverUrl = LosslessCoverMapper.toLosslessUrl(coverUrl),
                bannerUrl = bannerUrl?.let { LosslessCoverMapper.toLosslessUrl(it) },
                mediaType = MediaType.GAME,
                releaseYear = year,
                communityScore = score,
                sourceProvider = "IGDB",
                igdbId = id,
                steamAppId = steamId,
                platform = plats
            )
        }
    }

    suspend fun searchGames(
        clientId: String,
        clientSecret: String,
        query: String,
        limit: Int = 15
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val cleanTitle = query.replace("\"", "").trim()
        if (cleanTitle.isBlank() || clientId.isBlank() || clientSecret.isBlank()) return@withContext emptyList()
        try {
            val token = getOrFetchAccessToken(clientId, clientSecret) ?: return@withContext emptyList()
            val body = "fields id,name,summary,first_release_date,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id,platforms.abbreviation,platforms.name,websites.url,external_games.external_game_source,external_games.uid,external_games.url; search \"$cleanTitle\"; where version_parent = null; limit $limit;"
            val arr = postIgdb("games", clientId, token, body) ?: return@withContext emptyList()
            parseSearchResults(arr)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun fetchDiscoverGames(
        clientId: String,
        clientSecret: String,
        shelf: String,
        limit: Int = 14
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || clientSecret.isBlank()) return@withContext emptyList()
        try {
            val token = getOrFetchAccessToken(clientId, clientSecret) ?: return@withContext emptyList()
            val nowSec = System.currentTimeMillis() / 1000L
            val recentCutoff = nowSec - (365L * 24L * 3600L)
            val commonFields = "fields id,name,summary,first_release_date,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id,platforms.abbreviation,platforms.name,websites.url,external_games.external_game_source,external_games.uid,external_games.url"
            val query = when (shelf.lowercase()) {
                "top_rated" -> "$commonFields; where aggregated_rating >= 85 & total_rating_count >= 50 & cover != null & version_parent = null; sort total_rating desc; limit $limit;"
                "upcoming" -> "$commonFields; where first_release_date > $nowSec & hypes >= 10 & cover != null & version_parent = null; sort hypes desc; limit $limit;"
                else -> "$commonFields; where first_release_date >= $recentCutoff & first_release_date <= $nowSec & total_rating_count >= 20 & cover != null & version_parent = null; sort total_rating_count desc; limit $limit;"
            }
            val arr = postIgdb("games", clientId, token, query) ?: return@withContext emptyList()
            parseSearchResults(arr)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun fetchFullGameDetails(
        clientId: String,
        clientSecret: String,
        igdbId: Long?,
        steamAppId: Long?,
        title: String
    ): IgdbFullGameDetails? = withContext(Dispatchers.IO) {
        if (clientId.isBlank() || clientSecret.isBlank()) return@withContext null
        try {
            val token = getOrFetchAccessToken(clientId, clientSecret) ?: return@withContext null
            var targetIgdbId: Long? = igdbId?.takeIf { it > 0L }

            // If only Steam AppID is known (e.g. migrated DB entry), resolve canonical IGDB game ID directly
            if (targetIgdbId == null && steamAppId != null && steamAppId > 0L) {
                val extArr = postIgdb(
                    "external_games",
                    clientId,
                    token,
                    "fields game; where external_game_source = 1 & uid = \"$steamAppId\"; limit 1;"
                )
                targetIgdbId = extArr?.firstOrNull().obj()?.get("game").longVal()
            }

            val fullFields = "fields id,name,slug,summary,storyline,first_release_date,rating,rating_count,aggregated_rating,aggregated_rating_count," +
                "cover.image_id,artworks.image_id,screenshots.image_id,videos.video_id,videos.name," +
                "platforms.name,platforms.abbreviation,genres.name,themes.name,game_modes.name,player_perspectives.name,game_engines.name," +
                "alternative_names.name,involved_companies.developer,involved_companies.publisher,involved_companies.company.id,involved_companies.company.name,involved_companies.company.logo.image_id," +
                "dlcs.id,dlcs.name,dlcs.cover.image_id,dlcs.first_release_date," +
                "expansions.id,expansions.name,expansions.cover.image_id,expansions.first_release_date," +
                "standalone_expansions.id,standalone_expansions.name,standalone_expansions.cover.image_id," +
                "remakes.id,remakes.name,remakes.cover.image_id,remasters.id,remasters.name,remasters.cover.image_id," +
                "parent_game.id,parent_game.name,parent_game.cover.image_id," +
                "similar_games.id,similar_games.name,similar_games.cover.image_id,similar_games.rating,similar_games.first_release_date," +
                "external_games.external_game_source,external_games.uid,external_games.url,websites.url," +
                "age_ratings.rating_category.rating,age_ratings.organization.name"

            val gameArr = if (targetIgdbId != null) {
                postIgdb("games", clientId, token, "$fullFields; where id = $targetIgdbId; limit 1;")
            } else {
                val cleanTitle = title.replace("\"", "").trim()
                if (cleanTitle.isBlank()) null
                else postIgdb("games", clientId, token, "$fullFields; search \"$cleanTitle\"; limit 6;")
            } ?: return@withContext null

            val candidates = gameArr.mapNotNull { it.obj() }
            if (candidates.isEmpty()) return@withContext null

            val cleanTargetName = title.trim()
            val chosen = candidates.firstOrNull {
                it["name"].str().equals(cleanTargetName, ignoreCase = true)
            } ?: candidates.firstOrNull {
                it["cover"] is JsonObject && ((it["artworks"].arr()?.isNotEmpty() == true) || (it["screenshots"].arr()?.isNotEmpty() == true))
            } ?: candidates.first()

            val resolvedId = chosen["id"].longVal() ?: return@withContext null
            val resolvedName = chosen["name"].str() ?: title

            val ttbArr = postIgdb(
                "game_time_to_beats",
                clientId,
                token,
                "fields hastily,normally,completely,count; where game_id = $resolvedId; limit 1;"
            )
            val ttbObj = ttbArr?.firstOrNull().obj()
            val hltb = GameHltbTimes(
                hastilySeconds = ttbObj?.get("hastily").intVal(),
                normallySeconds = ttbObj?.get("normally").intVal(),
                completelySeconds = ttbObj?.get("completely").intVal(),
                count = ttbObj?.get("count").intVal()
            )

            val coverUrl = igdbCoverUrl(chosen["cover"].obj()?.get("image_id").str())
            val artworks = chosen["artworks"].arr()?.mapNotNull {
                igdb1080pUrl(it.obj()?.get("image_id").str())
            }.orEmpty().distinct()
            val screenshots = chosen["screenshots"].arr()?.mapNotNull {
                igdb1080pUrl(it.obj()?.get("image_id").str())
            }.orEmpty().distinct()

            val gallery = buildList {
                artworks.forEachIndexed { idx, u ->
                    add(GameMediaAsset(url = u, thumbnailUrl = u, label = "Artwork ${idx + 1}", isScreenshot = false))
                }
                screenshots.forEachIndexed { idx, u ->
                    add(GameMediaAsset(url = u, thumbnailUrl = u, label = "Screenshot ${idx + 1}", isScreenshot = true))
                }
            }

            val trailers = chosen["videos"].arr()?.mapNotNull { vEl ->
                val vo = vEl.obj() ?: return@mapNotNull null
                val ytId = vo["video_id"].str() ?: return@mapNotNull null
                val vName = vo["name"].str() ?: "Trailer"
                MediaTrailer(title = vName, youtubeKey = ytId, type = "Trailer")
            }.orEmpty().distinctBy { it.youtubeKey }

            val platforms = chosen["platforms"].arr()?.mapNotNull { pEl ->
                val po = pEl.obj() ?: return@mapNotNull null
                normalizePlatformDisplay(po["name"].str(), po["abbreviation"].str())
            }.orEmpty().distinct()

            val tags = buildList {
                var idx = 100L
                chosen["genres"].arr()?.forEach { gEl ->
                    gEl.obj()?.get("name").str()?.let { n ->
                        add(AnimeTagItem(id = idx++, name = n, category = "genre"))
                    }
                }
                chosen["themes"].arr()?.forEach { tEl ->
                    tEl.obj()?.get("name").str()?.let { n ->
                        add(AnimeTagItem(id = idx++, name = n, category = "theme"))
                    }
                }
                chosen["game_modes"].arr()?.forEach { mEl ->
                    mEl.obj()?.get("name").str()?.let { n ->
                        add(AnimeTagItem(id = idx++, name = n, category = "mode"))
                    }
                }
                chosen["player_perspectives"].arr()?.forEach { pEl ->
                    pEl.obj()?.get("name").str()?.let { n ->
                        add(AnimeTagItem(id = idx++, name = n, category = "mode"))
                    }
                }
            }.distinctBy { it.name.lowercase() }

            val perspectives = chosen["player_perspectives"].arr()?.mapNotNull { it.obj()?.get("name").str() }.orEmpty().distinct()
            val modes = chosen["game_modes"].arr()?.mapNotNull { it.obj()?.get("name").str() }.orEmpty().distinct()
            val engine = chosen["game_engines"].arr()?.mapNotNull { it.obj()?.get("name").str() }?.firstOrNull()
            val synonyms = chosen["alternative_names"].arr()?.mapNotNull { it.obj()?.get("name").str() }.orEmpty()
                .distinct().filter { !it.equals(resolvedName, ignoreCase = true) }.take(8)

            val companies = chosen["involved_companies"].arr()?.mapNotNull { icEl ->
                val ic = icEl.obj() ?: return@mapNotNull null
                val comp = ic["company"].obj() ?: return@mapNotNull null
                val compName = comp["name"].str() ?: return@mapNotNull null
                val logoId = comp["logo"].obj()?.get("image_id").str()
                val isDev = ic["developer"].boolVal() == true
                NetworkProductionCompany(
                    id = comp["id"].longVal(),
                    name = compName,
                    logoUrl = igdbLogoUrl(logoId),
                    kind = if (isDev) "company" else "network"
                )
            }.orEmpty().distinctBy { it.name.lowercase() }

            val expansionSeeds = mutableListOf<Pair<String, Long?>>()
            val relations = buildList {
                fun addRelated(fieldKey: String, relType: String, fmt: String, seedAsExpansion: Boolean = false) {
                    chosen[fieldKey].arr()?.forEach { rEl ->
                        val ro = rEl.obj() ?: return@forEach
                        val rId = ro["id"].longVal() ?: return@forEach
                        val rName = ro["name"].str() ?: return@forEach
                        val rCover = igdbCoverUrl(ro["cover"].obj()?.get("image_id").str())
                        val rYear = formatEpochYear(ro["first_release_date"].longVal())?.toIntOrNull()
                        if (seedAsExpansion) {
                            expansionSeeds.add(rName to rId)
                        }
                        add(
                            AnimeRelationItem(
                                id = rId,
                                title = rName,
                                relationType = relType,
                                format = fmt,
                                coverUrl = rCover,
                                year = rYear,
                                mediaType = MediaType.GAME
                            )
                        )
                    }
                }
                addRelated("expansions", "EXPANSION", "DLC", seedAsExpansion = true)
                addRelated("standalone_expansions", "STANDALONE", "Expansion", seedAsExpansion = true)
                addRelated("dlcs", "DLC", "DLC", seedAsExpansion = true)
                addRelated("remakes", "REMAKE", "Game")
                addRelated("remasters", "REMASTER", "Game")
                chosen["parent_game"].obj()?.let { po ->
                    val pId = po["id"].longVal()
                    val pName = po["name"].str()
                    if (pId != null && pName != null) {
                        add(
                            AnimeRelationItem(
                                id = pId,
                                title = pName,
                                relationType = "BASE_GAME",
                                format = "Game",
                                coverUrl = igdbCoverUrl(po["cover"].obj()?.get("image_id").str()),
                                mediaType = MediaType.GAME
                            )
                        )
                    }
                }
            }.distinctBy { it.id }

            val similarGames = chosen["similar_games"].arr()?.mapNotNull { sEl ->
                val so = sEl.obj() ?: return@mapNotNull null
                val sId = so["id"].longVal() ?: return@mapNotNull null
                val sName = so["name"].str() ?: return@mapNotNull null
                val sCover = igdbCoverUrl(so["cover"].obj()?.get("image_id").str()) ?: return@mapNotNull null
                RecommendationItem(
                    title = sName,
                    coverUrl = LosslessCoverMapper.toLosslessUrl(sCover),
                    releaseYear = formatEpochYear(so["first_release_date"].longVal()),
                    rating = so["rating"].floatVal()?.let { (it / 10f).coerceIn(0f, 10f) },
                    mediaType = MediaType.GAME,
                    igdbId = sId
                )
            }.orEmpty().take(12)

            val slug = chosen["slug"].str()
            val resolvedSteamAppId = extractSteamAppId(chosen) ?: steamAppId
            val links = buildList {
                if (!slug.isNullOrBlank()) {
                    add(ExternalLinkItem(id = 1L, url = "https://www.igdb.com/games/$slug", site = "IGDB", type = "INFO"))
                }
                chosen["websites"].arr()?.forEachIndexed { idx, wEl ->
                    val u = wEl.obj()?.get("url").str() ?: return@forEachIndexed
                    add(ExternalLinkItem(id = (idx + 10).toLong(), url = u, site = classifyWebsite(u), type = "INFO"))
                }
                if (resolvedSteamAppId != null && none { "store.steampowered.com" in it.url }) {
                    add(ExternalLinkItem(id = 9001L, url = "https://store.steampowered.com/app/$resolvedSteamAppId/", site = "Steam", type = "STORE"))
                }
            }.distinctBy { it.site to it.url }.take(14)

            val ageRatings = chosen["age_ratings"].arr()?.mapNotNull { aEl ->
                val ao = aEl.obj() ?: return@mapNotNull null
                val org = ao["organization"].obj()?.get("name").str()
                val ratingVal = ao["rating_category"].obj()?.get("rating").str() ?: return@mapNotNull null
                if (!org.isNullOrBlank()) "$org $ratingVal" else ratingVal
            }.orEmpty().distinct()

            val firstRelSec = chosen["first_release_date"].longVal()
            IgdbFullGameDetails(
                igdbId = resolvedId,
                name = resolvedName,
                slug = slug,
                summary = chosen["summary"].str(),
                storyline = chosen["storyline"].str(),
                releaseYear = formatEpochYear(firstRelSec),
                firstReleaseDateFormatted = formatEpochFullDate(firstRelSec),
                userRating = chosen["rating"].floatVal()?.let { (it / 10f).coerceIn(0f, 10f) },
                userRatingCount = chosen["rating_count"].intVal(),
                criticRating = chosen["aggregated_rating"].floatVal()?.roundToInt()?.coerceIn(0, 100),
                criticRatingCount = chosen["aggregated_rating_count"].intVal(),
                coverUrl = coverUrl,
                artworks = artworks,
                screenshots = screenshots,
                mediaGallery = gallery,
                trailers = trailers,
                platforms = platforms,
                tags = tags,
                perspectives = perspectives,
                gameModes = modes,
                engine = engine,
                synonyms = synonyms,
                companies = companies,
                relations = relations,
                similarGames = similarGames,
                expansionSeeds = expansionSeeds,
                steamAppId = resolvedSteamAppId,
                externalLinks = links,
                ageRatings = ageRatings,
                hltb = hltb
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "fetchFullGameDetails error for $title: ${e.message}")
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
            val arr = json.parseToJsonElement(body).arr() ?: return null
            if (arr.isEmpty()) return null

            val objects = arr.mapNotNull { it.obj() }
            if (objects.isEmpty()) return null

            fun JsonObject.hasVisuals(): Boolean {
                val hasArt = (this["artworks"] as? JsonArray)?.isNotEmpty() == true
                val hasCover = this["cover"] is JsonObject
                return hasArt || hasCover
            }

            val targetTitle = preferredTitle?.trim()?.takeIf { it.isNotBlank() }
            val chosen = objects.firstOrNull { o ->
                val name = o["name"].str()
                targetTitle != null && name.equals(targetTitle, ignoreCase = true) && o.hasVisuals()
            } ?: objects.firstOrNull { o ->
                val name = o["name"].str()
                targetTitle != null && name.equals(targetTitle, ignoreCase = true)
            } ?: objects.firstOrNull { it.hasVisuals() }
                ?: objects.first()

            val igdbId = chosen["id"].longVal()
            val gameName = chosen["name"].str()
            val coverUrl = igdbCoverUrl(chosen["cover"].obj()?.get("image_id").str())
            val artworks = chosen["artworks"].arr()?.mapNotNull { el ->
                igdb1080pUrl(el.obj()?.get("image_id").str())
            }.orEmpty()
            val screenshots = chosen["screenshots"].arr()?.mapNotNull { el ->
                igdb1080pUrl(el.obj()?.get("image_id").str())
            }.orEmpty()
            val rawRating = chosen["aggregated_rating"].floatVal() ?: chosen["rating"].floatVal()
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
                val hit = NetworkPacer.withIgdbPacing {
                    queryIgdbGames(clientId, token, slugQuery, preferredTitle = title)
                }
                if (hit != null) return@withContext hit
            }
            val cleanTitle = title.replace("\"", "").trim()
            if (cleanTitle.isBlank()) return@withContext null
            val searchQuery = "fields id,name,slug,rating,aggregated_rating,cover.image_id,artworks.image_id,screenshots.image_id; search \"$cleanTitle\"; limit 5;"
            NetworkPacer.withIgdbPacing {
                queryIgdbGames(clientId, token, searchQuery, preferredTitle = cleanTitle)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("IgdbClient", "fetchGameArtwork error for $title: ${e.message}")
            null
        }
    }
}
