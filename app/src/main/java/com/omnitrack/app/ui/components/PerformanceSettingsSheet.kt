package com.omnitrack.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.data.preferences.PerformanceSettings
import com.omnitrack.app.data.preferences.PreferenceManager
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

val LocalPerformanceSettings = compositionLocalOf { PerformanceSettings() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsSheet(
    preferenceManager: PreferenceManager,
    settings: PerformanceSettings,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AmoledBlack,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .background(AmoledBlack)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 18.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Performance & Smoothness",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Every switch ON = faster & lighter GPU/RAM usage",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                PerformanceSettingsContent(
                    preferenceManager = preferenceManager,
                    settings = settings,
                    initiallyExpanded = true
                )
            }
        }
    }
}

@Composable
fun PerformanceSettingsContent(
    preferenceManager: PreferenceManager,
    settings: PerformanceSettings,
    initiallyExpanded: Boolean = false
) {
    val scope = rememberCoroutineScope()
    var showAdvanced by remember { mutableStateOf(initiallyExpanded) }

    val reduceLiveBlur = !settings.liveNavBarBlur
    val activeCount = listOf(
        reduceLiveBlur,
        settings.disableCardDepthEffects,
        settings.disableCardAnimations
    ).count { it }

    Column(modifier = Modifier.fillMaxWidth()) {
        PerfSwitchRow(
            title = "Performance Mode",
            subtitle = "Turns ON all 3 visual trade-offs below for maximum FPS on slower phones",
            checked = settings.performanceModeEnabled,
            onCheckedChange = { enabled ->
                scope.launch { preferenceManager.setPerformanceModeEnabled(enabled) }
            }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showAdvanced = !showAdvanced }
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Visual Trade-Offs",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold
                )
                Text(
                    text = "All switches ON = faster \u00B7 $activeCount / 3 active",
                    fontSize = 11.5.sp,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = AccentGold,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = showAdvanced) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                PerfSwitchRow(
                    title = "Disable Floating Bar Glass Blur",
                    subtitle = "Replaces live backdrop blur with a fast translucent bar (biggest FPS boost)",
                    checked = reduceLiveBlur,
                    onCheckedChange = { reduce -> scope.launch { preferenceManager.setPerformanceOption("LIVE_BLUR", !reduce) } }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                PerfSwitchRow(
                    title = "Skip Card Depth & Sheen",
                    subtitle = "Removes specular glass borders & top reflections from poster cards",
                    checked = settings.disableCardDepthEffects,
                    onCheckedChange = { scope.launch { preferenceManager.setPerformanceOption("DISABLE_CARD_DEPTH", it) } }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                PerfSwitchRow(
                    title = "Reduce Animations",
                    subtitle = "Skips hero scroll/swipe parallax, cover fade-ins, tap bounce & tab zoom",
                    checked = settings.disableCardAnimations,
                    onCheckedChange = { scope.launch { preferenceManager.setPerformanceOption("DISABLE_ANIMS", it) } }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.6.dp)
                Text(
                    text = "GPU hardware bitmaps, smart grid cover sizing, offscreen tab limits & scroll-paused blur stay permanently active with no visual downsides.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )

                Text(
                    text = "Reset to Balanced Defaults",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    modifier = Modifier
                        .clickable { scope.launch { preferenceManager.resetPerformanceSettings() } }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun PerfSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnAccentColor,
                checkedTrackColor = AccentGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}
