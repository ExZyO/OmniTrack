package com.omnitrack.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path

enum class VoteType {
    NONE, UP, DOWN
}

/**
 * 1:1 Reddit Pill Voting Capsule:
 * - Outline Reddit Up/Down arrows
 * - Dynamic score counter with vibrant Reddit Orange (#FF4500) and Periwinkle Blue (#7193FF)
 * - 1dp vertical divider line
 * - Optional downvote number display
 * - AMOLED #1A1A1B background with subtle border
 */
@Composable
fun RedditVoteCapsule(
    initialScore: Int = 0,
    initialDownvotes: Int = 0,
    initialVote: VoteType = VoteType.NONE,
    showDownvoteCount: Boolean = true,
    onVoteChanged: ((VoteType, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var currentVote by remember(initialVote) { mutableStateOf(initialVote) }
    var score by remember(initialScore) { mutableIntStateOf(initialScore) }
    var downvotes by remember(initialDownvotes) { mutableIntStateOf(initialDownvotes) }

    val upvoteColor by animateColorAsState(
        targetValue = if (currentVote == VoteType.UP) Color(0xFFFF4500) else Color(0xFFD7DADC),
        animationSpec = tween(180),
        label = "upvote_color"
    )

    val downvoteColor by animateColorAsState(
        targetValue = if (currentVote == VoteType.DOWN) Color(0xFF7193FF) else Color(0xFFD7DADC),
        animationSpec = tween(180),
        label = "downvote_color"
    )

    val borderColor = Color(0x33FFFFFF)
    val capsuleBackground = Color(0xFF1A1A1B)

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(capsuleBackground)
            .border(1.dp, borderColor, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // UPVOTE BUTTON & SCORE
        Row(
            modifier = Modifier
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (currentVote) {
                        VoteType.UP -> {
                            currentVote = VoteType.NONE
                            score -= 1
                        }
                        VoteType.DOWN -> {
                            currentVote = VoteType.UP
                            score += 1
                            if (downvotes > 0) downvotes -= 1
                        }
                        VoteType.NONE -> {
                            currentVote = VoteType.UP
                            score += 1
                        }
                    }
                    onVoteChanged?.invoke(currentVote, score)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RedditArrowIcon(
                isUp = true,
                tint = upvoteColor,
                modifier = Modifier.size(16.dp)
            )

            Text(
                text = score.toString(),
                color = upvoteColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // VERTICAL DIVIDER LINE
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color(0x33FFFFFF))
        )

        // DOWNVOTE BUTTON & OPTIONAL COUNT
        Row(
            modifier = Modifier
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (currentVote) {
                        VoteType.DOWN -> {
                            currentVote = VoteType.NONE
                            if (downvotes > 0) downvotes -= 1
                        }
                        VoteType.UP -> {
                            currentVote = VoteType.DOWN
                            score -= 1
                            downvotes += 1
                        }
                        VoteType.NONE -> {
                            currentVote = VoteType.DOWN
                            downvotes += 1
                        }
                    }
                    onVoteChanged?.invoke(currentVote, score)
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            RedditArrowIcon(
                isUp = false,
                tint = downvoteColor,
                modifier = Modifier.size(16.dp)
            )

            if (showDownvoteCount && downvotes > 0) {
                Text(
                    text = downvotes.toString(),
                    color = downvoteColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Exact Reddit outline chevron arrow geometry
 */
@Composable
private fun RedditArrowIcon(
    isUp: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidth = 2.2.dp.toPx()

        val path = Path().apply {
            if (isUp) {
                // Pointing up: ^ with vertical stem
                moveTo(w * 0.18f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.16f)
                lineTo(w * 0.82f, h * 0.48f)
                moveTo(w * 0.50f, h * 0.18f)
                lineTo(w * 0.50f, h * 0.84f)
            } else {
                // Pointing down: v with vertical stem
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.50f, h * 0.84f)
                lineTo(w * 0.82f, h * 0.52f)
                moveTo(w * 0.50f, h * 0.82f)
                lineTo(w * 0.50f, h * 0.16f)
            }
        }

        drawPath(
            path = path,
            color = tint,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
