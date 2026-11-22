package com.omnitrack.app.data.enrichment

import android.util.Base64
import com.omnitrack.app.data.artwork.ArtworkCandidate
import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.ComicCharacterItem
import com.omnitrack.app.data.model.ComicCreatorItem
import com.omnitrack.app.data.model.ComicDetailMeta
import com.omnitrack.app.data.model.ComicVariantCover
import com.omnitrack.app.data.model.ExternalLinkItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ComicIssueSeed(
    val issueNumber: String,
    val issueTitle: String? = null,
    val entryType: String = "ISSUE",
    val storyArc: String? = null,
    val coverUrl: String? = null,
    val storeDate: String? = null,
    val coverDate: String? = null
)

data class ComicEnrichmentBundle(
    val comicDetailMeta: ComicDetailMeta = ComicDetailMeta(),
    val resolvedComicVineId: Long? = null,
    val resolvedMetronId: Long? = null,
    val resolvedOpenLibraryId: String? = null,
    val publisher: String? = null,
    val releaseYear: String? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val totalIssues: Int? = null,
    val communityScore: Float? = null,
    val tags: List<AnimeTagItem> = emptyList(),
    val similarComics: List<RecommendationItem> = emptyList(),
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val seedIssues: List<ComicIssueSeed> = emptyList()
)

