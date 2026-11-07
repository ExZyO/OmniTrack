package com.omnitrack.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.TrackingStatus
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.data.repository.MediaRepository
import com.omnitrack.app.ui.components.Episode
import com.omnitrack.app.ui.components.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.omnitrack.app.ui.screens.detail.components.CommunityReview
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

data class DetailUiState(
    val item: MediaItemEntity? = null,
    val seasons: List<Season> = emptyList(),
    val recommendations: List<RecommendationItem> = emptyList(),
    val reviews: List<CommunityReview> = emptyList(),
    val isPreviewMode: Boolean = false,
    val isLoading: Boolean = true,
    val hasPreviousEntry: Boolean = false,
    val hasNextEntry: Boolean = false
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
    private val enrichedIds = mutableSetOf<Long>()

    private fun maybeEnrich(item: MediaItemEntity) {
        if (!enrichedIds.add(item.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val fanartKey = preferenceManager?.fanartApiKey?.firstOrNull()?.ifBlank { null }
            val tmdbKey = preferenceManager?.tmdbApiKey?.firstOrNull()?.ifBlank { null }
            val mdbKey = preferenceManager?.mdblistApiKey?.firstOrNull()?.ifBlank { null }

            val enrichment = enrichmentRepository.enrich(
                item = item,
                fanartApiKey = fanartKey,
                tmdbApiKey = tmdbKey,
                mdblistApiKey = mdbKey
            )
            repository.updateMedia(
                item.copy(
                    clearartUrl = enrichment.clearartUrl ?: item.clearartUrl,
                    communityScore = enrichment.communityScore ?: item.communityScore,
                    imdbScore = enrichment.imdbScore ?: item.imdbScore,
                    rtScore = enrichment.rtScore ?: item.rtScore,
                    metacriticScore = enrichment.metacriticScore ?: item.metacriticScore,
                    trailerYoutubeKey = enrichment.trailerYoutubeKey ?: item.trailerYoutubeKey,
                    studioOrNetwork = enrichment.studioOrNetwork ?: item.studioOrNetwork,
                    runtimeMinutes = enrichment.runtimeMinutes ?: item.runtimeMinutes,
                    contentRating = enrichment.contentRating ?: item.contentRating,
                    airStatus = enrichment.airStatus ?: item.airStatus
                )
            )
            loadRecommendationsAndReviews(item)
        }
    }

    val uiState: StateFlow<DetailUiState> = kotlinx.coroutines.flow.combine(
        _mediaId,
        _previewItem,
        _recommendations,
        _reviews
    ) { currentId, previewItem, recs, reviews ->
        if (previewItem != null) {
            DetailUiState(
                item = previewItem,
                seasons = emptyList(),
                recommendations = recs,
                reviews = reviews,
                isPreviewMode = true,
                isLoading = false,
                hasPreviousEntry = false,
                hasNextEntry = false
            )
        } else {
            val currentItem = repository.getMediaById(currentId).firstOrNull()
            if (currentItem != null) {
                maybeEnrich(currentItem)
                val allItems = repository.getAllMedia().firstOrNull() ?: emptyList()
                val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
                val siblings = if (sameType.size > 1) sameType else allItems
                DetailUiState(
                    item = currentItem,
                    seasons = generateMockSeasons(currentItem),
                    recommendations = recs,
                    reviews = reviews,
                    isPreviewMode = false,
                    isLoading = false,
                    hasPreviousEntry = siblings.size > 1,
                    hasNextEntry = siblings.size > 1
                )
            } else {
                DetailUiState(isLoading = false)
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
    }

    fun addToLibrary(item: MediaItemEntity) {
        viewModelScope.launch {
            val toInsert = item.copy(id = 0L)
            val newId = repository.insertMedia(toInsert)
            _previewItem.value = null
            _mediaId.value = newId
        }
    }

    private fun loadRecommendationsAndReviews(item: MediaItemEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val recs = mutableListOf<RecommendationItem>()
            val revs = mutableListOf<CommunityReview>()

            if (item.tmdbId != null) {
                try {
                    val apiKey = "2c823019808d98d415f33cf2382e70e3"
                    val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
                    val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/recommendations?api_key=$apiKey"
                    val req = Request.Builder().url(url).get().build()
                    val client = OkHttpClient()
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

                try {
                    val apiKey = "2c823019808d98d415f33cf2382e70e3"
                    val type = if (item.mediaType == com.omnitrack.app.data.model.MediaType.MOVIE) "movie" else "tv"
                    val url = "https://api.themoviedb.org/3/$type/${item.tmdbId}/reviews?api_key=$apiKey"
                    val req = Request.Builder().url(url).get().build()
                    val client = OkHttpClient()
                    client.newCall(req).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val results = root["results"]?.jsonArray
                                if (results != null) {
                                    for (elem in results.take(6)) {
                                        val obj = elem.jsonObject
                                        val author = obj["author"]?.jsonPrimitive?.content ?: "Community Member"
                                        val content = obj["content"]?.jsonPrimitive?.content ?: continue
                                        val rating = obj["author_details"]?.jsonObject?.get("rating")?.jsonPrimitive?.content?.toFloatOrNull()
                                        revs.add(
                                            CommunityReview(
                                                author = author,
                                                source = "TMDb Community",
                                                rating = rating,
                                                comment = content
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Keyless AniList / Jikan Recommendations & Reviews for Anime / Manga
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
                                                title { english romaji }
                                                coverImage { large }
                                                bannerImage
                                                averageScore
                                                description
                                                startDate { year }
                                            }
                                        }
                                    }
                                    reviews(perPage: 6, sort: RATING_DESC) {
                                        nodes {
                                            user { name }
                                            score
                                            summary
                                            body
                                        }
                                    }
                                }
                            }
                        """.trimIndent()

                        val payload = kotlinx.serialization.json.buildJsonObject {
                            put("query", query)
                            putJsonObject("variables") {
                                put("id", item.anilistId.toInt())
                            }
                        }

                        val req = Request.Builder()
                            .url("https://graphql.anilist.co")
                            .post(payload.toString().toRequestBody("application/json".toMediaType()))
                            .build()

                        val client = OkHttpClient()
                        client.newCall(req).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string()
                                if (body != null) {
                                    val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    val mediaObj = root["data"]?.jsonObject?.get("Media")?.jsonObject
                                    val recNodes = mediaObj?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray
                                    if (recNodes != null) {
                                        for (elem in recNodes) {
                                            val recMedia = elem.jsonObject["mediaRecommendation"]?.jsonObject ?: continue
                                            val tObj = recMedia["title"]?.jsonObject
                                            val title = tObj?.get("english")?.jsonPrimitive?.content
                                                ?: tObj?.get("romaji")?.jsonPrimitive?.content ?: continue
                                            val cover = recMedia["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: continue
                                            val banner = recMedia["bannerImage"]?.jsonPrimitive?.content
                                            val score = recMedia["averageScore"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { it / 10f }
                                            val year = recMedia["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
                                            val desc = recMedia["description"]?.jsonPrimitive?.content?.replace(Regex("<[^>]*>"), "")
                                            val recAlId = recMedia["id"]?.jsonPrimitive?.content?.toLongOrNull()
                                            val recMalId = recMedia["idMal"]?.jsonPrimitive?.content?.toLongOrNull()

                                            recs.add(
                                                RecommendationItem(
                                                    title = title,
                                                    coverUrl = cover,
                                                    bannerUrl = banner,
                                                    releaseYear = year,
                                                    rating = score,
                                                    mediaType = item.mediaType,
                                                    synopsis = desc,
                                                    anilistId = recAlId,
                                                    malId = recMalId
                                                )
                                            )
                                        }
                                    }

                                    val revNodes = mediaObj?.get("reviews")?.jsonObject?.get("nodes")?.jsonArray
                                    if (revNodes != null) {
                                        for (elem in revNodes) {
                                            val rObj = elem.jsonObject
                                            val author = rObj["user"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "AniList Reviewer"
                                            val summary = rObj["summary"]?.jsonPrimitive?.content
                                            val bodyText = rObj["body"]?.jsonPrimitive?.content?.replace(Regex("<[^>]*>"), "") ?: summary ?: continue
                                            val score = rObj["score"]?.jsonPrimitive?.content?.toFloatOrNull()?.let { it / 10f }

                                            revs.add(
                                                CommunityReview(
                                                    author = author,
                                                    source = "AniList",
                                                    rating = score,
                                                    comment = summary?.let { "$it\n\n$bodyText" } ?: bodyText
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

            _recommendations.value = recs
            _reviews.value = revs
        }
    }

    fun navigateToPrevious(): Boolean {
        val currentId = _mediaId.value
        viewModelScope.launch {
            val allItems = repository.getAllMedia().first()
            val currentItem = allItems.firstOrNull { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val siblings = if (sameType.size > 1) sameType else allItems
            if (siblings.size <= 1) return@launch
            val currentIndex = siblings.indexOfFirst { it.id == currentId }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else siblings.size - 1
            _mediaId.value = siblings[prevIndex].id
        }
        return true
    }

    fun navigateToNext(): Boolean {
        val currentId = _mediaId.value
        viewModelScope.launch {
            val allItems = repository.getAllMedia().first()
            val currentItem = allItems.firstOrNull { it.id == currentId } ?: return@launch
            val sameType = allItems.filter { it.mediaType == currentItem.mediaType }
            val siblings = if (sameType.size > 1) sameType else allItems
            if (siblings.size <= 1) return@launch
            val currentIndex = siblings.indexOfFirst { it.id == currentId }
            val nextIndex = if (currentIndex in 0 until siblings.size - 1) currentIndex + 1 else 0
            _mediaId.value = siblings[nextIndex].id
        }
        return true
    }

    fun updateProgress(newProgress: Double) {
        viewModelScope.launch {
            repository.updateProgress(_mediaId.value, newProgress)
        }
    }

    fun updateCover(newCoverUrl: String) {
        viewModelScope.launch {
            repository.updateCover(_mediaId.value, newCoverUrl)
        }
    }

    fun updateBanner(newBannerUrl: String) {
        viewModelScope.launch {
            repository.updateBanner(_mediaId.value, newBannerUrl)
        }
    }

    fun updateCustomCoverPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            repository.updateCustomCover(_mediaId.value, path)
            if (context != null && path != null) {
                val file = java.io.File(path)
                val loader = coil3.SingletonImageLoader.get(context)
                loader.memoryCache?.remove(coil3.memory.MemoryCache.Key(file.absolutePath))
                loader.diskCache?.remove(file.absolutePath)
            }
        }
    }

    fun updateCustomBannerPath(path: String?, context: android.content.Context? = null) {
        viewModelScope.launch {
            repository.updateCustomBanner(_mediaId.value, path)
            if (context != null && path != null && path != "NONE") {
                val file = java.io.File(path)
                val loader = coil3.SingletonImageLoader.get(context)
                loader.memoryCache?.remove(coil3.memory.MemoryCache.Key(file.absolutePath))
                loader.diskCache?.remove(file.absolutePath)
            }
        }
    }

    fun resetCover(context: android.content.Context? = null) {
        viewModelScope.launch {
            val mediaId = _mediaId.value
            if (context != null) {
                val coverCache = (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache
                    ?: com.omnitrack.app.data.cache.CoverCache(context)
                coverCache.deleteCustomCover(mediaId)
            }
            repository.updateCustomCover(mediaId, null)
        }
    }

    fun resetBanner(context: android.content.Context? = null) {
        viewModelScope.launch {
            val mediaId = _mediaId.value
            if (context != null) {
                val coverCache = (context.applicationContext as? com.omnitrack.app.OmniTrackApplication)?.coverCache
                    ?: com.omnitrack.app.data.cache.CoverCache(context)
                coverCache.deleteCustomBanner(mediaId)
            }
            repository.updateCustomBanner(mediaId, null)
        }
    }

    fun toggleDisableBanner() {
        viewModelScope.launch {
            val currentItem = uiState.value.item ?: return@launch
            val nextPath = if (currentItem.customBannerPath == "NONE") null else "NONE"
            repository.updateCustomBanner(_mediaId.value, nextPath)
        }
    }

    fun updateArtworkRole(role: String) {
        viewModelScope.launch {
            repository.updateArtworkRole(_mediaId.value, role)
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
