package com.omnitrack.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.screens.detail.components.RecommendationItem
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class EntityBrowseData(
    val id: Long?,
    val name: String,
    val logoUrl: String? = null,
    val originCountry: String? = null,
    val items: List<RecommendationItem> = emptyList()
)

/**
 * 1:1 Nuvio Network & Production Company Catalog Filmography Browser:
 * Supports both TMDb (movies/shows) and AniList GraphQL (anime studios like MAPPA/Ufotable).
 * Strictly opens all tapped items in Ephemeral Preview Mode (never auto-inserting into Room).
 */
@Composable
fun TmdbEntityBrowseScreen(
    entityName: String,
    entityId: Long? = null,
    entityKind: String = "network", // "network", "company", or "anime_studio"
    apiKey: String,
    onBack: () -> Unit,
    onOpenMediaPreview: (RecommendationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var entityData by remember { mutableStateOf<EntityBrowseData?>(null) }

    LaunchedEffect(entityName, entityId) {
        isLoading = true
        entityData = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val itemsList = mutableListOf<RecommendationItem>()
                var resolvedLogo: String? = null
                var originCountry: String? = null

                if (entityKind == "anime_studio") {
                    // AniList GraphQL Studio Media Query
                    val graphqlQuery = """
                        query (${'$'}studio: String) {
                          Studio(search: ${'$'}studio) {
                            name
                            media(sort: POPULARITY_DESC, perPage: 30) {
                              nodes {
                                id
                                idMal
                                title { userPreferred }
                                coverImage { extraLarge large }
                                bannerImage
                                averageScore
                                startDate { year }
                                format
                              }
                            }
                          }
                        }
                    """.trimIndent()

                    val jsonPayload = """{"query":${Json.encodeToString(kotlinx.serialization.serializer(), graphqlQuery)},"variables":{"studio":"$entityName"}}"""
                    val req = Request.Builder()
                        .url("https://graphql.anilist.co")
                        .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val studioObj = root["data"]?.jsonObject?.get("Studio")?.jsonObject
                                val mediaNodes = studioObj?.get("media")?.jsonObject?.get("nodes")?.jsonArray
                                if (mediaNodes != null) {
                                    for (node in mediaNodes) {
                                        val obj = node.jsonObject
                                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                                        val malId = obj["idMal"]?.jsonPrimitive?.content?.toLongOrNull()
                                        val title = obj["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.content ?: continue
                                        val cover = obj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content
                                            ?: obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content ?: ""
                                        val banner = obj["bannerImage"]?.jsonPrimitive?.content
                                        val score = obj["averageScore"]?.jsonPrimitive?.content?.toFloatOrNull()
                                        val year = obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content
                                        itemsList.add(
                                            RecommendationItem(
                                                title = title,
                                                coverUrl = cover,
                                                bannerUrl = banner,
                                                releaseYear = year,
                                                rating = score?.let { it / 10f },
                                                mediaType = MediaType.ANIME,
                                                anilistId = id,
                                                malId = malId
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TMDb Network / Company Discover Query
                    val targetId = entityId
                    val isNetwork = entityKind.equals("network", ignoreCase = true)

                    // 1. Fetch details if ID available
                    if (targetId != null && targetId > 0) {
                        val detailsEndpoint = if (isNetwork) "network" else "company"
                        val detailsUrl = "https://api.themoviedb.org/3/$detailsEndpoint/$targetId?api_key=$apiKey"
                        val detailsReq = Request.Builder().url(detailsUrl).get().build()
                        client.newCall(detailsReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (body != null) {
                                    val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                    val logoPath = obj["logo_path"]?.jsonPrimitive?.content
                                    resolvedLogo = logoPath?.let { "https://image.tmdb.org/t/p/w300$it" }
                                    originCountry = obj["origin_country"]?.jsonPrimitive?.content
                                }
                            }
                        }
                    }

                    // 2. Discover TV or Movies
                    val discoverUrl = if (isNetwork && targetId != null && targetId > 0) {
                        "https://api.themoviedb.org/3/discover/tv?api_key=$apiKey&with_networks=$targetId&sort_by=popularity.desc"
                    } else if (targetId != null && targetId > 0) {
                        "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&with_companies=$targetId&sort_by=popularity.desc"
                    } else {
                        // Search by query text fallback
                        "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=${java.net.URLEncoder.encode(entityName, "UTF-8")}"
                    }

                    val req = Request.Builder().url(discoverUrl).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()
                            if (body != null) {
                                val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                                val results = root["results"]?.jsonArray
                                if (results != null) {
                                    for (elem in results) {
                                        val obj = elem.jsonObject
                                        val id = obj["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                                        val title = obj["name"]?.jsonPrimitive?.content
                                            ?: obj["title"]?.jsonPrimitive?.content ?: continue
                                        val posterPath = obj["poster_path"]?.jsonPrimitive?.content ?: continue
                                        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.content
                                        val year = (obj["first_air_date"]?.jsonPrimitive?.content ?: obj["release_date"]?.jsonPrimitive?.content)?.take(4)
                                        val rating = obj["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                                        val mediaTypeStr = obj["media_type"]?.jsonPrimitive?.content ?: if (isNetwork) "tv" else "movie"
                                        val mType = if (mediaTypeStr == "tv") MediaType.SHOW else MediaType.MOVIE

                                        itemsList.add(
                                            RecommendationItem(
                                                title = title,
                                                coverUrl = "https://image.tmdb.org/t/p/w500$posterPath",
                                                bannerUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
                                                releaseYear = year,
                                                rating = rating,
                                                mediaType = mType,
                                                tmdbId = id
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                EntityBrowseData(
                    id = entityId,
                    name = entityName,
                    logoUrl = resolvedLogo,
                    originCountry = originCountry,
                    items = itemsList
                )
            } catch (_: Exception) {
                null
            }
        }
        isLoading = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = entityName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    entityData?.items?.size?.let { count ->
                        Text(
                            text = "$count titles",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentArctic, modifier = Modifier.size(36.dp))
                }
            } else {
                val data = entityData
                if (data != null && data.items.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(data.items) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenMediaPreview(item) },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.67f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF141416))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                                ) {
                                    if (item.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = item.coverUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (item.rating != null && item.rating > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(AmoledBlack.copy(alpha = 0.85f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "★ ${String.format("%.1f", item.rating)}",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFFB800)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = item.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                item.releaseYear?.let { yr ->
                                    Text(
                                        text = yr,
                                        fontSize = 10.5.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No titles found for $entityName.",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
