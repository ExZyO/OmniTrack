package com.omnitrack.app.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import okhttp3.OkHttpClient
import okhttp3.Request

data class PersonFilmographyItem(
    val id: Long,
    val title: String,
    val posterUrl: String?,
    val mediaType: MediaType,
    val releaseYear: String?,
    val character: String?,
    val rating: Float?
)

data class PersonDetailData(
    val id: Long,
    val name: String,
    val biography: String?,
    val profilePhotoUrl: String?,
    val birthday: String?,
    val placeOfBirth: String?,
    val knownFor: List<PersonFilmographyItem> = emptyList(),
    val movies: List<PersonFilmographyItem> = emptyList(),
    val shows: List<PersonFilmographyItem> = emptyList()
)

/**
 * 1:1 Nuvio Person Detail Screen:
 * Displays actor/creator profile photo, biography, and filmography poster rails with click-to-preview.
 */
@Composable
fun PersonDetailScreen(
    personId: Long,
    personName: String,
    initialAvatarUrl: String? = null,
    apiKey: String,
    onBack: () -> Unit,
    onOpenMediaPreview: (RecommendationItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var personData by remember { mutableStateOf<PersonDetailData?>(null) }
    var bioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(personId) {
        isLoading = true
        personData = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val url = "https://api.themoviedb.org/3/person/$personId?api_key=$apiKey&append_to_response=combined_credits"
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: return@use null
                        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                        val name = root["name"]?.jsonPrimitive?.content ?: personName
                        val bio = root["biography"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                        val profilePath = root["profile_path"]?.jsonPrimitive?.content
                        val photo = profilePath?.let { "https://image.tmdb.org/t/p/h632$it" } ?: initialAvatarUrl
                        val birthday = root["birthday"]?.jsonPrimitive?.content
                        val place = root["place_of_birth"]?.jsonPrimitive?.content

                        val creditsObj = root["combined_credits"]?.jsonObject
                        val castArr = creditsObj?.get("cast")?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())

                        val knownList = mutableListOf<PersonFilmographyItem>()
                        val movieList = mutableListOf<PersonFilmographyItem>()
                        val showList = mutableListOf<PersonFilmographyItem>()

                        for (elem in castArr) {
                            val item = elem.jsonObject
                            val id = item["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: continue
                            val title = item["title"]?.jsonPrimitive?.content ?: item["name"]?.jsonPrimitive?.content ?: continue
                            val mediaTypeStr = item["media_type"]?.jsonPrimitive?.content ?: "movie"
                            val posterPath = item["poster_path"]?.jsonPrimitive?.content
                            val posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                            val year = (item["release_date"]?.jsonPrimitive?.content ?: item["first_air_date"]?.jsonPrimitive?.content)?.take(4)
                            val character = item["character"]?.jsonPrimitive?.content
                            val rating = item["vote_average"]?.jsonPrimitive?.content?.toFloatOrNull()
                            val voteCount = item["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

                            val mType = if (mediaTypeStr == "tv") MediaType.SHOW else MediaType.MOVIE
                            val filmoItem = PersonFilmographyItem(
                                id = id,
                                title = title,
                                posterUrl = posterUrl,
                                mediaType = mType,
                                releaseYear = year,
                                character = character,
                                rating = rating
                            )

                            if (voteCount >= 50 && knownList.size < 12) {
                                knownList.add(filmoItem)
                            }
                            if (mType == MediaType.MOVIE) {
                                movieList.add(filmoItem)
                            } else {
                                showList.add(filmoItem)
                            }
                        }

                        PersonDetailData(
                            id = personId,
                            name = name,
                            biography = bio,
                            profilePhotoUrl = photo,
                            birthday = birthday,
                            placeOfBirth = place,
                            knownFor = if (knownList.isNotEmpty()) knownList else movieList.take(10),
                            movies = movieList,
                            shows = showList
                        )
                    } else null
                }
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
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 40.dp)
        ) {
            // Header: Top Bar with Back Arrow
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
                Text(
                    text = personName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentArctic, modifier = Modifier.size(36.dp))
                }
            } else {
                val data = personData
                if (data != null) {
                    // Profile Header: Avatar + Specs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF141416))
                                .border(1.dp, GlassBorder, CircleShape)
                        ) {
                            if (!data.profilePhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = data.profilePhotoUrl,
                                    contentDescription = data.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = data.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            data.birthday?.let { bday ->
                                Text(
                                    text = "Born: $bday",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                            data.placeOfBirth?.let { place ->
                                Text(
                                    text = place,
                                    fontSize = 11.5.sp,
                                    color = TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Biography
                    if (!data.biography.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                                .animateContentSize()
                                .clickable { bioExpanded = !bioExpanded }
                        ) {
                            Text(
                                text = data.biography,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = TextSecondary,
                                maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (bioExpanded) "Show less" else "Read full biography",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentArctic,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Known For Poster Rail
                    if (data.knownFor.isNotEmpty()) {
                        FilmographyPosterRail(
                            title = "Known For",
                            items = data.knownFor,
                            onItemClick = onOpenMediaPreview
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Movies Poster Rail
                    if (data.movies.isNotEmpty()) {
                        FilmographyPosterRail(
                            title = "Movies (${data.movies.size})",
                            items = data.movies,
                            onItemClick = onOpenMediaPreview
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // TV Shows Poster Rail
                    if (data.shows.isNotEmpty()) {
                        FilmographyPosterRail(
                            title = "TV Shows (${data.shows.size})",
                            items = data.shows,
                            onItemClick = onOpenMediaPreview
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Could not load person information.",
                            fontSize = 14.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilmographyPosterRail(
    title: String,
    items: List<PersonFilmographyItem>,
    onItemClick: (RecommendationItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items) { item ->
                com.omnitrack.app.ui.components.NuvioRailPosterCard(
                    title = item.title,
                    coverUrl = item.posterUrl,
                    subtitle = item.character ?: item.releaseYear,
                    rating = item.rating,
                    onClick = {
                        onItemClick(
                            RecommendationItem(
                                title = item.title,
                                coverUrl = item.posterUrl ?: "",
                                releaseYear = item.releaseYear,
                                rating = item.rating,
                                mediaType = item.mediaType,
                                tmdbId = item.id
                            )
                        )
                    },
                    cardWidth = 135.dp,
                    cardHeight = 202.dp
                )
            }
        }
    }
}
