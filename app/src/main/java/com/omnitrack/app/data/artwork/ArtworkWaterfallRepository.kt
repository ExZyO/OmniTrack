package com.omnitrack.app.data.artwork

import com.omnitrack.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ArtworkWaterfallRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchArtworkCandidates(
        title: String,
        mediaType: MediaType
    ): List<ArtworkCandidate> = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext emptyList()

        coroutineScope {
            val anilistDeferred = async { fetchAniListCovers(title) }
            val mangadexDeferred = async { if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) fetchMangaDexCovers(title) else emptyList() }
            val bangumiDeferred = async { fetchBangumiCovers(title) }

            val results = awaitAll(anilistDeferred, mangadexDeferred, bangumiDeferred).flatten()
            // Deduplicate by URL and normalize to lossless
            results.distinctBy { LosslessCoverMapper.toLosslessUrl(it.url) }
                .map { it.copy(url = LosslessCoverMapper.toLosslessUrl(it.url)) }
        }
    }

    private fun fetchAniListCovers(title: String): List<ArtworkCandidate> {
        return try {
            val query = """
                query (${'$'}search: String) {
                    Page(page: 1, perPage: 6) {
                        media(search: ${'$'}search) {
                            id
                            title { romaji english native }
                            coverImage { extraLarge large }
                            bannerImage
                        }
                    }
                }
            """.trimIndent()

            val bodyJson = "{\"query\": \"${query.replace("\n", " ").replace("\"", "\\\"")}\", \"variables\": {\"search\": \"$title\"}}"
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("Accept", "application/json")
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
                    val itemTitle = item.jsonObject["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content ?: title
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
            val encoded = java.net.URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.mangadex.org/manga?title=$encoded&limit=4&includes[]=cover_art")
                .get()
                .header("Accept", "application/json")
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
                                        description = "Volume Cover"
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

    private fun fetchBangumiCovers(title: String): List<ArtworkCandidate> {
        return try {
            val encoded = java.net.URLEncoder.encode(title, "UTF-8")
            val request = Request.Builder()
                .url("https://api.bgm.tv/search/subject/$encoded?responseGroup=small&max_results=4")
                .get()
                .header("Accept", "application/json")
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
}
