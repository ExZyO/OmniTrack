package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ArtworkWaterfallRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchArtworkCandidates(
        title: String,
        mediaType: MediaType
    ): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()

        coroutineScope {
            val deferreds = mutableListOf<kotlinx.coroutines.Deferred<List<ArtworkCandidate>>>()

            when (mediaType) {
                MediaType.ANIME -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = true) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 2) })
                    deferreds.add(async { fetchKitsuCovers(title, isAnime = true) })
                    deferreds.add(async { fetchMangaDexCovers(title) })
                    deferreds.add(async { fetchTmdbArtwork(title, isMovie = false) })
                }
                MediaType.MANGA -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = false) })
                    deferreds.add(async { fetchMangaDexCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 1) })
                    deferreds.add(async { fetchKitsuCovers(title, isAnime = false) })
                    deferreds.add(async { fetchComickCovers(title) })
                }
                MediaType.NOVEL -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = false) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 1) })
                    deferreds.add(async { fetchMangaDexCovers(title) })
                    deferreds.add(async { fetchComickCovers(title) })
                }
                MediaType.SHOW, MediaType.MOVIE -> {
                    deferreds.add(async { fetchTvMazeCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 2) })
                    deferreds.add(async { fetchTmdbArtwork(title, isMovie = (mediaType == MediaType.MOVIE)) })
                }
                MediaType.GAME -> {
                    deferreds.add(async { fetchSteamCovers(title) })
                }
                MediaType.VISUAL_NOVEL -> {
                    deferreds.add(async { fetchVndbCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 4) })
                    deferreds.add(async { fetchAniListCovers(title, isAnime = false) })
                }
                MediaType.ALL -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = true) })
                    deferreds.add(async { fetchTvMazeCovers(title) })
                    deferreds.add(async { fetchSteamCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 1) })
                    deferreds.add(async { fetchTmdbArtwork(title, isMovie = true) })
                }
            }

            val results = deferreds.awaitAll().flatten()
            // Deduplicate by URL and normalize to lossless
            results.distinctBy { LosslessCoverMapper.toLosslessUrl(it.url) }
                .map { it.copy(url = LosslessCoverMapper.toLosslessUrl(it.url)) }
        }
    }

    private fun fetchAniListCovers(title: String, isAnime: Boolean): List<ArtworkCandidate> {
        return try {
            val typeStr = if (isAnime) "ANIME" else "MANGA"
            val query = """
                query (${'$'}search: String, ${'$'}type: MediaType) {
                    Page(page: 1, perPage: 8) {
                        media(search: ${'$'}search, type: ${'$'}type) {
                            id
                            title { romaji english native }
                            coverImage { extraLarge large }
                            bannerImage
                        }
                    }
                }
            """.trimIndent()

            val payload = buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("search", title)
                    put("type", typeStr)
                }
            }

            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val mediaList = parsed["data"]?.jsonObject?.get("Page")?.jsonObject?.get("media")?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                mediaList.forEach { elem ->
                    val item = elem.jsonObject
                    val cover = item["coverImage"]?.jsonObject
                    val url = cover?.get("extraLarge")?.jsonPrimitive?.content
                        ?: cover?.get("large")?.jsonPrimitive?.content
                    val itemTitle = item["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content
                        ?: item["title"]?.jsonObject?.get("english")?.jsonPrimitive?.content
                        ?: title
                    if (url != null) {
                        candidates.add(
                            ArtworkCandidate(
                                url = url,
                                sourceName = "AniList",
                                description = itemTitle,
                                isBanner = false
                            )
                        )
                    }
                    val banner = item["bannerImage"]?.jsonPrimitive?.content
                    if (banner != null) {
                        candidates.add(
                            ArtworkCandidate(
                                url = banner,
                                sourceName = "AniList",
                                description = "$itemTitle (Banner)",
                                isBanner = true
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchMangaDexCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.mangadex.org/manga?title=$encoded&limit=5&includes[]=cover_art")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val data = parsed["data"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                for (item in data) {
                    val mangaId = item.jsonObject["id"]?.jsonPrimitive?.content ?: continue
                    val relationships = item.jsonObject["relationships"]?.jsonArray ?: continue
                    for (rel in relationships) {
                        if (rel.jsonObject["type"]?.jsonPrimitive?.content == "cover_art") {
                            val fileName = rel.jsonObject["attributes"]?.jsonObject?.get("fileName")?.jsonPrimitive?.content
                            if (fileName != null) {
                                candidates.add(
                                    ArtworkCandidate(
                                        url = "https://uploads.mangadex.org/covers/$mangaId/$fileName",
                                        sourceName = "MangaDex",
                                        description = "Official Cover"
                                    )
                                )
                            }
                        }
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchBangumiCovers(title: String, type: Int): List<ArtworkCandidate> {
        return try {
            val payload = buildJsonObject {
                put("keyword", title.trim())
                putJsonObject("filter") {
                    putJsonArray("type") {
                        add(type)
                    }
                }
            }

            val request = Request.Builder()
                .url("https://api.bgm.tv/v0/search/subjects")
                .header("User-Agent", "OmniTrack/1.0.0 (Android; https://github.com/ExZyO/OmniTrack)")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val data = parsed["data"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                data.forEach { elem ->
                    val item = elem.jsonObject
                    val images = item["images"]?.jsonObject
                    val rawUrl = images?.get("large")?.jsonPrimitive?.content
                        ?: images?.get("common")?.jsonPrimitive?.content
                    val url = rawUrl?.let { if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it }
                    val name = item["name_cn"]?.jsonPrimitive?.content
                        ?: item["name"]?.jsonPrimitive?.content
                        ?: title

                    if (url != null) {
                        candidates.add(
                            ArtworkCandidate(
                                url = url,
                                sourceName = "Bangumi",
                                description = name,
                                isBanner = false
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchKitsuCovers(title: String, isAnime: Boolean): List<ArtworkCandidate> {
        return try {
            val endpoint = if (isAnime) "anime" else "manga"
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://kitsu.io/api/edge/$endpoint?filter[text]=$encoded&page[limit]=4")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val data = parsed["data"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                data.forEach { elem ->
                    val attrs = elem.jsonObject["attributes"]?.jsonObject
                    val posterImage = attrs?.get("posterImage")?.jsonObject
                    val url = posterImage?.get("original")?.jsonPrimitive?.content
                        ?: posterImage?.get("large")?.jsonPrimitive?.content
                    val canonicalTitle = attrs?.get("canonicalTitle")?.jsonPrimitive?.content ?: title
                    if (url != null) {
                        candidates.add(
                            ArtworkCandidate(
                                url = url,
                                sourceName = "Kitsu",
                                description = canonicalTitle,
                                isBanner = false
                            )
                        )
                    }
                    val coverImage = attrs?.get("coverImage")?.jsonObject?.get("original")?.jsonPrimitive?.content
                        ?: attrs?.get("coverImage")?.jsonObject?.get("large")?.jsonPrimitive?.content
                    if (coverImage != null) {
                        candidates.add(
                            ArtworkCandidate(
                                url = coverImage,
                                sourceName = "Kitsu",
                                description = "$canonicalTitle (Banner)",
                                isBanner = true
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchTvMazeCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.tvmaze.com/search/shows?q=$encoded")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val list = json.parseToJsonElement(responseStr).jsonArray

                list.mapNotNull { item ->
                    val show = item.jsonObject["show"]?.jsonObject
                    val image = show?.get("image")?.jsonObject
                    val url = image?.get("original")?.jsonPrimitive?.content
                        ?: image?.get("medium")?.jsonPrimitive?.content
                    val showName = show?.get("name")?.jsonPrimitive?.content ?: title

                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "TVMaze",
                            description = showName
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchSteamCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val items = parsed["items"]?.jsonArray ?: return emptyList()

                val candidates = mutableListOf<ArtworkCandidate>()
                items.forEach { elem ->
                    val id = elem.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
                    val name = elem.jsonObject["name"]?.jsonPrimitive?.content ?: title
                    // Steam high-res 600x900 library capsule (cover)
                    val verticalCapsule2xUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900_2x.jpg"
                    val verticalCapsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900.jpg"
                    val headerBannerUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"
                    val heroBannerUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/library_hero.jpg"

                    candidates.add(
                        ArtworkCandidate(
                            url = verticalCapsule2xUrl,
                            sourceName = "Steam",
                            description = "$name (2x HD)",
                            isBanner = false
                        )
                    )
                    candidates.add(
                        ArtworkCandidate(
                            url = verticalCapsuleUrl,
                            sourceName = "Steam",
                            description = name,
                            isBanner = false
                        )
                    )
                    candidates.add(
                        ArtworkCandidate(
                            url = heroBannerUrl,
                            sourceName = "Steam",
                            description = "$name (Hero Banner)",
                            isBanner = true
                        )
                    )
                    candidates.add(
                        ArtworkCandidate(
                            url = headerBannerUrl,
                            sourceName = "Steam",
                            description = "$name (Store Header)",
                            isBanner = true
                        )
                    )
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchVndbCovers(title: String): List<ArtworkCandidate> {
        return try {
            val payload = buildJsonObject {
                put("filters", json.parseToJsonElement("[\"search\", \"=\", \"$title\"]"))
                put("fields", "title, image.url")
            }

            val request = Request.Builder()
                .url("https://api.vndb.org/kana/vn")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val results = parsed["results"]?.jsonArray ?: return emptyList()

                results.mapNotNull { item ->
                    val imageObj = item.jsonObject["image"]?.jsonObject
                    val url = imageObj?.get("url")?.jsonPrimitive?.content
                    val vnTitle = item.jsonObject["title"]?.jsonPrimitive?.content ?: title
                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "VNDB",
                            description = vnTitle
                        )
                    } else null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchTmdbArtwork(title: String, isMovie: Boolean): List<ArtworkCandidate> {
        return try {
            val apiKey = "2c823019808d98d415f33cf2382e70e3" // Standard public TMDb v3 client key
            val cleanTitle = title.replace(Regex("[:\\-_–—]"), " ").replace(Regex("\\s+"), " ").trim()
            val encoded = URLEncoder.encode(cleanTitle.ifBlank { title }, "UTF-8")
            val endpoint = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&query=$encoded"

            val request = Request.Builder().url(url).get().build()
            val candidates = mutableListOf<ArtworkCandidate>()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val results = parsed["results"]?.jsonArray ?: return emptyList()

                for (elem in results.take(4)) {
                    val obj = elem.jsonObject
                    val tmdbId = obj["id"]?.jsonPrimitive?.content
                    val itemTitle = obj["title"]?.jsonPrimitive?.content
                        ?: obj["name"]?.jsonPrimitive?.content
                        ?: title

                    val posterPath = obj["poster_path"]?.jsonPrimitive?.content
                    if (!posterPath.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://image.tmdb.org/t/p/original$posterPath",
                                sourceName = "TMDb",
                                description = itemTitle,
                                isBanner = false
                            )
                        )
                    }
                    val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
                    if (!backdropPath.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://image.tmdb.org/t/p/original$backdropPath",
                                sourceName = "TMDb",
                                description = "$itemTitle (Backdrop)",
                                isBanner = true
                            )
                        )
                    }

                    // Query /images endpoint with include_image_language=en,ja,null for textless & Japanese anime art
                    if (!tmdbId.isNullOrBlank()) {
                        try {
                            val imagesUrl = "https://api.themoviedb.org/3/$endpoint/$tmdbId/images?api_key=$apiKey&include_image_language=en,ja,null"
                            val imgRequest = Request.Builder().url(imagesUrl).get().build()
                            client.newCall(imgRequest).execute().use { imgResponse ->
                                if (imgResponse.isSuccessful) {
                                    val imgBody = imgResponse.body?.string().orEmpty()
                                    val imgObj = json.parseToJsonElement(imgBody).jsonObject
                                    imgObj["posters"]?.jsonArray?.take(6)?.forEach { p ->
                                        val path = p.jsonObject["file_path"]?.jsonPrimitive?.content
                                        if (!path.isNullOrBlank()) {
                                            candidates.add(
                                                ArtworkCandidate(
                                                    url = "https://image.tmdb.org/t/p/original$path",
                                                    sourceName = "TMDb",
                                                    description = "$itemTitle (Poster)",
                                                    isBanner = false
                                                )
                                            )
                                        }
                                    }
                                    imgObj["backdrops"]?.jsonArray?.take(6)?.forEach { b ->
                                        val path = b.jsonObject["file_path"]?.jsonPrimitive?.content
                                        if (!path.isNullOrBlank()) {
                                            candidates.add(
                                                ArtworkCandidate(
                                                    url = "https://image.tmdb.org/t/p/original$path",
                                                    sourceName = "TMDb",
                                                    description = "$itemTitle (Backdrop)",
                                                    isBanner = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
            candidates.distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchComickCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = URLEncoder.encode(title, "UTF-8")
            val url = "https://api.comick.io/v1.0/search?q=$encoded&limit=6"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ExZyO/OmniTrack/1.0 (Android)")
                .header("Referer", "https://comick.io")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val list = json.parseToJsonElement(responseStr).jsonArray
                val candidates = mutableListOf<ArtworkCandidate>()
                for (elem in list) {
                    val obj = elem.jsonObject
                    val comicTitle = obj["title"]?.jsonPrimitive?.content ?: title
                    val b2key = obj["b2key"]?.jsonPrimitive?.content
                    if (!b2key.isNullOrBlank()) {
                        candidates.add(
                            ArtworkCandidate(
                                url = "https://meo.comick.picture/$b2key",
                                sourceName = "Comick",
                                description = comicTitle,
                                isBanner = false
                            )
                        )
                    }
                }
                candidates
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
