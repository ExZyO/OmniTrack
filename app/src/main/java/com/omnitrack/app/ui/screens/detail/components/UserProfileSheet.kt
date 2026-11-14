package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PersonAdd
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
import com.omnitrack.app.data.model.UserProfileData
import com.omnitrack.app.data.model.UserFavoriteMedia
import com.omnitrack.app.data.model.UserFavoriteCharacter
import com.omnitrack.app.data.model.UserFavoriteStaff
import com.omnitrack.app.ui.components.AniListMarkdownText
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import kotlinx.coroutines.launch

/**
 * Phase 4: Dartotsu User Profile Sheet & Social Engine.
 * Features:
 * - Banner backdrop + Circular avatar with verified ring
 * - AniList Follow / Unfollow mutation
 * - Direct Message composer bar with instant dispatch
 * - 2 Tabs: Overview (Bio, stats matrix, favorite rails) & Scores (Histogram & top genres)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    username: String?,
    anilistUserId: Int?,
    userAuthToken: String? = null,
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

                // Avatar, Info & Follow Row
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

                    // Follow Button
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

                // Direct Message Inline Composer
                if (showMessageInput) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(DarkSurfaceElevated)
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

                // 2-Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Overview",
                        color = if (selectedTab == 0) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = 0
                            }
                    )
                    Text(
                        text = "Scores & Analytics",
                        color = if (selectedTab == 1) Color.White else Color.White.copy(alpha = 0.55f),
                        fontSize = 17.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = 1
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

                    // User Stats Matrix
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "STATISTICS",
                            color = AccentArctic,
                            fontSize = 11.5.sp,
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

                    // Favorite Anime Rail
                    if (p.favoriteAnime.isNotEmpty()) {
                        FavoriteMediaRail(title = "Favorite Anime", list = p.favoriteAnime)
                    }

                    // Favorite Manga Rail
                    if (p.favoriteManga.isNotEmpty()) {
                        FavoriteMediaRail(title = "Favorite Manga", list = p.favoriteManga)
                    }

                    // Favorite Characters Rail
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
                                        modifier = Modifier.width(72.dp),
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
                                                    modifier = Modifier.fillMaxWidth(),
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

                    // Favorite Staff Rail
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
                                        modifier = Modifier.width(72.dp),
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
                                                    modifier = Modifier.fillMaxWidth(),
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

                } else {
                    // TAB 1: SCORES & ANALYTICS
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Score Distribution Histogram
                        if (p.scoreDistribution.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "SCORE DISTRIBUTION",
                                    color = AccentArctic,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )

                                val maxCount = (p.scoreDistribution.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    p.scoreDistribution.sortedBy { it.first }.forEach { (score, count) ->
                                        val barHeightFraction = (count.toFloat() / maxCount).coerceIn(0.08f, 1f)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(16.dp)
                                                    .height(80.dp * barHeightFraction)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(AccentArctic)
                                            )
                                            Text(
                                                text = "$score",
                                                color = Color.White.copy(alpha = 0.85f),
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Top Genres
                        if (p.topGenres.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurfaceElevated)
                                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "TOP GENRES",
                                    color = AccentArctic,
                                    fontSize = 11.5.sp,
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
private fun FavoriteMediaRail(title: String, list: List<com.omnitrack.app.data.model.UserFavoriteMedia>) {
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
                    modifier = Modifier.width(96.dp),
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
                            modifier = Modifier.fillMaxWidth(),
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
