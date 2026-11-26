package com.omnitrack.app.ui.screens.detail.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnitrack.app.data.enrichment.WatchProviderBundle
import com.omnitrack.app.data.enrichment.WatchProviderItem
import com.omnitrack.app.data.enrichment.WatchProvidersAndAwardsClient
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.DarkSurfaceElevated
import com.omnitrack.app.ui.theme.GlassBorder
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun DetailWhereToWatchSection(
    bundle: WatchProviderBundle?,
    configuredRegionPref: String,
    isLoading: Boolean,
    onSelectRegionPref: (String) -> Unit
) {
    val context = LocalContext.current
    var showRegionPicker by remember { mutableStateOf(false) }

    val effectiveRegion = bundle?.regionCode
        ?: if (configuredRegionPref == "AUTO" || configuredRegionPref.isBlank()) {
            Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US) ?: "US"
        } else {
            configuredRegionPref.uppercase(Locale.US)
        }
    val flag = WatchProvidersAndAwardsClient.flagForCountry(effectiveRegion)
    val regionButtonLabel = if (configuredRegionPref == "AUTO" || configuredRegionPref.isBlank()) {
        "$flag Auto ($effectiveRegion)"
    } else {
        "$flag $effectiveRegion"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WHERE TO WATCH",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = WatchProvidersAndAwardsClient.regionDisplayName(effectiveRegion),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.7.dp, GlassBorder, RoundedCornerShape(6.dp))
                    .clickable { showRegionPicker = true }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = regionButtonLabel,
                    color = TextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select country",
                    tint = TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        if (isLoading && bundle == null) {
            Text(
                text = "Checking regional availability...",
                color = TextMuted,
                fontSize = 12.5.sp
            )
        } else if (bundle == null || bundle.isEmpty()) {
            Text(
                text = "No streaming, rental, or purchase listings found in ${WatchProvidersAndAwardsClient.regionDisplayName(effectiveRegion)}. Tap the region selector above to check another country.",
                color = TextMuted,
                fontSize = 12.5.sp,
                lineHeight = 18.sp
            )
        } else {
            val groups = listOf(
                "Stream" to bundle.flatrate,
                "Free / Ads" to (bundle.free + bundle.ads).distinctBy { it.providerId },
                "Rent" to bundle.rent,
                "Buy" to bundle.buy
            ).filter { it.second.isNotEmpty() }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                groups.forEach { (categoryLabel, providers) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = categoryLabel.uppercase(Locale.US),
                            color = TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            providers.forEach { provider ->
                                WatchProviderLogoTile(
                                    provider = provider,
                                    onClick = {
                                        bundle.link?.let { url ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Provider listings powered by TMDb / JustWatch",
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 10.5.sp
                    )
                    bundle.link?.let { url ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Open JustWatch", color = AccentGold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showRegionPicker) {
        WatchRegionPickerSheet(
            selectedRegionPref = configuredRegionPref,
            availableCodes = bundle?.availableRegions.orEmpty(),
            onSelectRegion = { chosen ->
                onSelectRegionPref(chosen)
                showRegionPicker = false
            },
            onDismiss = { showRegionPicker = false }
        )
    }
}

@Composable
fun WatchProviderLogoTile(
    provider: WatchProviderItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceElevated)
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            ProviderMonogramFallback(provider.providerName)
            if (provider.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = provider.logoUrl,
                    contentDescription = provider.providerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Text(
            text = provider.providerName,
            color = TextSecondary,
            fontSize = 10.5.sp,
            maxLines = 2,
            lineHeight = 12.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProviderMonogramFallback(name: String) {
    val letters = remember(name) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase(Locale.US) }
            .ifBlank { "?" }
    }
    Text(
        text = letters,
        color = AccentGold,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchRegionPickerSheet(
    selectedRegionPref: String,
    availableCodes: List<String> = emptyList(),
    onSelectRegion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var search by remember { mutableStateOf("") }
    val detectedCode = remember {
        Locale.getDefault().country.takeIf { it.length == 2 }?.uppercase(Locale.US) ?: "US"
    }
    val allRegions = remember(availableCodes) {
        val base = WatchProvidersAndAwardsClient.SUPPORTED_WATCH_REGIONS
        val extraCodes = availableCodes
            .filter { code -> base.none { it.code.equals(code, ignoreCase = true) } }
            .map { code ->
                com.omnitrack.app.data.enrichment.WatchRegionOption(
                    code = code,
                    displayName = WatchProvidersAndAwardsClient.regionDisplayName(code),
                    flagEmoji = WatchProvidersAndAwardsClient.flagForCountry(code)
                )
            }
        (base + extraCodes).sortedBy { it.displayName }
    }
    val filtered = remember(search, allRegions) {
        val q = search.trim().lowercase(Locale.US)
        if (q.isBlank()) allRegions
        else allRegions.filter {
            it.displayName.lowercase(Locale.US).contains(q) || it.code.lowercase(Locale.US).contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STREAMING REGION",
                        color = AccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Choose country for Where to Watch & Streaming",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = { Text("Search country or code (e.g., Pakistan, IN, UK, JP)...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentGold,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = Color(0xFF0A0A0A),
                    unfocusedContainerColor = Color(0xFF0A0A0A)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                item(key = "AUTO") {
                    val isSelected = selectedRegionPref.equals("AUTO", ignoreCase = true) || selectedRegionPref.isBlank()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRegion("AUTO") }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Public, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Detect (Device Locale · $detectedCode)",
                                color = if (isSelected) AccentGold else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = WatchProvidersAndAwardsClient.regionDisplayName(detectedCode),
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                }

                items(filtered, key = { it.code }) { region ->
                    val isSelected = selectedRegionPref.equals(region.code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectRegion(region.code) }
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = region.flagEmoji, fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = region.displayName,
                                color = if (isSelected) AccentGold else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = region.code,
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = AccentGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                }
            }
        }
    }
}
