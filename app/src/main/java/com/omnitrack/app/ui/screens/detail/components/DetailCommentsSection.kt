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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Phase 2: Reddit Voting Capsule & Collapsible Thread Engine.
 * - 100% Unboxed AMOLED `#000000` design matching Nuvio-clean aesthetic.
 * - Flattened depth-indented hierarchy (`depth * 14.dp`, max 42.dp) avoiding nested scroll crashes.
 * - Tap-to-collapse thread hierarchy: `[-]` / `[+]` collapses entire subtree.
 * - Interactive vertical thread guide-lines that collapse replies when tapped.
 * - Dynamic `Ep 1, Ep 2...` vs `Ch 1, Ch 2...` tag filter rail based on media type.
 * - Live Reddit voting capsule with separate upvote / downvote counters.
 */
@Composable
fun DetailCommentsSection(
    mediaType: MediaType,
    mediaTitle: String,
    episodeOrChapterCount: Int? = null,
    comments: List<DiscussionComment> = rememberSampleDiscussionComments(mediaType),
    onUserClick: ((String, Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    // Dynamic Filter Rail: "Ep" for Anime/Shows, "Ch" for Manga/Novels
    val tagPrefix = if (mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL) "Ch" else "Ep"
    val count = (episodeOrChapterCount ?: 24).coerceIn(1, 48)
    val availableTags = remember(tagPrefix, count) {
        listOf("All", "General") + (1..count.coerceAtMost(12)).map { "$tagPrefix $it" }
    }
    var selectedTag by remember { mutableStateOf("All") }

    // Collapsed Comment State
    val collapsedIds = remember { mutableStateMapOf<String, Boolean>() }

    // Filter comments by selected tag
    val filteredComments = remember(comments, selectedTag) {
        if (selectedTag == "All") comments
        else comments.filter { it.tag == selectedTag || it.replies.any { r -> r.tag == selectedTag } }
    }

    // Flatten tree respecting collapsed states (Rule 3: Avoid nested scroll crash)
    val flattenedNodes = remember(filteredComments, collapsedIds.toMap()) {
        flattenCommentTree(filteredComments, collapsedIds)
    }

    // Local comment input state
    var newCommentText by remember { mutableStateOf("") }
    var localCommentsList by remember { mutableStateOf(comments) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Tag Filter Horizontal Rail
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

        // Threaded Comments (Rendered linearly in Column, zero LazyColumn nesting)
        if (flattenedNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No discussion comments yet for $selectedTag.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
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
                        onUserClick = { onUserClick?.invoke(node.comment.author, node.comment.anilistUserId) }
                    )
                }
            }
        }

        // Unboxed Inline Comment Composer
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
                        text = "Join the discussion...",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
                BasicTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(AccentArctic),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (newCommentText.isNotBlank()) {
                            val newComment = DiscussionComment(
                                id = "local_${System.currentTimeMillis()}",
                                author = "You",
                                tag = if (selectedTag != "All") selectedTag else "General",
                                content = newCommentText.trim(),
                                timeAgo = "Just now",
                                upvotes = 1
                            )
                            localCommentsList = listOf(newComment) + localCommentsList
                            newCommentText = ""
                            focusManager.clearFocus()
                        }
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (newCommentText.isNotBlank()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Post comment",
                    tint = AccentArctic,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            val newComment = DiscussionComment(
                                id = "local_${System.currentTimeMillis()}",
                                author = "You",
                                tag = if (selectedTag != "All") selectedTag else "General",
                                content = newCommentText.trim(),
                                timeAgo = "Just now",
                                upvotes = 1
                            )
                            localCommentsList = listOf(newComment) + localCommentsList
                            newCommentText = ""
                            focusManager.clearFocus()
                        }
                )
            }
        }
    }
}

/**
 * Individual comment node rendered with flattened indentation and vertical thread guide lines.
 */
@Composable
private fun CommentNodeRow(
    node: FlattenedCommentNode,
    onToggleCollapse: () -> Unit,
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

        // Comment Content Container
        Column(
            modifier = Modifier.weight(1f),
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
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                // If collapsed, show hidden reply count summary
                if (node.isCollapsed && node.totalChildReplies > 0) {
                    Text(
                        text = "• ${node.totalChildReplies} replies hidden",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Body & Action Bar (Hidden when collapsed)
            if (!node.isCollapsed) {
                // Comment Body Text
                Text(
                    text = comment.content,
                    color = Color(0xFFEDEDED),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 28.dp)
                )

                // Actions: Reddit Voting Capsule + Reply Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RedditVoteCapsule(
                        initialScore = comment.upvotes,
                        initialDownvotes = comment.downvotes,
                        allowDownvote = true,
                        showDownvoteCount = true
                    )

                    // Reply Counter / Quick Action Pill
                    val replyCount = comment.replies.size
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, GlassBorder, CircleShape)
                            .clickable { onToggleCollapse() }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Replies",
                            tint = TextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (replyCount > 0) "$replyCount ${if (replyCount == 1) "reply" else "replies"}" else "Reply",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recursively flattens a tree of discussion comments into a linear list.
 * Respects collapsed state by omitting child subtrees.
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
 * High-quality seed discussion thread tree for live interactive testing in Phase 2.
 */
@Composable
private fun rememberSampleDiscussionComments(mediaType: MediaType): List<DiscussionComment> {
    val isReading = mediaType == MediaType.MANGA || mediaType == MediaType.NOVEL
    val prefix = if (isReading) "Ch" else "Ep"

    return remember(mediaType) {
        listOf(
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
            ),
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
            ),
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
    }
}
