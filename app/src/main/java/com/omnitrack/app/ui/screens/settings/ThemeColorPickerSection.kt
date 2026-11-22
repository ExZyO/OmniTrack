package com.omnitrack.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.AmoledBlack
import com.omnitrack.app.ui.theme.HsvColor
import com.omnitrack.app.ui.theme.OmniAppTheme
import com.omnitrack.app.ui.theme.OmniQuickAccentColor
import com.omnitrack.app.ui.theme.OmniThemePalettes
import com.omnitrack.app.ui.theme.OmniThemeSettings
import com.omnitrack.app.ui.theme.OnAccentColor
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary
import com.omnitrack.app.ui.theme.toHsv
import com.omnitrack.app.ui.theme.toThemeColorOrNull
import com.omnitrack.app.ui.theme.toThemeHex

@Composable
fun ThemeColorPickerSection(
    themeSettings: OmniThemeSettings,
    onSelectTheme: (OmniAppTheme) -> Unit,
    onToggleAmoledBlack: (Boolean) -> Unit,
    onCustomFirstColorChange: (String) -> Unit,
    onCustomSecondColorChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstColor = remember(themeSettings.customFirstHex) {
        themeSettings.customFirstHex.toThemeColorOrNull() ?: Color(0xFFE8C26A)
    }
    val secondColor = remember(themeSettings.customSecondHex) {
        themeSettings.customSecondHex.toThemeColorOrNull() ?: Color(0xFF35D6E8)
    }

    val classicThemes = remember {
        listOf(
            OmniAppTheme.GOLD,
            OmniAppTheme.WHITE,
            OmniAppTheme.CRIMSON,
            OmniAppTheme.OCEAN,
            OmniAppTheme.VIOLET,
            OmniAppTheme.EMERALD,
            OmniAppTheme.AMBER,
            OmniAppTheme.ROSE
        )
    }
    val enhancedThemes = remember {
        listOf(
            OmniAppTheme.MESSENGER,
            OmniAppTheme.AMETHYST,
            OmniAppTheme.BLOSSOM,
            OmniAppTheme.LAGOON,
            OmniAppTheme.SUNSET,
            OmniAppTheme.CUSTOM
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "CLASSIC ACCENTS",
            color = TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        OmniThemeGrid(
            themes = classicThemes,
            selectedTheme = themeSettings.theme,
            customFirst = firstColor,
            customSecond = secondColor,
            onThemeSelected = onSelectTheme
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        Text(
            text = "ENHANCED & CUSTOM",
            color = TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp
        )
        OmniThemeGrid(
            themes = enhancedThemes,
            selectedTheme = themeSettings.theme,
            customFirst = firstColor,
            customSecond = secondColor,
            onThemeSelected = onSelectTheme
        )

        AnimatedVisibility(visible = themeSettings.theme == OmniAppTheme.CUSTOM) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)
                CustomThemeColorPicker(
                    label = "Primary Accent Color",
                    selectedColor = firstColor,
                    onColorSelected = { color -> onCustomFirstColorChange(color.toThemeHex()) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.5.dp)
                CustomThemeColorPicker(
                    label = "Secondary Accent Color",
                    selectedColor = secondColor,
                    onColorSelected = { color -> onCustomSecondColorChange(color.toThemeHex()) }
                )
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.6.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleAmoledBlack(!themeSettings.amoledBlack) }
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Pure AMOLED Black Canvas",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Keep pitch-black #000000 canvas or use subtle theme-tinted darkness",
                    color = TextSecondary,
                    fontSize = 11.5.sp
                )
            }
            Switch(
                checked = themeSettings.amoledBlack,
                onCheckedChange = onToggleAmoledBlack,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccentColor,
                    checkedTrackColor = AccentGold,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.14f)
                )
            )
        }
    }
}

