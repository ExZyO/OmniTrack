package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
                }
                MediaType.MANGA -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = false) })
                    deferreds.add(async { fetchMangaDexCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 1) })
                    deferreds.add(async { fetchKitsuCovers(title, isAnime = false) })
                }
                MediaType.NOVEL -> {
                    deferreds.add(async { fetchAniListCovers(title, isAnime = false) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 1) })
                    deferreds.add(async { fetchMangaDexCovers(title) })
                }
                MediaType.SHOW, MediaType.MOVIE -> {
                    deferreds.add(async { fetchTvMazeCovers(title) })
                    deferreds.add(async { fetchBangumiCovers(title, type = 2) })
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

                mediaList.mapNotNull { item ->
                    val cover = item.jsonObject["coverImage"]?.jsonObject
                    val url = cover?.get("extraLarge")?.jsonPrimitive?.content
                        ?: cover?.get("large")?.jsonPrimitive?.content
                    val itemTitle = item.jsonObject["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content
                        ?: item.jsonObject["title"]?.jsonObject?.get("english")?.jsonPrimitive?.content
                        ?: title
                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "AniList",
                            description = itemTitle
                        )
                    } else null
                }
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
            val encoded = URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.bgm.tv/search/subject/$encoded?type=$type&responseGroup=small&max_results=5")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val responseStr = response.body?.string() ?: return emptyList()
                val parsed = json.parseToJsonElement(responseStr).jsonObject
                val list = parsed["list"]?.jsonArray ?: return emptyList()

                list.mapNotNull { item ->
                    val images = item.jsonObject["images"]?.jsonObject
                    val url = images?.get("large")?.jsonPrimitive?.content
                        ?: images?.get("common")?.jsonPrimitive?.content
                    val name = item.jsonObject["name_cn"]?.jsonPrimitive?.content
                        ?: item.jsonObject["name"]?.jsonPrimitive?.content

                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "Bangumi",
                            description = name
                        )
                    } else null
                }
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

                data.mapNotNull { item ->
                    val attrs = item.jsonObject["attributes"]?.jsonObject
                    val posterImage = attrs?.get("posterImage")?.jsonObject
                    val url = posterImage?.get("original")?.jsonPrimitive?.content
                        ?: posterImage?.get("large")?.jsonPrimitive?.content
                    val canonicalTitle = attrs?.get("canonicalTitle")?.jsonPrimitive?.content ?: title
                    if (url != null) {
                        ArtworkCandidate(
                            url = url,
                            sourceName = "Kitsu",
                            description = canonicalTitle
                        )
                    } else null
                }
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

                items.mapNotNull { item ->
                    val id = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val name = item.jsonObject["name"]?.jsonPrimitive?.content ?: title
                    // Steam high-res 600x900 library capsule
                    val verticalCapsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$id/library_600x900_2x.jpg"
                    ArtworkCandidate(
                        url = verticalCapsuleUrl,
                        sourceName = "Steam",
                        description = name
                    )
                }
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
}