class ComicEnrichmentSource(
    private val client: OkHttpClient = NetworkClient.sharedClient
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    companion object {
        private const val COMIC_UA = "OmniTrack/1.0 (Android; ComicTracker)"
        private const val PULL_TTL_MS = 30 * 60 * 1000L

        @Volatile
        private var cachedWeeklyPull: List<CalendarReleaseItem> = emptyList()

        @Volatile
        private var cachedWeeklyPullAtMs: Long = 0L
    }

    private fun JsonElement?.cleanString(): String? {
        if (this == null || this is JsonNull) return null
        val s = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return s.takeIf { it.isNotBlank() && !it.equals("null", true) && it != "N/A" }
    }

    private fun JsonElement?.asIntOrNull(): Int? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull
    private fun JsonElement?.asLongOrNull(): Long? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.longOrNull
    private fun JsonElement?.asFloatOrNull(): Float? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull

    private fun buildMetronAuthHeader(username: String, password: String): String {
        val raw = "$username:$password".toByteArray(StandardCharsets.UTF_8)
        return "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    suspend fun searchComics(query: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()
        val cvKey = PreferenceManager.cachedComicVineApiKey
        val mUser = PreferenceManager.cachedMetronUsername
        val mPass = PreferenceManager.cachedMetronPassword

        coroutineScope {
            val cvDef = if (cvKey.isNotBlank()) async { searchComicVine(trimmed, cvKey) } else null
            val metDef = if (mUser.isNotBlank() && mPass.isNotBlank()) async { searchMetron(trimmed, mUser, mPass) } else null
            val appleDef = async { searchAppleComics(trimmed) }
            val olDef = async { searchOpenLibraryComics(trimmed) }

            buildList {
                cvDef?.await()?.let { addAll(it) }
                metDef?.await()?.let { addAll(it) }
                addAll(appleDef.await())
                addAll(olDef.await())
            }.distinctBy { "${it.sourceProvider}_${it.comicVineId ?: it.metronId ?: it.openLibraryId ?: it.title.lowercase()}" }
        }
    }

    suspend fun searchComicVine(query: String, apiKey: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val enc = URLEncoder.encode(query.trim(), "UTF-8")
            val keyEnc = URLEncoder.encode(apiKey.trim(), "UTF-8")
            val url = "https://comicvine.gamespot.com/api/volumes/?api_key=$keyEnc&format=json&filter=name:$enc&limit=14&field_list=id,name,start_year,count_of_issues,publisher,image,deck"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", COMIC_UA)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@withGeneralPacing emptyList()
                val results = root["results"] as? JsonArray ?: return@withGeneralPacing emptyList()
                results.mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val id = o["id"].asLongOrNull() ?: return@mapNotNull null
                    val name = o["name"].cleanString() ?: return@mapNotNull null
                    val img = o["image"] as? JsonObject
                    val cover = listOf("super_url", "medium_url", "small_url", "original_url").firstNotNullOfOrNull { k ->
                        img?.get(k).cleanString()
                    } ?: ""
                    val publisher = (o["publisher"] as? JsonObject)?.get("name").cleanString()
                    val startYear = o["start_year"].cleanString()
                    val issues = o["count_of_issues"].asIntOrNull()
                    val deck = OmniSearchRepository.cleanSynopsis(o["deck"].cleanString())
                    SearchResultItem(
                        title = name,
                        synopsis = deck,
                        coverUrl = if (cover.isNotBlank()) LosslessCoverMapper.toLosslessUrl(cover) else "",
                        mediaType = MediaType.COMIC,
                        releaseYear = startYear,
                        sourceProvider = "Comic Vine",
                        comicVineId = id,
                        comicPublisher = publisher,
                        comicFormat = "SINGLE_ISSUES",
                        totalProgress = issues?.toDouble()
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun searchMetron(query: String, username: String, password: String): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        try {
            val enc = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://metron.cloud/api/series/?name=$enc"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", COMIC_UA)
                .header("Authorization", buildMetronAuthHeader(username, password))
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@withGeneralPacing emptyList()
                val results = root["results"] as? JsonArray ?: return@withGeneralPacing emptyList()
                results.take(14).mapNotNull { el ->
                    val o = el as? JsonObject ?: return@mapNotNull null
                    val id = o["id"].asLongOrNull() ?: return@mapNotNull null
                    val name = o["series"].cleanString() ?: o["name"].cleanString() ?: return@mapNotNull null
                    val year = o["year_began"].cleanString()
                    val count = o["issue_count"].asIntOrNull()
                    SearchResultItem(
                        title = name,
                        coverUrl = "",
                        mediaType = MediaType.COMIC,
                        releaseYear = year,
                        sourceProvider = "Metron",
                        metronId = id,
                        comicFormat = "SINGLE_ISSUES",
                        totalProgress = count?.toDouble()
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    suspend fun searchAppleComics(query: String, strictComicFilter: Boolean = true): List<SearchResultItem> = NetworkPacer.withGeneralPacing {
        suspend fun runAppleQuery(term: String): List<SearchResultItem> {
            return try {
                val enc = URLEncoder.encode(term, "UTF-8")
                val url = "https://itunes.apple.com/search?term=$enc&media=ebook&limit=20"
                val req = Request.Builder().url(url).header("User-Agent", COMIC_UA).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return emptyList()
                    val results = root["results"] as? JsonArray ?: return emptyList()
                    results.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val title = o["trackName"].cleanString() ?: o["collectionName"].cleanString() ?: return@mapNotNull null
                        val genres = (o["genres"] as? JsonArray)?.mapNotNull { it.cleanString() }.orEmpty()
                        val isComicGenre = genres.any {
                            val low = it.lowercase()
                            "comic" in low || "graphic novel" in low || "superhero" in low
                        }
                        if (strictComicFilter && !isComicGenre) return@mapNotNull null
                        val cov = (o["artworkUrl100"].cleanString() ?: o["artworkUrl60"].cleanString())
                            ?.replace("100x100bb", "1200x1200bb")
                            ?.replace("60x60bb", "1200x1200bb")
                            ?: return@mapNotNull null
                        val author = o["artistName"].cleanString()
                        val desc = OmniSearchRepository.cleanSynopsis(o["description"].cleanString())
                        val releaseYear = o["releaseDate"].cleanString()?.take(4)
                        val r5 = o["averageUserRating"].asFloatOrNull()
                        val publisher = detectPublisherFromText("$title ${desc.orEmpty()} ${author.orEmpty()}")
                        SearchResultItem(
                            title = title,
                            synopsis = desc,
                            coverUrl = cov,
                            mediaType = MediaType.COMIC,
                            releaseYear = releaseYear,
                            communityScore = r5?.let { it * 2f },
                            sourceProvider = "Apple Books",
                            bookAuthor = author,
                            comicPublisher = publisher,
                            comicFormat = "DIGITAL"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

        val primary = runAppleQuery(query)
        if (primary.isNotEmpty()) primary.take(14)
        else runAppleQuery("$query comic").take(14)
    }

    suspend fun searchOpenLibraryComics(query: String): List<SearchResultItem> = NetworkPacer.withOpenLibraryPacing {
        suspend fun runOl(subject: String): List<SearchResultItem> {
            return try {
                val encQ = URLEncoder.encode(query.trim(), "UTF-8")
                val encSub = URLEncoder.encode(subject, "UTF-8")
                val fields = "key,title,author_name,publisher,first_publish_year,number_of_pages_median,ratings_average,cover_i,isbn"
                val url = "https://openlibrary.org/search.json?q=$encQ&subject=$encSub&limit=14&fields=$fields"
                val req = Request.Builder().url(url).header("User-Agent", COMIC_UA).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return emptyList()
                    val docs = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                        ?.get("docs") as? JsonArray ?: return emptyList()
                    docs.mapNotNull { el ->
                        val o = el as? JsonObject ?: return@mapNotNull null
                        val title = o["title"].cleanString() ?: return@mapNotNull null
                        val covId = o["cover_i"].asIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                        val workId = o["key"].cleanString()?.substringAfterLast("/")
                        val author = (o["author_name"] as? JsonArray)?.firstOrNull()?.cleanString()
                        val pub = (o["publisher"] as? JsonArray)?.firstOrNull()?.cleanString()
                        val year = o["first_publish_year"].asIntOrNull()?.toString()
                        val r5 = o["ratings_average"].asFloatOrNull()
                        val isbn = (o["isbn"] as? JsonArray)?.firstOrNull()?.cleanString()
                        SearchResultItem(
                            title = title,
                            coverUrl = LosslessCoverMapper.toLosslessUrl("https://covers.openlibrary.org/b/id/$covId-L.jpg"),
                            mediaType = MediaType.COMIC,
                            releaseYear = year,
                            communityScore = r5?.let { it * 2f },
                            sourceProvider = "Open Library",
                            openLibraryId = workId,
                            isbn = isbn,
                            bookAuthor = author,
                            comicPublisher = pub ?: detectPublisherFromText(title),
                            comicFormat = "TPB"
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }
        val first = runOl("comic books, strips, etc.")
        if (first.isNotEmpty()) first else runOl("graphic novels")
    }

    suspend fun fetchDiscoverComicsShelves(shelf: String): List<SearchResultItem> = withContext(Dispatchers.IO) {
        when (shelf.lowercase()) {
            "pull_list" -> {
                val mUser = PreferenceManager.cachedMetronUsername
                val mPass = PreferenceManager.cachedMetronPassword
                if (mUser.isNotBlank() && mPass.isNotBlank()) {
                    val metItems = fetchMetronWeeklyIssuesAsSearch(mUser, mPass)
                    if (metItems.isNotEmpty()) return@withContext metItems
                }
                searchAppleComics("new comic graphic novel 2025 2026", strictComicFilter = true).ifEmpty {
                    searchOpenLibraryComics("batman spider-man x-men")
                }.take(14)
            }
            "superhero", "trending" -> {
                val a = searchAppleComics("batman spider-man watchmen x-men invincible", strictComicFilter = true)
                val b = searchOpenLibraryComics("watchmen batman dark knight")
                (a + b).distinctBy { it.title.lowercase() }.take(14)
            }
            else -> {
                val a = searchAppleComics("saga hellboy walking dead monstress descender", strictComicFilter = true)
                val b = searchOpenLibraryComics("saga hellboy sandman")
                (a + b).distinctBy { it.title.lowercase() }.take(14)
            }
        }
    }

    private suspend fun fetchMetronWeeklyIssuesRaw(username: String, password: String): List<JsonObject> = NetworkPacer.withGeneralPacing {
        try {
            val today = LocalDate.now(ZoneId.systemDefault())
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val end = start.plusDays(6)
            val url = "https://metron.cloud/api/issue/?store_date_range_after=$start&store_date_range_before=$end"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", COMIC_UA)
                .header("Authorization", buildMetronAuthHeader(username, password))
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                val arr = (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)
                    ?.get("results") as? JsonArray ?: return@withGeneralPacing emptyList()
                arr.mapNotNull { it as? JsonObject }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
    }

    private suspend fun fetchMetronWeeklyIssuesAsSearch(username: String, password: String): List<SearchResultItem> {
        return fetchMetronWeeklyIssuesRaw(username, password)
            .mapNotNull { o ->
                val seriesObj = o["series"] as? JsonObject ?: return@mapNotNull null
                val name = seriesObj["name"].cleanString() ?: return@mapNotNull null
                val cover = o["image"].cleanString().orEmpty()
                if (cover.isBlank()) return@mapNotNull null
                val issueNum = o["number"].cleanString()
                val pub = (seriesObj["publisher"] as? JsonObject)?.get("name").cleanString()
                val metronIssueId = o["id"].asLongOrNull()
                val seriesYear = seriesObj["year_began"].cleanString()
                SearchResultItem(
                    title = if (!issueNum.isNullOrBlank()) "$name #$issueNum" else name,
                    coverUrl = LosslessCoverMapper.toLosslessUrl(cover),
                    mediaType = MediaType.COMIC,
                    releaseYear = seriesYear,
                    sourceProvider = "Metron",
                    metronId = metronIssueId,
                    comicPublisher = pub,
                    comicFormat = "SINGLE_ISSUES"
                )
            }
            .distinctBy { it.title.lowercase() }
            .take(14)
    }

    suspend fun fetchWeeklyComicReleases(force: Boolean = false): List<CalendarReleaseItem> = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        if (!force && cachedWeeklyPull.isNotEmpty() && (nowMs - cachedWeeklyPullAtMs) < PULL_TTL_MS) {
            return@withContext cachedWeeklyPull
        }
        val zone = ZoneId.systemDefault()
        val mUser = PreferenceManager.cachedMetronUsername
        val mPass = PreferenceManager.cachedMetronPassword

        val items = if (mUser.isNotBlank() && mPass.isNotBlank()) {
            fetchMetronWeeklyIssuesRaw(mUser, mPass).mapNotNull { o ->
                val id = o["id"].asLongOrNull() ?: return@mapNotNull null
                val seriesObj = o["series"] as? JsonObject ?: return@mapNotNull null
                val title = seriesObj["name"].cleanString() ?: return@mapNotNull null
                val num = o["number"].cleanString()?.toIntOrNull()
                val cover = o["image"].cleanString().orEmpty()
                val storeDateStr = o["store_date"].cleanString() ?: o["cover_date"].cleanString()
                val date = runCatching { LocalDate.parse(storeDateStr) }.getOrNull() ?: LocalDate.now(zone)
                val epochSec = date.atTime(9, 0).atZone(zone).toEpochSecond()
                val pub = (seriesObj["publisher"] as? JsonObject)?.get("name").cleanString()
                CalendarReleaseItem(
                    id = "metron_issue_$id",
                    title = title,
                    coverUrl = if (cover.isNotBlank()) LosslessCoverMapper.toLosslessUrl(cover) else "",
                    mediaType = MediaType.COMIC,
                    episodeNumber = num,
                    airingAtEpochSec = epochSec,
                    metronId = id,
                    formatLabel = pub?.uppercase() ?: "COMIC"
                )
            }
        } else {
            // Keyless Pull-List fallback: New Comic Book Day (Wednesday 09:00 local)
            val wednesday = LocalDate.now(zone).with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            val ncEpoch = wednesday.atTime(9, 0).atZone(zone).toEpochSecond()
            searchAppleComics("marvel dc image new comic issue", strictComicFilter = true)
                .take(8)
                .mapIndexed { idx, item ->
                    CalendarReleaseItem(
                        id = "comic_ncbd_${item.title.hashCode()}_$idx",
                        title = item.title,
                        coverUrl = item.coverUrl,
                        mediaType = MediaType.COMIC,
                        episodeNumber = null,
                        airingAtEpochSec = ncEpoch + (idx * 60L),
                        formatLabel = (item.comicPublisher ?: "NEW COMICS").uppercase()
                    )
                }
        }

        if (items.isNotEmpty()) {
            cachedWeeklyPull = items
            cachedWeeklyPullAtMs = System.currentTimeMillis()
        }
        items
    }

    suspend fun fetchFullComicData(item: MediaItemEntity): ComicEnrichmentBundle = withContext(Dispatchers.IO) {
        val cvKey = PreferenceManager.cachedComicVineApiKey
        val mUser = PreferenceManager.cachedMetronUsername
        val mPass = PreferenceManager.cachedMetronPassword

        coroutineScope {
            val cvDef = if (cvKey.isNotBlank()) async { fetchComicVineVolumeDetail(item, cvKey) } else null
            val metDef = if (mUser.isNotBlank() && mPass.isNotBlank()) async { fetchMetronDetail(item, mUser, mPass) } else null
            val appleDef = async { searchAppleComics(item.title, strictComicFilter = true).take(6) }
            val olDef = async { fetchOpenLibraryComicMeta(item) }
            val wikiDef = async { fetchWikipediaSummary(item.title) }

            val cvData = cvDef?.await()
            val metData = metDef?.await()
            val appleList = appleDef.await()
            val olData = olDef.await()
            val wikiDesc = wikiDef.await()

            val bestApple = appleList.firstOrNull()
            val variantCovers = buildList {
                cvData?.variantCovers?.let { addAll(it) }
                metData?.variantCovers?.let { addAll(it) }
                appleList.forEachIndexed { idx, a ->
                    if (a.coverUrl.isNotBlank()) {
                        add(ComicVariantCover(a.coverUrl, "Apple Books #${idx + 1}", a.bookAuthor))
                    }
                }
                olData?.variantCovers?.let { addAll(it) }
            }.distinctBy { it.url }.take(24)

            val seedIssues = (cvData?.seedIssues.orEmpty().ifEmpty { metData?.seedIssues.orEmpty() })
            val creators = buildList {
                cvData?.creators?.let { addAll(it) }
                if (isEmpty() && !bestApple?.bookAuthor.isNullOrBlank()) {
                    add(ComicCreatorItem(bestApple!!.bookAuthor!!, "Writer / Creator"))
                }
                olData?.creators?.let { addAll(it) }
            }.distinctBy { "${it.name.lowercase()}_${it.role.lowercase()}" }.take(20)

            val characters = cvData?.characters.orEmpty().take(20)
            val storyArcs = (cvData?.storyArcs.orEmpty() + metData?.storyArcs.orEmpty()).distinct().take(12)
            val publisher = item.comicPublisher?.takeIf { it.isNotBlank() }
                ?: cvData?.publisher
                ?: metData?.publisher
                ?: olData?.publisher
                ?: bestApple?.comicPublisher

            val synopsis = cvData?.synopsis?.takeIf { it.isNotBlank() }
                ?: bestApple?.synopsis?.takeIf { it.isNotBlank() }
                ?: wikiDesc
                ?: olData?.synopsis

            val totalIssues = item.totalProgress?.toInt()
                ?: cvData?.totalIssues
                ?: metData?.totalIssues
                ?: seedIssues.size.takeIf { it > 0 }

            val similar = appleList.drop(1).map { a ->
                RecommendationItem(
                    title = a.title,
                    coverUrl = a.coverUrl,
                    releaseYear = a.releaseYear,
                    rating = a.communityScore,
                    mediaType = MediaType.COMIC,
                    synopsis = a.synopsis,
                    bookAuthor = a.bookAuthor
                )
            }

            val extLinks = buildList {
                cvData?.siteUrl?.let { u ->
                    add(ExternalLinkItem(1001L, u, "Comic Vine"))
                }
                olData?.olKey?.let { key ->
                    add(ExternalLinkItem(1002L, "https://openlibrary.org/works/$key", "Open Library"))
                }
            }

            val tags = buildList {
                storyArcs.take(6).forEachIndexed { i, arc ->
                    add(AnimeTagItem(id = 7000L + i, name = arc, category = "Story Arc"))
                }
                olData?.subjects.orEmpty().take(6).forEachIndexed { i, sub ->
                    add(AnimeTagItem(id = 7100L + i, name = sub, category = "Genre"))
                }
            }

            ComicEnrichmentBundle(
                comicDetailMeta = ComicDetailMeta(
                    publisher = publisher,
                    startYear = cvData?.releaseYear ?: item.releaseYear ?: bestApple?.releaseYear,
                    issueCount = totalIssues,
                    storyArcs = storyArcs,
                    creators = creators,
                    characters = characters,
                    variantCovers = variantCovers,
                    appleBooksRating = bestApple?.communityScore?.let { it / 2f },
                    openLibraryRating = olData?.rating5,
                    comicVineUrl = cvData?.siteUrl
                ),
                resolvedComicVineId = cvData?.volumeId ?: item.comicVineId,
                resolvedMetronId = metData?.seriesId ?: item.metronId,
                resolvedOpenLibraryId = olData?.olKey ?: item.openLibraryId,
                publisher = publisher,
                releaseYear = cvData?.releaseYear ?: item.releaseYear ?: bestApple?.releaseYear ?: olData?.year,
                synopsis = synopsis,
                coverUrl = cvData?.coverUrl ?: bestApple?.coverUrl ?: olData?.coverUrl,
                bannerUrl = cvData?.variantCovers?.getOrNull(1)?.url,
                totalIssues = totalIssues,
                communityScore = bestApple?.communityScore ?: olData?.rating5?.let { it * 2f },
                tags = tags,
                similarComics = similar,
                externalLinks = extLinks,
                seedIssues = seedIssues
            )
        }
    }

    suspend fun fetchComicArtworkCandidates(
        title: String,
        comicVineId: Long? = null,
        openLibraryId: String? = null
    ): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<ArtworkCandidate>()
        val cvKey = PreferenceManager.cachedComicVineApiKey
        if (cvKey.isNotBlank()) {
            val vol = fetchComicVineVolumeDetail(
                MediaItemEntity(title = title, coverUrl = "", mediaType = MediaType.COMIC, comicVineId = comicVineId),
                cvKey
            )
            vol?.variantCovers?.forEach { v ->
                candidates.add(ArtworkCandidate(url = v.url, sourceName = "Comic Vine", description = v.label, isBanner = false))
            }
        }
        searchAppleComics(title, strictComicFilter = true).take(6).forEach { a ->
            if (a.coverUrl.isNotBlank()) {
                candidates.add(ArtworkCandidate(url = a.coverUrl, sourceName = "Apple Books", description = a.title, isBanner = false))
            }
        }
        searchOpenLibraryComics(title).take(6).forEach { ol ->
            if (ol.coverUrl.isNotBlank()) {
                candidates.add(ArtworkCandidate(url = ol.coverUrl, sourceName = "Open Library", description = ol.title, isBanner = false))
            }
        }
        candidates.distinctBy { it.url }
    }

    private data class CvParsed(
        val volumeId: Long? = null,
        val publisher: String? = null,
        val releaseYear: String? = null,
        val totalIssues: Int? = null,
        val synopsis: String? = null,
        val coverUrl: String? = null,
        val siteUrl: String? = null,
        val creators: List<ComicCreatorItem> = emptyList(),
        val characters: List<ComicCharacterItem> = emptyList(),
        val storyArcs: List<String> = emptyList(),
        val variantCovers: List<ComicVariantCover> = emptyList(),
        val seedIssues: List<ComicIssueSeed> = emptyList()
    )

    private suspend fun fetchComicVineVolumeDetail(item: MediaItemEntity, apiKey: String): CvParsed? = NetworkPacer.withGeneralPacing {
        try {
            val resolvedId = item.comicVineId ?: searchComicVine(item.title, apiKey).firstOrNull()?.comicVineId ?: return@withGeneralPacing null
            val keyEnc = URLEncoder.encode(apiKey.trim(), "UTF-8")
            val volUrl = "https://comicvine.gamespot.com/api/volume/4050-$resolvedId/?api_key=$keyEnc&format=json&field_list=id,name,start_year,count_of_issues,publisher,image,deck,description,characters,people,concepts,site_detail_url"
            val volReq = Request.Builder().url(volUrl).header("User-Agent", COMIC_UA).get().build()
            val volObj = client.newCall(volReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)?.get("results") as? JsonObject
            } ?: return@withGeneralPacing null

            val issUrl = "https://comicvine.gamespot.com/api/issues/?api_key=$keyEnc&format=json&filter=volume:$resolvedId&sort=issue_number:asc&limit=50&field_list=id,issue_number,name,cover_date,store_date,image"
            val issReq = Request.Builder().url(issUrl).header("User-Agent", COMIC_UA).get().build()
            val issArr = client.newCall(issReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)?.get("results") as? JsonArray
            }

            val img = volObj["image"] as? JsonObject
            val volCover = listOf("super_url", "medium_url", "original_url").firstNotNullOfOrNull { k ->
                img?.get(k).cleanString()
            }?.let { LosslessCoverMapper.toLosslessUrl(it) }
            val publisher = (volObj["publisher"] as? JsonObject)?.get("name").cleanString()
            val startYear = volObj["start_year"].cleanString()
            val issueCount = volObj["count_of_issues"].asIntOrNull()
            val rawDesc = volObj["description"].cleanString() ?: volObj["deck"].cleanString()
            val synopsis = OmniSearchRepository.cleanSynopsis(rawDesc)
            val siteUrl = volObj["site_detail_url"].cleanString()

            val people = (volObj["people"] as? JsonArray)?.mapNotNull { el ->
                val p = el as? JsonObject ?: return@mapNotNull null
                val name = p["name"].cleanString() ?: return@mapNotNull null
                ComicCreatorItem(name = name, role = "Creator")
            }.orEmpty()

            val characters = (volObj["characters"] as? JsonArray)?.mapNotNull { el ->
                val c = el as? JsonObject ?: return@mapNotNull null
                val name = c["name"].cleanString() ?: return@mapNotNull null
                ComicCharacterItem(name = name)
            }.orEmpty()

            val concepts = (volObj["concepts"] as? JsonArray)?.mapNotNull { el ->
                (el as? JsonObject)?.get("name").cleanString()
            }.orEmpty()

            val seeds = issArr?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val num = o["issue_number"].cleanString() ?: return@mapNotNull null
                val title = o["name"].cleanString()
                val covDate = o["cover_date"].cleanString()
                val strDate = o["store_date"].cleanString()
                val covImg = ((o["image"] as? JsonObject)?.get("medium_url").cleanString()
                    ?: (o["image"] as? JsonObject)?.get("small_url").cleanString())
                    ?.let { LosslessCoverMapper.toLosslessUrl(it) }
                ComicIssueSeed(
                    issueNumber = num,
                    issueTitle = title,
                    coverUrl = covImg,
                    storeDate = strDate,
                    coverDate = covDate
                )
            }.orEmpty()

            val variants = buildList {
                if (!volCover.isNullOrBlank()) add(ComicVariantCover(volCover, "Volume Cover"))
                seeds.forEach { s ->
                    if (!s.coverUrl.isNullOrBlank()) {
                        add(ComicVariantCover(s.coverUrl, "Issue #${s.issueNumber}"))
                    }
                }
            }.distinctBy { it.url }

            CvParsed(
                volumeId = resolvedId,
                publisher = publisher,
                releaseYear = startYear,
                totalIssues = issueCount,
                synopsis = synopsis,
                coverUrl = volCover,
                siteUrl = siteUrl,
                creators = people,
                characters = characters,
                storyArcs = concepts,
                variantCovers = variants,
                seedIssues = seeds
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private data class MetronParsed(
        val seriesId: Long? = null,
        val publisher: String? = null,
        val totalIssues: Int? = null,
        val storyArcs: List<String> = emptyList(),
        val variantCovers: List<ComicVariantCover> = emptyList(),
        val seedIssues: List<ComicIssueSeed> = emptyList()
    )

    private suspend fun fetchMetronDetail(item: MediaItemEntity, username: String, password: String): MetronParsed? = NetworkPacer.withGeneralPacing {
        try {
            val auth = buildMetronAuthHeader(username, password)
            val resolvedSeries = item.metronId ?: searchMetron(item.title, username, password).firstOrNull()?.metronId ?: return@withGeneralPacing null
            val url = "https://metron.cloud/api/issue/?series_id=$resolvedSeries"
            val req = Request.Builder().url(url).header("User-Agent", COMIC_UA).header("Authorization", auth).get().build()
            val arr = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                (json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject)?.get("results") as? JsonArray
            } ?: return@withGeneralPacing null

            val seeds = arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val num = o["number"].cleanString() ?: return@mapNotNull null
                val cov = o["image"].cleanString()?.let { LosslessCoverMapper.toLosslessUrl(it) }
                ComicIssueSeed(
                    issueNumber = num,
                    issueTitle = o["issue"].cleanString(),
                    coverUrl = cov,
                    storeDate = o["store_date"].cleanString(),
                    coverDate = o["cover_date"].cleanString()
                )
            }
            val variants = seeds.mapNotNull { s -> s.coverUrl?.let { ComicVariantCover(it, "Issue #${s.issueNumber}") } }
            MetronParsed(seriesId = resolvedSeries, totalIssues = seeds.size.takeIf { it > 0 }, variantCovers = variants, seedIssues = seeds)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private data class OlComicParsed(
        val olKey: String? = null,
        val publisher: String? = null,
        val year: String? = null,
        val synopsis: String? = null,
        val coverUrl: String? = null,
        val rating5: Float? = null,
        val subjects: List<String> = emptyList(),
        val creators: List<ComicCreatorItem> = emptyList(),
        val variantCovers: List<ComicVariantCover> = emptyList()
    )

    private suspend fun fetchOpenLibraryComicMeta(item: MediaItemEntity): OlComicParsed? = NetworkPacer.withOpenLibraryPacing {
        try {
            val workId = item.openLibraryId ?: searchOpenLibraryComics(item.title).firstOrNull()?.openLibraryId ?: return@withOpenLibraryPacing null
            val cleanId = workId.substringAfterLast("/")
            val req = Request.Builder().url("https://openlibrary.org/works/$cleanId.json").header("User-Agent", COMIC_UA).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@use null
                val desc = when (val d = o["description"]) {
                    is JsonPrimitive -> d.cleanString()
                    is JsonObject -> d["value"].cleanString()
                    else -> null
                }
                val covers = (o["covers"] as? JsonArray)?.mapNotNull { el ->
                    el.asIntOrNull()?.takeIf { it > 0 }?.let { cid ->
                        ComicVariantCover(
                            url = LosslessCoverMapper.toLosslessUrl("https://covers.openlibrary.org/b/id/$cid-L.jpg"),
                            label = "Edition Cover"
                        )
                    }
                }.orEmpty().take(6)
                val subjects = (o["subjects"] as? JsonArray)?.mapNotNull { it.cleanString() }?.take(8).orEmpty()
                OlComicParsed(
                    olKey = cleanId,
                    synopsis = OmniSearchRepository.cleanSynopsis(desc),
                    coverUrl = covers.firstOrNull()?.url,
                    subjects = subjects,
                    variantCovers = covers
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchWikipediaSummary(title: String): String? = NetworkPacer.withGeneralPacing {
        try {
            val slug = URLEncoder.encode(title.trim().replace(" ", "_"), "UTF-8").replace("+", "_")
            val req = Request.Builder().url("https://en.wikipedia.org/api/rest_v1/page/summary/$slug").header("User-Agent", COMIC_UA).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val o = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject ?: return@use null
                o["extract"].cleanString()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun detectPublisherFromText(text: String): String? {
        val low = text.lowercase()
        return when {
            "marvel" in low -> "Marvel Comics"
            "dc comics" in low || "vertigo" in low || "batman" in low || "superman" in low || "justice league" in low -> "DC Comics"
            "image comics" in low || "walking dead" in low || "invincible" in low || "saga" in low -> "Image Comics"
            "dark horse" in low || "hellboy" in low -> "Dark Horse Comics"
            else -> null
        }
    }
}
