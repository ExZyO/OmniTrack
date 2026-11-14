package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.BackHandler
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
import com.omnitrack.app.data.model.AnimeVoiceActorDetail
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.VoiceActorRoleItem
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

    var showingVoiceActor by remember { mutableStateOf(false) }

    BackHandler(enabled = showingVoiceActor) {
        showingVoiceActor = false
    }

    var isLoading by remember { mutableStateOf(true) }
    var biography by remember { mutableStateOf<String?>(null) }
    var nativeName by remember { mutableStateOf<String?>(null) }
    var appearances by remember { mutableStateOf<List<RecommendationItem>>(emptyList()) }
    var bioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(character.characterId) {
        isLoading = true
        showingVoiceActor = false
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
                            nativeName = charObj?.get("name")?.jsonObject?.get("native")?.jsonPrimitive?.contentOrNull
                            val rawBio = charObj?.get("description")?.jsonPrimitive?.contentOrNull
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
                                    val id = obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val malId = obj["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    val title = obj["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.contentOrNull ?: continue
                                    val cover = obj["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                                        ?: obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull ?: ""
                                    val banner = obj["bannerImage"]?.jsonPrimitive?.contentOrNull
                                    val score = obj["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                                    val year = obj["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull
                                    val fmt = obj["format"]?.jsonPrimitive?.contentOrNull ?: "TV"

                                    list.add(
                                        RecommendationItem(
                                            title = title,
                                            coverUrl = cover,
                                            bannerUrl = banner,
                                            mediaType = if (fmt.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME,
                                            rating = score?.let { it / 10f },
                                            tmdbId = null,
                                            anilistId = id,
                                            malId = malId,
                                            releaseYear = year
                                        )
                                    )
                                }
                                appearances = list
                            }
                        }
                    }
                }
            } catch (_: Exception) {} finally {
                isLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        if (showingVoiceActor && character.voiceActorId != null) {
            VoiceActorDetailContent(
                voiceActorId = character.voiceActorId,
                fallbackName = character.voiceActorName ?: "Voice Actor",
                fallbackImageUrl = character.voiceActorImageUrl,
                onBack = { showingVoiceActor = false },
                onOpenMedia = { item ->
                    onDismissRequest()
                    onOpenMediaPreview?.invoke(item)
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Character Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                // Voice Actor Row (100% Unboxed per Exile feedback)
                if (!character.voiceActorName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = character.voiceActorId != null) {
                                showingVoiceActor = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(character.voiceActorImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = character.voiceActorName,
                            modifier = Modifier
                                .size(48.dp)
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

                        if (character.voiceActorId != null) {
                            Text(
                                text = "›",
                                fontSize = 20.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Light
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
}

@Composable
private fun VoiceActorDetailContent(
    voiceActorId: Long,
    fallbackName: String,
    fallbackImageUrl: String?,
    onBack: () -> Unit,
    onOpenMedia: (RecommendationItem) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<AnimeVoiceActorDetail?>(null) }
    var bioExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(voiceActorId) {
        isLoading = true
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    query (${'$'}id: Int) {
                      Staff(id: ${'$'}id) {
                        id
                        name {
                          full
                          native
                        }
                        image {
                          large
                        }
                        description
                        age
                        dateOfBirth {
                          year
                          month
                          day
                        }
                        homeTown
                        primaryOccupations
                        characterMedia(sort: [FAVOURITES_DESC], page: 1, perPage: 25) {
                          edges {
                            characters {
                              id
                              name {
                                full
                              }
                              image {
                                large
                              }
                            }
                            characterRole
                            node {
                              id
                              idMal
                              title {
                                romaji
                                english
                                userPreferred
                              }
                              coverImage {
                                extraLarge
                                large
                              }
                              format
                              averageScore
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                      }
                    }
                """.trimIndent()

                val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                val jsonPayload = """{"query":"$escapedQuery","variables":{"id":$voiceActorId}}"""
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
                            val staff = root["data"]?.jsonObject?.get("Staff")?.jsonObject
                            if (staff != null) {
                                val sId = staff["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: voiceActorId
                                val name = staff["name"]?.jsonObject?.get("full")?.jsonPrimitive?.contentOrNull ?: fallbackName
                                val native = staff["name"]?.jsonObject?.get("native")?.jsonPrimitive?.contentOrNull
                                val img = staff["image"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull ?: fallbackImageUrl
                                val rawBio = staff["description"]?.jsonPrimitive?.contentOrNull
                                val cleanBio = rawBio
                                    ?.replace(Regex("""__.*?__""")) { it.value.removeSurrounding("__") }
                                    ?.replace(Regex("""\*\*.*?\*\*""")) { it.value.removeSurrounding("**") }
                                    ?.replace(Regex("""~{2,}.*?~{2,}"""), "")
                                    ?.replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
                                    ?.replace(Regex("""<.*?>"""), "")
                                    ?.trim()

                                val age = staff["age"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dobObj = staff["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                                val y = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val m = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val d = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dobStr = if (y != null && m != null && d != null) "$y-$m-$d" else if (m != null && d != null) "$m-$d" else null
                                val home = staff["homeTown"]?.jsonPrimitive?.contentOrNull
                                val occupations = staff["primaryOccupations"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }

                                val rolesList = mutableListOf<VoiceActorRoleItem>()
                                val edges = staff["characterMedia"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                                for (edgeElem in edges) {
                                    val edge = edgeElem.jsonObject
                                    val role = edge["characterRole"]?.jsonPrimitive?.contentOrNull ?: "SUPPORT"
                                    val charObj = edge["characters"]?.jsonArray?.firstOrNull()?.jsonObject
                                    val charId = charObj?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val charName = charObj["name"]?.jsonObject?.get("full")?.jsonPrimitive?.contentOrNull ?: "Character"
                                    val charImg = charObj["image"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull

                                    val mediaNode = edge["node"]?.jsonObject ?: continue
                                    val mId = mediaNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val eng = mediaNode["title"]?.jsonObject?.get("english")?.jsonPrimitive?.contentOrNull
                                    val rom = mediaNode["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.contentOrNull
                                    val usr = mediaNode["title"]?.jsonObject?.get("userPreferred")?.jsonPrimitive?.contentOrNull
                                    val mTitle = eng ?: usr ?: rom ?: "Media"
                                    val mCover = mediaNode["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.contentOrNull
                                        ?: mediaNode["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull
                                    val mFormat = mediaNode["format"]?.jsonPrimitive?.contentOrNull ?: "TV"
                                    val mYear = mediaNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                    val mTypeStr = mediaNode["type"]?.jsonPrimitive?.contentOrNull ?: "ANIME"
                                    val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) {
                                        if (mFormat.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA
                                    } else {
                                        if (mFormat.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME
                                    }

                                    rolesList.add(
                                        VoiceActorRoleItem(
                                            characterId = charId,
                                            characterName = charName,
                                            characterImageUrl = charImg,
                                            characterRole = role,
                                            mediaId = mId,
                                            mediaTitle = mTitle,
                                            mediaCoverUrl = mCover,
                                            mediaFormat = mFormat,
                                            mediaYear = mYear,
                                            mediaType = mType
                                        )
                                    )
                                }

                                detail = AnimeVoiceActorDetail(
                                    staffId = sId,
                                    name = name,
                                    nativeName = native,
                                    imageUrl = img,
                                    biography = cleanBio,
                                    age = age,
                                    dateOfBirth = dobStr,
                                    homeTown = home,
                                    primaryOccupations = occupations,
                                    roles = rolesList
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {} finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Back Navigation Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "‹",
                fontSize = 22.sp,
                color = AccentArctic,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Back to Character",
                fontSize = 14.sp,
                color = AccentArctic,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Voice Actor Profile Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(detail?.imageUrl ?: fallbackImageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = detail?.name ?: fallbackName,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, GlassBorder, CircleShape),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = detail?.name ?: fallbackName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (!detail?.nativeName.isNullOrBlank()) {
                    Text(
                        text = detail!!.nativeName!!,
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                val vitals = buildList {
                    detail?.age?.let { add("Age $it") }
                    detail?.homeTown?.let { add(it) }
                }.joinToString(" • ")

                if (vitals.isNotBlank()) {
                    Text(
                        text = vitals,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Biography
        val bioText = detail?.biography
        if (!bioText.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Biography",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = bioText,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 19.sp,
                    maxLines = if (bioExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable { bioExpanded = !bioExpanded }
                )

                if (bioText.length > 200) {
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

        // Dual-Paired Roles Rail (Character + Media)
        val roles = detail?.roles.orEmpty()
        if (roles.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Voice Acting Roles",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(roles, key = { "${it.mediaId}_${it.characterId}" }) { roleItem ->
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clickable {
                                    val rec = RecommendationItem(
                                        title = roleItem.mediaTitle,
                                        coverUrl = roleItem.mediaCoverUrl ?: "",
                                        bannerUrl = roleItem.mediaCoverUrl,
                                        mediaType = roleItem.mediaType,
                                        rating = null,
                                        tmdbId = null,
                                        anilistId = roleItem.mediaId,
                                        malId = null,
                                        releaseYear = roleItem.mediaYear?.toString()
                                    )
                                    onOpenMedia(rec)
                                },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Poster with Character avatar overlay in corner
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(138.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCard)
                                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                ) {
                                    if (!roleItem.mediaCoverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(roleItem.mediaCoverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = roleItem.mediaTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                }

                                // Character PiP in bottom-right
                                if (!roleItem.characterImageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(roleItem.characterImageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = roleItem.characterName,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, Color(0xFF000000), CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            Text(
                                text = roleItem.characterName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = roleItem.mediaTitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
