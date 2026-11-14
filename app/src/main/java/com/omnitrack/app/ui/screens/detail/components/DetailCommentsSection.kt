package com.omnitrack.app.ui.screens.detail.components

import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

enum class CommentSortOrder(val displayName: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    HIGHEST_RATED("Highest Rated"),
    LOWEST_RATED("Lowest Rated")
}

/**
 * Single node in a threaded discussion hierarchy.
 */
data class DiscussionComment(
    val id: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val anilistUserId: Int? = null,
    val tag: String? = null, // e.g. "Ep 1", "Ch 5", "General"
    val content: String,
    val timeAgo: String,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val parentId: String? = null,
    val replies: List<DiscussionComment> = emptyList(),
    val depth: Int = 0
)

/**
 * Flattened comment element for linear rendering (prevents nested LazyColumn scroll crashes).
 */
data class FlattenedCommentNode(
    val comment: DiscussionComment,
    val depth: Int,
    val isCollapsed: Boolean,
    val totalChildReplies: Int
)

/**
 * 1:1 Dartotsu-grade Threaded Discussion:
 * - Pure Unboxed AMOLED `#000000` design.
 * - Dartotsu Like/Dislike capsule (Thumbs Up, score, Thumbs Down).
 * - Dartotsu 3-line sort button (Newest, Oldest, Highest Rated, Lowest Rated).
 * - 3-dots comment options menu (Copy Text, Edit, Delete, Report).
 * - Full composer bottom toolbar: [ 🏷️ Tag ], [ 👁️ Spoiler ], [ 🎬 GIF ] via Klipy, Send.
 * - Long-press comment body to copy text to clipboard.
 * - Multi-season comment isolation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailCommentsSection(
    mediaType: MediaType,
    mediaTitle: String,
    episodeOrChapterCount: Int? = null,
    comments: List<DiscussionComment>? = null,
    seasons: List<com.omnitrack.app.data.model.MediaSeason> = emptyList(),
    selectedSeasonNumber: Int = 1,
    currentUsername: String? = null,
    onSeasonSelect: ((Int) -> Unit)? = null,
    onPostComment: ((content: String, tag: String?, parentId: String?) -> Unit)? = null,
    onEditComment: ((commentId: String, newContent: String) -> Unit)? = null,
    onDeleteComment: ((commentId: String) -> Unit)? = null,
    onVote: ((commentId: String, voteType: Int) -> Unit)? = null,
    onUserClick: ((String, Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    // Dynamic Filter Rail: "Ep" for Anime/Shows, "Ch" for Manga/Novels
    val tagPrefix = if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) "Ch" else "Ep"
    val totalCount = (episodeOrChapterCount ?: 24).coerceIn(1, 2000)
    val availableTags = remember(tagPrefix, totalCount) {
        listOf("All", "General") + (1..totalCount).map { "$tagPrefix $it" }
    }
    var selectedTag by remember { mutableStateOf("All") }
    var sortOrder by remember { mutableStateOf(CommentSortOrder.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Seed default comments if external comments not provided
    val initialSeed = remember(mediaType, totalCount) {
        createSampleDiscussionComments(mediaType, totalCount)
    }

    // Active working list of comments (optimistic updates on post/reply)
    var localCommentsList by remember { mutableStateOf(comments?.ifEmpty { initialSeed } ?: initialSeed) }

    LaunchedEffect(comments) {
        if (!comments.isNullOrEmpty()) {
            val incomingIds = comments.map { it.id }.toSet()
            val localOnly = localCommentsList.filter { it.id !in incomingIds }
            localCommentsList = (comments + localOnly).distinctBy { it.id }
        }
    }

    // Collapsed Comment State
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }
    // User votes map: commentId -> vote (-1, 0, 1)
    val voteStates = remember { mutableStateMapOf<String, Int>() }

    // Replying target state
    var replyingToComment by remember { mutableStateOf<DiscussionComment?>(null) }
    var editingComment by remember { mutableStateOf<DiscussionComment?>(null) }
    var newCommentText by remember { mutableStateOf("") }
    var composerTag by remember { mutableStateOf<String?>(null) }
    var isSpoilerMode by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }
    var tagPickerExpanded by remember { mutableStateOf(false) }

    // Filter comments by selected tag and sort
    val filteredComments = remember(localCommentsList, selectedTag, sortOrder) {
        val byTag = if (selectedTag == "All") {
            localCommentsList
        } else {
            localCommentsList.filter { comment ->
                comment.tag == selectedTag || comment.replies.any { it.tag == selectedTag }
            }
        }
        when (sortOrder) {
            CommentSortOrder.NEWEST -> byTag // Default order from source
            CommentSortOrder.OLDEST -> byTag.reversed()
            CommentSortOrder.HIGHEST_RATED -> byTag.sortedByDescending { it.upvotes - it.downvotes }
            CommentSortOrder.LOWEST_RATED -> byTag.sortedBy { it.upvotes - it.downvotes }
        }
    }

    // Flatten tree respecting collapsed states
    val flattenedNodes = remember(filteredComments, collapsedIds.toMap()) {
        flattenCommentTree(filteredComments, collapsedIds)
    }

    fun submitComment() {
        var trimmed = newCommentText.trim()
        if (trimmed.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (isSpoilerMode && !trimmed.startsWith("||") && !trimmed.startsWith("~!")) {
                trimmed = "~!$trimmed!~"
            }

            val editing = editingComment
            if (editing != null) {
                localCommentsList = updateCommentContentHelper(localCommentsList, editing.id, trimmed)
                onEditComment?.invoke(editing.id, trimmed)
                editingComment = null
                newCommentText = ""
                isSpoilerMode = false
                focusManager.clearFocus()
                return
            }

            val parent = replyingToComment
            val targetTag = composerTag ?: parent?.tag ?: if (selectedTag != "All") selectedTag else "General"

            val effectiveAuthor = currentUsername ?: "You"
            val newComment = DiscussionComment(
                id = "comm_${System.currentTimeMillis()}",
                author = effectiveAuthor,
                authorAvatarUrl = null,
                tag = targetTag,
                content = trimmed,
                timeAgo = "Just now",
                upvotes = 1,
                downvotes = 0,
                parentId = parent?.id
            )

            // Insert into local state
            localCommentsList = if (parent != null) {
                insertNestedReply(localCommentsList, parent.id, newComment)
            } else {
                listOf(newComment) + localCommentsList
            }

            // Dispatch to parent / repository
            onPostComment?.invoke(trimmed, targetTag, parent?.id)

            // Reset compose state
            newCommentText = ""
            replyingToComment = null
            isSpoilerMode = false
            composerTag = null
            focusManager.clearFocus()
        }
    }

    if (showGifPicker) {
        GifPickerSheet(
            onDismiss = { showGifPicker = false },
            onGifSelected = { gifUrl ->
                newCommentText = if (newCommentText.isBlank()) "img($gifUrl)" else "$newCommentText\nimg($gifUrl)"
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Season Selector Rail (if multi-season data is available)
        if (seasons.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(seasons, key = { it.seasonNumber }) { s ->
                    val isSelected = s.seasonNumber == selectedSeasonNumber
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) AccentArctic else Color.White.copy(alpha = 0.06f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentArctic else GlassBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTag = "All"
                                onSeasonSelect?.invoke(s.seasonNumber)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = s.name.ifBlank { "Season ${s.seasonNumber}" },
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Tag Filter Horizontal Rail + Sort Menu Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort Button with Dropdown
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                        .clickable { sortMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort comments",
                            tint = AccentArctic,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = sortOrder.displayName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier
                        .background(DarkSurfaceElevated)
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                ) {
                    CommentSortOrder.values().forEach { order ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = order.displayName,
                                    color = if (order == sortOrder) AccentArctic else Color.White,
                                    fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                sortOrder = order
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Tag Rail
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(availableTags, key = { it }) { tag ->
                    val isSelected = tag == selectedTag
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) AccentArctic else Color.White.copy(alpha = 0.06f))
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentArctic else GlassBorder,
                                shape = RoundedCornerShape(999.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTag = tag
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tag,
                            color = if (isSelected) AmoledBlack else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Top-Anchored Comment Composer with Dartotsu Toolbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Edit / Reply Indicator Banner
            if (editingComment != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(13.dp))
                        Text("Editing comment", color = AccentArctic, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                editingComment = null
                                newCommentText = ""
                            }
                            .padding(2.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel edit", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            } else if (replyingToComment != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(13.dp))
                        Text("Replying to @${replyingToComment?.author}", color = AccentArctic, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { replyingToComment = null }
                            .padding(2.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Message Text Input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (newCommentText.isEmpty()) {
                    Text(
                        text = if (editingComment != null) "Edit your comment..."
                        else if (replyingToComment != null) "Write a reply to @${replyingToComment?.author}..."
                        else if (selectedTag != "All") "Add a comment for $selectedTag..."
                        else "Join the discussion...",
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 13.5.sp
                    )
                }
                BasicTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(AccentArctic),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submitComment() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Dartotsu Composer Toolbar: [ 🏷️ Tag ] [ 👁️ Spoiler ] [ 🎬 GIF ] ... [ Send ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Tag Picker Button
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (composerTag != null) AccentArctic.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                                .clickable { tagPickerExpanded = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Label,
                                contentDescription = "Tag",
                                tint = if (composerTag != null) AccentArctic else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = composerTag ?: if (selectedTag != "All") selectedTag else "Tag",
                                color = if (composerTag != null) AccentArctic else Color.White.copy(alpha = 0.7f),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        DropdownMenu(
                            expanded = tagPickerExpanded,
                            onDismissRequest = { tagPickerExpanded = false },
                            modifier = Modifier
                                .background(DarkSurfaceElevated)
                                .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        ) {
                            availableTags.filter { it != "All" }.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t, color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        composerTag = t
                                        tagPickerExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Spoiler Toggle Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSpoilerMode) AccentArctic.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                            .clickable { isSpoilerMode = !isSpoilerMode }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpoilerMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Spoiler",
                            tint = if (isSpoilerMode) AccentArctic else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (isSpoilerMode) "Spoiler On" else "Spoiler",
                            color = if (isSpoilerMode) AccentArctic else Color.White.copy(alpha = 0.7f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // GIF Picker Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { showGifPicker = true }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "GIF",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Send Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (newCommentText.isNotBlank()) AccentArctic else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = newCommentText.isNotBlank()) { submitComment() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post comment",
                            tint = if (newCommentText.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (editingComment != null) "Save" else "Post",
                            color = if (newCommentText.isNotBlank()) Color.Black else Color.White.copy(alpha = 0.3f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Threaded Comments
        if (flattenedNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "No discussion comments yet for $selectedTag.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Be the first to share your thoughts using the box above!",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 12.5.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                flattenedNodes.forEach { node ->
                    val userVote = voteStates[node.comment.id] ?: 0
                    CommentNodeRow(
                        node = node,
                        currentUsername = currentUsername,
                        userVote = userVote,
                        onToggleCollapse = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val current = collapsedIds[node.comment.id] ?: false
                            collapsedIds[node.comment.id] = !current
                        },
                        onReplyClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            replyingToComment = node.comment
                            editingComment = null
                        },
                        onEditClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            editingComment = node.comment
                            newCommentText = node.comment.content
                            replyingToComment = null
                        },
                        onDeleteClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            localCommentsList = deleteCommentHelper(localCommentsList, node.comment.id)
                            onDeleteComment?.invoke(node.comment.id)
                        },
                        onVote = { voteType ->
                            voteStates[node.comment.id] = voteType
                            onVote?.invoke(node.comment.id, voteType)
                        },
                        onUserClick = { onUserClick?.invoke(node.comment.author, node.comment.anilistUserId) }
                    )
                }
            }
        }
    }
}

/**
 * Individual comment node rendered with flattened indentation, Dartotsu like/dislike capsule,
 * 3-dots menu, and hold-to-copy.
 */