@Composable
private fun OmniThemeGrid(
    themes: List<OmniAppTheme>,
    selectedTheme: OmniAppTheme,
    customFirst: Color,
    customSecond: Color,
    onThemeSelected: (OmniAppTheme) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 10.dp
        val columns = ((maxWidth + spacing) / (76.dp + spacing))
            .toInt()
            .coerceIn(3, 4)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            themes.chunked(columns).forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    rowThemes.forEach { theme ->
                        OmniThemeChip(
                            theme = theme,
                            isSelected = theme == selectedTheme,
                            customFirst = customFirst,
                            customSecond = customSecond,
                            onClick = { onThemeSelected(theme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(columns - rowThemes.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun OmniThemeChip(
    theme: OmniAppTheme,
    isSelected: Boolean,
    customFirst: Color,
    customSecond: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = OmniThemePalettes.resolve(
        OmniThemeSettings(
            theme = theme,
            amoledBlack = true,
            customFirstHex = customFirst.toThemeHex(),
            customSecondHex = customSecond.toThemeHex()
        )
    )
    val previewBrush = OmniThemePalettes.previewBrush(theme, customFirst, customSecond)
    val checkTint = if (palette.accent.luminance() > 0.48f) Color.Black else Color.White

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = palette.accent,
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (theme == OmniAppTheme.CUSTOM) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(customThemeRingBrush())
                        .padding(3.5.dp)
                        .clip(CircleShape)
                        .background(AmoledBlack),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(previewBrush),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = checkTint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = theme.displayName,
            fontSize = 11.sp,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .size(width = 30.dp, height = 2.5.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isSelected) previewBrush else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
        )
    }
}

private fun customThemeRingBrush(): Brush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF004D),
        Color(0xFFFFD23F),
        Color(0xFF00E676),
        Color(0xFF00D9FF),
        Color(0xFF3D5AFE),
        Color(0xFFFF4FD8),
        Color(0xFFFF004D)
    )
)

@Composable
private fun CustomThemeColorPicker(
    label: String,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var hexValue by remember(selectedColor) { mutableStateOf(selectedColor.toThemeHex()) }
    var hsv by remember(selectedColor) { mutableStateOf(selectedColor.toHsv()) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    fontSize = 13.5.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${selectedColor.toThemeHex()} \u00B7 Tap to open HSV & Hex tuner",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Quick preset circles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OmniQuickAccentColor.entries.forEach { option ->
                val selected = option.color.toThemeHex() == selectedColor.toThemeHex()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .semantics { contentDescription = option.label }
                        .border(
                            width = if (selected) 2.dp else 0.8.dp,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.16f),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(option.color)
                        .clickable {
                            hsv = option.color.toHsv()
                            hexValue = option.color.toThemeHex()
                            onColorSelected(option.color)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (option.color.luminance() > 0.52f) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaturationValuePicker(
                    hsv = hsv,
                    onHsvChanged = { updated ->
                        hsv = updated
                        val c = updated.toColor()
                        hexValue = c.toThemeHex()
                        onColorSelected(c)
                    }
                )
                HuePicker(
                    hue = hsv.hue,
                    onHueChanged = { hue ->
                        val updated = hsv.copy(hue = hue)
                        hsv = updated
                        val c = updated.toColor()
                        hexValue = c.toThemeHex()
                        onColorSelected(c)
                    }
                )
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { input ->
                        val normalized = input.take(7).uppercase()
                        hexValue = normalized
                        normalized.toThemeColorOrNull()?.let { parsed ->
                            hsv = parsed.toHsv()
                            onColorSelected(parsed)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Hex Color (#RRGGBB)", fontSize = 11.5.sp) },
                    placeholder = { Text("#E8C26A") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentGold
                    )
                )
            }
        }
    }
}

@Composable
private fun SaturationValuePicker(
    hsv: HsvColor,
    onHsvChanged: (HsvColor) -> Unit
) {
    fun update(position: Offset, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        onHsvChanged(
            hsv.copy(
                saturation = (position.x / width).coerceIn(0f, 1f),
                value = (1f - position.y / height).coerceIn(0f, 1f)
            )
        )
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(hsv.hue) {
                detectTapGestures { update(it, size.width.toFloat(), size.height.toFloat()) }
            }
            .pointerInput(hsv.hue) {
                detectDragGestures(
                    onDragStart = { update(it, size.width.toFloat(), size.height.toFloat()) },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position, size.width.toFloat(), size.height.toFloat())
                    }
                )
            }
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.White, HsvColor(hsv.hue, 1f, 1f).toColor())
            )
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
        )
        val cx = hsv.saturation.coerceIn(0f, 1f) * size.width
        val cy = (1f - hsv.value.coerceIn(0f, 1f)) * size.height
        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = 8.dp.toPx(),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

@Composable
private fun HuePicker(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    fun update(x: Float, width: Float) {
        if (width <= 0f) return
        onHueChanged((x / width).coerceIn(0f, 1f) * 360f)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                detectTapGestures { update(it.x, size.width.toFloat()) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { update(it.x, size.width.toFloat()) },
                    onDrag = { change, _ ->
                        change.consume()
                        update(change.position.x, size.width.toFloat())
                    }
                )
            }
    ) {
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                )
            )
        )
        val markerX = (hue / 360f).coerceIn(0f, 1f) * size.width
        drawCircle(
            color = Color.White,
            radius = 7.dp.toPx(),
            center = Offset(markerX, size.height / 2f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
