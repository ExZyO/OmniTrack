package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.ExternalLinkItem
import com.omnitrack.app.data.model.GameAchievementItem
import com.omnitrack.app.data.model.GameDetailMeta
import com.omnitrack.app.data.model.GameHltbTimes
import com.omnitrack.app.data.model.GameMediaAsset
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.math.roundToInt

data class GameFullEnrichmentResult(
    val resolvedIgdbId: Long? = null,
    val resolvedSteamAppId: Long? = null,
    val title: String? = null,
    val synonyms: List<String> = emptyList(),
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val clearLogoUrl: String? = null,
    val releaseYear: String? = null,
    val startDate: String? = null,
    val contentRating: String? = null,
    val runtimeMinutes: Int? = null,
    val platformsSummary: String? = null,
    val studioOrNetwork: String? = null,
    val communityScore: Float? = null,
    val metacriticScore: Int? = null,
    val steamPlaytimeHours: Double? = null,
    val gameDetailMeta: GameDetailMeta = GameDetailMeta(),
    val companies: List<NetworkProductionCompany> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val similarGames: List<RecommendationItem> = emptyList(),
    val tags: List<AnimeTagItem> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val discussionComments: List<DiscussionComment> = emptyList()
)

data class GameSteamSyncResult(
    val steamAppId: Long? = null,
    val playtimeHours: Double? = null,
    val achievements: List<GameAchievementItem> = emptyList(),
    val achievementsUnlocked: Int? = null,
    val achievementsTotal: Int? = null,
    val error: String? = null
)

