package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.enrichment.EnrichmentRepository
import com.omnitrack.app.data.model.AnimeCharacterItem
import com.omnitrack.app.data.model.AnimeStaffItem
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.data.model.UserProfileData
import com.omnitrack.app.data.model.UserFavoriteMedia
import com.omnitrack.app.data.model.UserFavoriteCharacter
import com.omnitrack.app.data.model.UserFavoriteStaff
import com.omnitrack.app.ui.components.AniListMarkdownText
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import kotlinx.coroutines.launch

/**
 * 1:1 Dartotsu User Profile Sheet & Social Engine:
 * - 100% Unboxed Pure AMOLED Black `#000000` design (zero boxy containers).
 * - Identifies `isSelf`: hides follow & DM buttons, shows "Your Profile" badge.
 * - Clickable favorite anime & manga posters that open detail previews.
 * - Unboxed statistics matrix, score distribution histogram, and genre breakdowns.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileSheet(
    username: String?,
    anilistUserId: Int?,
    userAuthToken: String? = null,
    currentUsername: String? = null,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null,
    onOpenCharacter: ((AnimeCharacterItem) -> Unit)? = null,
    onOpenStaff: ((AnimeStaffItem) -> Unit)? = null,
    enrichmentRepository: EnrichmentRepository = remember { EnrichmentRepository() },
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<UserProfileData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    var isSendingFollow by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Scores & Analytics

    var showMessageInput by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }

    val isSelf = remember(username, profile?.username, currentUsername) {
        val target = profile?.username ?: username
        !target.isNullOrBlank() && !currentUsername.isNullOrBlank() &&
            target.equals(currentUsername, ignoreCase = true)
    }

    BackHandler(enabled = true) {
        onDismiss()
    }

    LaunchedEffect(username, anilistUserId) {
        isLoading = true
        val data = enrichmentRepository.fetchAniListUserProfile(
            username = username,
            userId = anilistUserId,
            userAuthToken = userAuthToken
        )
        profile = data
        if (data != null) {
            isFollowing = data.isFollowing
        }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        val p = profile
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = AccentArctic,
                    strokeWidth = 3.dp
                )
            }
        } else if (p == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "User Profile Unavailable",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Could not resolve an AniList user profile for $username.",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    if (!p.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(p.bannerUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.linearGradient(
                                        listOf(AccentArctic.copy(alpha = 0.35f), Color.Black)
                                    )
                                )
                        )
                    }

                    // Scrim gradient
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black)
                                )
                            )
                    )
                }

                // Avatar, Info & Follow / Self Badge Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(2.dp, AccentArctic, CircleShape)
                    ) {
                        if (!p.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(p.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = p.username,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Name, Handle & Actions
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = p.username,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${p.username} • #${p.userId}",
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 12.sp
                        )
                    }

                    if (isSelf) {
                        // User's own profile: show subtle clean badge instead of Follow / DM
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Your Profile",
                                color = AccentArctic,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Other user's profile: Follow Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isFollowing) Color.White.copy(alpha = 0.12f) else AccentArctic)
                                .border(
                                    1.dp,
                                    if (isFollowing) GlassBorder else AccentArctic,
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable(enabled = !isSendingFollow) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (userAuthToken.isNullOrBlank()) {
                                        Toast.makeText(context, "Log in with AniList in Settings to follow users", Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }
                                    scope.launch {
                                        isSendingFollow = true
                                        val success = enrichmentRepository.toggleFollowUser(p.userId, userAuthToken)
                                        if (success) {
                                            isFollowing = !isFollowing
                                        }
                                        isSendingFollow = false
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (isFollowing) Color.White else Color.Black,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = if (isFollowing) Color.White else Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Message Action Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, GlassBorder, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showMessageInput = !showMessageInput
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mail,
                                contentDescription = "Message",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                // Direct Message Inline Composer (for other users)
                if (showMessageInput && !isSelf) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BasicTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            cursorBrush = SolidColor(AccentArctic),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (messageText.isNotBlank()) {
                                    if (userAuthToken.isNullOrBlank()) {
                                        Toast.makeText(context, "Log in with AniList in Settings to send DMs", Toast.LENGTH_SHORT).show()
                                        return@KeyboardActions
                                    }
                                    scope.launch {
                                        isSendingMessage = true
                                        val ok = enrichmentRepository.sendDirectMessage(p.userId, messageText.trim(), userAuthToken)
                                        isSendingMessage = false
                                        if (ok) {
                                            Toast.makeText(context, "Message sent to @${p.username}!", Toast.LENGTH_SHORT).show()
                                            messageText = ""
                                            showMessageInput = false
                                        } else {
                                            Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (messageText.isEmpty()) {
                                    Text(
                                        text = "Send private message to @${p.username}...",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        if (messageText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !isSendingMessage) {
                                        if (userAuthToken.isNullOrBlank()) {
                                            Toast.makeText(context, "Log in with AniList in Settings to send DMs", Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        scope.launch {
                                            isSendingMessage = true
                                            val ok = enrichmentRepository.sendDirectMessage(p.userId, messageText.trim(), userAuthToken)
                                            isSendingMessage = false
                                            if (ok) {
                                                Toast.makeText(context, "Message sent to @${p.username}!", Toast.LENGTH_SHORT).show()
                                                messageText = ""
                                                showMessageInput = false
                                            } else {
                                                Toast.makeText(context, "Failed to send message", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .padding(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = AccentArctic,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 3-Tab Switcher: Overview, Feed, Scores & Stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overview",
                        color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.50f),
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = 0
                            }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = 1
                            }
                    ) {
                        Text(
                            text = "Feed",
                            color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.50f),
                            fontSize = 17.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.SemiBold
                        )
                        if (p.activities.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selectedTab == 1) AccentArctic.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${p.activities.size}",
                                    color = if (selectedTab == 1) AccentArctic else Color.White.copy(alpha = 0.70f),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = "Scores & Stats",
                        color = if (selectedTab == 2) Color.White else Color.White.copy(alpha = 0.50f),
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = 2
                            }
                    )
                }

                if (selectedTab == 0) {
                    // TAB 0: OVERVIEW
                    // Bio
                    if (!p.bio.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "About",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            AniListMarkdownText(
                                text = p.bio,
                                primaryColor = Color.White,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp
                            )
                        }
                    }

                    // Pure Unboxed Statistics Matrix (zero enclosing box)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "STATISTICS",
                            color = AccentArctic,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(title = "Anime", value = "${p.animeCount}")
                            StatCell(title = "Episodes", value = "${p.episodesWatched}")
                            StatCell(title = "Days", value = String.format("%.1f", p.animeDaysWatched))
                            StatCell(title = "Mean Score", value = if (p.animeMeanScore > 0) "${p.animeMeanScore.toInt()}%" else "—")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(title = "Manga", value = "${p.mangaCount}")
                            StatCell(title = "Chapters", value = "${p.chaptersRead}")
                            StatCell(title = "Mean Score", value = if (p.mangaMeanScore > 0) "${p.mangaMeanScore.toInt()}%" else "—")
                            StatCell(title = "Follower", value = if (p.isFollower) "Yes" else "No")
                        }
                    }

                    // Favorite Anime Rail (Clickable!)
                    if (p.favoriteAnime.isNotEmpty()) {
                        FavoriteMediaRail(
                            title = "Favorite Anime",
                            list = p.favoriteAnime,
                            onOpenMediaPreview = onOpenMediaPreview
                        )
                    }

                    // Favorite Manga Rail (Clickable!)
                    if (p.favoriteManga.isNotEmpty()) {
                        FavoriteMediaRail(
                            title = "Favorite Manga",
                            list = p.favoriteManga,
                            onOpenMediaPreview = onOpenMediaPreview
                        )
                    }

                    // Favorite Characters Rail (Clickable!)
                    if (p.favoriteCharacters.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Favorite Characters",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                items(p.favoriteCharacters, key = { it.id }) { char ->
                                    Column(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable {
                                                onOpenCharacter?.invoke(
                                                    AnimeCharacterItem(
                                                        characterId = char.id,
                                                        characterName = char.name,
                                                        characterRole = "FAVORITE",
                                                        characterImageUrl = char.imageUrl
                                                    )
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(SurfaceCard)
                                                .border(1.dp, GlassBorder, CircleShape)
                                        ) {
                                            if (!char.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(char.imageUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = char.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        Text(
                                            text = char.name,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Favorite Staff Rail (Clickable!)
                    if (p.favoriteStaff.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Favorite Staff",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp)
                            ) {
                                items(p.favoriteStaff, key = { it.id }) { st ->
                                    Column(
                                        modifier = Modifier
                                            .width(72.dp)
                                            .clickable {
                                                onOpenStaff?.invoke(
                                                    AnimeStaffItem(
                                                        staffId = st.id,
                                                        name = st.name,
                                                        role = "FAVORITE",
                                                        imageUrl = st.imageUrl
                                                    )
                                                )
                                            },
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(SurfaceCard)
                                                .border(1.dp, GlassBorder, CircleShape)
                                        ) {
                                            if (!st.imageUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(st.imageUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = st.name,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                        Text(
                                            text = st.name,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else if (selectedTab == 1) {
                    // TAB 1: USER ACTIVITY FEED (Dartotsu 1:1 Parity)
                    if (p.activities.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.30f),
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No Recent Activity",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "This user hasn't posted or updated any list entries recently.",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            p.activities.forEach { act ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (!act.mediaTitle.isNullOrBlank()) {
                                                onOpenMediaPreview?.invoke(
                                                    RecommendationItem(
                                                        title = act.mediaTitle,
                                                        coverUrl = act.coverUrl ?: "",
                                                        mediaType = MediaType.ANIME,
                                                        anilistId = null
                                                    )
                                                )
                                            }
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    if (!act.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(act.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = act.mediaTitle,
                                            modifier = Modifier
                                                .width(48.dp)
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = act.type.replace("_", " "),
                                                color = AccentArctic,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = act.timeAgo,
                                                color = Color.White.copy(alpha = 0.50f),
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (!act.mediaTitle.isNullOrBlank()) {
                                            Text(
                                                text = act.mediaTitle,
                                                color = Color.White,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = act.text,
                                            color = Color.White.copy(alpha = 0.85f),
                                            fontSize = 12.5.sp,
                                            lineHeight = 17.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // TAB 2: SCORES & ANALYTICS (Dartotsu Parity with Score Distribution, Status, Format, and Top Genres)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(26.dp)
                    ) {
                        // 1. SCORE DISTRIBUTION HISTOGRAM
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "SCORE DISTRIBUTION",
                                color = AccentArctic,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Shows how many anime and manga titles this user rated at each score (1–10 scale).",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            // Quick score summary row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mean Score: ${if (p.animeMeanScore > 0) "${p.animeMeanScore.toInt()}%" else "—"}",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Total Scored: ${p.scoreDistribution.sumOf { it.second }} titles",
                                    color = Color.White.copy(alpha = 0.70f),
                                    fontSize = 12.sp
                                )
                            }

                            val maxCount = (p.scoreDistribution.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                            // 10-Score Column Bars (1 through 10)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                (1..10).forEach { score ->
                                    val count = p.scoreDistribution.find { it.first == score }?.second ?: 0
                                    val barHeightFraction = (count.toFloat() / maxCount).coerceIn(0f, 1f)

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Exact Count above bar
                                        Text(
                                            text = if (count > 0) "$count" else "·",
                                            color = if (count > 0) Color.White else Color.White.copy(alpha = 0.25f),
                                            fontSize = 10.sp,
                                            fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal
                                        )

                                        // The Bar itself
                                        Box(
                                            modifier = Modifier
                                                .width(18.dp)
                                                .height((80.dp * barHeightFraction).coerceAtLeast(if (count > 0) 6.dp else 2.dp))
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(
                                                    when {
                                                        count == 0 -> Color.White.copy(alpha = 0.08f)
                                                        score >= 8 -> AccentJade
                                                        score >= 5 -> AccentArctic
                                                        else -> AccentArctic.copy(alpha = 0.45f)
                                                    }
                                                )
                                        )

                                        // Bottom Score Label
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            Text(
                                                text = "$score",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. STATUS BREAKDOWN (Watching, Completed, Paused, Dropped, Planning)
                        if (p.statusBreakdown.isNotEmpty()) {
                            val totalStatus = p.statusBreakdown.sumOf { it.second }.coerceAtLeast(1)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "STATUS BREAKDOWN",
                                    color = AccentArctic,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Library entries categorized by current viewing status.",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    p.statusBreakdown.forEach { (status, count) ->
                                        val percent = (count * 100) / totalStatus
                                        val statusColor = when (status.uppercase()) {
                                            "CURRENT" -> Color(0xFF00E676)
                                            "COMPLETED" -> Color(0xFF00B0FF)
                                            "PAUSED" -> Color(0xFFFFD600)
                                            "DROPPED" -> Color(0xFFFF5252)
                                            else -> Color(0xFFB0BEC5)
                                        }
                                        val displayName = when (status.uppercase()) {
                                            "CURRENT" -> "Watching"
                                            "COMPLETED" -> "Completed"
                                            "PAUSED" -> "Paused"
                                            "DROPPED" -> "Dropped"
                                            "PLANNING" -> "Planning"
                                            else -> status.replaceFirstChar { it.uppercase() }
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(statusColor)
                                                    )
                                                    Text(
                                                        text = displayName,
                                                        color = Color.White,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Text(
                                                    text = "$count ($percent%)",
                                                    color = Color.White.copy(alpha = 0.70f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth((count.toFloat() / totalStatus).coerceIn(0.04f, 1f))
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(statusColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. FORMAT BREAKDOWN (TV, Movie, OVA, Manga, etc.)
                        if (p.formatBreakdown.isNotEmpty()) {
                            val totalFormat = p.formatBreakdown.sumOf { it.second }.coerceAtLeast(1)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "FORMAT BREAKDOWN",
                                    color = AccentArctic,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Media formats in this user's anime library.",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    p.formatBreakdown.forEach { (fmt, count) ->
                                        val percent = (count * 100) / totalFormat
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = fmt.replace("_", " "),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "$count ($percent%)",
                                                    color = AccentArctic,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. TOP GENRES
                        if (p.topGenres.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "TOP GENRES",
                                    color = AccentArctic,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )

                                val maxGenre = (p.topGenres.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                                p.topGenres.take(6).forEach { (genre, count) ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = genre,
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "$count titles",
                                                color = Color.White.copy(alpha = 0.80f),
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth((count.toFloat() / maxGenre).coerceIn(0.05f, 1f))
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp))
                                                    .background(AccentArctic)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun StatCell(title: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.80f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FavoriteMediaRail(
    title: String,
    list: List<com.omnitrack.app.data.model.UserFavoriteMedia>,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)?
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp)
        ) {
            items(list, key = { it.id }) { item ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clickable {
                            onOpenMediaPreview?.invoke(
                                RecommendationItem(
                                    title = item.title,
                                    coverUrl = item.coverUrl,
                                    mediaType = item.mediaType,
                                    anilistId = item.id
                                )
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
