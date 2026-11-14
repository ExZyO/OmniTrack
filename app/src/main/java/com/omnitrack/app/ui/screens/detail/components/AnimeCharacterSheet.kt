package com.omnitrack.app.ui.screens.detail.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.AnimeVoiceActorDetail
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.StaffProductionCreditItem
import com.omnitrack.app.data.model.VoiceActorRoleItem
import com.omnitrack.app.ui.components.AniListMarkdownText
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private fun JsonElement?.cleanString(): String? {
    if (this == null || this is JsonNull) return null
    val str = this.jsonPrimitive.contentOrNull?.trim()
    return if (str.isNullOrBlank() || str.equals("null", ignoreCase = true)) null else str
}

private fun formatAniListDate(year: Int?, month: Int?, day: Int?): String? {
    if (year == null && month == null && day == null) return null
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val mStr = if (month != null && month in 1..12) months[month - 1] else null
    return buildList {
        mStr?.let { add(it) }
        day?.let { add(it.toString()) }
        year?.let { add(it.toString()) }
    }.joinToString(" ")
}

private fun formatCompactNumber(num: Int?): String? {
    if (num == null || num <= 0) return null
    return if (num >= 1000) String.format("%.1fk", num / 1000f) else num.toString()
}

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
    var gender by remember { mutableStateOf<String?>(null) }
    var age by remember { mutableStateOf<String?>(null) }
    var dateOfBirth by remember { mutableStateOf<String?>(null) }
    var bloodType by remember { mutableStateOf<String?>(null) }
    var favourites by remember { mutableStateOf<Int?>(null) }
    var alternativeNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var appearances by remember { mutableStateOf<List<RecommendationItem>>(emptyList()) }

    LaunchedEffect(character.characterId) {
        isLoading = true
        showingVoiceActor = false
        withContext(Dispatchers.IO) {
            try {
                val query = """
                    query (${'$'}id: Int) {
                      Character(id: ${'$'}id) {
                        name {
                          full
                          native
                          alternative
                        }
                        image {
                          large
                        }
                        description
                        gender
                        dateOfBirth {
                          year
                          month
                          day
                        }
                        age
                        bloodType
                        favourites
                        anime: media(type: ANIME, sort: POPULARITY_DESC, page: 1, perPage: 25) {
                          edges {
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
                              bannerImage
                              format
                              averageScore
                              startDate {
                                year
                              }
                            }
                          }
                        }
                        manga: media(type: MANGA, sort: POPULARITY_DESC, page: 1, perPage: 25) {
                          edges {
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
                    }
                """.trimIndent()

                val jsonPayload = buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject { put("id", character.characterId) })
                }.toString()

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
                            if (charObj != null) {
                                nativeName = charObj["name"]?.jsonObject?.get("native").cleanString()
                                biography = charObj["description"].cleanString()
                                gender = charObj["gender"].cleanString()
                                age = charObj["age"].cleanString()
                                bloodType = charObj["bloodType"].cleanString()
                                favourites = charObj["favourites"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

                                val dobObj = charObj["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                                val dy = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dm = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dd = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                dateOfBirth = formatAniListDate(dy, dm, dd)

                                alternativeNames = charObj["name"]?.jsonObject?.get("alternative")
                                    ?.jsonArray.orEmpty().mapNotNull { it.cleanString() }

                                val list = mutableListOf<RecommendationItem>()
                                val animeEdges = charObj["anime"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                                val mangaEdges = charObj["manga"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()

                                for (edgeElem in (animeEdges + mangaEdges)) {
                                    val edge = edgeElem.jsonObject
                                    val charRole = edge["characterRole"].cleanString() ?: "SUPPORT"
                                    val node = edge["node"]?.jsonObject ?: continue
                                    val id = node["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val malId = node["idMal"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    val eng = node["title"]?.jsonObject?.get("english").cleanString()
                                    val rom = node["title"]?.jsonObject?.get("romaji").cleanString()
                                    val usr = node["title"]?.jsonObject?.get("userPreferred").cleanString()
                                    val title = eng ?: usr ?: rom ?: continue
                                    val cover = node["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                                        ?: node["coverImage"]?.jsonObject?.get("large").cleanString() ?: ""
                                    val banner = node["bannerImage"].cleanString()
                                    val score = node["averageScore"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                                    val year = node["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull
                                    val fmt = node["format"].cleanString() ?: "TV"

                                    list.add(
                                        RecommendationItem(
                                            title = title,
                                            coverUrl = cover,
                                            bannerUrl = banner,
                                            mediaType = if (fmt.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else if (fmt.equals("MANGA", ignoreCase = true) || fmt.equals("NOVEL", ignoreCase = true)) MediaType.MANGA else MediaType.ANIME,
                                            rating = score?.let { it / 10f },
                                            tmdbId = null,
                                            anilistId = id,
                                            malId = malId,
                                            releaseYear = if (!year.isNullOrBlank()) "$year • $charRole" else charRole
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
            StaffDetailContent(
                staffId = character.voiceActorId,
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
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Character Header (100% Unboxed)
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
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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

                        // Unboxed Role Label
                        Text(
                            text = if (character.characterRole == "MAIN") "MAIN CHARACTER" else "SUPPORTING CHARACTER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (character.characterRole == "MAIN") AccentArctic else TextMuted,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                // 100% Unboxed Vitals (NO BOXES / NO CHIPS)
                val vitalsList = buildList {
                    age?.let { add("$it yrs") }
                    gender?.let { add(it) }
                    dateOfBirth?.let { add(it) }
                    bloodType?.let { add("Blood $it") }
                    formatCompactNumber(favourites)?.let { add("$it ♥") }
                }
                if (vitalsList.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = vitalsList.joinToString("  •  "),
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        if (alternativeNames.isNotEmpty()) {
                            Text(
                                text = "AKA: ${alternativeNames.take(4).joinToString(", ")}",
                                fontSize = 11.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Voice Actor Row (100% Unboxed)
                if (!character.voiceActorName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = character.voiceActorId != null) {
                                showingVoiceActor = true
                            }
                            .padding(horizontal = 20.dp, vertical = 4.dp),
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
                                fontSize = 22.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }

                // Biography Section with Clickable Markdown / Spoilers
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

                        AniListMarkdownText(
                            text = biography!!,
                            collapsedMaxLines = 4
                        )
                    }
                }

                // Appearances Rail (100% Unboxed Poster Art)
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
                            itemsIndexed(appearances, key = { index, it -> "${it.anilistId}_${it.title}_$index" }) { _, appItem ->
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!appItem.releaseYear.isNullOrBlank()) {
                                        Text(
                                            text = appItem.releaseYear!!,
                                            fontSize = 10.sp,
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
        }
    }
}

/**
 * Standalone BottomSheet for viewing any Staff member (Director, Music, Creator, Voice Actor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeStaffSheet(
    staff: AnimeStaffItem?,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismissRequest: () -> Unit,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null
) {
    if (staff == null) return

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        StaffDetailContent(
            staffId = staff.staffId,
            fallbackName = staff.name,
            fallbackImageUrl = staff.imageUrl,
            onBack = null,
            onOpenMedia = { item ->
                onDismissRequest()
                onOpenMediaPreview?.invoke(item)
            }
        )
    }
}

/**
 * 100% Unboxed AMOLED Staff Profile Content (both standalone & in-character transition).
 */
@Composable
private fun StaffDetailContent(
    staffId: Long,
    fallbackName: String,
    fallbackImageUrl: String?,
    onBack: (() -> Unit)? = null,
    onOpenMedia: (RecommendationItem) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<AnimeVoiceActorDetail?>(null) }

    LaunchedEffect(staffId) {
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
                        gender
                        yearsActive
                        homeTown
                        bloodType
                        languageV2
                        favourites
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
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                        anime: staffMedia(type: ANIME, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            staffRole
                            node {
                              id
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
                              startDate {
                                year
                              }
                              type
                            }
                          }
                        }
                        manga: staffMedia(type: MANGA, sort: [POPULARITY_DESC], page: 1, perPage: 25) {
                          edges {
                            staffRole
                            node {
                              id
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

                val jsonPayload = buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject { put("id", staffId) })
                }.toString()

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
                                val sId = staff["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: staffId
                                val name = staff["name"]?.jsonObject?.get("full").cleanString() ?: fallbackName
                                val native = staff["name"]?.jsonObject?.get("native").cleanString()
                                val img = staff["image"]?.jsonObject?.get("large").cleanString() ?: fallbackImageUrl
                                val rawBio = staff["description"].cleanString()

                                val age = staff["age"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dobObj = staff["dateOfBirth"]?.let { if (it is JsonObject) it else null }
                                val y = dobObj?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val m = dobObj?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val d = dobObj?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val dobStr = formatAniListDate(y, m, d)
                                val gender = staff["gender"].cleanString()
                                val home = staff["homeTown"].cleanString()
                                val blood = staff["bloodType"].cleanString()
                                val lang = staff["languageV2"].cleanString()
                                val favs = staff["favourites"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val occupations = staff["primaryOccupations"]?.jsonArray.orEmpty().mapNotNull { it.cleanString() }

                                val yearsList = staff["yearsActive"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull?.toIntOrNull() }
                                val yearsStr = when {
                                    yearsList.size >= 2 -> "${yearsList.first()} – ${yearsList.last()}"
                                    yearsList.size == 1 -> "${yearsList.first()} – present"
                                    else -> null
                                }

                                // 1. Voiced Character Roles
                                val rolesList = mutableListOf<VoiceActorRoleItem>()
                                val edges = staff["characterMedia"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                                for (edgeElem in edges) {
                                    val edge = edgeElem.jsonObject
                                    val role = edge["characterRole"].cleanString() ?: "SUPPORT"
                                    val charObj = edge["characters"]?.jsonArray?.firstOrNull()?.jsonObject
                                    val charId = charObj?.get("id")?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val charName = charObj["name"]?.jsonObject?.get("full").cleanString() ?: "Character"
                                    val charImg = charObj["image"]?.jsonObject?.get("large").cleanString()

                                    val mediaNode = edge["node"]?.jsonObject ?: continue
                                    val mId = mediaNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val eng = mediaNode["title"]?.jsonObject?.get("english").cleanString()
                                    val rom = mediaNode["title"]?.jsonObject?.get("romaji").cleanString()
                                    val usr = mediaNode["title"]?.jsonObject?.get("userPreferred").cleanString()
                                    val mTitle = eng ?: usr ?: rom ?: "Media"
                                    val mCover = mediaNode["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                                        ?: mediaNode["coverImage"]?.jsonObject?.get("large").cleanString()
                                    val mFormat = mediaNode["format"].cleanString() ?: "TV"
                                    val mYear = mediaNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                    val mTypeStr = mediaNode["type"].cleanString() ?: "ANIME"
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

                                // 2. Production Staff Credits (Anime & Manga)
                                val productionCredits = mutableListOf<StaffProductionCreditItem>()
                                val animeProdEdges = staff["anime"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                                val mangaProdEdges = staff["manga"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
                                for (edgeElem in (animeProdEdges + mangaProdEdges)) {
                                    val edge = edgeElem.jsonObject
                                    val staffRole = edge["staffRole"].cleanString() ?: continue
                                    val mediaNode = edge["node"]?.jsonObject ?: continue
                                    val mId = mediaNode["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: continue
                                    val eng = mediaNode["title"]?.jsonObject?.get("english").cleanString()
                                    val rom = mediaNode["title"]?.jsonObject?.get("romaji").cleanString()
                                    val usr = mediaNode["title"]?.jsonObject?.get("userPreferred").cleanString()
                                    val mTitle = eng ?: usr ?: rom ?: "Media"
                                    val mCover = mediaNode["coverImage"]?.jsonObject?.get("extraLarge").cleanString()
                                        ?: mediaNode["coverImage"]?.jsonObject?.get("large").cleanString()
                                    val mFormat = mediaNode["format"].cleanString() ?: "TV"
                                    val mYear = mediaNode["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                    val mTypeStr = mediaNode["type"].cleanString() ?: "ANIME"
                                    val mType = if (mTypeStr.equals("MANGA", ignoreCase = true)) {
                                        if (mFormat.equals("NOVEL", ignoreCase = true)) MediaType.NOVEL else MediaType.MANGA
                                    } else {
                                        if (mFormat.equals("MOVIE", ignoreCase = true)) MediaType.MOVIE else MediaType.ANIME
                                    }

                                    productionCredits.add(
                                        StaffProductionCreditItem(
                                            mediaId = mId,
                                            mediaTitle = mTitle,
                                            mediaCoverUrl = mCover,
                                            mediaFormat = mFormat,
                                            mediaYear = mYear,
                                            staffRole = staffRole,
                                            mediaType = mType
                                        )
                                    )
                                }

                                detail = AnimeVoiceActorDetail(
                                    staffId = sId,
                                    name = name,
                                    nativeName = native,
                                    imageUrl = img,
                                    biography = rawBio,
                                    age = age,
                                    dateOfBirth = dobStr,
                                    homeTown = home,
                                    primaryOccupations = occupations,
                                    roles = rolesList,
                                    gender = gender,
                                    yearsActive = yearsStr,
                                    bloodType = blood,
                                    language = lang,
                                    favourites = favs,
                                    productionCredits = productionCredits
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
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Back Navigation Button (if transitioning from character)
        if (onBack != null) {
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
        }

        // Staff Profile Header (100% Unboxed)
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

                val primaryOcc = detail?.primaryOccupations.orEmpty()
                if (primaryOcc.isNotEmpty()) {
                    Text(
                        text = primaryOcc.joinToString(" • "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentArctic
                    )
                }
            }
        }

        // 100% Unboxed Vitals (NO BOXES / NO CHIPS)
        val vitalsList = buildList {
            detail?.age?.let { add("$it yrs") }
            detail?.gender?.let { add(it) }
            detail?.dateOfBirth?.let { add("Born $it") }
            detail?.homeTown?.let { add(it) }
            detail?.yearsActive?.let { add("Active $it") }
            detail?.bloodType?.let { add("Blood $it") }
            formatCompactNumber(detail?.favourites)?.let { add("$it ♥") }
        }
        if (vitalsList.isNotEmpty()) {
            Text(
                text = vitalsList.joinToString("  •  "),
                fontSize = 12.sp,
                color = TextSecondary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Biography with Clickable Links
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

                AniListMarkdownText(
                    text = bioText,
                    collapsedMaxLines = 4
                )
            }
        }

        // Production Credits Rail (Director, Music, Creator, etc.)
        val productions = detail?.productionCredits.orEmpty()
        if (productions.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Production Credits",
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
                    itemsIndexed(productions, key = { index, it -> "${it.mediaId}_${it.staffRole}_$index" }) { _, prodItem ->
                        Column(
                            modifier = Modifier
                                .width(96.dp)
                                .clickable {
                                    val rec = RecommendationItem(
                                        title = prodItem.mediaTitle,
                                        coverUrl = prodItem.mediaCoverUrl ?: "",
                                        bannerUrl = prodItem.mediaCoverUrl,
                                        mediaType = prodItem.mediaType,
                                        rating = null,
                                        tmdbId = null,
                                        anilistId = prodItem.mediaId,
                                        malId = null,
                                        releaseYear = prodItem.mediaYear?.toString()
                                    )
                                    onOpenMedia(rec)
                                },
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            ) {
                                if (!prodItem.mediaCoverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(prodItem.mediaCoverUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = prodItem.mediaTitle,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.matchParentSize()
                                    )
                                }
                            }

                            Text(
                                text = prodItem.mediaTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = prodItem.staffRole,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentArctic,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            val metaStr = buildList {
                                prodItem.mediaFormat?.let { add(it) }
                                prodItem.mediaYear?.let { add(it.toString()) }
                            }.joinToString(" • ")

                            if (metaStr.isNotBlank()) {
                                Text(
                                    text = metaStr,
                                    fontSize = 10.sp,
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

        // Voiced Characters Rail (Unboxed with Paired Art)
        val roles = detail?.roles.orEmpty()
        if (roles.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Voiced Characters",
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
                    itemsIndexed(roles, key = { index, it -> "${it.mediaId}_${it.characterId}_$index" }) { _, roleItem ->
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