class GameEnrichmentSource(
    private val client: OkHttpClient = NetworkClient.getClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val igdbClient = IgdbApiClient(client)

    private fun JsonElement?.str(): String? {
        if (this == null || this is JsonNull) return null
        val s = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return s.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.intVal(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
    private fun JsonElement?.longVal(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonElement?.floatVal(): Float? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull
    private fun JsonElement?.boolVal(): Boolean? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

    private fun normalizeTitleKey(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]"), "")

    suspend fun fetchFullGameData(
        item: MediaItemEntity,
        igdbClientId: String? = null,
        igdbClientSecret: String? = null,
        steamGridDbApiKey: String? = null,
        steamApiKey: String? = null,
        steamId64: String? = null
    ): GameFullEnrichmentResult? = withContext(Dispatchers.IO) {
        try {
            val hasIgdbKeys = !igdbClientId.isNullOrBlank() && !igdbClientSecret.isNullOrBlank()
            val initialSteamId = item.steamAppId?.takeIf { it > 0L }

            val igdbData = if (hasIgdbKeys) {
                igdbClient.fetchFullGameDetails(
                    clientId = igdbClientId!!,
                    clientSecret = igdbClientSecret!!,
                    igdbId = item.igdbId,
                    steamAppId = initialSteamId,
                    title = item.title
                )
            } else null

            val resolvedIgdbId = igdbData?.igdbId ?: item.igdbId
            val displayName = igdbData?.name?.takeIf { it.isNotBlank() } ?: item.title

            val isNonPcExclusive = igdbData != null &&
                igdbData.platforms.isNotEmpty() &&
                igdbData.platforms.none { p ->
                    val low = p.lowercase()
                    "pc" in low || "linux" in low || "mac" in low || "steam" in low
                }

            val resolvedSteamAppId = initialSteamId
                ?: igdbData?.steamAppId
                ?: if (!isNonPcExclusive) resolveExactSteamAppId(displayName) else null

            coroutineScope {
                val steamStoreDeferred = async {
                    resolvedSteamAppId?.let { fetchSteamStoreBundle(it) }
                }
                val deckDeferred = async {
                    resolvedSteamAppId?.let { fetchSteamDeckCategory(it) }
                }
                val protonDeferred = async {
                    resolvedSteamAppId?.let { fetchProtonDbSummary(it) }
                }
                val globalAchDeferred = async {
                    resolvedSteamAppId?.let { fetchGlobalAchievementPercentages(it) } ?: emptyMap()
                }
                val playerAchDeferred = async {
                    if (resolvedSteamAppId != null && !steamApiKey.isNullOrBlank() && !steamId64.isNullOrBlank()) {
                        fetchSteamPlayerAchievements(steamApiKey, steamId64, resolvedSteamAppId)
                    } else null
                }
                val playerPlaytimeDeferred = async {
                    if (resolvedSteamAppId != null && !steamApiKey.isNullOrBlank() && !steamId64.isNullOrBlank()) {
                        fetchSteamGamePlaytimeHours(steamApiKey, steamId64, resolvedSteamAppId)
                    } else null
                }
                val redditDeferred = async {
                    fetchRedditDiscussions(displayName)
                }
                val bangumiDeferred = async {
                    if (igdbData == null || igdbData.userRating == null) {
                        fetchBangumiGameScore(displayName)
                    } else null
                }

                val steamStore = steamStoreDeferred.await()
                val deckCategory = deckDeferred.await()
                val protonSummary = protonDeferred.await()
                val globalAchPcts = globalAchDeferred.await()
                val playerAch = playerAchDeferred.await()
                val steamPlaytimeHours = playerPlaytimeDeferred.await()
                val redditComments = redditDeferred.await()
                val bangumiScore = bangumiDeferred.await()

                val needsSgdbHero = steamStore?.libraryHeroUrl == null && igdbData?.artworks.isNullOrEmpty()
                val needsSgdbLogo = steamStore?.logoUrl == null
                val sgdbPair = if (!steamGridDbApiKey.isNullOrBlank() && (needsSgdbHero || needsSgdbLogo)) {
                    fetchSteamGridDbHeroAndLogo(resolvedSteamAppId, displayName, steamGridDbApiKey)
                } else null

                val combinedGallery = buildList {
                    igdbData?.mediaGallery?.let { addAll(it) }
                    steamStore?.screenshots?.forEachIndexed { idx, sUrl ->
                        if (none { it.url == sUrl }) {
                            add(GameMediaAsset(url = sUrl, thumbnailUrl = sUrl, label = "Steam Screenshot ${idx + 1}", isScreenshot = true))
                        }
                    }
                    sgdbPair?.first?.let { hero ->
                        if (none { it.url == hero }) {
                            add(0, GameMediaAsset(url = hero, thumbnailUrl = hero, label = "SteamGridDB Hero", isScreenshot = false))
                        }
                    }
                    steamStore?.libraryHeroUrl?.let { hero ->
                        if (none { it.url == hero }) {
                            add(0, GameMediaAsset(url = hero, thumbnailUrl = hero, label = "Steam Hero", isScreenshot = false))
                        }
                    }
                }.distinctBy { it.url }.take(24)

                val achievements = if (playerAch != null && playerAch.isNotEmpty()) {
                    playerAch.map { ach ->
                        val rarity = globalAchPcts[ach.apiName] ?: ach.globalRarityPct
                        ach.copy(globalRarityPct = rarity)
                    }.sortedWith(
                        compareByDescending<GameAchievementItem> { it.isUnlocked }
                            .thenBy { it.globalRarityPct ?: 100f }
                    )
                } else emptyList()

                val achUnlockedCount = if (achievements.isNotEmpty()) achievements.count { it.isUnlocked } else item.steamAchievementsUnlocked
                val achTotalCount = when {
                    achievements.isNotEmpty() -> achievements.size
                    globalAchPcts.isNotEmpty() -> globalAchPcts.size
                    else -> item.steamAchievementsTotal
                }

                val existingNonFallbackBanner = item.bannerUrl?.takeIf {
                    it.isNotBlank() && !it.contains("header.jpg")
                }
                val finalBanner = sgdbPair?.first
                    ?: steamStore?.libraryHeroUrl
                    ?: igdbData?.artworks?.firstOrNull()
                    ?: steamStore?.backgroundRaw
                    ?: existingNonFallbackBanner
                    ?: igdbData?.screenshots?.firstOrNull()
                    ?: steamStore?.headerImage
                    ?: item.bannerUrl?.takeIf { it.isNotBlank() }

                val finalLogo = steamStore?.logoUrl ?: sgdbPair?.second ?: item.clearartUrl

                val finalCompanies = igdbData?.companies?.takeIf { it.isNotEmpty() }
                    ?: steamStore?.companies.orEmpty()

                val finalTags = igdbData?.tags?.takeIf { it.isNotEmpty() }
                    ?: steamStore?.genres?.mapIndexed { idx, g ->
                        AnimeTagItem(id = (idx + 500).toLong(), name = g, category = "genre")
                    }.orEmpty()

                val finalLinks = buildList {
                    igdbData?.externalLinks?.let { addAll(it) }
                    if (resolvedSteamAppId != null && none { "store.steampowered.com" in it.url }) {
                        add(ExternalLinkItem(id = 9001L, url = "https://store.steampowered.com/app/$resolvedSteamAppId/", site = "Steam", type = "STORE"))
                    }
                    steamStore?.metacriticUrl?.let { mcUrl ->
                        add(ExternalLinkItem(id = 9002L, url = mcUrl, site = "Metacritic", type = "INFO"))
                    }
                    if (protonSummary != null && resolvedSteamAppId != null) {
                        add(ExternalLinkItem(id = 9003L, url = "https://www.protondb.com/app/$resolvedSteamAppId", site = "ProtonDB", type = "INFO"))
                    }
                }.distinctBy { it.url }.take(14)

                val meta = GameDetailMeta(
                    igdbId = resolvedIgdbId,
                    steamAppId = resolvedSteamAppId,
                    igdbUserRating = igdbData?.userRating,
                    igdbUserCount = igdbData?.userRatingCount,
                    igdbCriticRating = igdbData?.criticRating,
                    igdbCriticCount = igdbData?.criticRatingCount,
                    metacriticScore = steamStore?.metacriticScore ?: item.metacriticScore,
                    steamPositivePct = steamStore?.positivePct,
                    steamReviewDesc = steamStore?.reviewDesc,
                    bangumiScore = bangumiScore,
                    storyline = igdbData?.storyline,
                    hltb = igdbData?.hltb ?: GameHltbTimes(),
                    steamDeckCategory = deckCategory,
                    protonDbTier = protonSummary?.first,
                    protonDbTotal = protonSummary?.second,
                    gameEngine = igdbData?.engine,
                    perspectives = igdbData?.perspectives.orEmpty(),
                    gameModes = igdbData?.gameModes.orEmpty(),
                    ageRatings = igdbData?.ageRatings.orEmpty(),
                    supportedPlatforms = igdbData?.platforms.orEmpty(),
                    mediaGallery = combinedGallery,
                    achievements = achievements,
                    achievementsUnlocked = achUnlockedCount,
                    achievementsTotal = achTotalCount,
                    steamSyncedAtMs = if (playerAch != null || steamPlaytimeHours != null) System.currentTimeMillis() else null,
                    expansionSeeds = igdbData?.expansionSeeds.orEmpty()
                )

                val communityOut = igdbData?.userRating
                    ?: steamStore?.positivePct?.let { it / 10f }
                    ?: bangumiScore
                    ?: item.communityScore

                val platformsSummary = igdbData?.platforms?.take(6)?.joinToString(", ")?.ifBlank { null }
                    ?: item.platform

                GameFullEnrichmentResult(
                    resolvedIgdbId = resolvedIgdbId,
                    resolvedSteamAppId = resolvedSteamAppId,
                    title = igdbData?.name,
                    synonyms = igdbData?.synonyms.orEmpty(),
                    synopsis = igdbData?.summary ?: steamStore?.shortDescription ?: item.synopsis,
                    coverUrl = igdbData?.coverUrl
                        ?: resolvedSteamAppId?.let { "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$it/library_600x900_2x.jpg" },
                    bannerUrl = finalBanner,
                    clearLogoUrl = finalLogo,
                    releaseYear = igdbData?.releaseYear ?: steamStore?.releaseYear ?: item.releaseYear,
                    startDate = igdbData?.firstReleaseDateFormatted ?: steamStore?.releaseDateText,
                    contentRating = igdbData?.ageRatings?.firstOrNull() ?: item.contentRating,
                    runtimeMinutes = igdbData?.hltb?.mainMinutes ?: item.runtimeMinutes,
                    platformsSummary = platformsSummary,
                    studioOrNetwork = finalCompanies.firstOrNull { it.kind == "company" }?.name
                        ?: finalCompanies.firstOrNull()?.name
                        ?: item.studioOrNetwork,
                    communityScore = communityOut,
                    metacriticScore = steamStore?.metacriticScore ?: item.metacriticScore,
                    steamPlaytimeHours = steamPlaytimeHours,
                    gameDetailMeta = meta,
                    companies = finalCompanies,
                    relations = igdbData?.relations.orEmpty(),
                    similarGames = igdbData?.similarGames.orEmpty(),
                    tags = finalTags,
                    trailers = igdbData?.trailers.orEmpty(),
                    externalLinks = finalLinks,
                    discussionComments = redditComments
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.e("GameEnrich", "Error enriching Game ${item.title}: ${e.message}")
            null
        }
    }

    suspend fun syncSteamStatsOnly(
        title: String,
        knownSteamAppId: Long?,
        steamApiKey: String?,
        steamId64: String?
    ): GameSteamSyncResult = withContext(Dispatchers.IO) {
        if (steamApiKey.isNullOrBlank() || steamId64.isNullOrBlank()) {
            return@withContext GameSteamSyncResult(error = "Connect Steam in Tracker Accounts first")
        }
        try {
            val resolvedId = knownSteamAppId?.takeIf { it > 0L } ?: resolveExactSteamAppId(title)
                ?: return@withContext GameSteamSyncResult(error = "No matching Steam AppID found for \"$title\"")

            coroutineScope {
                val playtimeDeferred = async {
                    fetchSteamGamePlaytimeHours(steamApiKey, steamId64, resolvedId)
                }
                val achDeferred = async {
                    fetchSteamPlayerAchievements(steamApiKey, steamId64, resolvedId)
                }
                val globalDeferred = async {
                    fetchGlobalAchievementPercentages(resolvedId)
                }

                val playtime = playtimeDeferred.await()
                val rawAch = achDeferred.await()
                val globalPcts = globalDeferred.await()

                val mergedAch = rawAch.map { ach ->
                    ach.copy(globalRarityPct = globalPcts[ach.apiName] ?: ach.globalRarityPct)
                }.sortedWith(
                    compareByDescending<GameAchievementItem> { it.isUnlocked }
                        .thenBy { it.globalRarityPct ?: 100f }
                )

                val unlockedCount = if (mergedAch.isNotEmpty()) mergedAch.count { it.isUnlocked } else null
                val totalCount = when {
                    mergedAch.isNotEmpty() -> mergedAch.size
                    globalPcts.isNotEmpty() -> globalPcts.size
                    else -> null
                }

                GameSteamSyncResult(
                    steamAppId = resolvedId,
                    playtimeHours = playtime,
                    achievements = mergedAch,
                    achievementsUnlocked = unlockedCount,
                    achievementsTotal = totalCount
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            GameSteamSyncResult(error = e.message ?: "Steam sync failed")
        }
    }

    private suspend fun resolveExactSteamAppId(title: String): Long? = withContext(Dispatchers.IO) {
        val targetNorm = normalizeTitleKey(title)
        if (targetNorm.length < 2) return@withContext null
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val items = json.parseToJsonElement(body).obj()?.get("items").arr() ?: return@withContext null
                items.firstNotNullOfOrNull { el ->
                    val o = el.obj() ?: return@firstNotNullOfOrNull null
                    val name = o["name"].str() ?: return@firstNotNullOfOrNull null
                    if (normalizeTitleKey(name) == targetNorm) o["id"].longVal() else null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private data class SteamStoreBundle(
        val logoUrl: String? = null,
        val libraryHeroUrl: String? = null,
        val backgroundRaw: String? = null,
        val headerImage: String? = null,
        val metacriticScore: Int? = null,
        val metacriticUrl: String? = null,
        val positivePct: Int? = null,
        val reviewDesc: String? = null,
        val shortDescription: String? = null,
        val releaseDateText: String? = null,
        val releaseYear: String? = null,
        val genres: List<String> = emptyList(),
        val screenshots: List<String> = emptyList(),
        val companies: List<NetworkProductionCompany> = emptyList()
    )

    private fun checkUrlOk(url: String): Boolean {
        return try {
            val req = Request.Builder().url(url).head().build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun fetchSteamStoreBundle(appId: Long): SteamStoreBundle? = NetworkPacer.withSteamPacing {
        try {
            val logoUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/logo.png".takeIf(::checkUrlOk)
            val heroUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg".takeIf(::checkUrlOk)

            var metacritic: Int? = null
            var metacriticUrl: String? = null
            var bgRaw: String? = null
            var headerImg: String? = null
            var shortDesc: String? = null
            var relDate: String? = null
            var relYear: String? = null
            val genres = mutableListOf<String>()
            val shots = mutableListOf<String>()
            val companies = mutableListOf<NetworkProductionCompany>()

            val detailsReq = Request.Builder()
                .url("https://store.steampowered.com/api/appdetails?appids=$appId&l=english")
                .get()
                .build()
            client.newCall(detailsReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val appNode = json.parseToJsonElement(body).obj()?.get(appId.toString()).obj()
                        if (appNode?.get("success").boolVal() == true) {
                            val data = appNode?.get("data").obj()
                            val mcObj = data?.get("metacritic").obj()
                            metacritic = mcObj?.get("score").intVal()
                            metacriticUrl = mcObj?.get("url").str()
                            bgRaw = data?.get("background_raw").str() ?: data?.get("background").str()
                            headerImg = data?.get("header_image").str()
                            shortDesc = data?.get("short_description").str()
                            relDate = data?.get("release_date").obj()?.get("date").str()
                            relYear = relDate?.let { Regex("(19|20)\\d{2}").find(it)?.value }

                            data?.get("genres").arr()?.forEach { gEl ->
                                gEl.obj()?.get("description").str()?.let { genres.add(it) }
                            }
                            data?.get("screenshots").arr()?.take(10)?.forEach { sEl ->
                                sEl.obj()?.get("path_full").str()?.let { shots.add(it) }
                            }
                            data?.get("developers").arr()?.forEach { dEl ->
                                dEl.str()?.let { companies.add(NetworkProductionCompany(name = it, kind = "company")) }
                            }
                            data?.get("publishers").arr()?.forEach { pEl ->
                                pEl.str()?.let { name ->
                                    if (companies.none { it.name.equals(name, true) }) {
                                        companies.add(NetworkProductionCompany(name = name, kind = "network"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            var positivePct: Int? = null
            var reviewDesc: String? = null
            val revReq = Request.Builder()
                .url("https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all&num_per_page=0")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                .get()
                .build()
            client.newCall(revReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    if (!body.isNullOrBlank()) {
                        val summary = json.parseToJsonElement(body).obj()?.get("query_summary").obj()
                        val pos = summary?.get("total_positive").intVal() ?: 0
                        val total = summary?.get("total_reviews").intVal() ?: 0
                        reviewDesc = summary?.get("review_score_desc").str()
                        if (total > 0) {
                            positivePct = ((pos * 100.0) / total).roundToInt().coerceIn(0, 100)
                        }
                    }
                }
            }

            SteamStoreBundle(
                logoUrl = logoUrl,
                libraryHeroUrl = heroUrl,
                backgroundRaw = bgRaw,
                headerImage = headerImg,
                metacriticScore = metacritic,
                metacriticUrl = metacriticUrl,
                positivePct = positivePct,
                reviewDesc = reviewDesc,
                shortDescription = shortDesc,
                releaseDateText = relDate,
                releaseYear = relYear,
                genres = genres.distinct(),
                screenshots = shots.distinct(),
                companies = companies
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchSteamDeckCategory(appId: Long): Int? = NetworkPacer.withGeneralPacing {
        try {
            val req = Request.Builder()
                .url("https://store.steampowered.com/saleaction/ajaxgetdeckappcompatibilityreport?nAppID=$appId")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val body = resp.body?.string() ?: return@withGeneralPacing null
                json.parseToJsonElement(body).obj()?.get("results").obj()?.get("resolved_category").intVal()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchProtonDbSummary(appId: Long): Pair<String, Int?>? = NetworkPacer.withGeneralPacing {
        try {
            val req = Request.Builder()
                .url("https://www.protondb.com/api/v1/reports/summaries/$appId.json")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val body = resp.body?.string() ?: return@withGeneralPacing null
                val obj = json.parseToJsonElement(body).obj() ?: return@withGeneralPacing null
                val tier = obj["tier"].str() ?: return@withGeneralPacing null
                val total = obj["total"].intVal()
                tier to total
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchGlobalAchievementPercentages(appId: Long): Map<String, Float> = NetworkPacer.withGeneralPacing {
        try {
            val req = Request.Builder()
                .url("https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v0002/?gameid=$appId&format=json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyMap()
                val body = resp.body?.string() ?: return@withGeneralPacing emptyMap()
                val arr = json.parseToJsonElement(body).obj()
                    ?.get("achievementpercentages").obj()
                    ?.get("achievements").arr() ?: return@withGeneralPacing emptyMap()
                buildMap {
                    arr.forEach { el ->
                        val o = el.obj() ?: return@forEach
                        val name = o["name"].str() ?: return@forEach
                        val pct = o["percent"].floatVal() ?: return@forEach
                        put(name, pct.coerceIn(0f, 100f))
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyMap()
        }
    }

    suspend fun fetchSteamPlayerAchievements(
        steamApiKey: String,
        steamId64: String,
        appId: Long
    ): List<GameAchievementItem> = NetworkPacer.withGeneralPacing {
        if (steamApiKey.isBlank() || steamId64.isBlank()) return@withGeneralPacing emptyList()
        try {
            // 1. Game Schema (icons, titles, descriptions, hidden flags)
            val schemaUrl = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=${steamApiKey.trim()}&appid=$appId&l=english"
            val schemaMap = LinkedHashMap<String, GameAchievementItem>()
            client.newCall(Request.Builder().url(schemaUrl).get().build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val achArr = json.parseToJsonElement(body).obj()
                        ?.get("game").obj()
                        ?.get("availableGameStats").obj()
                        ?.get("achievements").arr()
                    achArr?.forEach { el ->
                        val o = el.obj() ?: return@forEach
                        val apiName = o["name"].str() ?: return@forEach
                        val displayName = o["displayName"].str() ?: apiName
                        schemaMap[apiName] = GameAchievementItem(
                            apiName = apiName,
                            displayName = displayName,
                            description = o["description"].str(),
                            iconUrl = o["icon"].str(),
                            iconGrayUrl = o["icongray"].str(),
                            isHidden = (o["hidden"].intVal() ?: 0) == 1
                        )
                    }
                }
            }

            // 2. Player Unlock Status
            val playerUrl = "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?appid=$appId&key=${steamApiKey.trim()}&steamid=${steamId64.trim()}&l=english"
            val playerResults = mutableListOf<GameAchievementItem>()
            client.newCall(Request.Builder().url(playerUrl).get().build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    val pStats = json.parseToJsonElement(body).obj()?.get("playerstats").obj()
                    if (pStats?.get("success").boolVal() == true) {
                        pStats?.get("achievements").arr()?.forEach { el ->
                            val o = el.obj() ?: return@forEach
                            val apiName = o["apiname"].str() ?: return@forEach
                            val achieved = (o["achieved"].intVal() ?: 0) == 1
                            val unlockTime = o["unlocktime"].longVal()?.takeIf { it > 0L }
                            val schema = schemaMap[apiName]
                            playerResults.add(
                                GameAchievementItem(
                                    apiName = apiName,
                                    displayName = schema?.displayName ?: o["name"].str() ?: apiName,
                                    description = schema?.description ?: o["description"].str(),
                                    iconUrl = schema?.iconUrl,
                                    iconGrayUrl = schema?.iconGrayUrl,
                                    isUnlocked = achieved,
                                    unlockTimeSec = unlockTime,
                                    isHidden = schema?.isHidden == true
                                )
                            )
                        }
                    }
                }
            }

            if (playerResults.isNotEmpty()) playerResults else schemaMap.values.toList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun fetchSteamGamePlaytimeHours(
        steamApiKey: String,
        steamId64: String,
        appId: Long
    ): Double? = NetworkPacer.withGeneralPacing {
        if (steamApiKey.isBlank() || steamId64.isBlank()) return@withGeneralPacing null
        try {
            val url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=${steamApiKey.trim()}&steamid=${steamId64.trim()}&appids_filter[0]=$appId&include_played_free_games=1&format=json"
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val body = resp.body?.string() ?: return@withGeneralPacing null
                val gamesArr = json.parseToJsonElement(body).obj()
                    ?.get("response").obj()
                    ?.get("games").arr() ?: return@withGeneralPacing null
                val minutes = gamesArr.firstOrNull { el ->
                    el.obj()?.get("appid").longVal() == appId
                }.obj()?.get("playtime_forever").intVal() ?: return@withGeneralPacing null
                ((minutes / 6.0).roundToInt() / 10.0)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchSteamGridDbHeroAndLogo(
        steamAppId: Long?,
        title: String,
        apiKey: String
    ): Pair<String?, String?>? = withContext(Dispatchers.IO) {
        try {
            val cleanKey = apiKey.trim()
            if (cleanKey.isBlank()) return@withContext null

            var sgdbGameId: Long? = null
            if (steamAppId != null && steamAppId > 0L) {
                val gameReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/games/steam/$steamAppId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(gameReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        if (obj?.get("success").boolVal() == true) {
                            sgdbGameId = obj?.get("data").obj()?.get("id").longVal()
                        }
                    }
                }
            }
            if (sgdbGameId == null && title.isNotBlank()) {
                val encoded = URLEncoder.encode(title.trim(), "UTF-8")
                val searchReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/search/autocomplete/$encoded")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(searchReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val obj = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        if (obj?.get("success").boolVal() == true) {
                            sgdbGameId = obj?.get("data").arr()?.firstOrNull().obj()?.get("id").longVal()
                        }
                    }
                }
            }
            val targetId = sgdbGameId ?: return@withContext null

            fun fetchBestAsset(endpoint: String): String? {
                val assetReq = Request.Builder()
                    .url("https://www.steamgriddb.com/api/v2/$endpoint/game/$targetId")
                    .header("Authorization", "Bearer $cleanKey")
                    .get()
                    .build()
                client.newCall(assetReq).execute().use { resp ->
                    if (!resp.isSuccessful) return null
                    val body = resp.body?.string() ?: return null
                    val obj = json.parseToJsonElement(body).obj() ?: return null
                    if (obj["success"].boolVal() != true) return null
                    val list = obj["data"].arr()?.mapNotNull { it.obj() }.orEmpty()
                    return list.firstOrNull { it["humor"].boolVal() != true && it["nsfw"].boolVal() != true }?.get("url").str()
                        ?: list.firstOrNull { it["nsfw"].boolVal() != true }?.get("url").str()
                        ?: list.firstOrNull()?.get("url").str()
                }
            }

            fetchBestAsset("heroes") to fetchBestAsset("logos")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchBangumiGameScore(title: String): Float? = NetworkPacer.withBangumiPacing {
        try {
            val encoded = URLEncoder.encode(title.trim(), "UTF-8")
            val req = Request.Builder()
                .url("https://api.bgm.tv/search/subject/$encoded?type=4&responseGroup=medium&max_results=3")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withBangumiPacing null
                val body = resp.body?.string() ?: return@withBangumiPacing null
                val list = json.parseToJsonElement(body).obj()?.get("list").arr() ?: return@withBangumiPacing null
                list.firstNotNullOfOrNull { el ->
                    el.obj()?.get("rating").obj()?.get("score").floatVal()?.takeIf { it > 0f }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchRedditDiscussions(title: String): List<DiscussionComment> = NetworkPacer.withGeneralPacing {
        try {
            val clean = title.trim()
            if (clean.isBlank()) return@withGeneralPacing emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val req = Request.Builder()
                .url("https://www.reddit.com/r/patientgamers+gaming/search.json?q=$encoded&restrict_sr=1&sort=top&limit=8")
                .header("User-Agent", "OmniTrack/1.0 (Android)")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val body = resp.body?.string() ?: return@withGeneralPacing emptyList()
                val children = json.parseToJsonElement(body).obj()?.get("data").obj()
                    ?.get("children").arr() ?: return@withGeneralPacing emptyList()
                children.mapNotNull { el ->
                    val d = el.obj()?.get("data").obj() ?: return@mapNotNull null
                    val id = d["id"].str() ?: return@mapNotNull null
                    val postTitle = d["title"].str() ?: return@mapNotNull null
                    val self = d["selftext"].str()?.take(400).orEmpty()
                    val combined = if (self.isNotBlank()) "$postTitle\n\n$self" else postTitle
                    val author = d["author"].str() ?: "redditor"
                    val sub = d["subreddit_name_prefixed"].str() ?: "r/gaming"
                    val score = d["score"].intVal() ?: 0
                    val flair = d["link_flair_text"].str() ?: sub
                    DiscussionComment(
                        id = "reddit_$id",
                        author = "u/$author · $sub",
                        tag = flair,
                        content = combined,
                        timeAgo = "Reddit",
                        upvotes = score
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }
}
