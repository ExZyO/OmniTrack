package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.SurfaceCard
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

@Composable
fun DetailAnimeCastSection(
    characters: List<AnimeCharacterItem>,
    modifier: Modifier = Modifier,
    isMangaOrNovel: Boolean = false,
    spoilerThreshold: Int = Int.MAX_VALUE,
    onCharacterClick: ((AnimeCharacterItem) -> Unit)? = null,
    onVoiceActorClick: ((AnimeCharacterItem) -> Unit)? = null
) {
    if (characters.isEmpty()) return
    val context = LocalContext.current
    var revealedCharIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isMangaOrNovel) "Characters" else "Characters & Voice Actors",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp
            )
            Text(
                text = if (isMangaOrNovel) "${characters.size} characters" else "${characters.size} cast",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(characters, key = { index, item -> "${item.characterId}_$index" }) { _, item ->
                val isSpoilerMasked = item.spoilerLevel > spoilerThreshold && item.characterId !in revealedCharIds
                Column(
                    modifier = Modifier
                        .width(104.dp)
                        .then(
                            if (isSpoilerMasked) {
                                Modifier.clickable { revealedCharIds = revealedCharIds + item.characterId }
                            } else if (onCharacterClick != null) {
                                Modifier.clickable { onCharacterClick(item) }
                            } else {
                                Modifier
                            }
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Portrait Box with PiP Seiyuu badge
                    Box(
                        modifier = Modifier
                            .width(104.dp)
                            .height(128.dp)
                    ) {
                        // Character Portrait
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(124.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceCard)
                                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        ) {
                            if (!item.characterImageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.characterImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.characterName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .let { if (isSpoilerMasked) it.blur(12.dp) else it }
                                )
                            }
                            if (isSpoilerMasked) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.38f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Spoiler",
                                        color = Color.White.copy(alpha = 0.80f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Overlapping Seiyuu PiP Avatar Badge
                        if (!item.voiceActorImageUrl.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF000000))
                                    .border(2.dp, Color(0xFF000000), CircleShape)
                                    .then(
                                        if (!isSpoilerMasked && onVoiceActorClick != null && item.voiceActorId != null) {
                                            Modifier.clickable { onVoiceActorClick(item) }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.voiceActorImageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.voiceActorName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .matchParentSize()
                                        .let { if (isSpoilerMasked) it.blur(10.dp) else it }
                                )
                            }
                        }
                    }

                    // Character Name & Voice Actor / Role
                    Text(
                        text = item.characterName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isSpoilerMasked) Modifier.blur(6.dp) else Modifier
                    )

                    val secondaryText = when {
                        !item.voiceActorName.isNullOrBlank() -> item.voiceActorName
                        isMangaOrNovel || item.vndbCharacterId != null -> when (item.characterRole.uppercase()) {
                            "PROTAGONIST" -> "Protagonist"
                            "MAIN" -> "Main Heroine"
                            else -> item.characterRole
                                .replace("_", " ")
                                .lowercase()
                                .replaceFirstChar { it.uppercase() }
                        }
                        else -> "Unknown VA"
                    }
                    Text(
                        text = secondaryText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isSpoilerMasked) Modifier.blur(6.dp) else Modifier
                    )
                }
            }
        }
    }
}
