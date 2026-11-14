package com.omnitrack.app.ui.screens.detail.components

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.MediaType
import com.omnitrack.app.ui.components.RedditVoteCapsule
import com.omnitrack.app.ui.components.VoteType
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

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
 * Phase 2 & 3: Reddit Voting Capsule, Interactive Collapsible Comments & Real Comment Engine.
 * - 100% Unboxed AMOLED `#000000` design matching Nuvio-clean aesthetic.
 * - Clicking ANYWHERE on a comment toggles collapse/expand of its replies.
 * - Holding (long-press) enables text selection and copying of the comment body.
 * - Top-anchored Inline Comment Composer directly under the filter chips (no scrolling required).
 * - Active "Replying to @username" indicator with quick cancel.
 * - Full dynamic episode/chapter range without the 12-episode limit.
 * - Live optimistic comment & nested reply addition.
 */
@Composable
fun DetailCommentsSection(
    mediaType: MediaType,
    mediaTitle: String,
    episodeOrChapterCount: Int? = null,
    comments: List<DiscussionComment>? = null,
    seasons: List<com.omnitrack.app.data.model.MediaSeason> = emptyList(),
    selectedSeasonNumber: Int = 1,
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

    // Dynamic Filter Rail: "Ep" for Anime/Shows, "Ch" for Manga/Novels
    val tagPrefix = if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) "Ch" else "Ep"
    // Uncapped episode/chapter count: supports shows with 24, 75, 100+ episodes or chapters
    val totalCount = (episodeOrChapterCount ?: 24).coerceIn(1, 2000)
    val availableTags = remember(tagPrefix, totalCount) {
        listOf("All", "General") + (1..totalCount).map { "$tagPrefix $it" }
    }
    var selectedTag by remember { mutableStateOf("All") }

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

    // Collapsed Comment State: tracks whether a node's replies are collapsed
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }

    // Replying target state: if set, compose bar indicates "Replying to @username"
    var replyingToComment by remember { mutableStateOf<DiscussionComment?>(null) }
    var editingComment by remember { mutableStateOf<DiscussionComment?>(null) }
    var newCommentText by remember { mutableStateOf("") }

    // Filter comments by selected tag
    val filteredComments = remember(localCommentsList, selectedTag) {
        if (selectedTag == "All") {
            localCommentsList
        } else {
            localCommentsList.filter { comment ->
                comment.tag == selectedTag || comment.replies.any { it.tag == selectedTag }
            }
        }
    }

    // Flatten tree respecting collapsed states (Rule 3: Avoid nested scroll crash)
    val flattenedNodes = remember(filteredComments, collapsedIds.toMap()) {
        flattenCommentTree(filteredComments, collapsedIds)
    }

    fun submitComment() {
        val trimmed = newCommentText.trim()
        if (trimmed.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            val editing = editingComment
            if (editing != null) {
                localCommentsList = updateCommentContentHelper(localCommentsList, editing.id, trimmed)
                onEditComment?.invoke(editing.id, trimmed)
                editingComment = null
                newCommentText = ""
                focusManager.clearFocus()
                return
            }

            val parent = replyingToComment
            val targetTag = parent?.tag ?: if (selectedTag != "All") selectedTag else "General"

            val newComment = DiscussionComment(
                id = "comm_${System.currentTimeMillis()}",
                author = "You",
                authorAvatarUrl = null,
                tag = targetTag,
                content = trimmed,
                timeAgo = "Just now",
                upvotes = 1,
                downvotes = 0,
                parentId = parent?.id
            )

            // Insert into local state (optimistic instant update)
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
            focusManager.clearFocus()
        }
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

        // Tag Filter Horizontal Rail (Uncapped)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
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

        // Top-Anchored Comment Composer (Right under filter chips for 0-scroll access)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Edit indicator banner
            if (editingComment != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = AccentArctic,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Editing your comment",
                            color = AccentArctic,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editingComment = null
                                newCommentText = ""
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel edit",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Reply target indicator banner
            if (replyingToComment != null && editingComment == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Reply,
                            contentDescription = null,
                            tint = AccentArctic,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Replying to @${replyingToComment?.author}",
                            color = AccentArctic,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                replyingToComment = null
                            }
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Message Composer Input Box
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (newCommentText.isEmpty()) {
                        Text(
                            text = if (editingComment != null) "Edit your comment..." else if (replyingToComment != null) "Write a reply..." else if (selectedTag != "All") "Add a comment for $selectedTag..." else "Join the discussion...",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(AccentArctic),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (newCommentText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { submitComment() }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Post comment",
                            tint = AccentArctic,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Threaded Comments (Rendered linearly in Column, zero LazyColumn nesting)
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
                    CommentNodeRow(
                        node = node,
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
 * Individual comment node rendered with flattened indentation and vertical thread guide lines.
 * - Clicking ANYWHERE on the card toggles expand/collapse of its replies.
 * - Holding (long-pressing) allows selecting and copying the comment text via SelectionContainer.
 */
@Composable
private fun CommentNodeRow(
    node: FlattenedCommentNode,
    onToggleCollapse: () -> Unit,
    onReplyClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onVote: (Int) -> Unit,
    onUserClick: () -> Unit
) {
    val comment = node.comment
    val indentPadding = (node.depth.coerceAtMost(3) * 14).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding)
            .height(IntrinsicSize.Min)
    ) {
        // Vertical Thread Guide Line (Tappable to collapse subtree)
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

        // Comment Content Container (Whole container clickable to toggle collapse)
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleCollapse
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: [-] / [+] Toggle + Avatar + Author + Tag + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                // Optional Episode/Chapter Tag
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

                // If collapsed, show hidden reply count summary
                if (node.isCollapsed && node.totalChildReplies > 0) {
                    Text(
                        text = "• ${node.totalChildReplies} replies hidden",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Body & Action Bar (Hidden when collapsed)
            if (!node.isCollapsed) {
                // Comment Body: Rich formatting for GIFs, images, spoilers, quotes, markdown & hold-to-select
                CommentRichContent(
                    content = comment.content,
                    modifier = Modifier.padding(start = 28.dp)
                )

                // Actions: Reddit Voting Capsule + Reply Pill + Edit/Delete
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RedditVoteCapsule(
                        initialScore = comment.upvotes,
                        initialDownvotes = comment.downvotes,
                        allowDownvote = true,
                        showDownvoteCount = true,
                        onVoteChanged = { voteType, _ ->
                            val score = when (voteType) {
                                VoteType.UP -> 1
                                VoteType.DOWN -> -1
                                VoteType.NONE -> 0
                            }
                            onVote(score)
                        }
                    )

                    // Reply Action Pill (Wires directly to top composer)
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

                    // Edit & Delete Action Pills (for user's own comments)
                    if (comment.author == "You") {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, GlassBorder, CircleShape)
                                .clickable(onClick = onEditClick)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Edit",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = 0.08f))
                                .border(1.dp, Color.Red.copy(alpha = 0.25f), CircleShape)
                                .clickable(onClick = onDeleteClick)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF6B6B),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "Delete",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
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

/**
 * Recursively inserts a reply under a specified parent comment ID in the comment tree.
 */
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
 * Ensures discussions don't stop after episode 1 or 2.
 */
internal fun createSampleDiscussionComments(mediaType: MediaType, totalEpisodes: Int = 24): List<DiscussionComment> {
    val isReading = mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL
    val prefix = if (isReading) "Ch" else "Ep"
    val list = mutableListOf<DiscussionComment>()

        // Episode 1 discussions
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

        // General discussion
        list.add(
            DiscussionComment(
                id = "comm_2",
                author = "ShadowSleuth",
                authorAvatarUrl = null,
                anilistUserId = 8901,
                tag = "General",
                content = "Can anyone confirm how closely the upcoming arc follows the source? No spoilers please, just pacing impressions!",
                timeAgo = "4h ago",
                upvotes = 95,
                downvotes = 3,
                replies = listOf(
                    DiscussionComment(
                        id = "comm_2_1",
                        author = "MangaConnoisseur",
                        authorAvatarUrl = null,
                        tag = "General",
                        content = "Pacing is roughly 2-3 chapters per episode. It breathes nicely without feeling rushed at all.",
                        timeAgo = "3h ago",
                        upvotes = 63,
                        downvotes = 0,
                        depth = 1
                    )
                )
            )
        )

        // Episode 2 discussion
        list.add(
            DiscussionComment(
                id = "comm_3",
                author = "RetroOtaku",
                authorAvatarUrl = null,
                anilistUserId = 12450,
                tag = "$prefix 2",
                content = "The deduction process with the powdered cosmetic poison was brilliant. You can really tell the author did their historical pharmacology research.",
                timeAgo = "6h ago",
                upvotes = 210,
                downvotes = 4
            )
        )

        // Episode 3 discussion
        list.add(
            DiscussionComment(
                id = "comm_4",
                author = "JinshiEnjoyer",
                authorAvatarUrl = null,
                anilistUserId = 19441,
                tag = "$prefix 3",
                content = "The dynamic between Jinshi and Maomao is hilarious. She looks at him like he's an annoying insect while everyone else swoons.",
                timeAgo = "8h ago",
                upvotes = 188,
                downvotes = 2,
                replies = listOf(
                    DiscussionComment(
                        id = "comm_4_1",
                        author = "PalaceWatcher",
                        authorAvatarUrl = null,
                        tag = "$prefix 3",
                        content = "Her complete immunity to his charm attack broke him emotionally lol.",
                        timeAgo = "7h ago",
                        upvotes = 94,
                        downvotes = 0,
                        depth = 1
                    )
                )
            )
        )

        // Episode 4 discussion
        list.add(
            DiscussionComment(
                id = "comm_5",
                author = "ApothecaryStan",
                authorAvatarUrl = null,
                anilistUserId = 22104,
                tag = "$prefix 4",
                content = "The visual metaphor of the inner palace as a gilded birdcage was so beautifully executed here.",
                timeAgo = "12h ago",
                upvotes = 156,
                downvotes = 1
            )
        )

        // Generate discussions for remaining episodes (up to total episodes or 48)
        val maxSeed = totalEpisodes.coerceIn(1, 48)
        val samplePrompts = listOf(
            "Key turning point in the storyline. The subtle hints dropped earlier are starting to connect together seamlessly.",
            "The cliffhanger at the end of this episode had my jaw on the floor! Next week cannot come fast enough.",
            "The soundtrack during the climax scene was absolutely chilling. Incredible direction and pacing.",
            "The character development here compared to the beginning of the arc is so well earned.",
            "That subtle foreshadowing in the middle of the episode was brilliant. Caught it on a rewatch!",
            "Easily one of the best episodes of the season so far. The production quality jumped up another level.",
            "The voice acting performance in this episode gave me genuine goosebumps. Outstanding delivery."
        )
        val sampleUsers = listOf(
            "SakuraBloom", "MidnightWatcher", "GhibliNostalgia", "ChronoTriggered", "EchoKnight", "CrimsonValkyrie", "AetherDrifter"
        )
        for (ep in 5..maxSeed) {
            val user = sampleUsers[(ep - 5) % sampleUsers.size]
            val prompt = samplePrompts[(ep - 5) % samplePrompts.size]
            list.add(
                DiscussionComment(
                    id = "comm_seed_$ep",
                    author = user,
                    authorAvatarUrl = null,
                    anilistUserId = 30000 + ep,
                    tag = "$prefix $ep",
                    content = prompt,
                    timeAgo = "${(ep * 2) % 24 + 1}h ago",
                    upvotes = 45 + (ep * 11) % 180,
                    downvotes = ep % 3
                )
            )
        }

        return list
}
