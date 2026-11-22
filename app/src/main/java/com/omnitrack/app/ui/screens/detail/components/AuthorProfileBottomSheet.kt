package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.model.BookAuthorProfile
import com.omnitrack.app.data.preferences.FallbackBackgroundStyle
import com.omnitrack.app.data.preferences.PresentationSettings
import com.omnitrack.app.ui.components.ArtworkBackdrop
import com.omnitrack.app.ui.components.CoverGlowBox
import com.omnitrack.app.ui.components.FormattedRichText
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.net.URLEncoder

@Composable
fun AuthorProfileBottomSheet(
    authorProfile: BookAuthorProfile,
    presentationSettings: PresentationSettings? = null,
    backdropBitmap: ImageBitmap? = null,
    backdropTargetUrl: String? = null,
    onSelectWork: (RecommendationItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    InWindowBottomSheet(
        onDismiss = onDismiss,
        isAtTop = { scrollState.value == 0 },
        wrapHeightMax = 740.dp,
        showDragHandle = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .heightIn(max = 740.dp)
        ) {
            when (presentationSettings?.fallbackBackgroundStyle) {
                FallbackBackgroundStyle.CINEMATIC_BLUR -> {
                    ArtworkBackdrop(image = backdropBitmap, modifier = Modifier.matchParentSize())
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.62f)))
                }
                FallbackBackgroundStyle.DOMINANT_COLOR -> {
                    CoverGlowBox(imageUrl = backdropTargetUrl, modifier = Modifier.matchParentSize()) {}
                    Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.62f)))
                }
                else -> {
                    Box(modifier = Modifier.matchParentSize().background(AmoledBlack))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 22.dp, vertical = 18.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.24f))
                    )
                }

                // Header with large portrait & dates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!authorProfile.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = authorProfile.photoUrl,
                                contentDescription = authorProfile.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = authorProfile.name.take(1).uppercase(),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "AUTHOR PROFILE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = authorProfile.name,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            lineHeight = 26.sp
                        )
                        authorProfile.birthDeath?.takeIf { it.isNotBlank() }?.let { dates ->
                            Text(
                                text = dates,
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                // Wikipedia & Open Library quick links
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val wikiUrl = "https://en.wikipedia.org/wiki/Special:Search?search=" +
                        URLEncoder.encode(authorProfile.name, "UTF-8")
                    val olUrl = authorProfile.openLibraryKey?.takeIf { it.isNotBlank() }?.let { key ->
                        val clean = if (key.startsWith("/")) key else "/authors/$key"
                        "https://openlibrary.org$clean"
                    } ?: ("https://openlibrary.org/search/authors?q=" + URLEncoder.encode(authorProfile.name, "UTF-8"))

                    AuthorExternalLinkChip(
                        label = "Wikipedia",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(wikiUrl)))
                            }
                        }
                    )
                    AuthorExternalLinkChip(
                        label = "Open Library",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(olUrl)))
                            }
                        }
                    )
                }

                // Cleaned Biography with clickable links
                authorProfile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                    HorizontalDivider(thickness = 0.6.dp, color = Color.White.copy(alpha = 0.10f))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "BIOGRAPHY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 0.9.sp
                        )
                        FormattedRichText(
                            rawText = bio,
                            color = TextSecondary,
                            fontSize = 13.5.sp,
                            lineHeight = 20.5.sp
                        )
                    }
                }

                // Notable Works 3-column poster grid
                if (authorProfile.works.isNotEmpty()) {
                    HorizontalDivider(thickness = 0.6.dp, color = Color.White.copy(alpha = 0.10f))
                    Text(
                        text = "NOTABLE WORKS (${authorProfile.works.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.9.sp
                    )
                    val rows = authorProfile.works.chunked(3)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        rows.forEach { rowWorks ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                rowWorks.forEach { work ->
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                onDismiss()
                                                onSelectWork(work)
                                            },
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(2f / 3f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White.copy(alpha = 0.06f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!work.coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = work.coverUrl,
                                                    contentDescription = work.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(
                                                    text = work.title.take(1).uppercase(),
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                        Text(
                                            text = work.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 15.sp
                                        )
                                        work.releaseYear?.takeIf { it.isNotBlank() }?.let { yr ->
                                            Text(
                                                text = yr,
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                                repeat(3 - rowWorks.size) {
                                    Spacer(modifier = Modifier.weight(1f))
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
private fun AuthorExternalLinkChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}
