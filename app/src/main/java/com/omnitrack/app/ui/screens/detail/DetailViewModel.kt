package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.MediaEpisode
import com.omnitrack.app.data.model.MediaSeason
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.omnitrack.app.ui.screens.detail.components.CastMember
import com.omnitrack.app.ui.screens.detail.components.CommunityReview
import com.omnitrack.app.ui.screens.detail.components.MediaTrailer
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import okhttp3.Request

data class DetailUiState(
    val item: MediaItemEntity? = null,
    val seasons: List<Season> = emptyList(),
    val realSeasons: List<MediaSeason> = emptyList(),
    val realEpisodes: List<MediaEpisode> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
    val recommendations: List<RecommendationItem> = emptyList(),
    val reviews: List<CommunityReview> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val trailers: List<MediaTrailer> = emptyList(),
    val isPreviewMode: Boolean = false,
    val isLoading: Boolean = true,
    val hasPreviousEntry: Boolean = false,
    val hasNextEntry: Boolean = false
)

private data class PreviewAndAux(
    val previewItem: MediaItemEntity?,
    val recommendations: List<RecommendationItem>,
    val reviews: List<CommunityReview>,
    val cast: List<CastMember>,
    val trailers: List<MediaTrailer>,
    val realSeasons: List<MediaSeason>,
    val realEpisodes: List<MediaEpisode>,
    val selectedSeasonNumber: Int
)

