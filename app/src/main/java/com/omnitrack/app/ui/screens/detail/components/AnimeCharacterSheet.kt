package com.omnitrack.app.ui.screens.detail.components

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeCharacterSheet(
    character: AnimeCharacterItem?,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null
) {
    if (character == null) return
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var biography by remember { mutableStateOf<String?>(null) }
    var nativeName by remember { mutableStateOf<String?>(null) }
    var appearances by remember { mutableStateOf<List<RecommendationItem>>(emptyList()) }
    var bioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(character.characterId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    query (${'$'}id: Int) {
                      Character(id: ${'$'}id) {
                        name {
                          native
                        }
                        description
                        media(sort: POPULARITY_DESC, perPage: 15) {
                          nodes {
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
                            format
                            averageScore
                            startDate {
                              year
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                val jsonPayload = """{"query":"$escapedQuery","variables":{"id":${character.characterId}}}"""
                val req = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "OmniTrack/1.0")
                    .build()

                OkHttpClient().newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null) {
                            val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                            val charObj = root["data"]?.jsonObject?.get("Character")?.jsonObject
                            nativeName = charObj?.get("name")?.jsonObject?.get("native")?.jsonPrimitive?.content
                            val rawBio = charObj?.get("description")?.jsonPrimitive?.content
                            biography = rawBio
                                ?.replace(Regex("""__.*?__""")) { it.value.removeSurrounding("__") }
                                ?.replace(Regex("""\*\*.*?\*\*""")) { it.value.removeSurrounding("**") }
                                ?.replace(Regex("""~{2,}.*?~{2,}"""), "")
                                ?.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                                ?.replace(Regex("""<.*?>"""), "")
                                ?.trim()

                            val mediaNodes = charObj?.get("media")?.jsonObject?.get("nodes")?.jsonArray
                            if (mediaNodes != null) {
                                val list = mutableListOf<RecommendationItem>()
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
                                    val fmt = obj["format"]?.jsonPrimitive?.content ?: "TV"

                                    val targetType = when (fmt.uppercase()) {
                                        "MANGA" -> MediaType.MANGA
                                        "NOVEL" -> MediaType.NOVEL
                                        "MOVIE" -> MediaType.MOVIE
                                        else -> MediaType.ANIME
                                    }

                                    list.add(
                                        RecommendationItem(
                                            title = title,
                                            coverUrl = cover,
                                            bannerUrl = banner,
                                            releaseYear = year,
                                            rating = score?.let { it / 10f },
                                            mediaType = targetType,
                                            anilistId = id,
                                            malId = malId
                                        )
                                    )
                                }
                                appearances = list
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Character Avatar + Name + Role
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Character Portrait
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(character.characterImageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = character.characterName,
                    modifier = Modifier
                        .width(84.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = character.characterName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!nativeName.isNullOrBlank()) {
                        Text(
                            text = nativeName!!,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextSecondary
                        )
                    }

                    // Role Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (character.characterRole == "MAIN") AccentArctic.copy(alpha = 0.15f)
                                else SurfaceCard
                            )
                            .border(
                                0.5.dp,
                                if (character.characterRole == "MAIN") AccentArctic.copy(alpha = 0.4f)
                                else GlassBorder,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = character.characterRole,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (character.characterRole == "MAIN") AccentArctic else TextMuted,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Voice Actor Card (Seiyuu)
            if (!character.voiceActorName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(character.voiceActorImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = character.voiceActorName,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlassBorder, CircleShape),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = character.voiceActorName!!,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Voice Actor • ${character.voiceActorLanguage}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Biography Section
            if (isLoading && biography == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AccentArctic,
                        strokeWidth = 2.dp
                    )
                }
            } else if (!biography.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "About",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = biography!!,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        lineHeight = 19.sp,
                        maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .animateContentSize()
                            .clickable { bioExpanded = !bioExpanded }
                    )

                    if (biography!!.length > 200) {
                        Text(
                            text = if (bioExpanded) "Show Less" else "Read More",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentArctic,
                            modifier = Modifier.clickable { bioExpanded = !bioExpanded }
                        )
                    }
                }
            }

            // Appearances / Filmography Rail
            if (appearances.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Appearances",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(appearances, key = { it.anilistId ?: it.title.hashCode().toLong() }) { appItem ->
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .clickable {
                                        onDismissRequest()
                                        onOpenMediaPreview?.invoke(appItem)
                                    },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(appItem.coverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = appItem.title,
                                        modifier = Modifier.matchParentSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Text(
                                    text = appItem.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
