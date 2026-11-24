package com.omnitrack.app.data.fanfic

import com.omnitrack.app.data.db.entity.FanficChapterEntity
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.FanficMetaPayload
import com.omnitrack.app.data.model.FanficRating
import com.omnitrack.app.data.model.FanficSite
import com.omnitrack.app.data.model.FanficSourceLink
import com.omnitrack.app.data.model.FanficThreadmarkCategory
import com.omnitrack.app.data.model.FanficWorkStatus
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.model.canonicalizeFanficUrl
import com.omnitrack.app.data.model.decodeFanficMeta
import com.omnitrack.app.data.model.encodeFanficMeta
import com.omnitrack.app.data.model.isLikelyWattpadFanfic
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.search.SearchResultItem
import com.omnitrack.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

data class FanficParsedChapter(
    val chapterNumber: Int,
    val title: String,
    val category: FanficThreadmarkCategory = FanficThreadmarkCategory.MAIN,
    val sourceSite: FanficSite = FanficSite.AO3,
    val chapterUrl: String? = null,
    val wordCount: Int? = null,
    val publishedDate: String? = null
)

data class FanficParsedWork(
    val site: FanficSite,
    val canonicalUrl: String,
    val workId: String?,
    val title: String,
    val author: String = "Unknown Author",
    val authorUrl: String? = null,
    val summary: String? = null,
    val coverUrl: String? = null,
    val rating: FanficRating = FanficRating.NOT_RATED,
    val workStatus: FanficWorkStatus = FanficWorkStatus.ONGOING,
    val wordCount: Int? = null,
    val totalChapters: Int? = null,
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeformTags: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val language: String? = null,
    val kudosCount: Int? = null,
    val bookmarksCount: Int? = null,
    val hitsCount: Int? = null,
    val commentsCount: Int? = null,
    val publishedDate: String? = null,
    val updatedDate: String? = null,
    val isCrossover: Boolean = fandoms.size > 1,
    val isArchiveLocked: Boolean = false,
    val chapters: List<FanficParsedChapter> = emptyList(),
    val mirrorSources: List<FanficSourceLink> = emptyList()
) {
    fun buildTagsSearchString(): String =
        (fandoms + relationships + characters + freeformTags + warnings + listOf(author, site.displayName))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" | ")

    fun toMetaPayload(extraMirrors: List<FanficSourceLink> = emptyList()): FanficMetaPayload {
        val now = System.currentTimeMillis()
        val mainChapCount = chapters.count { it.category == FanficThreadmarkCategory.MAIN }
            .takeIf { it > 0 } ?: totalChapters
        val primaryLink = FanficSourceLink(
            site = site.code,
            url = canonicalUrl,
            externalId = workId,
            isPrimary = true,
            lastChapterCount = mainChapCount,
            lastWordCount = wordCount,
            lastCheckedMs = now,
            lastUpdatedLabel = updatedDate ?: publishedDate
        )
        val mergedMirrors = (listOf(primaryLink) + mirrorSources + extraMirrors)
            .distinctBy { "${it.site}:${it.url}" }
        return FanficMetaPayload(
            authorUrl = authorUrl,
            fandoms = fandoms,
            relationships = relationships,
            characters = characters,
            freeformTags = freeformTags,
            warnings = warnings,
            language = language,
            kudosCount = kudosCount,
            bookmarksCount = bookmarksCount,
            hitsCount = hitsCount,
            commentsCount = commentsCount,
            publishedDate = publishedDate,
            updatedDate = updatedDate,
            isArchiveLocked = isArchiveLocked,
            mirrorSources = mergedMirrors
        )
    }

    fun toChapterEntities(mediaId: Long): List<FanficChapterEntity> = chapters.mapIndexed { idx, ch ->
        FanficChapterEntity(
            mediaId = mediaId,
            chapterNumber = ch.chapterNumber,
            title = ch.title,
            category = ch.category.name,
            sourceSite = ch.sourceSite.code,
            chapterUrl = ch.chapterUrl,
            wordCount = ch.wordCount,
            publishedDate = ch.publishedDate,
            isRead = false,
            sortOrder = idx
        )
    }

    fun toMediaItemEntity(
        status: TrackingStatus = TrackingStatus.PLAN_TO_TRACK,
        existing: MediaItemEntity? = null,
        targetMediaType: MediaType = MediaType.FANFICTION
    ): MediaItemEntity {
        val mainChapCount = chapters.count { it.category == FanficThreadmarkCategory.MAIN }
            .takeIf { it > 0 } ?: totalChapters
        val existingMeta = decodeFanficMeta(existing?.fanficMetaJson)
        val newMeta = toMetaPayload(existingMeta.mirrorSources)
        val metaJson = encodeFanficMeta(newMeta)
        val year = (updatedDate ?: publishedDate)?.let {
            Regex("""\b(19|20)\d{2}\b""").find(it)?.value
        } ?: existing?.releaseYear
        val resolvedType = existing?.mediaType ?: targetMediaType
        val resolvedPlatform = if (resolvedType == MediaType.NOVEL) "WEB_NOVEL" else site.displayName

        return if (existing != null) {
            existing.copy(
                title = title.ifBlank { existing.title },
                synopsis = summary ?: existing.synopsis,
                coverUrl = existing.coverUrl.ifBlank { coverUrl.orEmpty() },
                bookAuthor = author.takeIf { it.isNotBlank() } ?: existing.bookAuthor,
                platform = existing.platform?.takeIf { resolvedType == MediaType.NOVEL && it.isNotBlank() } ?: resolvedPlatform,
                totalProgress = mainChapCount?.toDouble() ?: existing.totalProgress,
                airStatus = workStatus.label,
                releaseYear = year,
                fanficSourceSite = existing.fanficSourceSite ?: site.code,
                fanficSourceUrl = existing.fanficSourceUrl ?: canonicalUrl,
                fanficWorkId = existing.fanficWorkId ?: workId,
                fanficPrimaryFandom = fandoms.firstOrNull() ?: existing.fanficPrimaryFandom,
                fanficWordCount = wordCount ?: existing.fanficWordCount,
                fanficRating = rating.code,
                fanficWorkStatus = workStatus.name,
                fanficIsCrossover = isCrossover,
                fanficTagsSearch = buildTagsSearchString(),
                fanficMetaJson = metaJson,
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            MediaItemEntity(
                title = title,
                synopsis = summary,
                coverUrl = coverUrl.orEmpty(),
                mediaType = resolvedType,
                status = status,
                currentProgress = 0.0,
                totalProgress = mainChapCount?.toDouble(),
                platform = resolvedPlatform,
                bookAuthor = author,
                airStatus = workStatus.label,
                releaseYear = year,
                fanficSourceSite = site.code,
                fanficSourceUrl = canonicalUrl,
                fanficWorkId = workId,
                fanficPrimaryFandom = fandoms.firstOrNull(),
                fanficWordCount = wordCount,
                fanficRating = rating.code,
                fanficWorkStatus = workStatus.name,
                fanficIsCrossover = isCrossover,
                fanficTagsSearch = buildTagsSearchString(),
                fanficMetaJson = metaJson,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    fun toSearchResultItem(targetMediaType: MediaType = MediaType.FANFICTION): SearchResultItem {
        val mainChapCount = chapters.count { it.category == FanficThreadmarkCategory.MAIN }
            .takeIf { it > 0 } ?: totalChapters
        val metaJson = encodeFanficMeta(toMetaPayload())
        val year = (updatedDate ?: publishedDate)?.let {
            Regex("""\b(19|20)\d{2}\b""").find(it)?.value
        }
        return SearchResultItem(
            title = title,
            synopsis = summary,
            coverUrl = coverUrl.orEmpty(),
            mediaType = targetMediaType,
            releaseYear = year,
            sourceProvider = site.shortBadge,
            bookAuthor = author,
            platform = if (targetMediaType == MediaType.NOVEL) "WEB_NOVEL" else site.displayName,
            totalProgress = mainChapCount?.toDouble(),
            fanficSourceSite = site.code,
            fanficSourceUrl = canonicalUrl,
            fanficWorkId = workId,
            fanficPrimaryFandom = fandoms.firstOrNull(),
            fanficWordCount = wordCount,
            fanficRating = rating.code,
            fanficWorkStatus = workStatus.name,
            fanficIsCrossover = isCrossover,
            fanficTagsSearch = buildTagsSearchString(),
            fanficMetaJson = metaJson
        )
    }
}

sealed interface FanficFetchResult {
    data class Success(val work: FanficParsedWork) : FanficFetchResult
    data class RequiresWebView(val site: FanficSite, val url: String, val reason: String) : FanficFetchResult
    data class Error(val message: String) : FanficFetchResult
}

data class FanficMirrorUpdateOutcome(
    val updatedWork: FanficParsedWork?,
    val updatedMirrors: List<FanficSourceLink>,
    val newChaptersFound: Int,
    val requiresWebViewSite: FanficSite? = null,
    val requiresWebViewUrl: String? = null,
    val error: String? = null
)

object FanficSiteEngine {

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ao3Mutex = Mutex()
    private val forumMutex = Mutex()
    private val wattpadMutex = Mutex()
    private var lastAo3ReqMs = 0L
    private var lastForumReqMs = 0L
    private var lastWattpadReqMs = 0L

    private fun httpClient(): OkHttpClient = NetworkClient.getClient()

    private suspend fun <T> withWattpadDelay(block: suspend () -> T): T =
        wattpadMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = (lastWattpadReqMs + 550L) - now
            if (wait > 0L) delay(wait)
            val result = block()
            lastWattpadReqMs = System.currentTimeMillis()
            result
        }

    private suspend fun <T> withPoliteDelay(mutex: Mutex, isAo3: Boolean, block: suspend () -> T): T =
        mutex.withLock {
            val now = System.currentTimeMillis()
            val last = if (isAo3) lastAo3ReqMs else lastForumReqMs
            val minGap = if (isAo3) 900L else 650L
            val wait = (last + minGap) - now
            if (wait > 0L) delay(wait)
            val result = block()
            val done = System.currentTimeMillis()
            if (isAo3) lastAo3ReqMs = done else lastForumReqMs = done
            result
        }

    private fun isCloudflareChallenge(code: Int, body: String): Boolean {
        if (code == 403 || code == 503 || code == 429) {
            val low = body.lowercase()
            if ("cf-browser-verification" in low ||
                "just a moment" in low ||
                "cf_chl_opt" in low ||
                "challenge-platform" in low ||
                "enable javascript and cookies to continue" in low
            ) return true
        }
        return false
    }

    private fun executeGet(
        url: String,
        cookie: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): Triple<Int, String, String> {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
            .header("Accept-Language", "en-US,en;q=0.9")
        if (!cookie.isNullOrBlank()) {
            builder.header("Cookie", cookie)
        }
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }
        httpClient().newCall(builder.build()).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val body = resp.body?.string().orEmpty()
            return Triple(resp.code, finalUrl, body)
        }
    }

    suspend fun fetchFromUrl(
        rawUrl: String,
        cookieProvider: suspend (FanficSite) -> String? = { null }
    ): FanficFetchResult = withContext(Dispatchers.IO) {
        try {
            val (site, canonical, workId) = canonicalizeFanficUrl(rawUrl)
            val cookie = cookieProvider(site)
            when (site) {
                FanficSite.AO3 -> fetchAo3Work(canonical, workId, cookie)
                FanficSite.SB, FanficSite.SV, FanficSite.QQ -> fetchXenForoThread(canonical, site, workId, cookie)
                FanficSite.FFN -> fetchViaFicHub(canonical, workId)
                FanficSite.WATTPAD -> fetchWattpadStory(canonical, workId, cookie)
                FanficSite.OTHER -> FanficFetchResult.RequiresWebView(
                    site = FanficSite.OTHER,
                    url = canonical,
                    reason = "Open page in WebView to capture metadata"
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("FanficSiteEngine", "fetchFromUrl error: ${e.message}")
            FanficFetchResult.Error(e.message ?: "Failed to fetch fanfiction metadata")
        }
    }

    suspend fun fetchAo3Work(
        canonicalUrl: String,
        workId: String? = canonicalizeFanficUrl(canonicalUrl).third,
        cookie: String? = null
    ): FanficFetchResult = withContext(Dispatchers.IO) {
        val numericId = workId?.removePrefix("ao3:") ?: Regex("""/works/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)
            ?: return@withContext FanficFetchResult.Error("Invalid AO3 work URL")
        val workUrl = "https://archiveofourown.org/works/$numericId?view_adult=true"
        val (code, finalUrl, html) = withPoliteDelay(ao3Mutex, isAo3 = true) {
            executeGet(workUrl, cookie = cookie)
        }
        if (isCloudflareChallenge(code, html)) {
            return@withContext FanficFetchResult.RequiresWebView(
                site = FanficSite.AO3,
                url = workUrl,
                reason = "AO3 requested browser verification — complete check in WebView then tap Import Page"
            )
        }
        if (finalUrl.contains("/users/login") || html.contains("This work is only available to registered users")) {
            return@withContext FanficFetchResult.RequiresWebView(
                site = FanficSite.AO3,
                url = workUrl,
                reason = "Archive-locked work — sign in to AO3 in WebView or tap Save Session"
            )
        }
        if (code !in 200..299 || html.isBlank()) {
            return@withContext FanficFetchResult.Error("AO3 returned HTTP $code")
        }
        val doc = Jsoup.parse(html, "https://archiveofourown.org")
        val baseWork = parseAo3WorkDocument(doc, "https://archiveofourown.org/works/$numericId", numericId)
            ?: return@withContext FanficFetchResult.RequiresWebView(
                site = FanficSite.AO3,
                url = workUrl,
                reason = "Could not parse AO3 work — open in WebView to import"
            )

        val expectedChapCount = baseWork.totalChapters ?: baseWork.chapters.size
        val chapters = if (expectedChapCount > 1 || baseWork.workStatus == FanficWorkStatus.ONGOING) {
            runCatching {
                val navUrl = "https://archiveofourown.org/works/$numericId/navigate?view_adult=true"
                val (navCode, _, navHtml) = withPoliteDelay(ao3Mutex, isAo3 = true) {
                    executeGet(navUrl, cookie = cookie)
                }
                if (navCode in 200..299 && navHtml.isNotBlank()) {
                    parseAo3NavigateDocument(Jsoup.parse(navHtml, "https://archiveofourown.org"))
                } else emptyList()
            }.getOrDefault(emptyList())
        } else emptyList()

        val resolvedChapters = chapters.ifEmpty {
            baseWork.chapters.ifEmpty {
                listOf(
                    FanficParsedChapter(
                        chapterNumber = 1,
                        title = baseWork.title,
                        category = FanficThreadmarkCategory.MAIN,
                        sourceSite = FanficSite.AO3,
                        chapterUrl = "https://archiveofourown.org/works/$numericId/chapters/1",
                        wordCount = baseWork.wordCount,
                        publishedDate = baseWork.publishedDate
                    )
                )
            }
        }

        FanficFetchResult.Success(
            baseWork.copy(
                chapters = resolvedChapters,
                totalChapters = baseWork.totalChapters ?: resolvedChapters.size
            )
        )
    }

    private fun parseAo3NavigateDocument(doc: Document): List<FanficParsedChapter> {
        val items = doc.select("ol.chapter.index li")
        return items.mapIndexedNotNull { idx, li ->
            val link = li.selectFirst("a") ?: return@mapIndexedNotNull null
            val rawTitle = link.text().trim()
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val dateText = li.selectFirst("span.datetime")?.text()?.trim()?.trim('(', ')')
            val cleanTitle = rawTitle.replace(Regex("""^\d+\.\s*"""), "").ifBlank { "Chapter ${idx + 1}" }
            FanficParsedChapter(
                chapterNumber = idx + 1,
                title = cleanTitle,
                category = FanficThreadmarkCategory.MAIN,
                sourceSite = FanficSite.AO3,
                chapterUrl = href.takeIf { it.isNotBlank() },
                publishedDate = dateText
            )
        }
    }

    private fun parseAo3WorkDocument(doc: Document, canonicalUrl: String, numericId: String): FanficParsedWork? {
        val titleEl = doc.selectFirst("h2.title.heading") ?: doc.selectFirst("#workskin h2.title")
            ?: doc.selectFirst(".title.heading") ?: return null
        val title = titleEl.text().trim().ifBlank { "AO3 Work $numericId" }
        val authorLinks = doc.select("h3.byline.heading a[rel=author]")
        val author = authorLinks.joinToString(", ") { it.text().trim() }.ifBlank { "Anonymous" }
        val authorUrl = authorLinks.firstOrNull()?.absUrl("href")?.takeIf { it.isNotBlank() }
        val summary = doc.selectFirst("div.summary blockquote.userstuff")?.text()?.trim()

        val fandoms = doc.select("dd.fandom.tags a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val ratingText = doc.selectFirst("dd.rating.tags a.tag")?.text()?.trim()
        val warnings = doc.select("dd.warning.tags a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val relationships = doc.select("dd.relationship.tags a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val characters = doc.select("dd.character.tags a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val freeformTags = doc.select("dd.freeform.tags a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val language = doc.selectFirst("dd.language")?.text()?.trim()?.takeIf { it.isNotBlank() }

        val words = doc.selectFirst("dd.words")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val chaptersRaw = doc.selectFirst("dd.chapters")?.text()?.trim().orEmpty()
        val chapParts = chaptersRaw.split("/")
        val currentChap = chapParts.getOrNull(0)?.replace(",", "")?.trim()?.toIntOrNull()
        val totalExpectedRaw = chapParts.getOrNull(1)?.replace(",", "")?.trim()
        val isComplete = totalExpectedRaw != null && totalExpectedRaw != "?" &&
            currentChap != null && totalExpectedRaw.toIntOrNull() == currentChap
        val workStatus = if (isComplete) FanficWorkStatus.COMPLETE else FanficWorkStatus.ONGOING

        val kudos = doc.selectFirst("dd.kudos")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val bookmarks = doc.selectFirst("dd.bookmarks")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val hits = doc.selectFirst("dd.hits")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val comments = doc.selectFirst("dd.comments")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val publishedDate = doc.selectFirst("dd.published")?.text()?.trim()
        val updatedDate = doc.selectFirst("dd.status")?.text()?.trim() ?: publishedDate
        val coverUrl = doc.selectFirst("#chapters img[src], div.work img[src]")?.absUrl("src")?.takeIf {
            it.startsWith("http") && !it.contains("ao3.org/images/")
        }

        return FanficParsedWork(
            site = FanficSite.AO3,
            canonicalUrl = canonicalUrl,
            workId = "ao3:$numericId",
            title = title,
            author = author,
            authorUrl = authorUrl,
            summary = summary,
            coverUrl = coverUrl,
            rating = FanficRating.fromRaw(ratingText),
            workStatus = workStatus,
            wordCount = words,
            totalChapters = currentChap,
            fandoms = fandoms,
            relationships = relationships,
            characters = characters,
            freeformTags = freeformTags,
            warnings = warnings,
            language = language,
            kudosCount = kudos,
            bookmarksCount = bookmarks,
            hitsCount = hits,
            commentsCount = comments,
            publishedDate = publishedDate,
            updatedDate = updatedDate,
            isCrossover = fandoms.size > 1,
            isArchiveLocked = doc.selectFirst("img[title*='Restricted']") != null
        )
    }

    suspend fun searchAo3(
        query: String = "",
        page: Int = 1,
        ratingFilter: String? = null,
        completeOnly: Boolean = false,
        minWords: Int? = null,
        crossoverOnly: Boolean = false,
        sortBy: String = "kudos_count",
        cookie: String? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        try {
            val encQ = URLEncoder.encode(query.trim(), "UTF-8")
            val ratingId = when (ratingFilter?.uppercase()) {
                "G" -> "10"
                "T" -> "11"
                "M" -> "12"
                "E" -> "13"
                else -> ""
            }
            val completeParam = if (completeOnly) "T" else ""
            val crossParam = if (crossoverOnly) "T" else ""
            val wordsParam = if (minWords != null && minWords > 0) URLEncoder.encode(">$minWords", "UTF-8") else ""
            val url = buildString {
                append("https://archiveofourown.org/works/search?commit=Search&page=$page")
                append("&work_search[query]=$encQ")
                if (ratingId.isNotBlank()) append("&work_search[rating_ids]=$ratingId")
                if (completeParam.isNotBlank()) append("&work_search[complete]=$completeParam")
                if (crossParam.isNotBlank()) append("&work_search[crossover]=$crossParam")
                if (wordsParam.isNotBlank()) append("&work_search[word_count]=$wordsParam")
                append("&work_search[sort_column]=${URLEncoder.encode(sortBy, "UTF-8")}")
                append("&work_search[sort_direction]=desc")
            }
            val (code, _, html) = withPoliteDelay(ao3Mutex, isAo3 = true) {
                executeGet(url, cookie = cookie)
            }
            if (code !in 200..299 || html.isBlank() || isCloudflareChallenge(code, html)) {
                return@withContext emptyList()
            }
            parseAo3BlurbList(Jsoup.parse(html, "https://archiveofourown.org"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("FanficSiteEngine", "searchAo3 error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchAo3DiscoverShelf(
        shelfKey: String,
        page: Int = 1,
        cookie: String? = null
    ): List<SearchResultItem> = when (shelfKey) {
        "ao3_updated" -> searchAo3(query = "", page = page, minWords = 15000, sortBy = "revised_at", cookie = cookie)
        "ao3_complete" -> searchAo3(query = "", page = page, completeOnly = true, minWords = 20000, sortBy = "kudos_count", cookie = cookie)
        "ao3_crossovers" -> searchAo3(query = "", page = page, crossoverOnly = true, minWords = 15000, sortBy = "kudos_count", cookie = cookie)
        else -> searchAo3(query = "", page = page, minWords = 20000, sortBy = "kudos_count", cookie = cookie)
    }

    suspend fun fetchAo3AccountWorks(
        username: String,
        mode: String, // "bookmarks" or "subscriptions"
        cookie: String,
        maxPages: Int = 3
    ): Result<List<FanficParsedWork>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = username.trim()
            require(cleanUser.isNotBlank()) { "Enter your AO3 username" }
            val collected = mutableListOf<FanficParsedWork>()
            val encUser = URLEncoder.encode(cleanUser, "UTF-8")
            val endpoint = if (mode == "subscriptions") "subscriptions" else "bookmarks"
            for (p in 1..maxPages) {
                val url = "https://archiveofourown.org/users/$encUser/$endpoint?page=$p"
                val (code, finalUrl, html) = withPoliteDelay(ao3Mutex, isAo3 = true) {
                    executeGet(url, cookie = cookie)
                }
                if (finalUrl.contains("/users/login")) {
                    throw IllegalStateException("AO3 session expired — sign in to AO3 again")
                }
                if (code !in 200..299 || html.isBlank()) break
                val doc = Jsoup.parse(html, "https://archiveofourown.org")
                val batch = if (endpoint == "subscriptions") {
                    parseAo3SubscriptionsList(doc)
                } else {
                    parseAo3BlurbWorks(doc)
                }
                if (batch.isEmpty()) break
                collected.addAll(batch)
                val nextBtn = doc.selectFirst("ol.pagination li.next a")
                if (nextBtn == null) break
            }
            collected.distinctBy { it.workId ?: it.canonicalUrl }
        }
    }

    private fun parseAo3BlurbList(doc: Document): List<SearchResultItem> =
        parseAo3BlurbWorks(doc).map { it.toSearchResultItem() }

    private fun parseAo3BlurbWorks(doc: Document): List<FanficParsedWork> {
        val blurbs = doc.select("ol.index.group > li.blurb.group, li.work.blurb.group, li.bookmark.blurb.group")
        return blurbs.mapNotNull { li -> parseSingleAo3Blurb(li) }
    }

    private fun parseSingleAo3Blurb(li: Element): FanficParsedWork? {
        val workLink = li.selectFirst("h4.heading a[href*='/works/']") ?: return null
        val href = workLink.absUrl("href").ifBlank { "https://archiveofourown.org${workLink.attr("href")}" }
        val (_, canonical, workId) = canonicalizeFanficUrl(href)
        val title = workLink.text().trim()
        if (title.isBlank()) return null

        val authors = li.select("h4.heading a[rel=author]").joinToString(", ") { it.text().trim() }.ifBlank { "Anonymous" }
        val authorUrl = li.selectFirst("h4.heading a[rel=author]")?.absUrl("href")
        val fandoms = li.select("h5.fandoms.heading a.tag").map { it.text().trim() }.filter { it.isNotBlank() }
        val ratingTitle = li.selectFirst("ul.required-tags span.rating")?.attr("title")
            ?: li.selectFirst("ul.required-tags span.rating span.text")?.text()
        val isCompleteText = li.selectFirst("ul.required-tags span.iswip")?.attr("title").orEmpty()
        val summary = li.selectFirst("blockquote.summary")?.text()?.trim()

        val warnings = li.select("ul.tags li.warnings a.tag").map { it.text().trim() }
        val relationships = li.select("ul.tags li.relationships a.tag").map { it.text().trim() }
        val characters = li.select("ul.tags li.characters a.tag").map { it.text().trim() }
        val freeform = li.select("ul.tags li.freeforms a.tag").map { it.text().trim() }

        val words = li.selectFirst("dd.words")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val chRaw = li.selectFirst("dd.chapters")?.text()?.trim().orEmpty()
        val chapParts = chRaw.split("/")
        val curCh = chapParts.getOrNull(0)?.replace(",", "")?.trim()?.toIntOrNull()
        val expCh = chapParts.getOrNull(1)?.replace(",", "")?.trim()
        val status = when {
            isCompleteText.contains("Complete", ignoreCase = true) -> FanficWorkStatus.COMPLETE
            expCh != null && expCh != "?" && curCh != null && expCh.toIntOrNull() == curCh -> FanficWorkStatus.COMPLETE
            else -> FanficWorkStatus.ONGOING
        }
        val kudos = li.selectFirst("dd.kudos")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val bookmarks = li.selectFirst("dd.bookmarks")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val hits = li.selectFirst("dd.hits")?.text()?.replace(",", "")?.trim()?.toIntOrNull()
        val updatedDate = li.selectFirst("p.datetime")?.text()?.trim()
        val lang = li.selectFirst("dd.language")?.text()?.trim()

        return FanficParsedWork(
            site = FanficSite.AO3,
            canonicalUrl = canonical,
            workId = workId,
            title = title,
            author = authors,
            authorUrl = authorUrl,
            summary = summary,
            rating = FanficRating.fromRaw(ratingTitle),
            workStatus = status,
            wordCount = words,
            totalChapters = curCh,
            fandoms = fandoms,
            relationships = relationships,
            characters = characters,
            freeformTags = freeform,
            warnings = warnings,
            language = lang,
            kudosCount = kudos,
            bookmarksCount = bookmarks,
            hitsCount = hits,
            updatedDate = updatedDate,
            isCrossover = fandoms.size > 1
        )
    }

    private fun parseAo3SubscriptionsList(doc: Document): List<FanficParsedWork> {
        return doc.select("ul.subscription.index.group li, dl.subscription.index dt").mapNotNull { el ->
            val link = el.selectFirst("a[href*='/works/']") ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { "https://archiveofourown.org${link.attr("href")}" }
            val (_, canonical, workId) = canonicalizeFanficUrl(href)
            val author = el.selectFirst("a[rel=author]")?.text()?.trim().orEmpty().ifBlank { "AO3 Author" }
            FanficParsedWork(
                site = FanficSite.AO3,
                canonicalUrl = canonical,
                workId = workId,
                title = link.text().trim(),
                author = author
            )
        }
    }

    suspend fun fetchXenForoThread(
        canonicalUrl: String,
        site: FanficSite,
        workId: String? = canonicalizeFanficUrl(canonicalUrl).third,
        cookie: String? = null
    ): FanficFetchResult = withContext(Dispatchers.IO) {
        val threadBase = canonicalUrl.trimEnd('/')
        val (code, finalUrl, html) = withPoliteDelay(forumMutex, isAo3 = false) {
            executeGet(threadBase, cookie = cookie)
        }
        if (isCloudflareChallenge(code, html)) {
            return@withContext FanficFetchResult.RequiresWebView(
                site = site,
                url = threadBase,
                reason = "${site.displayName} requires browser verification — complete in WebView and tap Import Page"
            )
        }
        val doc = Jsoup.parse(html, site.baseUrl)
        if (finalUrl.contains("/login") || isXenForoLoginWall(doc, html)) {
            return@withContext FanficFetchResult.RequiresWebView(
                site = site,
                url = threadBase,
                reason = "${site.displayName} requires login to view this thread"
            )
        }
        if (code !in 200..299 || html.isBlank()) {
            return@withContext FanficFetchResult.Error("${site.displayName} returned HTTP $code")
        }

        val parsedBase = parseXenForoThreadDocument(doc, site, threadBase, workId)
            ?: return@withContext FanficFetchResult.RequiresWebView(
                site = site,
                url = threadBase,
                reason = "Unable to read thread header — open in WebView to import"
            )

        // Fetch dedicated /threadmarks listing for complete chapter + category list
        val tmUrl = "$threadBase/threadmarks"
        val tmResult = runCatching {
            withPoliteDelay(forumMutex, isAo3 = false) {
                executeGet(tmUrl, cookie = cookie)
            }
        }.getOrNull()

        val allChapters = mutableListOf<FanficParsedChapter>()
        var totalWords = parsedBase.wordCount
        if (tmResult != null && tmResult.first in 200..299 && tmResult.third.isNotBlank()) {
            val tmDoc = Jsoup.parse(tmResult.third, site.baseUrl)
            val parsedMainTm = parseXenForoThreadmarksPage(tmDoc, site, FanficThreadmarkCategory.MAIN)
            allChapters.addAll(parsedMainTm.first)
            if (parsedMainTm.second != null && (totalWords == null || parsedMainTm.second!! > totalWords)) {
                totalWords = parsedMainTm.second
            }

            // Secondary threadmark categories (Sidestories / Omakes / Informational / Media)
            val categoryLinks = tmDoc.select("a.tabs-tab[href*='threadmarks'], .block-tabHeader a[href*='threadmarks'], .threadmarkCategory a[href*='category_id']")
                .mapNotNull { el ->
                    val cat = FanficThreadmarkCategory.fromRaw(el.text())
                    val abs = el.absUrl("href").trim()
                    if (cat == FanficThreadmarkCategory.MAIN || abs.isBlank()) null else cat to abs
                }
                .distinctBy { it.first }
                .take(4)

            for ((cat, catUrl) in categoryLinks) {
                runCatching {
                    val (cCode, _, cHtml) = withPoliteDelay(forumMutex, isAo3 = false) {
                        executeGet(catUrl, cookie = cookie)
                    }
                    if (cCode in 200..299 && cHtml.isNotBlank()) {
                        val subList = parseXenForoThreadmarksPage(Jsoup.parse(cHtml, site.baseUrl), site, cat).first
                        allChapters.addAll(subList)
                    }
                }
            }
        }

        val finalChapters = if (allChapters.isNotEmpty()) allChapters else parsedBase.chapters
        val mainCount = finalChapters.count { it.category == FanficThreadmarkCategory.MAIN }
            .takeIf { it > 0 } ?: parsedBase.totalChapters

        FanficFetchResult.Success(
            parsedBase.copy(
                wordCount = totalWords,
                totalChapters = mainCount,
                chapters = finalChapters
            )
        )
    }

    private fun isXenForoLoginWall(doc: Document, html: String): Boolean {
        if (doc.selectFirst("form[action*='login/login']") != null && doc.selectFirst("h1.p-title-value") == null) {
            return true
        }
        val errMsg = doc.selectFirst(".blockMessage--error, .block-rowMessage--error")?.text().orEmpty().lowercase()
        return "must be logged-in" in errMsg || "you do not have permission" in errMsg ||
            html.contains("Please log in to view this page", ignoreCase = true)
    }

    private fun parseXenForoThreadDocument(
        doc: Document,
        site: FanficSite,
        canonicalUrl: String,
        workId: String?
    ): FanficParsedWork? {
        val h1 = doc.selectFirst("h1.p-title-value") ?: return null
        val prefixes = h1.select(".label, .labelLink").map { it.text().trim() }.filter { it.isNotBlank() }
        val cleanTitle = h1.clone().apply { select(".label, .labelLink, .threadmark-header").remove() }.text().trim()
        if (cleanTitle.isBlank()) return null

        val authorEl = doc.selectFirst("div.p-description a.username, article.message--post:first-of-type a.username")
        val author = authorEl?.text()?.trim().orEmpty().ifBlank { "Forum Author" }
        val authorUrl = authorEl?.absUrl("href")?.takeIf { it.isNotBlank() }
        val tags = doc.select("a.tagItem").map { it.text().trim() }.filter { it.isNotBlank() }
        val opBody = doc.selectFirst("article.message--post:first-of-type div.bbWrapper")?.text()?.trim()
            ?.take(800)
        val wordText = doc.selectFirst("span.threadmarks-wordCount, dl.pairs--threadmarkWordCount dd, a.threadmarks-trigger")
            ?.text().orEmpty()
        val parsedWords = parseXenForoWordCount(wordText)
        val isComplete = prefixes.any { it.contains("Complete", ignoreCase = true) } ||
            tags.any { it.equals("Complete", ignoreCase = true) }
        val isHiatus = prefixes.any { it.contains("Hiatus", ignoreCase = true) || it.contains("Dormant", ignoreCase = true) }
        val workStatus = when {
            isComplete -> FanficWorkStatus.COMPLETE
            isHiatus -> FanficWorkStatus.HIATUS
            else -> FanficWorkStatus.ONGOING
        }
        val rating = if (site == FanficSite.QQ && (canonicalUrl.contains("nsfw", true) || tags.any { it.contains("nsfw", true) || it.contains("lemon", true) })) {
            FanficRating.MATURE
        } else FanficRating.TEEN

        val fandoms = (prefixes + tags).filterNot {
            it.equals("Complete", true) || it.equals("Ongoing", true) || it.equals("Snippet", true)
        }.take(5)
        val isCross = prefixes.any { it.contains("Cross", ignoreCase = true) || it.contains("Xover", ignoreCase = true) } ||
            tags.any { it.contains("Crossover", ignoreCase = true) }

        val updatedDate = doc.selectFirst("div.p-description time, article.message--post:last-of-type time")
            ?.let { it.attr("title").ifBlank { it.text() } }?.trim()

        return FanficParsedWork(
            site = site,
            canonicalUrl = canonicalUrl,
            workId = workId,
            title = cleanTitle,
            author = author,
            authorUrl = authorUrl,
            summary = opBody,
            rating = rating,
            workStatus = workStatus,
            wordCount = parsedWords,
            fandoms = fandoms,
            freeformTags = tags,
            updatedDate = updatedDate,
            isCrossover = isCross
        )
    }

    private fun parseXenForoThreadmarksPage(
        doc: Document,
        site: FanficSite,
        category: FanficThreadmarkCategory
    ): Pair<List<FanficParsedChapter>, Int?> {
        val totalWords = parseXenForoWordCount(
            doc.selectFirst("dl.pairs:contains(Words) dd, .threadmarkListingHeader-stats, .p-description")?.text().orEmpty()
        )
        val items = doc.select("div.structItem--threadmark, li.threadmarkItem, div.threadmarkListing-item")
        val parsed = items.mapIndexedNotNull { idx, row ->
            val link = row.selectFirst("a.threadmarkListingItem-link, div.structItem-title a, a[href*='/posts/'], a[href*='post-']")
                ?: return@mapIndexedNotNull null
            val chTitle = link.text().trim().ifBlank { "Threadmark ${idx + 1}" }
            val chUrl = link.absUrl("href").ifBlank {
                val raw = link.attr("href")
                if (raw.startsWith("/")) "${site.baseUrl}$raw" else raw
            }
            val wCount = parseXenForoWordCount(row.selectFirst(".threadmarkListingItem-wordCount, .structItem-minor, .structItem-cell--meta")?.text().orEmpty())
            val dateText = row.selectFirst("time")?.let { it.attr("title").ifBlank { it.text() } }?.trim()
            FanficParsedChapter(
                chapterNumber = idx + 1,
                title = chTitle,
                category = category,
                sourceSite = site,
                chapterUrl = chUrl.takeIf { it.isNotBlank() },
                wordCount = wCount,
                publishedDate = dateText
            )
        }
        return parsed to totalWords
    }

    private fun parseXenForoWordCount(raw: String): Int? {
        if (raw.isBlank()) return null
        val mMatch = Regex("""(\d+(?:\.\d+)?)\s*m\b""", RegexOption.IGNORE_CASE).find(raw)
        if (mMatch != null) return ((mMatch.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000_000).toInt().takeIf { it > 0 }
        val kMatch = Regex("""(\d+(?:\.\d+)?)\s*k\b""", RegexOption.IGNORE_CASE).find(raw)
        if (kMatch != null) return ((kMatch.groupValues[1].toDoubleOrNull() ?: 0.0) * 1_000).toInt().takeIf { it > 0 }
        val intMatch = Regex("""([\d,]+)\s*(?:words?|w\b)""", RegexOption.IGNORE_CASE).find(raw)
        return intMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()
    }

    suspend fun fetchXenForoWatchedThreads(
        site: FanficSite,
        cookie: String
    ): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        runCatching {
            val watchedUrl = "${site.baseUrl}/watched/threads"
            val (code, finalUrl, html) = withPoliteDelay(forumMutex, isAo3 = false) {
                executeGet(watchedUrl, cookie = cookie)
            }
            if (finalUrl.contains("/login")) {
                throw IllegalStateException("${site.displayName} session expired — sign in again")
            }
            if (isCloudflareChallenge(code, html)) {
                throw IllegalStateException("${site.displayName} requested browser verification")
            }
            if (code !in 200..299 || html.isBlank()) {
                throw IllegalStateException("${site.displayName} returned HTTP $code")
            }
            val doc = Jsoup.parse(html, site.baseUrl)
            doc.select("div.structItem--thread div.structItem-title a[href*='/threads/']")
                .mapNotNull { a ->
                    val title = a.text().trim()
                    val href = a.absUrl("href").ifBlank { "${site.baseUrl}${a.attr("href")}" }
                    if (title.isBlank() || href.isBlank()) null else canonicalizeFanficUrl(href).second to title
                }
                .distinctBy { it.first }
        }
    }

    suspend fun fetchViaFicHub(
        canonicalUrl: String,
        workId: String? = canonicalizeFanficUrl(canonicalUrl).third
    ): FanficFetchResult = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://fichub.net/api/v0/meta?q=${URLEncoder.encode(canonicalUrl, "UTF-8")}"
            val (code, _, body) = executeGet(apiUrl)
            if (code !in 200..299 || body.isBlank()) {
                return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.FFN,
                    url = canonicalUrl,
                    reason = "FF.net Cloudflare is active and FicHub had no cached record — open FF.net in WebView and tap Import Page"
                )
            }
            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val err = obj?.get("err")?.jsonPrimitive?.intOrNull ?: 0
            val meta = obj?.get("meta") as? JsonObject
                ?: return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.FFN,
                    url = canonicalUrl,
                    reason = "Open FF.net page in WebView and tap Import Page (${obj?.get("msg")?.jsonPrimitive?.contentOrNull ?: "not cached"})"
                )
            if (err != 0) {
                return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.FFN,
                    url = canonicalUrl,
                    reason = "Open FF.net page in WebView and tap Import Page"
                )
            }
            val title = meta["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "FF.net Story" }
            val author = meta["author"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "FF.net Author" }
            val authorUrl = meta["authorUrl"]?.jsonPrimitive?.contentOrNull
            val rawDesc = meta["description"]?.jsonPrimitive?.contentOrNull
            val summary = rawDesc?.let { Jsoup.parse(it).text().trim() }
            val words = meta["words"]?.jsonPrimitive?.intOrNull
            val chapCount = meta["chapters"]?.jsonPrimitive?.intOrNull
            val statusStr = meta["status"]?.jsonPrimitive?.contentOrNull
            val updated = meta["updated"]?.jsonPrimitive?.contentOrNull
            val created = meta["created"]?.jsonPrimitive?.contentOrNull
            val extra = meta["rawExtendedMeta"] as? JsonObject ?: meta["extraMeta"] as? JsonObject

            val ratedRaw = extra?.get("rated")?.jsonPrimitive?.contentOrNull
                ?: extra?.get("rating")?.jsonPrimitive?.contentOrNull
            val rawFandom = extra?.get("fandom")?.jsonPrimitive?.contentOrNull
            val fandoms = rawFandom?.split("+", ",", "&")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
            val genres = (extra?.get("genres") as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val chars = (extra?.get("characters") as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val coverUrl = extra?.get("cover")?.jsonPrimitive?.contentOrNull
                ?: meta["cover"]?.jsonPrimitive?.contentOrNull
            val storyId = workId?.removePrefix("ffn:")
                ?: Regex("""/s/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)
            val generatedChapters = if (chapCount != null && chapCount > 0 && storyId != null) {
                (1..chapCount).map { num ->
                    FanficParsedChapter(
                        chapterNumber = num,
                        title = "Chapter $num",
                        category = FanficThreadmarkCategory.MAIN,
                        sourceSite = FanficSite.FFN,
                        chapterUrl = "https://www.fanfiction.net/s/$storyId/$num/"
                    )
                }
            } else emptyList()

            FanficFetchResult.Success(
                FanficParsedWork(
                    site = FanficSite.FFN,
                    canonicalUrl = canonicalUrl,
                    workId = workId,
                    title = title,
                    author = author,
                    authorUrl = authorUrl,
                    summary = summary,
                    coverUrl = coverUrl,
                    rating = FanficRating.fromRaw(ratedRaw),
                    workStatus = FanficWorkStatus.fromRaw(statusStr),
                    wordCount = words,
                    totalChapters = chapCount,
                    fandoms = fandoms,
                    characters = chars,
                    freeformTags = genres,
                    publishedDate = created,
                    updatedDate = updated,
                    isCrossover = fandoms.size > 1,
                    chapters = generatedChapters
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            FanficFetchResult.RequiresWebView(
                site = FanficSite.FFN,
                url = canonicalUrl,
                reason = "Open FF.net in WebView and tap Import Page (${e.message ?: "network error"})"
            )
        }
    }

    fun parseHtmlSnapshot(url: String, html: String): FanficFetchResult {
        if (html.isBlank()) return FanficFetchResult.Error("Page HTML is empty")
        val (site, canonical, workId) = canonicalizeFanficUrl(url)
        val doc = Jsoup.parse(html, site.baseUrl.ifBlank { canonical })
        return when (site) {
            FanficSite.AO3 -> {
                val numericId = workId?.removePrefix("ao3:") ?: "0"
                val work = parseAo3WorkDocument(doc, canonical, numericId)
                    ?: return FanficFetchResult.Error("Navigate to an AO3 work page before importing")
                FanficFetchResult.Success(work)
            }
            FanficSite.FFN -> parseFfnHtmlDocument(doc, canonical, workId)
            FanficSite.WATTPAD -> parseWattpadHtmlDocument(doc, canonical, workId)
            FanficSite.SB, FanficSite.SV, FanficSite.QQ -> {
                val base = parseXenForoThreadDocument(doc, site, canonical, workId)
                val tmPair = parseXenForoThreadmarksPage(doc, site, FanficThreadmarkCategory.MAIN)
                val resolved = base ?: if (tmPair.first.isNotEmpty()) {
                    FanficParsedWork(
                        site = site,
                        canonicalUrl = canonical,
                        workId = workId,
                        title = doc.title().substringBefore(" | ").trim().ifBlank { "${site.shortBadge} Thread" },
                        wordCount = tmPair.second,
                        totalChapters = tmPair.first.size,
                        chapters = tmPair.first
                    )
                } else null
                if (resolved != null) {
                    val chaps = resolved.chapters.ifEmpty { tmPair.first }
                    FanficFetchResult.Success(
                        resolved.copy(
                            chapters = chaps,
                            totalChapters = resolved.totalChapters ?: chaps.size.takeIf { it > 0 },
                            wordCount = resolved.wordCount ?: tmPair.second
                        )
                    )
                } else {
                    FanficFetchResult.Error("Open the story thread or its Threadmarks page first")
                }
            }
            FanficSite.OTHER -> {
                val pageTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                    ?: doc.title().trim().ifBlank { "Web Fanfiction" }
                val desc = doc.selectFirst("meta[property=og:description], meta[name=description]")?.attr("content")?.trim()
                val img = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                FanficFetchResult.Success(
                    FanficParsedWork(
                        site = FanficSite.OTHER,
                        canonicalUrl = canonical,
                        workId = workId,
                        title = pageTitle,
                        summary = desc,
                        coverUrl = img
                    )
                )
            }
        }
    }

    private fun parseFfnHtmlDocument(doc: Document, canonicalUrl: String, workId: String?): FanficFetchResult {
        val profile = doc.selectFirst("#profile_top")
            ?: return FanficFetchResult.Error("Navigate to a FanFiction.net story page before importing")
        val title = profile.selectFirst("b.xcontrast_txt")?.text()?.trim()
            ?.takeIf { it.isNotBlank() } ?: "FF.net Story"
        val authorEl = profile.selectFirst("a.xcontrast_txt[href*='/u/']")
        val author = authorEl?.text()?.trim().orEmpty().ifBlank { "FF.net Author" }
        val authorUrl = authorEl?.absUrl("href")?.takeIf { it.isNotBlank() }
        val summary = profile.selectFirst("div.xcontrast_txt")?.text()?.trim()
        val coverUrl = profile.selectFirst("img.cimage")?.absUrl("src")?.takeIf { it.startsWith("http") }

        val grayMeta = profile.selectFirst("span.xgray")?.text().orEmpty()
        val ratedRaw = Regex("""Rated:\s*(?:Fiction\s+)?([A-Za-z+]+)""").find(grayMeta)?.groupValues?.getOrNull(1)
        val words = Regex("""Words:\s*([\d,]+)""").find(grayMeta)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()
        val chCount = Regex("""Chapters:\s*([\d,]+)""").find(grayMeta)?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull()
        val isComplete = grayMeta.contains("Status: Complete", ignoreCase = true)
        val fandomLine = doc.select("#pre_story_links a.xcontrast_txt").lastOrNull()?.text()?.trim().orEmpty()
        val fandoms = if (fandomLine.contains("+") || fandomLine.contains("Crossover")) {
            fandomLine.removeSuffix("Crossover").split("+").map { it.trim() }.filter { it.isNotBlank() }
        } else listOfNotNull(fandomLine.takeIf { it.isNotBlank() })

        val storyId = workId?.removePrefix("ffn:") ?: Regex("""/s/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)
        val chapOpts = doc.select("#chap_select").firstOrNull()?.select("option").orEmpty()
        val chapters = if (chapOpts.isNotEmpty() && storyId != null) {
            chapOpts.mapIndexed { idx, opt ->
                val num = opt.attr("value").toIntOrNull() ?: (idx + 1)
                val cleanName = opt.text().trim().replace(Regex("""^\d+\.\s*"""), "").ifBlank { "Chapter $num" }
                FanficParsedChapter(
                    chapterNumber = num,
                    title = cleanName,
                    category = FanficThreadmarkCategory.MAIN,
                    sourceSite = FanficSite.FFN,
                    chapterUrl = "https://www.fanfiction.net/s/$storyId/$num/"
                )
            }
        } else if (storyId != null) {
            listOf(
                FanficParsedChapter(
                    chapterNumber = 1,
                    title = title,
                    category = FanficThreadmarkCategory.MAIN,
                    sourceSite = FanficSite.FFN,
                    chapterUrl = "https://www.fanfiction.net/s/$storyId/1/"
                )
            )
        } else emptyList()

        return FanficFetchResult.Success(
            FanficParsedWork(
                site = FanficSite.FFN,
                canonicalUrl = canonicalUrl,
                workId = workId,
                title = title,
                author = author,
                authorUrl = authorUrl,
                summary = summary,
                coverUrl = coverUrl,
                rating = FanficRating.fromRaw(ratedRaw),
                workStatus = if (isComplete) FanficWorkStatus.COMPLETE else FanficWorkStatus.ONGOING,
                wordCount = words,
                totalChapters = chCount ?: chapters.size.takeIf { it > 0 },
                fandoms = fandoms,
                isCrossover = fandoms.size > 1,
                chapters = chapters
            )
        )
    }

    // -------------------------------------------------------------------------
    // WATTPAD FULL ENGINE (API v3 JSON + WebView Snapshot Fallback + Bulk Import)
    // -------------------------------------------------------------------------

    private const val WATTPAD_STORY_FIELDS =
        "id,title,description,cover,url,user(name,username),numParts,completed,mature,language(name),tags,readCount,voteCount,commentCount,modifyDate,createDate,isPaywalled,parts(id,title,url,wordCount,modifyDate,isPaywalled)"

    private const val WATTPAD_LIST_STORY_FIELDS =
        "stories(id,title,description,cover,url,user(name,username),numParts,completed,mature,language(name),tags,readCount,voteCount,commentCount,modifyDate,createDate,isPaywalled)"

    private fun wattpadJsonHeaders(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to "https://www.wattpad.com/"
    )

    private fun parseWattpadStoryJson(obj: JsonObject): FanficParsedWork? {
        val storyId = obj["id"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val description = obj["description"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        val cover = obj["cover"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http") }
        val canonical = "https://www.wattpad.com/story/$storyId"

        val userObj = obj["user"] as? JsonObject
        val username = userObj?.get("username")?.jsonPrimitive?.contentOrNull?.trim()
        val displayName = userObj?.get("name")?.jsonPrimitive?.contentOrNull?.trim()
        val author = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: "Wattpad Author"
        val authorUrl = username?.takeIf { it.isNotBlank() }?.let { "https://www.wattpad.com/user/$it" }

        val mature = obj["mature"]?.jsonPrimitive?.booleanOrNull == true
        val completed = obj["completed"]?.jsonPrimitive?.booleanOrNull == true
        val isStoryPaywalled = obj["isPaywalled"]?.jsonPrimitive?.booleanOrNull == true
        val numParts = obj["numParts"]?.jsonPrimitive?.intOrNull
        val readCount = obj["readCount"]?.jsonPrimitive?.intOrNull
        val voteCount = obj["voteCount"]?.jsonPrimitive?.intOrNull
        val commentCount = obj["commentCount"]?.jsonPrimitive?.intOrNull
        val createDate = obj["createDate"]?.jsonPrimitive?.contentOrNull?.take(10)
        val modifyDate = obj["modifyDate"]?.jsonPrimitive?.contentOrNull?.take(10) ?: createDate
        val language = (obj["language"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.trim()

        val rawTags = (obj["tags"] as? JsonArray)?.mapNotNull { el ->
            el.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        }.orEmpty()
        val tagsWithBadges = if (isStoryPaywalled) (rawTags + "Paid Story").distinct() else rawTags
        val isFanfic = isLikelyWattpadFanfic(tagsWithBadges, description, title)
        val fandoms = if (isFanfic) {
            rawTags.filterNot { it.equals("fanfiction", true) || it.equals("fanfic", true) }.take(3)
        } else {
            emptyList()
        }

        val partsArr = obj["parts"] as? JsonArray
        val parsedChapters = partsArr?.mapIndexedNotNull { idx, el ->
            val pObj = el as? JsonObject ?: return@mapIndexedNotNull null
            val partId = pObj["id"]?.jsonPrimitive?.contentOrNull?.trim()
            val rawPartTitle = pObj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "Part ${idx + 1}" }
            val partPaid = pObj["isPaywalled"]?.jsonPrimitive?.booleanOrNull == true
            val partTitle = if (partPaid && !rawPartTitle.contains("[Paid]", ignoreCase = true)) {
                "$rawPartTitle [Paid]"
            } else rawPartTitle
            val partUrl = pObj["url"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http") }
                ?: partId?.let { "https://www.wattpad.com/$it" }
            val partWords = pObj["wordCount"]?.jsonPrimitive?.intOrNull
            val partDate = pObj["modifyDate"]?.jsonPrimitive?.contentOrNull?.take(10)
            FanficParsedChapter(
                chapterNumber = idx + 1,
                title = partTitle,
                category = FanficThreadmarkCategory.MAIN,
                sourceSite = FanficSite.WATTPAD,
                chapterUrl = partUrl,
                wordCount = partWords,
                publishedDate = partDate
            )
        }.orEmpty()

        val totalWords = parsedChapters.mapNotNull { it.wordCount }.takeIf { it.isNotEmpty() }?.sum()

        return FanficParsedWork(
            site = FanficSite.WATTPAD,
            canonicalUrl = canonical,
            workId = "wp:$storyId",
            title = title,
            author = author,
            authorUrl = authorUrl,
            summary = description,
            coverUrl = cover,
            rating = if (mature) FanficRating.MATURE else FanficRating.TEEN,
            workStatus = if (completed) FanficWorkStatus.COMPLETE else FanficWorkStatus.ONGOING,
            wordCount = totalWords,
            totalChapters = numParts ?: parsedChapters.size.takeIf { it > 0 },
            fandoms = fandoms,
            freeformTags = tagsWithBadges,
            language = language,
            kudosCount = voteCount,
            hitsCount = readCount,
            commentsCount = commentCount,
            publishedDate = createDate,
            updatedDate = modifyDate,
            isCrossover = fandoms.size > 1,
            isArchiveLocked = isStoryPaywalled,
            chapters = parsedChapters
        )
    }

    suspend fun fetchWattpadStory(
        canonicalUrl: String,
        workId: String? = canonicalizeFanficUrl(canonicalUrl).third,
        cookie: String? = null
    ): FanficFetchResult = withContext(Dispatchers.IO) {
        try {
            var resolvedStoryId = workId?.takeIf { it.startsWith("wp:") }?.removePrefix("wp:")
                ?: Regex("""/story/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)

            // Resolve chapter/part URLs (e.g. wattpad.com/12345678-chapter-1) -> parent story ID
            if (resolvedStoryId.isNullOrBlank()) {
                val partId = workId?.takeIf { it.startsWith("wp_part:") }?.removePrefix("wp_part:")
                    ?: Regex("""wattpad\.com/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)
                if (!partId.isNullOrBlank()) {
                    val (pCode, _, pBody) = withWattpadDelay {
                        executeGet(
                            url = "https://www.wattpad.com/api/v3/story_parts/$partId?fields=groupId",
                            cookie = cookie,
                            extraHeaders = wattpadJsonHeaders()
                        )
                    }
                    if (pCode in 200..299 && pBody.trimStart().startsWith("{")) {
                        val pObj = runCatching { json.parseToJsonElement(pBody).jsonObject }.getOrNull()
                        resolvedStoryId = pObj?.get("groupId")?.jsonPrimitive?.contentOrNull?.trim()
                    }
                }
            }

            if (resolvedStoryId.isNullOrBlank()) {
                return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.WATTPAD,
                    url = canonicalUrl,
                    reason = "Open this Wattpad story in WebView and tap Import Page"
                )
            }

            val apiUrl = "https://www.wattpad.com/api/v3/stories/$resolvedStoryId?fields=${URLEncoder.encode(WATTPAD_STORY_FIELDS, "UTF-8")}"
            val (code, _, body) = withWattpadDelay {
                executeGet(url = apiUrl, cookie = cookie, extraHeaders = wattpadJsonHeaders())
            }
            if (isCloudflareChallenge(code, body) || code == 401 || code == 403) {
                return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.WATTPAD,
                    url = "https://www.wattpad.com/story/$resolvedStoryId",
                    reason = "Wattpad requested sign-in or browser verification — open in WebView then tap Import Page"
                )
            }
            if (code !in 200..299 || !body.trimStart().startsWith("{")) {
                return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.WATTPAD,
                    url = "https://www.wattpad.com/story/$resolvedStoryId",
                    reason = "Wattpad returned HTTP $code — open in WebView to import"
                )
            }

            val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            val parsed = obj?.let { parseWattpadStoryJson(it) }
                ?: return@withContext FanficFetchResult.RequiresWebView(
                    site = FanficSite.WATTPAD,
                    url = "https://www.wattpad.com/story/$resolvedStoryId",
                    reason = "Could not parse Wattpad story — open in WebView to import"
                )
            FanficFetchResult.Success(parsed)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("FanficSiteEngine", "fetchWattpadStory error: ${e.message}")
            FanficFetchResult.RequiresWebView(
                site = FanficSite.WATTPAD,
                url = canonicalUrl,
                reason = "Open Wattpad page in WebView and tap Import Page (${e.message ?: "network error"})"
            )
        }
    }

    suspend fun searchWattpad(
        query: String,
        page: Int = 1,
        cookie: String? = null,
        targetMediaType: MediaType? = null
    ): List<SearchResultItem> = withContext(Dispatchers.IO) {
        try {
            val cleanQ = query.trim()
            if (cleanQ.isBlank()) return@withContext emptyList()
            val limit = 20
            val offset = ((page.coerceAtLeast(1) - 1) * limit)
            val url = "https://www.wattpad.com/api/v3/stories?query=${URLEncoder.encode(cleanQ, "UTF-8")}&limit=$limit&offset=$offset&fields=${URLEncoder.encode(WATTPAD_LIST_STORY_FIELDS, "UTF-8")}"
            val (code, _, body) = withWattpadDelay {
                executeGet(url = url, cookie = cookie, extraHeaders = wattpadJsonHeaders())
            }
            if (code !in 200..299 || !body.trimStart().startsWith("{")) return@withContext emptyList()
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext emptyList()
            val stories = root["stories"] as? JsonArray ?: return@withContext emptyList()
            val parsedWorks = stories.mapNotNull { el ->
                (el as? JsonObject)?.let { parseWattpadStoryJson(it) }
            }
            parsedWorks.map { work ->
                val resolvedType = targetMediaType ?: if (isLikelyWattpadFanfic(work.freeformTags, work.summary, work.title)) {
                    MediaType.FANFICTION
                } else {
                    MediaType.NOVEL
                }
                work.toSearchResultItem(targetMediaType = resolvedType)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AppLogger.w("FanficSiteEngine", "searchWattpad error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchWattpadDiscoverShelf(
        shelfKey: String,
        page: Int = 1,
        cookie: String? = null
    ): List<SearchResultItem> = when (shelfKey) {
        "wattpad_fanfic" -> searchWattpad(
            query = "fanfiction",
            page = page,
            cookie = cookie,
            targetMediaType = MediaType.FANFICTION
        )
        "wattpad_webnovels" -> searchWattpad(
            query = "fantasy romance",
            page = page,
            cookie = cookie,
            targetMediaType = MediaType.NOVEL
        )
        "wattpad_scifi_novels" -> searchWattpad(
            query = "sci-fi adventure",
            page = page,
            cookie = cookie,
            targetMediaType = MediaType.NOVEL
        )
        else -> searchWattpad(
            query = "popular",
            page = page,
            cookie = cookie,
            targetMediaType = MediaType.FANFICTION
        )
    }

    suspend fun fetchWattpadUserReadingLists(
        username: String,
        cookie: String? = null
    ): Result<List<FanficParsedWork>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanUser = username.trim().removePrefix("@")
            require(cleanUser.isNotBlank()) { "Enter a Wattpad username" }
            val encUser = URLEncoder.encode(cleanUser, "UTF-8")
            val listsUrl = "https://www.wattpad.com/api/v3/users/$encUser/lists?limit=20&fields=lists(id,name,numStories)"
            val (code, _, body) = withWattpadDelay {
                executeGet(url = listsUrl, cookie = cookie, extraHeaders = wattpadJsonHeaders())
            }
            if (code !in 200..299 || !body.trimStart().startsWith("{")) {
                throw IllegalStateException("Could not load Wattpad reading lists for @$cleanUser (HTTP $code)")
            }
            val root = json.parseToJsonElement(body).jsonObject
            val lists = (root["lists"] as? JsonArray).orEmpty()
            val collected = mutableListOf<FanficParsedWork>()
            for (el in lists.take(10)) {
                val listObj = el as? JsonObject ?: continue
                val listId = listObj["id"]?.jsonPrimitive?.contentOrNull?.trim() ?: continue
                val storiesUrl = "https://www.wattpad.com/api/v3/lists/$listId/stories?limit=30&fields=${URLEncoder.encode(WATTPAD_LIST_STORY_FIELDS, "UTF-8")}"
                val (sCode, _, sBody) = withWattpadDelay {
                    executeGet(url = storiesUrl, cookie = cookie, extraHeaders = wattpadJsonHeaders())
                }
                if (sCode in 200..299 && sBody.trimStart().startsWith("{")) {
                    val sRoot = runCatching { json.parseToJsonElement(sBody).jsonObject }.getOrNull()
                    val arr = sRoot?.get("stories") as? JsonArray ?: continue
                    arr.forEach { st ->
                        (st as? JsonObject)?.let { parseWattpadStoryJson(it) }?.let { collected.add(it) }
                    }
                }
            }
            collected.distinctBy { it.workId ?: it.canonicalUrl }
        }
    }

    suspend fun fetchWattpadPrivateLibrary(
        username: String?,
        cookie: String
    ): Result<List<FanficParsedWork>> = withContext(Dispatchers.IO) {
        runCatching {
            require(cookie.isNotBlank()) { "Sign in to Wattpad in WebView first" }
            val cleanUser = username?.trim()?.removePrefix("@").orEmpty()
            val candidateUrls = buildList {
                if (cleanUser.isNotBlank()) {
                    val enc = URLEncoder.encode(cleanUser, "UTF-8")
                    add("https://www.wattpad.com/api/v3/users/$enc/library?limit=50&fields=${URLEncoder.encode(WATTPAD_LIST_STORY_FIELDS, "UTF-8")}")
                }
                add("https://www.wattpad.com/api/v3/library?limit=50&fields=${URLEncoder.encode(WATTPAD_LIST_STORY_FIELDS, "UTF-8")}")
            }
            for (url in candidateUrls) {
                val (code, _, body) = withWattpadDelay {
                    executeGet(url = url, cookie = cookie, extraHeaders = wattpadJsonHeaders())
                }
                if (code == 401 || code == 403) {
                    throw IllegalStateException("Wattpad session expired — sign in again in WebView")
                }
                if (code in 200..299 && body.trimStart().startsWith("{")) {
                    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: continue
                    val arr = root["stories"] as? JsonArray ?: continue
                    val parsed = arr.mapNotNull { (it as? JsonObject)?.let { o -> parseWattpadStoryJson(o) } }
                    if (parsed.isNotEmpty() || url == candidateUrls.last()) {
                        return@runCatching parsed.distinctBy { it.workId ?: it.canonicalUrl }
                    }
                }
            }
            throw IllegalStateException("Enter your Wattpad username and refresh your WebView session to import Private Library")
        }
    }

    private fun parseWattpadHtmlDocument(doc: Document, canonicalUrl: String, workId: String?): FanficFetchResult {
        val storyId = workId?.takeIf { it.startsWith("wp:") }?.removePrefix("wp:")
            ?: Regex("""/story/(\d+)""").find(canonicalUrl)?.groupValues?.getOrNull(1)
            ?: doc.selectFirst("link[rel=canonical]")?.attr("href")?.let { Regex("""/story/(\d+)""").find(it)?.groupValues?.getOrNull(1) }

        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?.removeSuffix(" - Wattpad")?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: return FanficFetchResult.Error("Navigate to a Wattpad story page before importing")
        val description = doc.selectFirst("meta[property=og:description], meta[name=description]")?.attr("content")?.trim()
        val cover = doc.selectFirst("meta[property=og:image]")?.attr("content")?.trim()?.takeIf { it.startsWith("http") }
        val authorEl = doc.selectFirst("a[href*='/user/']")
        val author = authorEl?.text()?.trim()?.takeIf { it.isNotBlank() } ?: "Wattpad Author"
        val authorUrl = authorEl?.absUrl("href")?.takeIf { it.startsWith("http") }
        val tags = doc.select("a[href*='/stories/']").map { it.text().trim() }.filter { it.isNotBlank() }.distinct().take(15)

        val tocLinks = doc.select("ul.table-of-contents a[href], .story-parts a[href], a[href*='wattpad.com/']").filter { a ->
            val href = a.absUrl("href")
            Regex("""wattpad\.com/\d+-""").containsMatchIn(href)
        }
        val chapters = tocLinks.mapIndexed { idx, a ->
            val chTitle = a.text().trim().ifBlank { "Part ${idx + 1}" }
            FanficParsedChapter(
                chapterNumber = idx + 1,
                title = chTitle,
                category = FanficThreadmarkCategory.MAIN,
                sourceSite = FanficSite.WATTPAD,
                chapterUrl = a.absUrl("href").substringBefore("?")
            )
        }.distinctBy { it.chapterUrl }

        val finalCanonical = storyId?.let { "https://www.wattpad.com/story/$it" } ?: canonicalUrl
        val finalWorkId = storyId?.let { "wp:$it" } ?: workId
        val isFanfic = isLikelyWattpadFanfic(tags, description, title)

        return FanficFetchResult.Success(
            FanficParsedWork(
                site = FanficSite.WATTPAD,
                canonicalUrl = finalCanonical,
                workId = finalWorkId,
                title = title,
                author = author,
                authorUrl = authorUrl,
                summary = description,
                coverUrl = cover,
                rating = if (doc.text().contains("Mature", ignoreCase = true) && doc.selectFirst(".mature-badge, [data-testid*='mature']") != null) {
                    FanficRating.MATURE
                } else FanficRating.TEEN,
                workStatus = if (doc.selectFirst(".completed-badge, [data-testid*='completed']") != null) {
                    FanficWorkStatus.COMPLETE
                } else FanficWorkStatus.ONGOING,
                totalChapters = chapters.size.takeIf { it > 0 },
                fandoms = if (isFanfic) tags.take(3) else emptyList(),
                freeformTags = tags,
                chapters = chapters
            )
        )
    }

    suspend fun checkUpdatesAcrossSources(
        item: MediaItemEntity,
        existingChaptersCount: Int,
        cookieProvider: suspend (FanficSite) -> String?
    ): FanficMirrorUpdateOutcome = withContext(Dispatchers.IO) {
        val meta = decodeFanficMeta(item.fanficMetaJson)
        val primaryUrl = item.fanficSourceUrl?.takeIf { it.isNotBlank() }
        val sources = buildList {
            if (primaryUrl != null) {
                add(
                    FanficSourceLink(
                        site = item.fanficSourceSite ?: FanficSite.fromUrl(primaryUrl).code,
                        url = primaryUrl,
                        externalId = item.fanficWorkId,
                        isPrimary = true
                    )
                )
            }
            addAll(meta.mirrorSources)
        }.distinctBy { "${it.site}:${it.url}" }

        if (sources.isEmpty()) {
            return@withContext FanficMirrorUpdateOutcome(
                updatedWork = null,
                updatedMirrors = emptyList(),
                newChaptersFound = 0,
                error = "No linked fanfiction source URL"
            )
        }

        var freshestWork: FanficParsedWork? = null
        var freshestChCount = existingChaptersCount
        var webViewHintSite: FanficSite? = null
        var webViewHintUrl: String? = null
        var lastErr: String? = null
        val updatedMirrors = mutableListOf<FanficSourceLink>()
        val now = System.currentTimeMillis()

        for (src in sources) {
            when (val res = fetchFromUrl(src.url, cookieProvider)) {
                is FanficFetchResult.Success -> {
                    val w = res.work
                    val mainCnt = w.chapters.count { it.category == FanficThreadmarkCategory.MAIN }
                        .takeIf { it > 0 } ?: w.totalChapters ?: 0
                    updatedMirrors.add(
                        src.copy(
                            lastChapterCount = mainCnt,
                            lastWordCount = w.wordCount,
                            lastCheckedMs = now,
                            lastUpdatedLabel = w.updatedDate ?: w.publishedDate
                        )
                    )
                    if (freshestWork == null || mainCnt >= freshestChCount) {
                        freshestWork = w
                        freshestChCount = maxOf(freshestChCount, mainCnt)
                    }
                }
                is FanficFetchResult.RequiresWebView -> {
                    updatedMirrors.add(src.copy(lastCheckedMs = now))
                    if (webViewHintSite == null) {
                        webViewHintSite = res.site
                        webViewHintUrl = res.url
                    }
                    lastErr = res.reason
                }
                is FanficFetchResult.Error -> {
                    updatedMirrors.add(src.copy(lastCheckedMs = now))
                    lastErr = res.message
                }
            }
        }

        val newCount = ((freshestWork?.let {
            it.chapters.count { c -> c.category == FanficThreadmarkCategory.MAIN }.takeIf { cnt -> cnt > 0 }
                ?: it.totalChapters
        } ?: existingChaptersCount) - existingChaptersCount).coerceAtLeast(0)

        FanficMirrorUpdateOutcome(
            updatedWork = freshestWork,
            updatedMirrors = updatedMirrors,
            newChaptersFound = newCount,
            requiresWebViewSite = if (freshestWork == null) webViewHintSite else null,
            requiresWebViewUrl = if (freshestWork == null) webViewHintUrl else null,
            error = if (freshestWork == null) lastErr else null
        )
    }
}
