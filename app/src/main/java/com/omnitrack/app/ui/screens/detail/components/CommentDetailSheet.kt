package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.components.RedditVoteCapsule
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary

/**
 * 1:1 Nuvio Community Review Detail Sheet:
 * Features Nuvio's exact spring height animation when swiping or navigating between comments of
 * different lengths (spring damping 0.9f, stiffness 500f). Smoothly glides up and down without
 * clipping or jumpy layout pops.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentDetailSheet(
    reviews: List<CommunityReview>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (reviews.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    var currentIndex by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(reviews.indices)) }
    var isSwipingForward by remember { mutableStateOf(true) }

    val currentReview = reviews.getOrNull(currentIndex) ?: return
    val scrollState = rememberScrollState()
    var isSpoilerRevealed by rememberSaveable(currentReview.author + currentReview.comment.take(15)) {
        mutableStateOf(!currentReview.hasSpoiler)
    }
    val isSpoilerHidden = currentReview.hasSpoiler && !isSpoilerRevealed

    LaunchedEffect(currentIndex) {
        scrollState.scrollTo(0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = 500f
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
                .pointerInput(reviews.size, currentIndex) {
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX < -45f && currentIndex < reviews.size - 1) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isSwipingForward = true
                                currentIndex++
                            } else if (totalDragX > 45f && currentIndex > 0) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isSwipingForward = false
                                currentIndex--
                            }
                            totalDragX = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val canGoBack = currentIndex > 0
            val canGoForward = currentIndex < reviews.size - 1

            // 1:1 Nuvio Header: Author name + @username on the left, Bilateral Circle Buttons on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentReview.author,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val handle = currentReview.username?.let { "@$it" } ?: "@${currentReview.source.replace(" ", "")}"
                    Text(
                        text = handle,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                // Bilateral Navigation Controls (Circle <, Index / Total, Circle >)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (canGoBack) Color.White.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .then(
                                if (canGoBack) {
                                    Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isSwipingForward = false
                                        currentIndex--
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous Review",
                            tint = if (canGoBack) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "${currentIndex + 1} / ${reviews.size}",
                        fontSize = 12.5.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (canGoForward) Color.White.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.04f)
                            )
                            .then(
                                if (canGoForward) {
                                    Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isSwipingForward = true
                                        currentIndex++
                                    }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next Review",
                            tint = if (canGoForward) Color.White else Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 1:1 Nuvio REVIEW Badge
            if (currentReview.isReview) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF1B3D2F))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "REVIEW",
                        color = Color(0xFF00E054),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Vertically scrollable review body with unconstrained wrap-content height & fluid spring animation
            AnimatedContent(
                targetState = currentReview,
                transitionSpec = {
                    if (isSwipingForward) {
                        (slideInHorizontally(tween(260)) { width -> width / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { width -> -width / 4 } + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(tween(260)) { width -> -width / 4 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(200)) { width -> width / 4 } + fadeOut(tween(160)))
                    }
                },
                label = "review_comment_body",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) { review ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    val spoilerHidden = review.hasSpoiler && !isSpoilerRevealed
                    if (spoilerHidden) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSpoilerRevealed = true
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "This review contains spoilers. Tap to reveal.",
                                color = Color(0xFFF59E0B),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Text(
                            text = review.comment,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 14.5.sp,
                                lineHeight = 23.sp,
                                color = TextPrimary
                            )
                        )
                    }
                }
            }

            // 1:1 Nuvio Footer Row: Rating & Likes on the Left, Date on the Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    currentReview.rating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AccentGold.copy(alpha = 0.15f))
                                .padding(horizontal = 9.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = AccentGold,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${rating.toInt()}/10",
                                color = AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    RedditVoteCapsule(
                        initialScore = currentReview.likes.coerceAtLeast(1),
                        allowDownvote = currentReview.source != "Trakt",
                        showDownvoteCount = false
                    )

                    Text(
                        text = currentReview.source,
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 11.5.sp
                    )
                }

                currentReview.date?.let { date ->
                    Text(
                        text = date,
                        fontSize = 11.5.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
