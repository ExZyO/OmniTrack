package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.data.db.entity.MediaItemEntity
import com.omnitrack.app.data.db.entity.VnRouteEntity
import com.omnitrack.app.data.model.VnDetailMeta
import com.omnitrack.app.data.model.VnSpoilerLevel
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.AmoledBlack
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DetailVnRoutesSection(
    item: MediaItemEntity,
    vnMeta: VnDetailMeta,
    routes: List<VnRouteEntity>,
    spoilerLevel: VnSpoilerLevel,
    onStepPlaytime: (Double) -> Unit,
    onSetExactPlaytime: (Double) -> Unit,
    onToggleRouteCompleted: (VnRouteEntity) -> Unit,
    onAddCustomRoute: (String) -> Unit,
    onOpenRouteDetail: (VnRouteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddBar by remember { mutableStateOf(false) }
    var newRouteName by remember { mutableStateOf("") }
    var showHoursDialog by remember { mutableStateOf(false) }
    var hoursInput by remember(item.playtimeHours) {
        mutableStateOf(String.format(Locale.US, "%.1f", item.playtimeHours))
    }
    var revealedSpoilerRouteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val estimatedMinutes = vnMeta.lengthMinutes ?: item.runtimeMinutes ?: 0
    val estimatedHours = if (estimatedMinutes > 0) estimatedMinutes / 60.0 else 0.0
    val pctText = if (estimatedHours > 0.0) {
        val pct = ((item.playtimeHours / estimatedHours) * 100.0).roundToInt().coerceIn(0, 100)
        val votesStr = vnMeta.lengthVotes?.let { " · $it votes" }.orEmpty()
        "$pct%$votesStr"
    } else ""

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Playtime & Length (Open directly on blurred backdrop, zero box)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playtime & Length",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )
            if (pctText.isNotEmpty()) {
                Text(
                    text = pctText,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.clickable {
                    hoursInput = String.format(Locale.US, "%.1f", item.playtimeHours)
                    showHoursDialog = true
                },
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.1fh", item.playtimeHours),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                if (estimatedHours > 0.0) {
                    Text(
                        text = String.format(Locale.US, "/ ~%.1fh avg", estimatedHours),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            // Bare typographic steppers (zero pill buttons)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "−0.5h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clickable { onStepPlaytime(-0.5) }
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "+0.5h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.90f),
                    modifier = Modifier
                        .clickable { onStepPlaytime(0.5) }
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = "+1h",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    modifier = Modifier
                        .clickable { onStepPlaytime(1.0) }
                        .padding(vertical = 2.dp)
                )
            }
        }

        if (estimatedHours > 0.0) {
            val fraction = (item.playtimeHours / estimatedHours).toFloat().coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(if (fraction >= 1f) AccentJade else Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Routes & Endings Checklist (Hairline open rows, hollow Nuvio check ring)
        val completedCount = routes.count { it.isCompleted }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Routes & Endings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.3).sp
                )
                if (routes.isNotEmpty()) {
                    Text(
                        text = "($completedCount / ${routes.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                text = if (showAddBar) "Close" else "+ Add Route",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clickable { showAddBar = !showAddBar }
                    .padding(vertical = 2.dp)
            )
        }

        if (showAddBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(0.dp, Color.Transparent)
                ) {
                    Column {
                        BasicTextField(
                            value = newRouteName,
                            onValueChange = { newRouteName = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.5.sp),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = newRouteName.trim()
                                if (trimmed.isNotEmpty()) {
                                    onAddCustomRoute(trimmed)
                                    newRouteName = ""
                                    showAddBar = false
                                }
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            decorationBox = { inner ->
                                if (newRouteName.isEmpty()) {
                                    Text(
                                        text = "Add custom route or ending...",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 13.sp
                                    )
                                }
                                inner()
                            }
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.24f))
                        )
                    }
                }
                Text(
                    text = "Add",
                    color = AccentJade,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val trimmed = newRouteName.trim()
                            if (trimmed.isNotEmpty()) {
                                onAddCustomRoute(trimmed)
                                newRouteName = ""
                                showAddBar = false
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            routes.forEachIndexed { index, route ->
                val isSpoilerHidden = route.isSpoiler &&
                    spoilerLevel.level < VnSpoilerLevel.ALL.level &&
                    route.id !in revealedSpoilerRouteIds

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isSpoilerHidden) {
                                revealedSpoilerRouteIds = revealedSpoilerRouteIds + route.id
                            } else {
                                onOpenRouteDetail(route)
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!route.characterImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(route.characterImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = route.routeName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = formatRouteTypeLabel(route.routeType).firstOrNull()?.toString() ?: "R",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSpoilerHidden) "${route.routeName} (Tap to reveal)" else route.routeName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (isSpoilerHidden) Modifier.blur(6.dp) else Modifier
                        )

                        val subParts = buildList {
                            add(formatRouteTypeLabel(route.routeType))
                            route.userRating?.let { add(String.format(Locale.US, "★ %.1f", it)) }
                            if (route.isBookmarked) add("Bookmarked")
                            if (!route.userNote.isNullOrBlank()) add("Note")
                        }
                        Text(
                            text = subParts.joinToString(" · "),
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nuvio hollow stroked checkmark ring
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (route.isCompleted) AccentJade.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.22f))
                            .border(
                                width = 1.4.dp,
                                color = if (route.isCompleted) AccentJade else Color.White.copy(alpha = 0.72f),
                                shape = CircleShape
                            )
                            .clickable { onToggleRouteCompleted(route) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (route.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Completed",
                                tint = AccentJade,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                if (index < routes.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }

    if (showHoursDialog) {
        AlertDialog(
            onDismissRequest = { showHoursDialog = false },
            containerColor = AmoledBlack,
            title = {
                Text("Playtime (Hours)", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = hoursInput,
                    onValueChange = { next ->
                        if (next.length <= 7 && next.all { it.isDigit() || it == '.' }) {
                            hoursInput = next
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = hoursInput.toDoubleOrNull()
                    if (parsed != null) onSetExactPlaytime(parsed)
                    showHoursDialog = false
                }) {
                    Text("Save", color = AccentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoursDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }
}

internal fun formatRouteTypeLabel(type: String): String = when (type.uppercase()) {
    "COMMON" -> "Common"
    "HEROINE" -> "Heroine"
    "TRUE" -> "True End"
    "NORMAL" -> "Normal"
    "BAD" -> "Bad End"
    "EXTRA" -> "Extra"
    else -> "Custom"
}
