package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
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
import com.omnitrack.app.data.model.UserActivityDay
import com.omnitrack.app.data.model.UserActivityItem
import com.omnitrack.app.data.model.UserFavoriteCharacter
import com.omnitrack.app.data.model.UserFavoriteMedia
import com.omnitrack.app.data.model.UserFavoriteStaff
import com.omnitrack.app.data.model.UserMediaListItem
import com.omnitrack.app.data.model.UserProfileData
import com.omnitrack.app.data.model.UserStatEntry
import com.omnitrack.app.data.model.UserStatSet
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AccentRose
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1:1 Dartotsu-grade User Profile, Feed & Social Analytics Sheet:
 * - 100% Unboxed Pure AMOLED Black `#000000` design.
 * - Social Actions: Subscribe/Follow, Share Profile, Share Card, Open in Browser, Copy Link, Copy User ID.
 * - Unboxed Activity Feed with chips filter: All, Anime, Manga, Messages.
 * - Full Media List Viewer: Clickable Anime/Manga counts open browsable list with Status filter, search, sort, and Random Entry picker!
 * - Activity Heatmap: 52-week calendar grid across months showing contributions.
 * - Deep Analytics & Scores: Anime / Manga toggle, Metric Switcher (Count, Time, Mean Score), and 12 breakdown charts:
 *   Scores, Formats, Statuses, Lengths, Release Years, Start Years, Genres, Tags, Countries, Voice Actors, Staff, Studios.
 * - Rich formatting for user bio and comments via CommentRichContent.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UserProfileSheet(
    username: String?,
    anilistUserId: Int?,
    userAuthToken: String? = null,
    currentUsername: String? = null,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)? = null,
    onOpenCharacter: ((AnimeCharacterItem) -> Unit)? = null,
    onOpenStaff: ((AnimeStaffItem) -> Unit)? = null,
    enrichmentRepository: EnrichmentRepository = remember { EnrichmentRepository() },
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf<UserProfileData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    var isSendingFollow by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Overview, 1: Feed, 2: Scores & Stats

    var showMessageInput by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }
    var isSendingMessage by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    // User Media List sheet state (Anime / Manga list browsing)
    var browsingMediaListType by rememberSaveable { mutableStateOf<Boolean?>(null) } // true: anime, false: manga, null: closed

    // Feed Filter State: "ALL", "ANIME", "MANGA", "TEXT"
    var feedFilter by remember { mutableStateOf("ALL") }

    // Analytics Media Toggle: true = Anime, false = Manga
    var analyticsIsAnime by remember { mutableStateOf(true) }
    // Analytics Metric: 0 = Count, 1 = Time (Hours/Chapters), 2 = Mean Score
    var analyticsMetric by remember { mutableIntStateOf(0) }

    val isSelf = remember(username, profile?.username, currentUsername) {
        val target = profile?.username ?: username
        !target.isNullOrBlank() && !currentUsername.isNullOrBlank() &&
            target.equals(currentUsername, ignoreCase = true)
    }

    BackHandler(enabled = true) {
        if (browsingMediaListType != null) {
            browsingMediaListType = null
        } else {
            onDismiss()
        }
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
        onDismissRequest = {
            if (browsingMediaListType != null) {
                browsingMediaListType = null
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        modifier = Modifier.navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            // Background presentation matching detail page
            when (presentationSettings?.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(
                        image = backdropBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(
                        imageUrl = backdropTargetUrl,
                        modifier = Modifier.fillMaxSize()
                    ) {}
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(AmoledBlack))
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
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
        } else if (browsingMediaListType != null) {
            // Full Browsable User Media List Sheet
            UserMediaListSheetView(
                userId = p.userId,
                username = p.username,
                isAnime = browsingMediaListType == true,
                enrichmentRepository = enrichmentRepository,
                onBack = { browsingMediaListType = null },
                onOpenMediaPreview = onOpenMediaPreview
            )
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
                                        listOf(AccentArctic.copy(alpha = 0.35f), AmoledBlack)
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
                                    listOf(Color.Transparent, AmoledBlack)
                                )
                            )
                    )
                }

                // Avatar, Info, Social Menu & Follow Button Row
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
                        // User's own profile badge
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
                                    tint = if (isFollowing) Color.White else AmoledBlack,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isFollowing) "Following" else "Follow",
                                    color = if (isFollowing) Color.White else AmoledBlack,
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

                    // 3-Dots Social Overflow Menu (Subscribe, Share Profile, Share Card, Browser, Copy Link/ID)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { showOverflowMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier
                                .background(AmoledBlack)
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                        ) {
                            if (!isSelf) {
                                DropdownMenuItem(
                                    text = { Text(if (isFollowing) "Unsubscribe / Unfollow" else "Subscribe / Follow", color = Color.White, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp)) },
                                    onClick = {
                                        showOverflowMenu = false
                                        if (userAuthToken.isNullOrBlank()) {
                                            Toast.makeText(context, "Log in with AniList in Settings to follow", Toast.LENGTH_SHORT).show()
                                        } else {
                                            scope.launch {
                                                isSendingFollow = true
                                                val success = enrichmentRepository.toggleFollowUser(p.userId, userAuthToken)
                                                if (success) isFollowing = !isFollowing
                                                isSendingFollow = false
                                            }
                                        }
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Share Profile", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, "https://anilist.co/user/${p.username}")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share ${p.username}'s Profile"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Card", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    val cardText = "${p.username}'s Anime Stats:\nAnime: ${p.animeCount} • Episodes: ${p.episodesWatched} • Score: ${if (p.animeMeanScore > 0) "${p.animeMeanScore.toInt()}%" else "—"}\nManga: ${p.mangaCount} • Chapters: ${p.chaptersRead}\nhttps://anilist.co/user/${p.username}"
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_TEXT, cardText)
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Card"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in Browser", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://anilist.co/user/${p.username}")))
                                    } catch (_: Exception) {}
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy Link", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    clipboard.setText(AnnotatedString("https://anilist.co/user/${p.username}"))
                                    Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy User ID", color = Color.White, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    clipboard.setText(AnnotatedString("${p.userId}"))
                                    Toast.makeText(context, "User ID #${p.userId} copied", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // Direct Message Inline Composer
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

                // Quick Clickable Media List Action Pills: [ 📺 Anime List (N) ] and [ 📖 Manga List (N) ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .clickable { browsingMediaListType = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Anime List (${p.animeCount})",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                            .clickable { browsingMediaListType = false }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Manga List (${p.mangaCount})",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 3-Tab Switcher: Overview, Feed, Scores & Stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    // Bio formatted with CommentRichContent so gifs, images, links and markdown render properly!
                    if (!p.bio.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "About",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            CommentRichContent(
                                content = p.bio,
                                modifier = Modifier.fillMaxWidth()
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
                            StatCell(title = "Anime", value = "${p.animeCount}") { browsingMediaListType = true }
                            StatCell(title = "Episodes", value = "${p.episodesWatched}")
                            StatCell(title = "Days", value = String.format("%.1f", p.animeDaysWatched))
                            StatCell(title = "Mean Score", value = if (p.animeMeanScore > 0) "${p.animeMeanScore.toInt()}%" else "—")
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatCell(title = "Manga", value = "${p.mangaCount}") { browsingMediaListType = false }
                            StatCell(title = "Chapters", value = "${p.chaptersRead}")
                            StatCell(title = "Mean Score", value = if (p.mangaMeanScore > 0) "${p.mangaMeanScore.toInt()}%" else "—")
                            StatCell(title = "Follower", value = if (p.isFollower) "Yes" else "No")
                        }
                    }

                    // Favorite Anime Rail
                    if (p.favoriteAnime.isNotEmpty()) {
                        FavoriteMediaRail(
                            title = "Favorite Anime",
                            list = p.favoriteAnime,
                            onOpenMediaPreview = onOpenMediaPreview
                        )
                    }

                    // Favorite Manga Rail
                    if (p.favoriteManga.isNotEmpty()) {
                        FavoriteMediaRail(
                            title = "Favorite Manga",
                            list = p.favoriteManga,
                            onOpenMediaPreview = onOpenMediaPreview
                        )
                    }

                    // Favorite Characters Rail
                    if (p.favoriteCharacters.isNotEmpty()) {
                        FavoriteCharacterRail(
                            title = "Favorite Characters",
                            list = p.favoriteCharacters,
                            onOpenCharacter = onOpenCharacter
                        )
                    }

                    // Favorite Staff Rail
                    if (p.favoriteStaff.isNotEmpty()) {
                        FavoriteStaffRail(
                            title = "Favorite Staff",
                            list = p.favoriteStaff,
                            onOpenStaff = onOpenStaff
                        )
                    }

                } else if (selectedTab == 1) {
                    // TAB 1: USER ACTIVITY FEED (100% Unboxed AMOLED Parity)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Filter Chips: All, Anime, Manga, Messages
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("ALL" to "All", "ANIME" to "Anime", "MANGA" to "Manga", "TEXT" to "Messages").forEach { (filterKey, label) ->
                                val isSelected = feedFilter == filterKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isSelected) AccentArctic else Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, if (isSelected) AccentArctic else GlassBorder, RoundedCornerShape(999.dp))
                                        .clickable { feedFilter = filterKey }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AmoledBlack else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        val filteredActivities = remember(p.activities, feedFilter) {
                            when (feedFilter) {
                                "ANIME" -> p.activities.filter { it.type.contains("ANIME", ignoreCase = true) || it.type.contains("LIST", ignoreCase = true) }
                                "MANGA" -> p.activities.filter { it.type.contains("MANGA", ignoreCase = true) }
                                "TEXT" -> p.activities.filter { it.type.contains("TEXT", ignoreCase = true) || it.type.contains("MESSAGE", ignoreCase = true) }
                                else -> p.activities
                            }
                        }

                        if (filteredActivities.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
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
                                    text = "No Activities",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "No recent updates for the selected filter.",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Unboxed feed items separated by subtle horizontal dividers (zero cards)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                filteredActivities.forEachIndexed { index, act ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!act.mediaTitle.isNullOrBlank()) {
                                                    onOpenMediaPreview?.invoke(
                                                        RecommendationItem(
                                                            title = act.mediaTitle,
                                                            coverUrl = act.coverUrl ?: "",
                                                            mediaType = act.mediaType,
                                                            anilistId = act.mediaId
                                                        )
                                                    )
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                                    .clip(RoundedCornerShape(8.dp)),
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
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            CommentRichContent(
                                                content = act.text,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    if (index < filteredActivities.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                                    }
                                }
                            }
                        }
                    }

                } else {
                    // TAB 2: SCORES & ANALYTICS (Activity Heatmap + 12 Deep Breakdown Charts)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        // 1. ACTIVITY HEATMAP (52-week calendar grid across months)
                        ActivityHeatmapSection(history = p.activityHistory)

                        // Segmented Anime / Manga Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .padding(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (analyticsIsAnime) AccentArctic else Color.Transparent)
                                    .clickable { analyticsIsAnime = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Anime Analytics",
                                    color = if (analyticsIsAnime) AmoledBlack else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!analyticsIsAnime) AccentArctic else Color.Transparent)
                                    .clickable { analyticsIsAnime = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Manga Analytics",
                                    color = if (!analyticsIsAnime) AmoledBlack else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Metric Switcher: Count, Time, Mean Score
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val timeLabel = if (analyticsIsAnime) "Time (Hours)" else "Chapters Read"
                            listOf(0 to "Count", 1 to timeLabel, 2 to "Mean Score").forEach { (idx, label) ->
                                val isSelected = analyticsMetric == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(if (isSelected) AccentArctic else Color.White.copy(alpha = 0.06f))
                                        .border(1.dp, if (isSelected) AccentArctic else GlassBorder, RoundedCornerShape(999.dp))
                                        .clickable { analyticsMetric = idx }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) AmoledBlack else TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        val activeStats = if (analyticsIsAnime) p.animeStats else p.mangaStats

                        // Dartotsu Overview Summary Cards
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val timeStr = if (analyticsIsAnime) "${activeStats.minutesWatched / 60}h" else "${activeStats.chaptersRead} ch"
                            val timeSub = if (analyticsIsAnime) "Time Watched" else "Chapters Read"
                            val scoreStr = if (activeStats.meanScore > 0) "${activeStats.meanScore.toInt()}%" else "—"

                            listOf(
                                "${activeStats.count}" to "Total Titles",
                                timeStr to timeSub,
                                scoreStr to "Mean Score",
                                String.format(Locale.US, "%.1f", activeStats.standardDeviation) to "Std Dev"
                            ).forEach { (valStr, label) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = valStr,
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = label,
                                            color = Color.White.copy(alpha = 0.55f),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Render all 12 Deep Categories
                        val categoryOrder = listOf(
                            "scores" to "SCORE DISTRIBUTION",
                            "formats" to "FORMAT BREAKDOWN",
                            "statuses" to "STATUS BREAKDOWN",
                            "lengths" to "LENGTH BREAKDOWN",
                            "releaseYears" to "RELEASE YEAR BREAKDOWN",
                            "startYears" to "START YEAR BREAKDOWN",
                            "genres" to "GENRES",
                            "tags" to "TAGS",
                            "countries" to "COUNTRIES OF ORIGIN",
                            "voiceActors" to "VOICE ACTORS (SEIYUU)",
                            "staff" to "STAFF & DIRECTORS",
                            "studios" to "ANIMATION STUDIOS"
                        )

                        categoryOrder.forEach { (catKey, catTitle) ->
                            val entries = activeStats.categories[catKey].orEmpty()
                            if (entries.isNotEmpty()) {
                                AnalyticsCategoryChart(
                                    categoryKey = catKey,
                                    title = catTitle,
                                    entries = entries,
                                    metric = analyticsMetric,
                                    isAnime = analyticsIsAnime
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}
}

/**
 * 52-Week Activity Heatmap with Month Headers and Levels 0..4
 */
@Composable
private fun ActivityHeatmapSection(history: List<UserActivityDay>) {
    val totalAmount = remember(history) { history.sumOf { it.amount } }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACTIVITY HISTORY",
                color = AccentArctic,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "$totalAmount contributions in past year",
                color = Color.White.copy(alpha = 0.60f),
                fontSize = 11.5.sp
            )
        }

        // 52-Week Horizontal Grid
        val columns = remember(history) {
            // Group 364 days into 52 weeks (7 days each)
            val padded = if (history.size < 364) {
                List(364 - history.size) { UserActivityDay(0L, 0, 0) } + history
            } else {
                history.takeLast(364)
            }
            padded.chunked(7)
        }

        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

        val heatmapScrollState = rememberScrollState()
        LaunchedEffect(columns) {
            heatmapScrollState.scrollTo(heatmapScrollState.maxValue)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(heatmapScrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Months header
            Row(
                modifier = Modifier.width((52 * 14).dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                months.forEach { m ->
                    Text(text = m, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                }
            }

            // 7 rows x 52 columns grid
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                columns.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            val color = when (day.level) {
                                1 -> AccentArctic.copy(alpha = 0.35f)
                                2 -> AccentArctic.copy(alpha = 0.60f)
                                3 -> AccentArctic.copy(alpha = 0.85f)
                                4 -> AccentArctic
                                else -> Color.White.copy(alpha = 0.05f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Less", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                listOf(0, 1, 2, 3, 4).forEach { lvl ->
                    val color = when (lvl) {
                        1 -> AccentArctic.copy(alpha = 0.35f)
                        2 -> AccentArctic.copy(alpha = 0.60f)
                        3 -> AccentArctic.copy(alpha = 0.85f)
                        4 -> AccentArctic
                        else -> Color.White.copy(alpha = 0.05f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
                Text(text = "More", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}

/**
 * Deep Analytics Multi-Variation Charts:
 * 1:1 Dartotsu Parity: ColumnChart for Scores, DonutChart for Formats/Statuses/Countries,
 * LineTrendline for Release/Start Years, RadarChart for Genres, and Ranked Bars for others.
 */
private val analyticsPalette = listOf(
    AccentArctic,          // cyan 0xFF38BDF8
    AccentJade,            // emerald 0xFF10B981
    AccentGold,            // amber gold 0xFFF59E0B
    AccentRose,            // soft red 0xFFF43F5E
    Color(0xFF818CF8),     // indigo
    Color(0xFFF472B6),     // pink
    Color(0xFF38BDF8),     // sky blue
    Color(0xFFFB923C),     // orange
    Color(0xFFA78BFA),     // violet
    Color(0xFF34D399),     // mint
    Color(0xFFFBBF24),     // bright yellow
    Color(0xFFF87171),     // light coral
    Color(0xFF2DD4BF),     // teal
    Color(0xFFE879F9),     // fuchsia
    Color(0xFF60A5FA),     // light blue
    Color(0xFFA3E635)      // lime
)

private fun formatCategoryEntryLabel(categoryKey: String, label: String, isAnime: Boolean): String {
    if (categoryKey.equals("lengths", ignoreCase = true)) {
        val unit = if (isAnime) "Episodes" else "Chapters"
        val singleUnit = if (isAnime) "Episode" else "Chapter"
        return when {
            label == "1" -> "1 $singleUnit"
            label.contains("-") -> {
                val parts = label.split("-")
                "${parts[0]}–${parts[1]} $unit"
            }
            label.endsWith("+") -> {
                "${label.removeSuffix("+")}+ $unit"
            }
            label.toIntOrNull() != null -> "$label $unit"
            else -> "$label $unit"
        }
    }
    return label
}

@Composable
private fun AnalyticsCategoryChart(
    categoryKey: String,
    title: String,
    entries: List<UserStatEntry>,
    metric: Int, // 0 = Count, 1 = Time, 2 = Mean Score
    isAnime: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = title,
            color = AccentArctic,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )

        when (categoryKey) {
            "scores" -> ScoreColumnChart(entries = entries, metric = metric, isAnime = isAnime)
            "formats", "statuses", "countries" -> {
                if (metric != 2) {
                    DonutRingChart(entries = entries, metric = metric, isAnime = isAnime)
                } else {
                    AnalyticsCategoryBars(entries = entries, metric = metric, isAnime = isAnime, categoryKey = categoryKey)
                }
            }
            "releaseYears", "startYears" -> YearTrendlineChart(entries = entries, metric = metric, isAnime = isAnime)
            "genres" -> {
                GenreRadarChart(entries = entries, metric = metric, isAnime = isAnime)
                AnalyticsCategoryBars(entries = entries, metric = metric, isAnime = isAnime, categoryKey = categoryKey)
            }
            else -> AnalyticsCategoryBars(entries = entries, metric = metric, isAnime = isAnime, categoryKey = categoryKey)
        }
    }
}

@Composable
private fun ScoreColumnChart(
    entries: List<UserStatEntry>,
    metric: Int,
    isAnime: Boolean
) {
    val map = remember(entries, metric) {
        val m = (1..10).associateWith { 0.0 }.toMutableMap()
        entries.forEach { e ->
            val scoreVal = e.label.toIntOrNull() ?: 0
            if (scoreVal in 1..10) {
                val v = when (metric) {
                    1 -> if (isAnime) e.minutesWatched / 60.0 else e.chaptersRead.toDouble()
                    2 -> e.meanScore
                    else -> e.count.toDouble()
                }
                m[scoreVal] = v
            }
        }
        m
    }
    val maxVal = map.values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            for (score in 1..10) {
                val v = map[score] ?: 0.0
                val fraction = (v / maxVal).toFloat().coerceIn(0.04f, 1f)
                val barColor = when {
                    score >= 8 -> AccentJade
                    score >= 6 -> AccentArctic
                    score >= 4 -> AccentGold
                    else -> AccentRose
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    if (v > 0) {
                        Text(
                            text = if (v >= 1000) "${(v / 1000).toInt()}k" else "${v.toInt()}",
                            color = Color.White.copy(alpha = 0.70f),
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(fraction * 0.78f)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$score",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Score Distribution Tier Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                Triple(AccentJade, "8-10", "Masterpiece"),
                Triple(AccentArctic, "6-7", "Good"),
                Triple(AccentGold, "4-5", "Average"),
                Triple(AccentRose, "1-3", "Low")
            ).forEach { (color, range, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = "$range $label",
                        color = Color.White.copy(alpha = 0.70f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DonutRingChart(
    entries: List<UserStatEntry>,
    metric: Int,
    isAnime: Boolean
) {
    val unitLabel = when (metric) {
        1 -> if (isAnime) "HOURS" else "CHAPTERS"
        2 -> "SCORE"
        else -> "ENTRIES"
    }

    val palette = analyticsPalette

    val validEntries = remember(entries, metric) {
        entries.mapNotNull { e ->
            val v = when (metric) {
                1 -> if (isAnime) e.minutesWatched / 60.0 else e.chaptersRead.toDouble()
                2 -> e.meanScore
                else -> e.count.toDouble()
            }
            if (v > 0) e.label to v else null
        }.sortedByDescending { it.second }.take(8)
    }

    val total = validEntries.sumOf { it.second }.coerceAtLeast(1.0)
    val totalStr = when (metric) {
        1 -> "${total.toInt()}h"
        2 -> "${(total / validEntries.size.coerceAtLeast(1)).toInt()}%"
        else -> "${total.toInt()}"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                var startAngle = -90f
                validEntries.forEachIndexed { i, item ->
                    val sweep = ((item.second / total) * 360f).toFloat()
                    val color = palette[i % palette.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalStr,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = unitLabel,
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Legend Pills
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            validEntries.forEachIndexed { i, item ->
                val pct = ((item.second / total) * 100).toInt()
                val color = palette[i % palette.size]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Text(
                        text = "${item.first} ($pct%)",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun YearTrendlineChart(
    entries: List<UserStatEntry>,
    metric: Int,
    isAnime: Boolean
) {
    val sorted = remember(entries) {
        entries.mapNotNull { e ->
            val yr = e.label.toIntOrNull()
            if (yr != null && yr in 1970..2030) yr to e else null
        }.sortedBy { it.first }
    }
    if (sorted.size < 2) {
        AnalyticsCategoryBars(entries = entries, metric = metric, isAnime = isAnime)
        return
    }

    val values = sorted.map { (_, e) ->
        when (metric) {
            1 -> if (isAnime) e.minutesWatched / 60.0 else e.chaptersRead.toDouble()
            2 -> e.meanScore
            else -> e.count.toDouble()
        }
    }
    val maxV = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(horizontal = 8.dp)
        ) {
            val w = size.width
            val h = size.height
            val step = w / (sorted.size - 1).coerceAtLeast(1)

            val path = Path()
            val fillPath = Path()

            sorted.forEachIndexed { i, _ ->
                val x = i * step
                val y = h - ((values[i] / maxV).toFloat() * (h - 14.dp.toPx())) - 7.dp.toPx()
                if (i == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((sorted.size - 1) * step, h)
            fillPath.close()

            // Gradient fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(AccentArctic.copy(alpha = 0.35f), Color.Transparent)
                )
            )

            // Line
            drawPath(
                path = path,
                color = AccentArctic,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // Circles at points
            sorted.forEachIndexed { i, _ ->
                val x = i * step
                val y = h - ((values[i] / maxV).toFloat() * (h - 14.dp.toPx())) - 7.dp.toPx()
                drawCircle(color = AccentArctic, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
            }
        }

        // Years along bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "${sorted.first().first}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            if (sorted.size > 2) {
                val mid = sorted[sorted.size / 2].first
                Text(text = "$mid", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
            Text(text = "${sorted.last().first}", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreRadarChart(
    entries: List<UserStatEntry>,
    metric: Int,
    isAnime: Boolean
) {
    val top = remember(entries, metric) {
        entries.map { e ->
            val v = when (metric) {
                1 -> if (isAnime) e.minutesWatched / 60.0 else e.chaptersRead.toDouble()
                2 -> e.meanScore
                else -> e.count.toDouble()
            }
            e.label to v
        }.sortedByDescending { it.second }.take(6)
    }
    if (top.size < 3) return

    val maxV = top.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = (size.minDimension / 2f) * 0.85f
                val n = top.size
                val angleStep = (2 * Math.PI / n).toFloat()

                // Draw concentric web rings
                for (level in 1..3) {
                    val ringR = r * (level / 3f)
                    val ringPath = Path()
                    for (i in 0 until n) {
                        val angle = -Math.PI / 2 + i * angleStep
                        val x = (cx + ringR * Math.cos(angle)).toFloat()
                        val y = (cy + ringR * Math.sin(angle)).toFloat()
                        if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                    }
                    ringPath.close()
                    drawPath(path = ringPath, color = Color.White.copy(alpha = 0.08f), style = Stroke(width = 1.dp.toPx()))
                }

                // Draw data polygon
                val dataPath = Path()
                top.forEachIndexed { i, item ->
                    val angle = -Math.PI / 2 + i * angleStep
                    val dataR = r * (item.second / maxV).toFloat().coerceIn(0.1f, 1f)
                    val x = (cx + dataR * Math.cos(angle)).toFloat()
                    val y = (cy + dataR * Math.sin(angle)).toFloat()
                    if (i == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
                    val vertexColor = analyticsPalette[i % analyticsPalette.size]
                    drawCircle(color = vertexColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                }
                dataPath.close()

                drawPath(
                    path = dataPath,
                    color = AccentArctic.copy(alpha = 0.20f),
                    style = Fill
                )
                drawPath(
                    path = dataPath,
                    color = AccentArctic.copy(alpha = 0.80f),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        // Explicit Genre Legend Pills
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            top.forEachIndexed { i, (genre, v) ->
                val displayVal = when (metric) {
                    1 -> if (isAnime) "${v.toInt()}h" else "${v.toInt()} ch"
                    2 -> String.format("%.1f", v)
                    else -> "${v.toInt()}"
                }
                val genreColor = analyticsPalette[i % analyticsPalette.size]
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(genreColor))
                    Text(
                        text = "$genre: $displayVal",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCategoryBars(
    entries: List<UserStatEntry>,
    metric: Int,
    isAnime: Boolean,
    categoryKey: String = ""
) {
    val sorted = remember(entries, metric) {
        when (metric) {
            1 -> entries.sortedByDescending { if (isAnime) it.minutesWatched else it.chaptersRead }
            2 -> entries.sortedByDescending { it.meanScore }
            else -> entries.sortedByDescending { it.count }
        }.take(10)
    }

    val maxMetricValue = remember(sorted, metric) {
        val maxValue = when (metric) {
            1 -> sorted.maxOfOrNull { if (isAnime) it.minutesWatched.toDouble() else it.chaptersRead.toDouble() } ?: 1.0
            2 -> 100.0
            else -> sorted.maxOfOrNull { it.count.toDouble() } ?: 1.0
        }
        maxValue.coerceAtLeast(1.0)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sorted.forEachIndexed { index, entry ->
            val metricVal = when (metric) {
                1 -> if (isAnime) entry.minutesWatched.toDouble() else entry.chaptersRead.toDouble()
                2 -> entry.meanScore
                else -> entry.count.toDouble()
            }

            val metricStr = when (metric) {
                1 -> if (isAnime) "${entry.minutesWatched / 60}h" else "${entry.chaptersRead} ch"
                2 -> if (entry.meanScore > 0) "${entry.meanScore.toInt()}%" else "—"
                else -> "${entry.count} titles"
            }

            val barFraction = (metricVal / maxMetricValue).toFloat().coerceIn(0.04f, 1f)
            val barColor = analyticsPalette[index % analyticsPalette.size]
            val displayLabel = formatCategoryEntryLabel(categoryKey, entry.label, isAnime)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(barColor)
                        )
                        Text(
                            text = displayLabel,
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = metricStr,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}

/**
 * Full Browsable User Media List Sheet (Anime & Manga List Parity)
 */
@Composable
private fun UserMediaListSheetView(
    userId: Int,
    username: String,
    isAnime: Boolean,
    enrichmentRepository: EnrichmentRepository,
    onBack: () -> Unit,
    onOpenMediaPreview: ((RecommendationItem) -> Unit)?
) {
    val context = LocalContext.current
    var mediaList by remember { mutableStateOf<List<UserMediaListItem>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedStatusFilter by rememberSaveable { mutableStateOf("ALL") }
    var sortOrder by rememberSaveable { mutableStateOf("SCORE") } // SCORE, TITLE, PROGRESS
    var isReverseSort by rememberSaveable { mutableStateOf(false) }
    val listState = rememberSaveable(isAnime, userId, saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(userId, isAnime) {
        isLoading = true
        mediaList = enrichmentRepository.fetchUserMediaList(userId, isAnime)
        isLoading = false
    }

    val filteredList = remember(mediaList, searchQuery, selectedStatusFilter, sortOrder, isReverseSort) {
        var result = mediaList.orEmpty()
        if (selectedStatusFilter != "ALL") {
            result = result.filter { it.status.equals(selectedStatusFilter, ignoreCase = true) }
        }
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.title.contains(searchQuery.trim(), ignoreCase = true) }
        }
        result = when (sortOrder) {
            "TITLE" -> result.sortedBy { it.title }
            "PROGRESS" -> result.sortedByDescending { it.progress }
            else -> result.sortedByDescending { it.score }
        }
        if (isReverseSort) result.reversed() else result
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Bar: Back, Title, Random Entry Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = if (isAnime) "Anime List" else "Manga List",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@$username • ${filteredList.size} entries",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.5.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Prominent Random Entry Pill Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentArctic.copy(alpha = 0.15f))
                        .border(1.dp, AccentArctic.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable {
                            if (filteredList.isNotEmpty()) {
                                val randomItem = filteredList.random()
                                onOpenMediaPreview?.invoke(
                                    RecommendationItem(
                                        title = randomItem.title,
                                        coverUrl = randomItem.coverUrl,
                                        mediaType = randomItem.mediaType,
                                        anilistId = randomItem.mediaId
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Random",
                            tint = AccentArctic,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Random",
                            color = AccentArctic,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Reverse Order Toggle Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isReverseSort) AccentArctic.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f))
                        .border(1.dp, if (isReverseSort) AccentArctic else GlassBorder, RoundedCornerShape(8.dp))
                        .clickable { isReverseSort = !isReverseSort }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Reverse Order",
                            tint = if (isReverseSort) AccentArctic else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isReverseSort) "Desc" else "Asc",
                            color = if (isReverseSort) AccentArctic else Color.White.copy(alpha = 0.75f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .border(1.dp, GlassBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                cursorBrush = SolidColor(AccentArctic),
                singleLine = true,
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text("Search title in list...", color = TextMuted, fontSize = 13.sp)
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }

        // Status Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val statusOptions = if (isAnime) {
                listOf("ALL" to "All", "CURRENT" to "Watching", "COMPLETED" to "Completed", "PAUSED" to "Paused", "DROPPED" to "Dropped", "PLANNING" to "Planning")
            } else {
                listOf("ALL" to "All", "CURRENT" to "Reading", "COMPLETED" to "Completed", "PAUSED" to "Paused", "DROPPED" to "Dropped", "PLANNING" to "Planning")
            }

            statusOptions.forEach { (key, label) ->
                val isSelected = selectedStatusFilter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) AccentArctic else Color.White.copy(alpha = 0.06f))
                        .border(1.dp, if (isSelected) AccentArctic else GlassBorder, RoundedCornerShape(999.dp))
                        .clickable { selectedStatusFilter = key }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) AmoledBlack else TextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentArctic, modifier = Modifier.size(32.dp))
            }
        } else if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("No entries found", color = TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenMediaPreview?.invoke(
                                    RecommendationItem(
                                        title = item.title,
                                        coverUrl = item.coverUrl,
                                        mediaType = item.mediaType,
                                        anilistId = item.mediaId
                                    )
                                )
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.title,
                            modifier = Modifier
                                .width(46.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = item.title,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Pill
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = item.status,
                                        color = AccentArctic,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Progress
                                val progressText = if (item.totalProgress != null && item.totalProgress > 0) {
                                    "${item.progress}/${item.totalProgress}"
                                } else {
                                    "${item.progress} eps"
                                }
                                Text(
                                    text = progressText,
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.5.sp
                                )

                                if (item.score > 0f) {
                                    Text(
                                        text = "★ ${item.score}",
                                        color = AccentGold,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
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
private fun StatCell(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Text(
            text = value,
            color = if (onClick != null) AccentArctic else Color.White,
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
    list: List<UserFavoriteMedia>,
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

@Composable
private fun FavoriteCharacterRail(
    title: String,
    list: List<UserFavoriteCharacter>,
    onOpenCharacter: ((AnimeCharacterItem) -> Unit)?
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
                        .width(76.dp)
                        .clickable {
                            onOpenCharacter?.invoke(
                                AnimeCharacterItem(
                                    characterId = item.id,
                                    characterName = item.name,
                                    characterRole = "FAVOURITE",
                                    characterImageUrl = item.imageUrl
                                )
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.5.dp, GlassBorder, CircleShape)
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteStaffRail(
    title: String,
    list: List<UserFavoriteStaff>,
    onOpenStaff: ((AnimeStaffItem) -> Unit)?
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
                        .width(76.dp)
                        .clickable {
                            onOpenStaff?.invoke(
                                AnimeStaffItem(
                                    staffId = item.id,
                                    name = item.name,
                                    role = "Staff",
                                    imageUrl = item.imageUrl
                                )
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.5.dp, GlassBorder, CircleShape)
                    ) {
                        if (!item.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(item.imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