class DetailViewModel(
    private val repository: MediaRepository,
    mediaId: Long,
    private val enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
    private val preferenceManager: PreferenceManager? = null
) : ViewModel() {

    private val _mediaId = MutableStateFlow(mediaId)
    private val _previewItem = MutableStateFlow<MediaItemEntity?>(null)
    private val _recommendations = MutableStateFlow<List<RecommendationItem>>(emptyList())
    private val _reviews = MutableStateFlow<List<CommunityReview>>(emptyList())
    private val _cast = MutableStateFlow<List<CastMember>>(emptyList())
    private val _trailers = MutableStateFlow<List<MediaTrailer>>(emptyList())
    private val _realSeasons = MutableStateFlow<List<MediaSeason>>(emptyList())
    private val _realEpisodes = MutableStateFlow<List<MediaEpisode>>(emptyList())
    private val _selectedSeasonNumber = MutableStateFlow(1)
    private val enrichedIds = mutableSetOf<Long>()

    init {
        if (mediaId == -1L) {
            val ephemeral = EphemeralPreviewHolder.previewItem
            if (ephemeral != null) {
                _previewItem.value = ephemeral
                loadRecommendationsAndReviews(ephemeral)
            }
        }
    }

    private fun maybeEnrich(item: MediaItemEntity, force: Boolean = false) {
        val hasStaleRatings = item.imdbScore == 6.7f || (item.imdbScore != null && item.traktScore == null)
        if (!force && !hasStaleRatings && !enrichedIds.add(item.id)) return
        enrichedIds.add(item.id)
        viewModelScope.launch(Dispatchers.IO) {
            val fanartKey = preferenceManager?.fanartApiKey?.firstOrNull()?.ifBlank { null }
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val mdbKey = preferenceManager?.mdblistApiKey?.firstOrNull()?.ifBlank { null }

            val enrichment = enrichmentRepository.enrich(
                item = item,
                fanartApiKey = fanartKey,
                tmdbApiKey = tmdbKey,
                mdblistApiKey = mdbKey
            )
            val updated = item.copy(
                clearartUrl = enrichment.clearartUrl ?: item.clearartUrl,
                communityScore = enrichment.communityScore ?: item.communityScore,
                imdbScore = enrichment.imdbScore ?: item.imdbScore,
                rtScore = enrichment.rtScore ?: item.rtScore,
                metacriticScore = enrichment.metacriticScore ?: item.metacriticScore,
                traktScore = enrichment.traktScore ?: item.traktScore,
                audienceScore = enrichment.audienceScore ?: item.audienceScore,
                releaseYear = enrichment.releaseYear ?: item.releaseYear,
                trailerYoutubeKey = enrichment.trailerYoutubeKey ?: item.trailerYoutubeKey,
                studioOrNetwork = enrichment.studioOrNetwork ?: item.studioOrNetwork,
                runtimeMinutes = enrichment.runtimeMinutes ?: item.runtimeMinutes,
                contentRating = enrichment.contentRating ?: item.contentRating,
                airStatus = enrichment.airStatus ?: item.airStatus,
                totalProgress = if (item.totalProgress == null || item.totalProgress == 0.0) enrichment.totalEpisodes?.toDouble() ?: item.totalProgress else item.totalProgress
            )
            repository.updateMedia(updated)
            if (enrichment.videos.isNotEmpty()) {
                _trailers.value = enrichment.videos
            }
            loadRecommendationsAndReviews(updated)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState> = combine(
        listOf(
            _previewItem,
            _recommendations,
            _reviews,
            _cast,
            _trailers,
            _realSeasons,
            _realEpisodes,
            _selectedSeasonNumber
        )
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        PreviewAndAux(
            previewItem = args[0] as? MediaItemEntity,
            recommendations = args[1] as List<RecommendationItem>,
            reviews = args[2] as List<CommunityReview>,
            cast = args[3] as List<CastMember>,
            trailers = args[4] as List<MediaTrailer>,
            realSeasons = args[5] as List<MediaSeason>,
            realEpisodes = args[6] as List<MediaEpisode>,
            selectedSeasonNumber = args[7] as Int
        )
    }.flatMapLatest { aux ->
        if (aux.previewItem != null) {
            flowOf(
                DetailUiState(
                    item = aux.previewItem,
                    seasons = emptyList(),
                    realSeasons = aux.realSeasons,
                    realEpisodes = aux.realEpisodes,
                    selectedSeasonNumber = aux.selectedSeasonNumber,
                    watchedEpisodes = emptySet(),
                    recommendations = aux.recommendations,
                    reviews = aux.reviews,
                    cast = aux.cast,
                    trailers = aux.trailers,
                    isPreviewMode = true,
                    isLoading = false,
                    hasPreviousEntry = false,
                    hasNextEntry = false
                )
            )
        } else {
            _mediaId.flatMapLatest { currentId ->
                repository.getMediaById(currentId).flatMapLatest { currentItem ->
                    if (currentItem != null) {
                        maybeEnrich(currentItem)
                        combine(
                            repository.getAllMedia(),
                            repository.getEpisodeProgress(currentItem.id)
                        ) { allItems, progressList ->
                            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
                            val siblings = if (sameType.size > 1) sameType else allItems
                            val watchedSet = progressList.filter { it.isWatched }
                                .map { it.seasonNumber to it.episodeNumber }.toSet()
                            DetailUiState(
                                item = currentItem,
                                seasons = generateMockSeasons(currentItem),
                                realSeasons = aux.realSeasons,
                                realEpisodes = aux.realEpisodes,
                                selectedSeasonNumber = aux.selectedSeasonNumber,
                                watchedEpisodes = watchedSet,
                                recommendations = aux.recommendations,
                                reviews = aux.reviews,
                                cast = aux.cast,
                                trailers = if (aux.trailers.isEmpty() && currentItem.trailerYoutubeKey != null) {
                                    listOf(MediaTrailer("Official Trailer", currentItem.trailerYoutubeKey))
                                } else aux.trailers,
                                isPreviewMode = false,
                                isLoading = false,
                                hasPreviousEntry = siblings.size > 1,
                                hasNextEntry = siblings.size > 1
                            )
                        }
                    } else {
                        flowOf(DetailUiState(isLoading = false))
                    }
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailUiState()
    )

    fun openPreview(rec: RecommendationItem) {
        val previewEntity = MediaItemEntity(
            id = -1L,
            title = rec.title,
            coverUrl = rec.coverUrl,
            bannerUrl = rec.bannerUrl,
            mediaType = rec.mediaType,
            synopsis = rec.synopsis,
            communityScore = rec.rating,
            tmdbId = rec.tmdbId,
            anilistId = rec.anilistId,
            malId = rec.malId,
            status = TrackingStatus.PLAN_TO_TRACK
        )
        _previewItem.value = previewEntity
    }

    fun closePreview() {
        _previewItem.value = null
        EphemeralPreviewHolder.previewItem = null
    }

    fun addToLibrary(item: MediaItemEntity) {
        viewModelScope.launch {
            val toInsert = item.copy(id = 0L)
            val newId = repository.insertMedia(toInsert)
            EphemeralPreviewHolder.previewItem = null
            _previewItem.value = null
            _mediaId.value = newId
        }
    }

    private fun loadRecommendationsAndReviews(item: MediaItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val recs = mutableListOf<RecommendationItem>()
            val revs = mutableListOf<CommunityReview>()
            val castMembers = mutableListOf<CastMember>()
            val trailerList = mutableListOf<MediaTrailer>()

            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"

            if (item.tmdbId != null) {
                val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
                val client = OkHttpClient()

                // Recommendations
                try {
                    val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/recommendations?api_key=$tmdbKey"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val results = root["results"]?.jsonArray
                                if (results != null) {
                                    for (elem in results.take(10)) {
                                        val obj = elem.jsonObject
                                        val title = obj["title"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content ?: continue
                                        val poster = obj["poster_path"]?.jsonPrimitive?.content ?: continue
                                        val rating = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                                        val year = (obj["release_date"]?.jsonPrimitive?.content ?: obj["first_air_date"]?.jsonPrimitive?.content)?.take(4)
                                        val backdrop = obj["backdrop_path"]?.jsonPrimitive?.content
                                        val synopsis = obj["overview"]?.jsonPrimitive?.content
                                        val recId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()

                                        recs.add(
                                            RecommendationItem(
                                                title = title,
                                                coverUrl = "https://image.tmdb.org/t/p/w500$poster",
                                                releaseYear = year,
                                                rating = rating,
                                                mediaType = item.mediaType,
                                                synopsis = synopsis,
                                                tmdbId = recId,
                                                bannerUrl = backdrop?.let { "https://image.tmdb.org/t/p/original$it" }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Credits (Cast)
                try {
                    val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/credits?api_key=$tmdbKey"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val castArray = root["cast"]?.jsonArray
                                if (castArray != null) {
                                    for (elem in castArray.take(15)) {
                                        val obj = elem.jsonObject
                                        val name = obj["name"]?.jsonPrimitive?.content ?: continue
                                        val character = obj["character"]?.jsonPrimitive?.content
                                        val profilePath = obj["profile_path"]?.jsonPrimitive?.content
                                        val personId = obj["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                        castMembers.add(
                                            CastMember(
                                                name = name,
                                                character = character,
                                                avatarUrl = profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
                                                tmdbPersonId = personId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Videos (Trailers)
                try {
                    val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/videos?api_key=$tmdbKey"
                    val req = Request.Builder().url(url).get().build()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val results = root["results"]?.jsonArray
                                if (results != null) {
                                    for (elem in results) {
                                        val obj = elem.jsonObject
                                        val site = obj["site"]?.jsonPrimitive?.content ?: ""
                                        val key = obj["key"]?.jsonPrimitive?.content ?: continue
                                        val title = obj["name"]?.jsonPrimitive?.content ?: "Official Trailer"
                                        val typeStr = obj["type"]?.jsonPrimitive?.content ?: "Trailer"
                                        if (site.equals("YouTube", ignoreCase = true)) {
                                            trailerList.add(MediaTrailer(title = title, youtubeKey = key, type = typeStr))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // 1:1 Nuvio Trakt Community Reviews (up to 100 comments with likes, ratings, dates)
                try {
                    val searchType = if (type == "movie") "movie" else "show"
                    val traktApiKey = com.omnitrack.app.data.sync.TrackerAccountVerifier.TRAKT_CLIENT_ID
                    val traktSearchUrl = "https://api.trakt.tv/search/tmdb/${item.tmdbId}?type=$searchType"
                    val traktSearchReq = Request.Builder()
                        .url(traktSearchUrl)
                        .header("Content-Type", "application/json")
                        .header("trakt-api-version", "2")
                        .header("trakt-api-key", traktApiKey)
                        .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                        .get()
                        .build()

                    val traktSlug: String? = client.newCall(traktSearchReq).execute().use { searchResp ->
                        if (searchResp.isSuccessful) {
                            val searchBody = searchResp.body?.string().orEmpty()
                            val searchJson = Json { ignoreUnknownKeys = true }.parseToJsonElement(searchBody).jsonArray
                            val firstItem = searchJson.firstOrNull()?.jsonObject
                            val mediaObj = firstItem?.get(if (type == "movie") "movie" else "show")?.jsonObject
                            val ids = mediaObj?.get("ids")?.jsonObject
                            ids?.get("slug")?.jsonPrimitive?.content ?: ids?.get("trakt")?.jsonPrimitive?.content
                        } else null
                    }

                    val commentsEndpointType = if (type == "movie") "movies" else "shows"
                    val targetSlug = traktSlug ?: item.tmdbId?.toString()
                    if (targetSlug != null) {
                        val commentsUrl = "https://api.trakt.tv/$commentsEndpointType/$targetSlug/comments/likes?page=1&limit=100"
                        val commentsReq = Request.Builder()
                            .url(commentsUrl)
                            .header("Content-Type", "application/json")
                            .header("trakt-api-version", "2")
                            .header("trakt-api-key", traktApiKey)
                            .header("User-Agent", "OmniTrack/1.0.0 (Android)")
                            .get()
                            .build()

                        client.newCall(commentsReq).execute().use { commentsResp ->
                            if (commentsResp.isSuccessful) {
                                val commentsBody = commentsResp.body?.string().orEmpty()
                                val commentsArray = Json { ignoreUnknownKeys = true }.parseToJsonElement(commentsBody).jsonArray
                                for (elem in commentsArray) {
                                    val commentObj = elem.jsonObject
                                    val commentText = commentObj["comment"]?.jsonPrimitive?.content ?: continue
                                    val userObj = commentObj["user"]?.jsonObject
                                    val authorName = userObj?.get("name")?.jsonPrimitive?.content
                                        ?: userObj?.get("username")?.jsonPrimitive?.content
                                        ?: "Trakt Member"
                                    val userRating = commentObj["user_rating"]?.jsonPrimitive?.content?.toFloatOrNull()
                                    val likes = commentObj["likes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                                    val spoiler = commentObj["spoiler"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                                    val reviewFlag = commentObj["review"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                                        ?: (commentText.length > 200)
                                    val date = commentObj["created_at"]?.jsonPrimitive?.content?.take(10)

                                    val sanitized = sanitizeCommunityComment(commentText)
                                    if (sanitized.isNotBlank()) {
                                        revs.add(
                                            CommunityReview(
                                                author = authorName,
                                                source = "Trakt",
                                                rating = userRating,
                                                comment = sanitized,
                                                date = date,
                                                hasSpoiler = spoiler,
                                                isReview = reviewFlag,
                                                likes = likes
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                // Fallback to TMDb Community Reviews if Trakt is empty
                if (revs.isEmpty()) {
                    try {
                        val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/reviews?api_key=$tmdbKey"
                        val req = Request.Builder().url(url).get().build()
                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (body != null) {
                                    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    val results = root["results"]?.jsonArray
                                    if (results != null) {
                                        for (elem in results.take(15)) {
                                            val obj = elem.jsonObject
                                            val author = obj["author"]?.jsonPrimitive?.content ?: "Community Member"
                                            val content = obj["content"]?.jsonPrimitive?.content ?: continue
                                            val rating = obj["author_details"]?.jsonObject?.get("rating")?.jsonPrimitive?.content?.toFloatOrNull()
                                            val hasSpoiler = content.contains("[spoiler]", ignoreCase = true) || content.contains("spoiler alert", ignoreCase = true)
                                            revs.add(
                                                CommunityReview(
                                                    author = author,
                                                    source = "TMDb Community",
                                                    rating = rating,
                                                    comment = sanitizeCommunityComment(content),
                                                    hasSpoiler = hasSpoiler,
                                                    isReview = content.length > 150
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Keyless AniList / Jikan Recommendations, Cast & Reviews for Anime / Manga
            if (item.anilistId != null || item.malId != null) {
                if (item.anilistId != null) {
                    try {
                        val query = """
                            query (${'$'}id: Int) {
                                Media(id: ${'$'}id) {
                                    recommendations(perPage: 10, sort: RATING_DESC) {
                                        nodes {
                                            mediaRecommendation {
                                                id
                                                idMal
                                                title {
                                                    userPreferred
                                                }
                                                coverImage {
                                                    extraLarge
                                                    large
                                                }
                                                bannerImage
                                                description
                                                averageScore
                                                startDate {
                                                    year
                                                }
                                            }
                                        }
                                    }
                                    characters(sort: ROLE, perPage: 12) {
                                        edges {
                                            role
                                            node {
                                                name { userPreferred }
                                                image { medium }
                                            }
                                        }
                                    }
                                    reviews(perPage: 6, sort: RATING_DESC) {
                                        nodes {
                                            user {
                                                name
                                            }
                                            summary
                                            body
                                            score
                                        }
                                    }
                                }
                            }
                        """.trimIndent()

                        val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                        val bodyJson = """{"query":"$escapedQuery","variables":{"id":${item.anilistId}}}"""

                        val req = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(bodyJson.toRequestBody("application/json".toMediaType()))
                            .build()

                        val client = OkHttpClient()
                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (body != null) {
                                    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    val mediaObj = root["data"]?.jsonObject?.get("Media")?.jsonObject

                                    // AniList Characters (Cast)
                                    val charEdges = mediaObj?.get("characters")?.jsonObject?.get("edges")?.jsonArray
                                    if (charEdges != null && castMembers.isEmpty()) {
                                        for (elem in charEdges) {
                                            val edgeObj = elem.jsonObject
                                            val node = edgeObj["node"]?.jsonObject
                                            val charName = node?.get("name")?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content ?: continue
                                            val charImg = node["image"]?.jsonObject?.get("medium")?.jsonPrimitive?.content
                                            val role = edgeObj["role"]?.jsonPrimitive?.content
                                            castMembers.add(
                                                CastMember(
                                                    name = charName,
                                                    character = role,
                                                    avatarUrl = charImg
                                                )
                                            )
                                        }
                                    }

                                    // AniList Recs
                                    val recNodes = mediaObj?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray
                                    if (recNodes != null) {
                                        for (elem in recNodes) {
                                            val recMedia = elem.jsonObject["mediaRecommendation"]?.jsonObject ?: continue
                                            val title = recMedia["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content ?: continue
                                            val cover = recMedia["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                                                ?: recMedia["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: continue
                                            val banner = recMedia["bannerImage"]?.jsonPrimitive?.content
                                            val synopsis = recMedia["description"]?.jsonPrimitive?.content?.replace(Regex("<[^>]*>"), "")
                                            val rating = recMedia["averageScore"]?.jsonPrimitive?.content?.toFloatOrNull()?.div(10f)
                                            val year = recMedia["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
                                            val alId = recMedia["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                            val mId = recMedia["idMal"]?.jsonPrimitive?.content?.toLongOrNull()

                                            recs.add(
                                                RecommendationItem(
                                                    title = title,
                                                    coverUrl = cover,
                                                    bannerUrl = banner,
                                                    releaseYear = year,
                                                    rating = rating,
                                                    mediaType = item.mediaType,
                                                    synopsis = synopsis,
                                                    anilistId = alId,
                                                    malId = mId
                                                )
                                            )
                                        }
                                    }

                                    // AniList Reviews
                                    val reviewNodes = mediaObj?.get("reviews")?.jsonObject?.get("nodes")?.jsonArray
                                    if (reviewNodes != null) {
                                        for (elem in reviewNodes) {
                                            val revObj = elem.jsonObject
                                            val author = revObj["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "AniList User"
                                            val summary = revObj["summary"]?.jsonPrimitive?.content ?: ""
                                            val reviewBody = revObj["body"]?.jsonPrimitive?.content ?: ""
                                            val score = revObj["score"]?.jsonPrimitive?.content?.toFloatOrNull()?.div(10f)
                                            val text = if (summary.isNotBlank()) "$summary\n\n$reviewBody" else reviewBody
                                            if (text.isNotBlank()) {
                                                revs.add(
                                                    CommunityReview(
                                                        author = author,
                                                        source = "AniList",
                                                        rating = score,
                                                        comment = text.replace(Regex("<[^>]*>"), ""),
                                                        isReview = true
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            _recommendations.value = recs.distinctBy { it.title }
            _reviews.value = revs.distinctBy { it.author + it.source }
            _cast.value = castMembers.distinctBy { it.name }
            _trailers.value = trailerList.distinctBy { it.youtubeKey }

            if (item.tmdbId != null && (item.mediaType == com.omnitrack.app.data.model.MediaType.SHOW || item.mediaType == com.omnitrack.app.data.model.MediaType.ANIME)) {
                try {
                    val seasons = enrichmentRepository.fetchTvSeasons(item.tmdbId, tmdbKey)
                    if (seasons.isNotEmpty()) {
                        _realSeasons.value = seasons
                        val firstSeason = seasons.firstOrNull { it.seasonNumber > 0 } ?: seasons.first()
                        _selectedSeasonNumber.value = firstSeason.seasonNumber
                        val eps = enrichmentRepository.fetchTvSeasonEpisodes(item.tmdbId, firstSeason.seasonNumber, tmdbKey)
                        _realEpisodes.value = eps
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _selectedSeasonNumber.value = seasonNumber
        viewModelScope.launch(Dispatchers.IO) {
            val item = _previewItem.value ?: repository.getMediaById(_mediaId.value).firstOrNull()
            val tmdbId = item?.tmdbId ?: return@launch
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null } ?: "844dba0bfd8f3a4f3799f6130ef9e335"
            val eps = enrichmentRepository.fetchTvSeasonEpisodes(tmdbId, seasonNumber, tmdbKey)
            _realEpisodes.value = eps
        }
    }

    fun toggleEpisodeWatched(episode: MediaEpisode) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val isCurrentlyWatched = currentProgress.any { it.seasonNumber == episode.seasonNumber && it.episodeNumber == episode.episodeNumber && it.isWatched }
            repository.setEpisodeWatched(mediaId, episode.seasonNumber, episode.episodeNumber, !isCurrentlyWatched)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val watchedCount = updatedProgress.count { it.isWatched }
            repository.updateProgress(mediaId, watchedCount.toDouble())
        }
    }

    fun markPreviousEpisodesWatched(episode: MediaEpisode) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val epsToMark = _realEpisodes.value.filter { it.episodeNumber <= episode.episodeNumber }
            val pairs = epsToMark.map { it.seasonNumber to it.episodeNumber }
            repository.setMultipleEpisodesWatched(mediaId, pairs, true)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val watchedCount = updatedProgress.count { it.isWatched }
            repository.updateProgress(mediaId, watchedCount.toDouble())
        }
    }

    fun toggleSeasonWatched(seasonNumber: Int) {
        val mediaId = _mediaId.value
        if (mediaId <= 0) return
        viewModelScope.launch(Dispatchers.IO) {
            val currentProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val seasonEps = _realEpisodes.value.filter { it.seasonNumber == seasonNumber }
            val allWatched = seasonEps.isNotEmpty() && seasonEps.all { ep ->
                currentProgress.any { it.seasonNumber == ep.seasonNumber && it.episodeNumber == ep.episodeNumber && it.isWatched }
            }
            val pairs = seasonEps.map { it.seasonNumber to it.episodeNumber }
            repository.setMultipleEpisodesWatched(mediaId, pairs, !allWatched)
            val updatedProgress = repository.getEpisodeProgress(mediaId).firstOrNull().orEmpty()
            val watchedCount = updatedProgress.count { it.isWatched }
            repository.updateProgress(mediaId, watchedCount.toDouble())
        }
    }

    fun navigateToPrevious(): Boolean {
        viewModelScope.launch {
            val currentId = _mediaId.value
            val allItems = repository.getAllMedia().firstOrNull() ?: return@launch
            val currentItem = allItems.find { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val pool = if (sameType.size > 1) sameType else allItems
            val currentIndex = pool.indexOfFirst { it.id == currentId }
            if (currentIndex > 0) {
                val prevItem = pool[currentIndex - 1]
                _mediaId.value = prevItem.id
                _previewItem.value = null
                loadRecommendationsAndReviews(prevItem)
            }
        }
        return true
    }

    fun navigateToNext(): Boolean {
        viewModelScope.launch {
            val currentId = _mediaId.value
            val allItems = repository.getAllMedia().firstOrNull() ?: return@launch
            val currentItem = allItems.find { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val pool = if (sameType.size > 1) sameType else allItems
            val currentIndex = pool.indexOfFirst { it.id == currentId }
            if (currentIndex >= 0 && currentIndex < pool.size - 1) {
                val nextItem = pool[currentIndex + 1]
                _mediaId.value = nextItem.id
                _previewItem.value = null
                loadRecommendationsAndReviews(nextItem)
            }
        }
        return true
    }

    fun updateProgress(newProgress: Double) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            val max = item.totalProgress ?: Double.MAX_VALUE
            val clamped = newProgress.coerceIn(0.0, max)
            val updated = item.copy(currentProgress = clamped)
            repository.updateMedia(updated)
        }
    }

    fun updatePlaytime(hours: Double) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(playtimeHours = hours.coerceAtLeast(0.0)))
        }
    }

    fun togglePlatform(platform: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            val current = item.platforms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableSet()
                ?: item.platform?.let { mutableSetOf(it) }
                ?: mutableSetOf()
            if (current.contains(platform)) {
                current.remove(platform)
            } else {
                current.add(platform)
            }
            val joined = current.joinToString(",")
            repository.updateMedia(item.copy(platforms = joined, platform = current.firstOrNull()))
        }
    }

    fun updateCover(newCoverUrl: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(
                item.copy(
                    coverUrl = newCoverUrl,
                    customCoverPath = null
                )
            )
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(
                item.copy(
                    bannerUrl = newBannerUrl,
                    customBannerPath = null
                )
            )
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank() && item.customCoverPath != path) {
                try { java.io.File(item.customCoverPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customCoverPath = path))
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank() && item.customBannerPath != path) {
                try { java.io.File(item.customBannerPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customBannerPath = path))
        }
    }

    fun resetCover(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customCoverPath.isNullOrBlank()) {
                try { java.io.File(item.customCoverPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(customCoverPath = null))
        }
    }

    fun resetBanner(context: android.content.Context? = null) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            if (!item.customBannerPath.isNullOrBlank()) {
                try { java.io.File(item.customBannerPath).delete() } catch (_: Exception) {}
            }
            repository.updateMedia(item.copy(bannerUrl = null, customBannerPath = null))
        }
    }

    fun toggleDisableBanner() {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            val newRole = if (item.artworkRole == "DISABLE_BANNER") "DEFAULT" else "DISABLE_BANNER"
            repository.updateMedia(item.copy(artworkRole = newRole))
        }
    }

    fun updateArtworkRole(role: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(artworkRole = role))
        }
    }

    fun updateRating(rating: Float?) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(userRating = rating))
        }
    }

    fun updateStatus(status: TrackingStatus) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(status = status))
        }
    }

    fun updatePlatform(platform: String) {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.updateMedia(item.copy(platform = platform))
        }
    }

    fun updateMediaItem(item: MediaItemEntity) {
        viewModelScope.launch {
            repository.updateMedia(item)
        }
    }

    fun deleteItem() {
        viewModelScope.launch {
            val item = uiState.value.item ?: return@launch
            repository.deleteMedia(item)
        }
    }

    /**
     * Generate mock season/episode data from the item's progress
     */
    private fun generateMockSeasons(item: MediaItemEntity): List<Season> {
        val total = item.totalProgress?.toInt() ?: return emptyList()
        val watched = item.currentProgress.toInt()

        return if (total <= 26) {
            listOf(
                Season(
                    name = "Season 1",
                    episodes = (1..total).map { ep ->
                        Episode(
                            number = ep,
                            title = "Episode $ep",
                            isWatched = ep <= watched
                        )
                    }
                )
            )
        } else {
            val seasons = (total + 11) / 12
            (1..seasons).map { s ->
                val start = (s - 1) * 12 + 1
                val end = minOf(s * 12, total)
                Season(
                    name = "Season $s",
                    episodes = (start..end).map { ep ->
                        Episode(
                            number = ep,
                            title = "Episode $ep",
                            isWatched = ep <= watched
                        )
                    }
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: MediaRepository,
            mediaId: Long,
            enrichmentRepository: EnrichmentRepository = EnrichmentRepository(),
            preferenceManager: PreferenceManager? = null
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, mediaId, enrichmentRepository, preferenceManager) as T
                }
            }
        }
    }
}

fun sanitizeCommunityComment(raw: String): String {
    return raw
        .replace(Regex("""(?is)\[spoiler\].*?\[/spoiler\]"""), "")
        .replace(Regex("""(?i)\[/?spoiler\]"""), "")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]*>"""), "")
        .replace(Regex("""(?m)^#{1,6}\s*"""), "")
        .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1")
        .replace(Regex("""(?s)\*{3}(.*?)\*{3}"""), "$1")
        .replace(Regex("""(?s)_{3}(.*?)_{3}"""), "$1")
        .replace(Regex("""(?s)\*{2}(.*?)\*{2}"""), "$1")
        .replace(Regex("""(?s)_{2}(.*?)_{2}"""), "$1")
        .replace(Regex("""(?s)\*(.*?)\*"""), "$1")
        .replace(Regex("""(?s)_(.*?)_"""), "$1")
        .replace(Regex("""(?s)~~(.*?)~~"""), "$1")
        .replace(Regex("""_{2,}"""), "")
        .replace(Regex("""\*{2,}"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
