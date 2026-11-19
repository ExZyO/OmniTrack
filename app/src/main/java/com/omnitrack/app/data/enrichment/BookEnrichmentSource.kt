package com.omnitrack.app.data.enrichment

import com.omnitrack.app.data.artwork.LosslessCoverMapper
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.model.AnimeRelationItem
import com.omnitrack.app.data.model.AnimeTagItem
import com.omnitrack.app.data.model.BookAuthorProfile
import com.omnitrack.app.data.model.BookDetailMeta
import com.omnitrack.app.data.model.BookEditionItem
import com.omnitrack.app.data.model.BookIllustrationAsset
import com.omnitrack.app.data.model.BookLiteraryForm
import com.omnitrack.app.data.model.ExternalLinkItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.inferBookLiteraryForm
import com.omnitrack.app.data.network.NetworkClient
import com.omnitrack.app.data.network.NetworkPacer
import com.omnitrack.app.data.search.OmniSearchRepository
import com.omnitrack.app.ui.screens.detail.components.DiscussionComment
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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import kotlin.math.roundToInt

data class BookFullEnrichmentResult(
    val resolvedOpenLibraryId: String? = null,
    val resolvedHardcoverId: String? = null,
    val resolvedIsbn: String? = null,
    val title: String? = null,
    val synopsis: String? = null,
    val coverUrl: String? = null,
    val bannerUrl: String? = null,
    val releaseYear: String? = null,
    val authorName: String? = null,
    val publisher: String? = null,
    val translator: String? = null,
    val editionName: String? = null,
    val totalPages: Int? = null,
    val totalAudioMinutes: Int? = null,
    val communityScore: Float? = null,
    val literaryFormLabel: String? = null,
    val bookDetailMeta: BookDetailMeta = BookDetailMeta(),
    val tags: List<AnimeTagItem> = emptyList(),
    val relations: List<AnimeRelationItem> = emptyList(),
    val similarBooks: List<RecommendationItem> = emptyList(),
    val externalLinks: List<ExternalLinkItem> = emptyList(),
    val discussionComments: List<DiscussionComment> = emptyList(),
    val sectionSeedTitles: List<String> = emptyList()
)