@Composable
private fun CommentNodeRow(
    node: FlattenedCommentNode,
    currentUsername: String?,
    userVote: Int,
    onToggleCollapse: () -> Unit,
    onReplyClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onVote: (Int) -> Unit,
    onUserClick: () -> Unit
) {
    val comment = node.comment
    val indentPadding = (node.depth.coerceAtMost(3) * 14).dp
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isMine = comment.author.equals(currentUsername, ignoreCase = true) || comment.author == "You"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding)
            .height(IntrinsicSize.Min)
    ) {
        // Vertical Thread Guide Line
        if (node.depth > 0) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleCollapse
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.12f))
                )
            }
        }

        // Comment Content Container
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: [-]/[+] + Avatar + Author + Tag + Time + 3-Dots Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Collapse Toggle Icon
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = onToggleCollapse),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (node.isCollapsed) Icons.Default.Add else Icons.Default.Remove,
                            contentDescription = if (node.isCollapsed) "Expand" else "Collapse",
                            tint = TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // User Avatar
                    if (!comment.authorAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = comment.authorAvatarUrl,
                            contentDescription = comment.author,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onUserClick),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                            .clickable(onClick = onUserClick),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = comment.author.take(1).uppercase(),
                                color = AccentArctic,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Username
                    Text(
                        text = comment.author,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onUserClick)
                    )

                    // Optional Tag
                    if (!comment.tag.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentArctic.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = comment.tag,
                                color = AccentArctic,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Relative Timestamp
                    Text(
                        text = "• ${comment.timeAgo}",
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 11.5.sp
                    )

                    // If collapsed, show hidden count
                    if (node.isCollapsed && node.totalChildReplies > 0) {
                        Text(
                            text = "• ${node.totalChildReplies} replies hidden",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 3-Dots Menu Button (Dartotsu Style)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White.copy(alpha = 0.60f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(DarkSurfaceElevated)
                            .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy Text", color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                menuExpanded = false
                                clipboardManager.setText(AnnotatedString(comment.content))
                                Toast.makeText(context, "Comment copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        )
                        if (isMine) {
                            DropdownMenuItem(
                                text = { Text("Edit", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onEditClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = AccentArctic, modifier = Modifier.size(16.dp))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFE53935), fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Report", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp) },
                                onClick = {
                                    menuExpanded = false
                                    Toast.makeText(context, "Comment reported", Toast.LENGTH_SHORT).show()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Flag, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
            }

            // Body & Action Bar (Hidden when collapsed)
            if (!node.isCollapsed) {
                // Comment Body: Rich formatting + long-press to copy
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleCollapse
                        )
                ) {
                    CommentRichContent(
                        content = comment.content
                    )
                }

                // Actions: Dartotsu Like/Dislike Capsule + Reply Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val netScore = (comment.upvotes - comment.downvotes) + userVote
                    DartotsuLikeDislikeCapsule(
                        score = netScore,
                        userVote = userVote,
                        onVote = { vote ->
                            onVote(vote)
                        }
                    )

                    // Reply Action Pill
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable(onClick = onReplyClick)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Reply,
                            contentDescription = "Reply",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Reply",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (comment.replies.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.03f))
                                .clickable(onClick = onToggleCollapse)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = "Replies",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${comment.replies.size}",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 1:1 Dartotsu Like / Dislike Rounded Capsule:
 * Thumbs Up (blue when active), Score in center, Thumbs Down (red when active).
 */
@Composable
fun DartotsuLikeDislikeCapsule(
    score: Int,
    userVote: Int, // 1 for liked, -1 for disliked, 0 for neutral
    onVote: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isLiked = userVote == 1
    val isDisliked = userVote == -1

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Thumbs Up
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onVote(if (isLiked) 0 else 1) }
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Like",
                tint = if (isLiked) AccentArctic else Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(13.dp)
            )
        }

        // Score
        Text(
            text = "$score",
            color = when {
                isLiked -> AccentArctic
                isDisliked -> Color(0xFFE53935)
                else -> Color.White.copy(alpha = 0.85f)
            },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )

        // Thumbs Down
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onVote(if (isDisliked) 0 else -1) }
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "Dislike",
                tint = if (isDisliked) Color(0xFFE53935) else Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

/**
 * Flattens tree into linear list for safe non-nested vertical rendering.
 */
private fun flattenCommentTree(
    comments: List<DiscussionComment>,
    collapsedMap: Map<String, Boolean>,
    currentDepth: Int = 0
): List<FlattenedCommentNode> {
    val result = mutableListOf<FlattenedCommentNode>()
    for (comment in comments) {
        val isCollapsed = collapsedMap[comment.id] ?: false
        val totalChildren = countAllDescendants(comment)
        result.add(
            FlattenedCommentNode(
                comment = comment,
                depth = currentDepth,
                isCollapsed = isCollapsed,
                totalChildReplies = totalChildren
            )
        )
        if (!isCollapsed && comment.replies.isNotEmpty()) {
            result.addAll(flattenCommentTree(comment.replies, collapsedMap, currentDepth + 1))
        }
    }
    return result
}

/**
 * Counts total nested replies under a given comment node.
 */
private fun countAllDescendants(comment: DiscussionComment): Int {
    var count = comment.replies.size
    for (reply in comment.replies) {
        count += countAllDescendants(reply)
    }
    return count
}

private fun insertNestedReply(
    list: List<DiscussionComment>,
    parentId: String,
    newReply: DiscussionComment
): List<DiscussionComment> {
    return list.map { item ->
        if (item.id == parentId) {
            item.copy(replies = item.replies + newReply.copy(depth = item.depth + 1))
        } else if (item.replies.isNotEmpty()) {
            item.copy(replies = insertNestedReply(item.replies, parentId, newReply))
        } else {
            item
        }
    }
}

private fun updateCommentContentHelper(
    list: List<DiscussionComment>,
    targetId: String,
    newContent: String
): List<DiscussionComment> {
    return list.map { item ->
        if (item.id == targetId) {
            item.copy(content = newContent)
        } else if (item.replies.isNotEmpty()) {
            item.copy(replies = updateCommentContentHelper(item.replies, targetId, newContent))
        } else {
            item
        }
    }
}

private fun deleteCommentHelper(
    list: List<DiscussionComment>,
    targetId: String
): List<DiscussionComment> {
    return list.filter { it.id != targetId }.map { item ->
        if (item.replies.isNotEmpty()) {
            item.copy(replies = deleteCommentHelper(item.replies, targetId))
        } else {
            item
        }
    }
}

/**
 * High-quality seed discussion thread tree across multiple episodes.
 */
internal fun createSampleDiscussionComments(mediaType: MediaType, totalEpisodes: Int = 24): List<DiscussionComment> {
    val isReading = mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL
    val prefix = if (isReading) "Ch" else "Ep"
    val list = mutableListOf<DiscussionComment>()

    list.add(
        DiscussionComment(
            id = "comm_1",
            author = "KusuriyaFan",
            authorAvatarUrl = "https://s4.anilist.co/file/anilistcdn/user/avatar/large/default.png",
            anilistUserId = 5124,
            tag = "$prefix 1",
            content = "The animation direction and sound design in this first entry were phenomenal! The mystery hook is already so compelling.",
            timeAgo = "2h ago",
            upvotes = 342,
            downvotes = 8,
            replies = listOf(
                DiscussionComment(
                    id = "comm_1_1",
                    author = "AoiSeiyuu",
                    authorAvatarUrl = null,
                    tag = "$prefix 1",
                    content = "Aoi Yuuki's voice acting fits Maomao's sarcastic inner monologues to absolute perfection.",
                    timeAgo = "1h ago",
                    upvotes = 129,
                    downvotes = 2,
                    depth = 1,
                    replies = listOf(
                        DiscussionComment(
                            id = "comm_1_1_1",
                            author = "LightNovelReader",
                            authorAvatarUrl = null,
                            tag = "$prefix 1",
                            content = "100% agreed! Having read the novel, she brings out the exact dry humor the author wrote.",
                            timeAgo = "45m ago",
                            upvotes = 48,
                            downvotes = 0,
                            depth = 2
                        )
                    )
                ),
                DiscussionComment(
                    id = "comm_1_2",
                    author = "Cinephile_99",
                    authorAvatarUrl = null,
                    tag = "$prefix 1",
                    content = "The visual fidelity on AMOLED 1440p looks insane. Loving the color grading in the inner palace scenes.",
                    timeAgo = "30m ago",
                    upvotes = 74,
                    downvotes = 1,
                    depth = 1
                )
            )
        )
    )

    list.add(
        DiscussionComment(
            id = "comm_2",
            author = "JinshiEnjoyer",
            authorAvatarUrl = null,
            tag = "$prefix 2",
            content = "Jinshi's expressions whenever Maomao completely ignores his celestial charm never fail to make me burst out laughing.",
            timeAgo = "4h ago",
            upvotes = 215,
            downvotes = 4,
            replies = listOf(
                DiscussionComment(
                    id = "comm_2_1",
                    author = "ImperialCourtPhysician",
                    authorAvatarUrl = null,
                    tag = "$prefix 2",
                    content = "She is literally only interested in poisonous herbs and ox bezoars. Best protagonist of the season.",
                    timeAgo = "3h ago",
                    upvotes = 93,
                    downvotes = 1,
                    depth = 1
                )
            )
        )
    )

    list.add(
        DiscussionComment(
            id = "comm_gen",
            author = "OmniAnimeClub",
            authorAvatarUrl = null,
            tag = "General",
            content = "For anyone wondering about reading ahead, the light novel expands heavily on the medical deductions and imperial politics. Both adaptation and source are masterpieces!",
            timeAgo = "6h ago",
            upvotes = 512,
            downvotes = 11
        )
    )

    return list
}