class BookEnrichmentSource(
    private val client: OkHttpClient = NetworkClient.getClient()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val hardcoverClient = HardcoverApiClient(client)

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.arr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.str(): String? {
        if (this == null || this is JsonNull) return null
        val s = (this as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
        return s.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    private fun JsonElement?.intVal(): Int? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.intOrNull

    private fun JsonElement?.floatVal(): Float? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.floatOrNull

    private fun parseDescriptionField(el: JsonElement?): String? {
        if (el == null || el is JsonNull) return null
        return el.str() ?: el.obj()?.get("value").str()
    }

    private fun extractCleanWorkId(raw: String?): String? {
        val clean = raw?.trim()?.substringAfterLast("/")?.takeIf { it.matches(Regex("^OL\\d+W$")) }
        return clean
    }

    private fun upgradeAppleCover(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return url
            .replace("100x100bb", "1200x1200bb")
            .replace("60x60bb", "1200x1200bb")
            .replace("200x200bb", "1200x1200bb")
            .replace("http://", "https://")
    }

    suspend fun fetchFullBookData(
        item: MediaItemEntity,
        hardcoverToken: String
    ): BookFullEnrichmentResult? = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                val authorHint = item.bookAuthor?.takeIf { it.isNotBlank() }
                    ?: item.studioOrNetwork?.takeIf { it.isNotBlank() }

                val olDeferred = async {
                    fetchOpenLibraryBundle(
                        existingWorkId = extractCleanWorkId(item.openLibraryId),
                        title = item.title,
                        authorHint = authorHint
                    )
                }
                val hcDeferred = async {
                    if (hardcoverToken.isNotBlank()) {
                        hardcoverClient.fetchBookDetails(
                            token = hardcoverToken,
                            hardcoverId = item.hardcoverId,
                            title = item.title,
                            author = authorHint
                        )
                    } else null
                }
                val wikiDeferred = async {
                    fetchWikipediaAndCommons(item.title, authorHint)
                }
                val appleEbookDeferred = async {
                    fetchAppleBooksEntry(item.title, authorHint, mediaType = "ebook")
                }
                val appleAudioDeferred = async {
                    fetchAppleBooksEntry(item.title, authorHint, mediaType = "audiobook")
                }
                val poetryDeferred = async {
                    fetchPoetryDbPoem(item.title, authorHint)
                }
                val redditDeferred = async {
                    fetchRedditBookDiscussions(item.title)
                }

                val ol = olDeferred.await()
                val hc = hcDeferred.await()
                val wiki = wikiDeferred.await()
                val appleEbook = appleEbookDeferred.await()
                val appleAudio = appleAudioDeferred.await()
                val poetry = poetryDeferred.await()
                val reddit = redditDeferred.await()

                val wikidataDeferred = async {
                    wiki?.wikidataId?.let { fetchWikidataBookMetadata(it) }
                }
                val wikidata = wikidataDeferred.await()

                val resolvedTitle = hc?.title ?: ol?.title ?: item.title
                val resolvedAuthor = authorHint
                    ?: ol?.authorName
                    ?: hc?.authorName
                    ?: appleEbook?.artistName
                    ?: poetry?.author

                val resolvedWorkId = extractCleanWorkId(item.openLibraryId) ?: ol?.workId
                val resolvedHardcoverId = item.hardcoverId?.takeIf { it.isNotBlank() } ?: hc?.hardcoverId
                val resolvedIsbn = item.isbn?.takeIf { it.isNotBlank() }
                    ?: hc?.isbn13
                    ?: ol?.isbn
                    ?: ol?.editions?.firstNotNullOfOrNull { it.isbn13 }

                val bestSynopsis = listOfNotNull(
                    ol?.description?.let { OmniSearchRepository.cleanSynopsis(it) },
                    hc?.description?.let { OmniSearchRepository.cleanSynopsis(it) },
                    wiki?.extract?.let { OmniSearchRepository.cleanSynopsis(it) },
                    appleEbook?.description?.let { OmniSearchRepository.cleanSynopsis(it) },
                    item.synopsis?.let { OmniSearchRepository.cleanSynopsis(it) }
                ).maxByOrNull { it.length }

                val subjects = (ol?.subjects.orEmpty() + hc?.genres.orEmpty() + wikidata?.genres.orEmpty())
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.length <= 36 }
                    .distinctBy { it.lowercase() }

                val inferredForm = inferBookLiteraryForm(
                    title = resolvedTitle,
                    subjects = subjects,
                    description = bestSynopsis
                )

                val wideArtworkBanner = wiki?.illustrations
                    ?.firstOrNull { it.isWideHero }?.url

                val resolvedCover = (
                    appleEbook?.coverUrl
                        ?: hc?.coverUrl
                        ?: ol?.coverUrl
                        ?: item.coverUrl?.takeIf { it.isNotBlank() }
                        ?: wiki?.portraitThumbUrl
                    )?.let { LosslessCoverMapper.toLosslessUrl(it) }

                val totalPages = item.totalProgress?.toInt()?.takeIf { it > 0 }
                    ?: hc?.pages
                    ?: ol?.medianPages
                    ?: ol?.editions?.firstNotNullOfOrNull { it.pageCount }

                val totalAudioMins = item.bookTotalAudioMinutes?.takeIf { it > 0 }
                    ?: hc?.audioSeconds?.takeIf { it > 0 }?.let { (it / 60).coerceAtLeast(1) }
                    ?: appleAudio?.durationMinutes

                val releaseYear = item.releaseYear?.takeIf { it.isNotBlank() }
                    ?: ol?.firstPublishYear
                    ?: hc?.releaseYear
                    ?: appleEbook?.releaseYear

                val releaseYearNum = releaseYear?.take(4)?.toIntOrNull()
                val encodedTitle = URLEncoder.encode(resolvedTitle, "UTF-8")
                val gutenbergUrl = if (releaseYearNum != null && releaseYearNum <= 1928) {
                    "https://www.gutenberg.org/ebooks/search/?query=$encodedTitle"
                } else null
                val standardEbooksUrl = if (releaseYearNum != null && releaseYearNum <= 1928) {
                    "https://standardebooks.org/ebooks?query=$encodedTitle"
                } else null

                val combinedRating10 = listOfNotNull(
                    hc?.rating5?.let { it * 2f },
                    ol?.rating5?.let { it * 2f },
                    appleEbook?.rating5?.let { it * 2f }
                ).firstOrNull() ?: item.communityScore

                val awardsList = (wikidata?.awards.orEmpty()).distinct()
                val moodsList = hc?.moods.orEmpty().distinct()

                val combinedTags = buildList {
                    subjects.take(12).forEachIndexed { idx, s ->
                        add(AnimeTagItem(id = (idx + 1).toLong(), name = s, category = "genre"))
                    }
                    moodsList.take(8).forEachIndexed { idx, m ->
                        add(AnimeTagItem(id = (idx + 200).toLong(), name = m, category = "mood"))
                    }
                    awardsList.take(6).forEachIndexed { idx, a ->
                        add(AnimeTagItem(id = (idx + 400).toLong(), name = a, category = "award"))
                    }
                }.distinctBy { "${it.category}_${it.name.lowercase()}" }

                val bookRelations = wikidata?.relations.orEmpty()

                val externalLinks = buildList {
                    resolvedWorkId?.let { wid ->
                        add(ExternalLinkItem(id = 7001L, url = "https://openlibrary.org/works/$wid", site = "Open Library", type = "INFO"))
                    }
                    resolvedHardcoverId?.let { hid ->
                        val path = hc?.slug?.takeIf { it.isNotBlank() } ?: hid
                        add(ExternalLinkItem(id = 7002L, url = "https://hardcover.app/books/$path", site = "Hardcover", type = "TRACKER"))
                    }
                    wiki?.pageUrl?.let { wp ->
                        add(ExternalLinkItem(id = 7003L, url = wp, site = "Wikipedia", type = "INFO"))
                    }
                    gutenbergUrl?.let { gb ->
                        add(ExternalLinkItem(id = 7004L, url = gb, site = "Project Gutenberg", type = "READ"))
                    }
                    standardEbooksUrl?.let { se ->
                        add(ExternalLinkItem(id = 7005L, url = se, site = "Standard Ebooks", type = "READ"))
                    }
                    appleEbook?.trackViewUrl?.let { ab ->
                        add(ExternalLinkItem(id = 7006L, url = ab, site = "Apple Books", type = "STORE"))
                    }
                    appleAudio?.trackViewUrl?.let { aa ->
                        add(ExternalLinkItem(id = 7007L, url = aa, site = "Apple Audiobooks", type = "AUDIO"))
                    }
                }.distinctBy { it.url }

                val firstEdition = ol?.editions?.firstOrNull()
                val seedTitles = buildCanonicalSectionSeeds(resolvedTitle, inferredForm)

                val meta = BookDetailMeta(
                    openLibraryWorkId = resolvedWorkId,
                    hardcoverId = resolvedHardcoverId,
                    isbn13 = resolvedIsbn,
                    wikidataId = wiki?.wikidataId,
                    authorName = resolvedAuthor,
                    authorProfile = ol?.authorProfile?.let { prof ->
                        prof.copy(
                            works = prof.works.filterNot {
                                it.openLibraryId == resolvedWorkId || it.title.equals(resolvedTitle, ignoreCase = true)
                            }.take(14)
                        )
                    },
                    literaryForm = inferredForm,
                    originalLanguage = wikidata?.originalLanguage,
                    publisher = firstEdition?.publisher,
                    translator = item.bookTranslator ?: firstEdition?.translator,
                    editionName = item.bookEditionLabel ?: firstEdition?.title?.takeIf { !it.equals(resolvedTitle, ignoreCase = true) },
                    pageCount = totalPages,
                    lineCount = poetry?.lineCount,
                    audioDurationMinutes = totalAudioMins,
                    audiobookNarrator = appleAudio?.artistName?.takeIf { !it.equals(resolvedAuthor, ignoreCase = true) },
                    seriesName = hc?.seriesName ?: wikidata?.seriesName,
                    seriesPosition = hc?.seriesPosition,
                    seriesTotal = hc?.seriesBooksCount,
                    openLibraryRating = ol?.rating5,
                    openLibraryRatingCount = ol?.ratingCount,
                    hardcoverRating = hc?.rating5,
                    hardcoverRatingCount = hc?.ratingsCount,
                    appleBooksRating = appleEbook?.rating5 ?: appleAudio?.rating5,
                    appleBooksRatingCount = appleEbook?.ratingCount ?: appleAudio?.ratingCount,
                    wantToReadCount = ol?.wantToReadCount,
                    alreadyReadCount = ol?.alreadyReadCount,
                    moods = moodsList,
                    awards = awardsList,
                    contentWarnings = hc?.contentWarnings.orEmpty(),
                    editions = ol?.editions.orEmpty(),
                    illustrations = wiki?.illustrations.orEmpty(),
                    poemSampleLines = poetry?.lines.orEmpty(),
                    gutenbergUrl = gutenbergUrl,
                    standardEbooksUrl = standardEbooksUrl
                )

                val similarBooks = meta.authorProfile?.works.orEmpty()

                BookFullEnrichmentResult(
                    resolvedOpenLibraryId = resolvedWorkId,
                    resolvedHardcoverId = resolvedHardcoverId,
                    resolvedIsbn = resolvedIsbn,
                    title = resolvedTitle,
                    synopsis = bestSynopsis,
                    coverUrl = resolvedCover,
                    bannerUrl = wideArtworkBanner,
                    releaseYear = releaseYear,
                    authorName = resolvedAuthor,
                    publisher = firstEdition?.publisher,
                    translator = item.bookTranslator ?: firstEdition?.translator,
                    editionName = item.bookEditionLabel ?: meta.editionName,
                    totalPages = totalPages,
                    totalAudioMinutes = totalAudioMins,
                    communityScore = combinedRating10,
                    literaryFormLabel = inferredForm.label,
                    bookDetailMeta = meta,
                    tags = combinedTags,
                    relations = bookRelations,
                    similarBooks = similarBooks,
                    externalLinks = externalLinks,
                    discussionComments = reddit,
                    sectionSeedTitles = seedTitles
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun buildCanonicalSectionSeeds(title: String, form: BookLiteraryForm): List<String> {
        val lower = title.lowercase()
        return when {
            "paradise lost" in lower -> (1..12).map { "Book ${toRoman(it)}" }
            "iliad" in lower || "odyssey" in lower -> (1..24).map { "Book ${toRoman(it)}" }
            "aeneid" in lower -> (1..12).map { "Book ${toRoman(it)}" }
            "divine comedy" in lower || "la divina commedia" in lower -> listOf(
                "Inferno (Cantos I–XXXIV)",
                "Purgatorio (Cantos I–XXXIII)",
                "Paradiso (Cantos I–XXXIII)"
            )
            "faust" in lower -> listOf(
                "Part I · Prelude & Study",
                "Part I · Gretchen Tragedy",
                "Part II · Acts I–III",
                "Part II · Acts IV–V"
            )
            form == BookLiteraryForm.PLAY || form == BookLiteraryForm.VERSE_PLAY ->
                (1..5).map { "Act ${toRoman(it)}" }
            form == BookLiteraryForm.EPIC_POEM ->
                (1..12).map { "Canto ${toRoman(it)}" }
            else -> listOf("Read #1")
        }
    }

    private fun toRoman(n: Int): String {
        val numerals = listOf(
            10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"
        )
        var rem = n
        val sb = StringBuilder()
        for ((v, sym) in numerals) {
            while (rem >= v) {
                sb.append(sym)
                rem -= v
            }
        }
        return sb.toString()
    }

    private data class OpenLibraryBundle(
        val workId: String,
        val title: String? = null,
        val description: String? = null,
        val firstPublishYear: String? = null,
        val medianPages: Int? = null,
        val coverUrl: String? = null,
        val authorName: String? = null,
        val isbn: String? = null,
        val rating5: Float? = null,
        val ratingCount: Int? = null,
        val wantToReadCount: Int? = null,
        val alreadyReadCount: Int? = null,
        val subjects: List<String> = emptyList(),
        val editions: List<BookEditionItem> = emptyList(),
        val authorProfile: BookAuthorProfile? = null
    )

    private suspend fun fetchOpenLibraryBundle(
        existingWorkId: String?,
        title: String,
        authorHint: String?
    ): OpenLibraryBundle? {
        var resolvedWorkId = existingWorkId
        var searchTitle: String? = null
        var searchAuthor: String? = null
        var searchAuthorKey: String? = null
        var searchYear: String? = null
        var searchPages: Int? = null
        var searchCoverUrl: String? = null
        var searchIsbn: String? = null
        var searchRating5: Float? = null
        var searchRatingCount: Int? = null
        var searchWant: Int? = null
        var searchRead: Int? = null

        val searchDoc = NetworkPacer.withOpenLibraryPacing {
            try {
                val encTitle = URLEncoder.encode(title.trim(), "UTF-8")
                val encAuthor = authorHint?.split(",")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                    ?.let { "&author=${URLEncoder.encode(it, "UTF-8")}" }.orEmpty()
                val fields = "key,title,author_name,author_key,first_publish_year,number_of_pages_median,ratings_average,ratings_count,want_to_read_count,already_read_count,cover_i,isbn"
                val url = "https://openlibrary.org/search.json?title=$encTitle$encAuthor&limit=5&fields=$fields"
                val req = Request.Builder().url(url).header("User-Agent", "OmniTrack/1.0").get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withOpenLibraryPacing null
                    val body = resp.body?.string().orEmpty()
                    val docs = json.parseToJsonElement(body).obj()?.get("docs").arr().orEmpty()
                    docs.firstOrNull { el ->
                        val docTitle = el.obj()?.get("title").str().orEmpty()
                        docTitle.equals(title.trim(), ignoreCase = true)
                    }.obj() ?: docs.firstOrNull { el ->
                        el.obj()?.get("cover_i").intVal() != null
                    }.obj() ?: docs.firstOrNull().obj()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

        if (searchDoc != null) {
            if (resolvedWorkId == null) {
                resolvedWorkId = extractCleanWorkId(searchDoc["key"].str())
            }
            searchTitle = searchDoc["title"].str()
            searchAuthor = searchDoc["author_name"].arr()?.firstOrNull()?.str()
            searchAuthorKey = searchDoc["author_key"].arr()?.firstOrNull()?.str()
            searchYear = searchDoc["first_publish_year"].intVal()?.toString()
            searchPages = searchDoc["number_of_pages_median"].intVal()
            searchCoverUrl = searchDoc["cover_i"].intVal()?.takeIf { it > 0 }
                ?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
            searchIsbn = searchDoc["isbn"].arr()?.firstOrNull { el ->
                val s = el.str().orEmpty()
                s.length == 13 || s.length == 10
            }?.str() ?: searchDoc["isbn"].arr()?.firstOrNull()?.str()
            searchRating5 = searchDoc["ratings_average"].floatVal()
            searchRatingCount = searchDoc["ratings_count"].intVal()
            searchWant = searchDoc["want_to_read_count"].intVal()
            searchRead = searchDoc["already_read_count"].intVal()
        }

        val workId = resolvedWorkId ?: return null

        return coroutineScope {
            val workDeferred = async {
                NetworkPacer.withOpenLibraryPacing {
                    try {
                        val req = Request.Builder()
                            .url("https://openlibrary.org/works/$workId.json")
                            .header("User-Agent", "OmniTrack/1.0")
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withOpenLibraryPacing null
                            json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                }
            }

            val ratingsDeferred = async {
                NetworkPacer.withOpenLibraryPacing {
                    try {
                        val req = Request.Builder()
                            .url("https://openlibrary.org/works/$workId/ratings.json")
                            .header("User-Agent", "OmniTrack/1.0")
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withOpenLibraryPacing null
                            json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        null
                    }
                }
            }

            val editionsDeferred = async {
                NetworkPacer.withOpenLibraryPacing {
                    try {
                        val req = Request.Builder()
                            .url("https://openlibrary.org/works/$workId/editions.json?limit=16")
                            .header("User-Agent", "OmniTrack/1.0")
                            .get()
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@withOpenLibraryPacing emptyList()
                            val entries = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                                ?.get("entries").arr().orEmpty()
                            entries.mapNotNull { el ->
                                val eObj = el.obj() ?: return@mapNotNull null
                                val edTitle = eObj["title"].str() ?: return@mapNotNull null
                                val publisher = eObj["publishers"].arr()?.firstOrNull()?.str()
                                val pubYear = eObj["publish_date"].str()
                                val pages = eObj["number_of_pages"].intVal()
                                val isbn13 = eObj["isbn_13"].arr()?.firstOrNull()?.str()
                                    ?: eObj["isbn_10"].arr()?.firstOrNull()?.str()
                                val covId = eObj["covers"].arr()?.firstOrNull()?.intVal()?.takeIf { it > 0 }
                                val edCover = covId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
                                val fmt = eObj["physical_format"].str()
                                val notesTranslator = eObj["by_statement"].str()
                                    ?.takeIf { it.contains("trans", ignoreCase = true) }
                                val edKey = eObj["key"].str()?.substringAfterLast("/")
                                BookEditionItem(
                                    title = edTitle,
                                    publisher = publisher,
                                    publishYear = pubYear,
                                    translator = notesTranslator,
                                    format = fmt,
                                    pageCount = pages,
                                    isbn13 = isbn13,
                                    coverUrl = edCover,
                                    openLibraryEditionKey = edKey
                                )
                            }.distinctBy { "${it.publisher}_${it.publishYear}_${it.pageCount}" }.take(12)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emptyList()
                    }
                }
            }

            val workObj = workDeferred.await()
            val ratingsObj = ratingsDeferred.await()
            val editions = editionsDeferred.await()

            val authorKeyFromWork = workObj?.get("authors").arr()?.firstNotNullOfOrNull { el ->
                el.obj()?.get("author").obj()?.get("key").str()?.substringAfterLast("/")
            } ?: searchAuthorKey

            val authorProfile = authorKeyFromWork?.let { aKey ->
                fetchOpenLibraryAuthorProfile(aKey, searchAuthor)
            }

            val ratingSummary = ratingsObj?.get("summary").obj()
            val finalRating5 = ratingSummary?.get("average").floatVal() ?: searchRating5
            val finalRatingCount = ratingSummary?.get("count").intVal() ?: searchRatingCount

            val workCovers = workObj?.get("covers").arr()?.mapNotNull { it.intVal()?.takeIf { id -> id > 0 } }.orEmpty()
            val workCoverUrl = workCovers.firstOrNull()?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
                ?: searchCoverUrl

            val workSubjects = workObj?.get("subjects").arr()?.mapNotNull { it.str() }.orEmpty()

            OpenLibraryBundle(
                workId = workId,
                title = workObj?.get("title").str() ?: searchTitle,
                description = parseDescriptionField(workObj?.get("description")),
                firstPublishYear = workObj?.get("first_publish_date").str()?.take(4) ?: searchYear,
                medianPages = searchPages,
                coverUrl = workCoverUrl,
                authorName = authorProfile?.name ?: searchAuthor,
                isbn = searchIsbn ?: editions.firstNotNullOfOrNull { it.isbn13 },
                rating5 = finalRating5,
                ratingCount = finalRatingCount,
                wantToReadCount = searchWant,
                alreadyReadCount = searchRead,
                subjects = workSubjects,
                editions = editions,
                authorProfile = authorProfile
            )
        }
    }

    private suspend fun fetchOpenLibraryAuthorProfile(
        authorKey: String,
        fallbackName: String?
    ): BookAuthorProfile? = coroutineScope {
        val bioDeferred = async {
            NetworkPacer.withOpenLibraryPacing {
                try {
                    val req = Request.Builder()
                        .url("https://openlibrary.org/authors/$authorKey.json")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withOpenLibraryPacing null
                        json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }
        }
        val worksDeferred = async {
            NetworkPacer.withOpenLibraryPacing {
                try {
                    val req = Request.Builder()
                        .url("https://openlibrary.org/authors/$authorKey/works.json?limit=18")
                        .header("User-Agent", "OmniTrack/1.0")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withOpenLibraryPacing emptyList()
                        json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                            ?.get("entries").arr().orEmpty()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyList()
                }
            }
        }

        val authorObj = bioDeferred.await()
        val worksEntries = worksDeferred.await()
        val authorName = authorObj?.get("name").str() ?: fallbackName ?: return@coroutineScope null
        val birth = authorObj?.get("birth_date").str()
        val death = authorObj?.get("death_date").str()
        val lifespan = when {
            !birth.isNullOrBlank() && !death.isNullOrBlank() -> "$birth – $death"
            !birth.isNullOrBlank() -> "b. $birth"
            else -> null
        }
        val photoId = authorObj?.get("photos").arr()?.firstOrNull()?.intVal()?.takeIf { it > 0 }
        val photoUrl = photoId?.let { "https://covers.openlibrary.org/a/id/$it-M.jpg" }

        val workRecs = worksEntries.mapNotNull { el ->
            val wObj = el.obj() ?: return@mapNotNull null
            val wTitle = wObj["title"].str() ?: return@mapNotNull null
            val covId = wObj["covers"].arr()?.firstOrNull()?.intVal()?.takeIf { it > 0 } ?: return@mapNotNull null
            val wId = wObj["key"].str()?.substringAfterLast("/")
            val year = wObj["first_publish_date"].str()?.take(4)
            RecommendationItem(
                title = wTitle,
                coverUrl = "https://covers.openlibrary.org/b/id/$covId-L.jpg",
                releaseYear = year,
                mediaType = MediaType.BOOK,
                openLibraryId = wId,
                bookAuthor = authorName
            )
        }.distinctBy { it.title.lowercase() }

        BookAuthorProfile(
            openLibraryKey = authorKey,
            name = authorName,
            bio = parseDescriptionField(authorObj?.get("bio"))?.let { OmniSearchRepository.cleanSynopsis(it) },
            birthDeath = lifespan,
            photoUrl = photoUrl,
            works = workRecs
        )
    }

    private data class WikiResult(
        val pageTitle: String,
        val pageUrl: String? = null,
        val wikidataId: String? = null,
        val extract: String? = null,
        val portraitThumbUrl: String? = null,
        val illustrations: List<BookIllustrationAsset> = emptyList()
    )

    private suspend fun fetchWikipediaAndCommons(title: String, authorHint: String?): WikiResult? {
        suspend fun fetchSummary(wikiSlug: String): JsonObject? = NetworkPacer.withGeneralPacing {
            try {
                val safeSlug = URLEncoder.encode(wikiSlug.replace(" ", "_"), "UTF-8")
                    .replace("+", "%20")
                val req = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/summary/$safeSlug")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing null
                    json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

        var summaryObj = fetchSummary(title.trim())
        val type = summaryObj?.get("type").str()
        if (summaryObj == null || type == "disambiguation" ||
            summaryObj["extract"].str()?.contains("may refer to:", ignoreCase = true) == true
        ) {
            val bestSlug = NetworkPacer.withGeneralPacing {
                try {
                    val queryStr = listOfNotNull(title.trim(), authorHint?.split(",")?.firstOrNull()?.trim(), "book")
                        .joinToString(" ")
                    val enc = URLEncoder.encode(queryStr, "UTF-8")
                    val req = Request.Builder()
                        .url("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$enc&format=json&srlimit=3")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing null
                        json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                            ?.get("query").obj()?.get("search").arr()
                            ?.firstOrNull().obj()?.get("title").str()
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            }
            if (!bestSlug.isNullOrBlank()) {
                summaryObj = fetchSummary(bestSlug) ?: summaryObj
            }
        }

        val resolvedPageTitle = summaryObj?.get("title").str() ?: return null
        val qid = summaryObj["wikibase_item"].str()
        val extract = summaryObj["extract"].str()
        val pageUrl = summaryObj["content_urls"].obj()?.get("desktop").obj()?.get("page").str()
        val origImg = summaryObj["originalimage"].obj()
        val origImgUrl = origImg?.get("source").str()
        val origW = origImg?.get("width").intVal() ?: 0
        val origH = origImg?.get("height").intVal() ?: 0

        val fileTitles = NetworkPacer.withGeneralPacing {
            try {
                val safeSlug = URLEncoder.encode(resolvedPageTitle.replace(" ", "_"), "UTF-8")
                    .replace("+", "%20")
                val req = Request.Builder()
                    .url("https://en.wikipedia.org/api/rest_v1/page/media-list/$safeSlug")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyList()
                    val items = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        ?.get("items").arr().orEmpty()
                    items.mapNotNull { el ->
                        val obj = el.obj() ?: return@mapNotNull null
                        if (obj["type"].str() != "image") return@mapNotNull null
                        val fileTitle = obj["title"].str() ?: return@mapNotNull null
                        val lower = fileTitle.lowercase()
                        if (lower.endsWith(".svg") ||
                            "commons-logo" in lower || "wiki" in lower ||
                            "ambox" in lower || "symbol" in lower ||
                            "flag_of" in lower || "question_book" in lower ||
                            "edit-clear" in lower || "speakerlink" in lower
                        ) return@mapNotNull null
                        val cap = obj["caption"].obj()?.get("text").str()
                        fileTitle to cap
                    }.take(10)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyList()
            }
        }

        val illustrations = mutableListOf<BookIllustrationAsset>()
        if (origImgUrl != null && !origImgUrl.endsWith(".svg", ignoreCase = true) && origW >= 400) {
            val isWide = origW > (origH * 1.15f)
            illustrations.add(
                BookIllustrationAsset(
                    url = origImgUrl,
                    thumbnailUrl = summaryObj["thumbnail"].obj()?.get("source").str(),
                    caption = resolvedPageTitle,
                    isWideHero = isWide
                )
            )
        }

        if (fileTitles.isNotEmpty()) {
            val captionMap = fileTitles.associate { it.first to it.second }
            val joinedTitles = URLEncoder.encode(fileTitles.joinToString("|") { it.first }, "UTF-8")
            NetworkPacer.withGeneralPacing {
                try {
                    val req = Request.Builder()
                        .url("https://en.wikipedia.org/w/api.php?action=query&titles=$joinedTitles&prop=imageinfo&iiprop=url|size&iiurlwidth=900&format=json")
                        .header("User-Agent", "OmniTrack/1.0 (Android)")
                        .get()
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@withGeneralPacing
                        val pages = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                            ?.get("query").obj()?.get("pages").obj() ?: return@withGeneralPacing
                        pages.values.forEach { pEl ->
                            val pObj = pEl.obj() ?: return@forEach
                            val t = pObj["title"].str().orEmpty()
                            val ii = pObj["imageinfo"].arr()?.firstOrNull().obj() ?: return@forEach
                            val fullUrl = ii["url"].str() ?: return@forEach
                            if (fullUrl.endsWith(".svg", ignoreCase = true)) return@forEach
                            val thumbUrl = ii["thumburl"].str() ?: fullUrl
                            val w = ii["width"].intVal() ?: 0
                            val h = ii["height"].intVal() ?: 0
                            if (w < 420 || h < 260) return@forEach
                            val isWide = w > (h * 1.15f)
                            illustrations.add(
                                BookIllustrationAsset(
                                    url = fullUrl,
                                    thumbnailUrl = thumbUrl,
                                    caption = captionMap[t] ?: t.removePrefix("File:").substringBeforeLast(".").replace('_', ' '),
                                    isWideHero = isWide
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
        }

        val dedupedIllustrations = illustrations
            .distinctBy { it.url }
            .sortedByDescending { it.isWideHero }
            .take(12)

        return WikiResult(
            pageTitle = resolvedPageTitle,
            pageUrl = pageUrl,
            wikidataId = qid,
            extract = extract,
            portraitThumbUrl = if (origW in 1..origH || origImgUrl?.contains("cover", true) == true) origImgUrl else null,
            illustrations = dedupedIllustrations
        )
    }

    private data class WikidataBookMeta(
        val originalLanguage: String? = null,
        val genres: List<String> = emptyList(),
        val awards: List<String> = emptyList(),
        val seriesName: String? = null,
        val relations: List<AnimeRelationItem> = emptyList()
    )

    private suspend fun fetchWikidataBookMetadata(qid: String): WikidataBookMeta? {
        val entity = NetworkPacer.withGeneralPacing {
            try {
                val req = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$qid&props=claims&format=json")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing null
                    json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        ?.get("entities").obj()?.get(qid).obj()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        } ?: return null

        val claims = entity["claims"].obj() ?: return null
        fun claimQids(prop: String, limit: Int = 5): List<String> =
            claims[prop].arr().orEmpty().mapNotNull { el ->
                el.obj()?.get("mainsnak").obj()?.get("datavalue").obj()
                    ?.get("value").obj()?.get("id").str()
            }.distinct().take(limit)

        val langQids = claimQids("P364", 1)
        val genreQids = claimQids("P136", 5)
        val awardQids = claimQids("P166", 6)
        val seriesQids = claimQids("P179", 1)
        val prequelQids = claimQids("P155", 2)
        val sequelQids = claimQids("P156", 2)

        val allQids = (langQids + genreQids + awardQids + seriesQids + prequelQids + sequelQids).distinct()
        if (allQids.isEmpty()) return null

        val labelsMap = NetworkPacer.withGeneralPacing {
            try {
                val joined = allQids.joinToString("|")
                val req = Request.Builder()
                    .url("https://www.wikidata.org/w/api.php?action=wbgetentities&ids=$joined&props=labels&languages=en&format=json")
                    .header("User-Agent", "OmniTrack/1.0 (Android)")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withGeneralPacing emptyMap()
                    val ents = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                        ?.get("entities").obj() ?: return@withGeneralPacing emptyMap()
                    ents.mapNotNull { (k, v) ->
                        val lbl = v.obj()?.get("labels").obj()?.get("en").obj()?.get("value").str()
                        if (lbl != null) k to lbl else null
                    }.toMap()
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                emptyMap()
            }
        }

        val relations = buildList {
            prequelQids.forEachIndexed { idx, id ->
                labelsMap[id]?.let { label ->
                    add(
                        AnimeRelationItem(
                            id = (8000 + idx).toLong(),
                            title = label,
                            relationType = "PREQUEL",
                            format = "BOOK",
                            coverUrl = null,
                            mediaType = MediaType.BOOK
                        )
                    )
                }
            }
            sequelQids.forEachIndexed { idx, id ->
                labelsMap[id]?.let { label ->
                    add(
                        AnimeRelationItem(
                            id = (8100 + idx).toLong(),
                            title = label,
                            relationType = "SEQUEL",
                            format = "BOOK",
                            coverUrl = null,
                            mediaType = MediaType.BOOK
                        )
                    )
                }
            }
        }

        return WikidataBookMeta(
            originalLanguage = langQids.firstNotNullOfOrNull { labelsMap[it] }
                ?.replaceFirstChar { it.uppercase() },
            genres = genreQids.mapNotNull { labelsMap[it]?.replaceFirstChar { c -> c.uppercase() } },
            awards = awardQids.mapNotNull { labelsMap[it] },
            seriesName = seriesQids.firstNotNullOfOrNull { labelsMap[it] },
            relations = relations
        )
    }

    private data class AppleBookMatch(
        val coverUrl: String? = null,
        val rating5: Float? = null,
        val ratingCount: Int? = null,
        val description: String? = null,
        val releaseYear: String? = null,
        val artistName: String? = null,
        val trackViewUrl: String? = null,
        val durationMinutes: Int? = null
    )

    private suspend fun fetchAppleBooksEntry(
        title: String,
        authorHint: String?,
        mediaType: String
    ): AppleBookMatch? = NetworkPacer.withGeneralPacing {
        try {
            val term = listOfNotNull(title.trim(), authorHint?.split(",")?.firstOrNull()?.trim()).joinToString(" ")
            val encoded = URLEncoder.encode(term, "UTF-8")
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=$encoded&media=$mediaType&limit=5")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val results = json.parseToJsonElement(resp.body?.string().orEmpty()).obj()
                    ?.get("results").arr().orEmpty()
                val titleLower = title.trim().lowercase()
                val match = results.firstOrNull { el ->
                    val o = el.obj() ?: return@firstOrNull false
                    val t = (o["trackName"].str() ?: o["collectionName"].str()).orEmpty().lowercase()
                    t == titleLower || t.startsWith(titleLower) || titleLower in t
                }.obj() ?: return@withGeneralPacing null

                val cov = upgradeAppleCover(match["artworkUrl100"].str() ?: match["artworkUrl60"].str())
                val durMillis = match["collectionTimeMillis"].str()?.toLongOrNull()
                    ?: match["trackTimeMillis"].str()?.toLongOrNull()
                AppleBookMatch(
                    coverUrl = cov,
                    rating5 = match["averageUserRating"].floatVal(),
                    ratingCount = match["userRatingCount"].intVal(),
                    description = match["description"].str(),
                    releaseYear = match["releaseDate"].str()?.take(4),
                    artistName = match["artistName"].str(),
                    trackViewUrl = match["trackViewUrl"].str() ?: match["collectionViewUrl"].str(),
                    durationMinutes = durMillis?.takeIf { it > 60_000L }?.let { (it / 60_000L).toInt() }
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private data class PoetryDbPoem(
        val title: String,
        val author: String?,
        val lineCount: Int?,
        val lines: List<String>
    )

    private suspend fun fetchPoetryDbPoem(
        title: String,
        authorHint: String?
    ): PoetryDbPoem? = NetworkPacer.withGeneralPacing {
        try {
            val cleanTitle = title.trim()
            if (cleanTitle.length < 3 || cleanTitle.length > 72) return@withGeneralPacing null
            val encoded = URLEncoder.encode(cleanTitle, "UTF-8").replace("+", "%20")
            val req = Request.Builder()
                .url("https://poetrydb.org/title/$encoded/title,author,linecount,lines")
                .header("User-Agent", "OmniTrack/1.0")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withGeneralPacing null
                val arr = json.parseToJsonElement(resp.body?.string().orEmpty()).arr()
                    ?: return@withGeneralPacing null
                val best = arr.firstOrNull { el ->
                    val o = el.obj() ?: return@firstOrNull false
                    val pAuthor = o["author"].str().orEmpty()
                    authorHint.isNullOrBlank() ||
                        authorHint.split(",").first().trim().lowercase() in pAuthor.lowercase()
                }.obj() ?: arr.firstOrNull().obj() ?: return@withGeneralPacing null

                val pTitle = best["title"].str() ?: return@withGeneralPacing null
                val pAuthor = best["author"].str()
                val lc = best["linecount"].str()?.toIntOrNull()
                val sampleLines = best["lines"].arr().orEmpty().mapNotNull { it.str() }.take(28)
                PoetryDbPoem(
                    title = pTitle,
                    author = pAuthor,
                    lineCount = lc,
                    lines = sampleLines
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun fetchRedditBookDiscussions(title: String): List<DiscussionComment> = NetworkPacer.withGeneralPacing {
        try {
            val clean = title.trim()
            if (clean.isBlank()) return@withGeneralPacing emptyList()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val req = Request.Builder()
                .url("https://www.reddit.com/r/books+literature+poetry/search.json?q=$encoded&restrict_sr=1&sort=top&limit=8")
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
                    val sub = d["subreddit_name_prefixed"].str() ?: "r/books"
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
