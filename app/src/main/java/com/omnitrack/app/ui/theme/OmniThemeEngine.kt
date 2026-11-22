package com.omnitrack.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class OmniAppTheme(val displayName: String, val isEnhanced: Boolean) {
    GOLD("Obsidian Gold", false),
    WHITE("Monochrome", false),
    CRIMSON("Crimson", false),
    OCEAN("Ocean", false),
    VIOLET("Violet", false),
    EMERALD("Emerald", false),
    AMBER("Amber", false),
    ROSE("Rose", false),
    MESSENGER("Messenger", true),
    AMETHYST("Amethyst", true),
    BLOSSOM("Blossom", true),
    LAGOON("Lagoon", true),
    SUNSET("Sunset", true),
    CUSTOM("Custom", true);

    companion object {
        fun fromName(raw: String?): OmniAppTheme =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GOLD
    }
}

enum class OmniQuickAccentColor(val color: Color, val label: String) {
    GOLD(Color(0xFFE8C26A), "Gold"),
    PINK(Color(0xFFFF5F9E), "Pink"),
    PURPLE(Color(0xFF9B5CFF), "Purple"),
    BLUE(Color(0xFF397CFF), "Blue"),
    CYAN(Color(0xFF35D6E8), "Cyan"),
    GREEN(Color(0xFF57D67A), "Green"),
    YELLOW(Color(0xFFFFD447), "Yellow"),
    RED(Color(0xFFFF5263), "Red"),
    WHITE(Color(0xFFF4F7FF), "White")
}

data class OmniThemePalette(
    val accent: Color,
    val accentVariant: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val border: Color
)

data class OmniThemeSettings(
    val theme: OmniAppTheme = OmniAppTheme.GOLD,
    val amoledBlack: Boolean = true,
    val customFirstHex: String = "#E8C26A",
    val customSecondHex: String = "#35D6E8"
)

data class HsvColor(
    val hue: Float,
    val saturation: Float,
    val value: Float
) {
    fun toColor(): Color {
        val normalizedHue = ((hue % 360f) + 360f) % 360f
        val chroma = value * saturation
        val section = normalizedHue / 60f
        val secondary = chroma * (1f - abs(section % 2f - 1f))
        val match = value - chroma
        val (r, g, b) = when (section.toInt()) {
            0 -> Triple(chroma, secondary, 0f)
            1 -> Triple(secondary, chroma, 0f)
            2 -> Triple(0f, chroma, secondary)
            3 -> Triple(0f, secondary, chroma)
            4 -> Triple(secondary, 0f, chroma)
            else -> Triple(chroma, 0f, secondary)
        }
        return Color(
            red = (r + match).coerceIn(0f, 1f),
            green = (g + match).coerceIn(0f, 1f),
            blue = (b + match).coerceIn(0f, 1f)
        )
    }
}

fun Color.toHsv(): HsvColor {
    val highest = max(red, max(green, blue))
    val lowest = min(red, min(green, blue))
    val delta = highest - lowest
    val hue = when {
        delta == 0f -> 0f
        highest == red -> 60f * (((green - blue) / delta) % 6f)
        highest == green -> 60f * ((blue - red) / delta + 2f)
        else -> 60f * ((red - green) / delta + 4f)
    }
    return HsvColor(
        hue = if (hue < 0f) hue + 360f else hue,
        saturation = if (highest == 0f) 0f else delta / highest,
        value = highest
    )
}

fun Color.toThemeHex(): String {
    fun channel(value: Float): String =
        (value.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            .toString(16).padStart(2, '0').uppercase()
    return "#${channel(red)}${channel(green)}${channel(blue)}"
}

fun String?.toThemeColorOrNull(): Color? {
    val hex = this?.trim()?.removePrefix("#").orEmpty()
    if (hex.length != 6 || hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    return Color(
        red = hex.substring(0, 2).toInt(16) / 255f,
        green = hex.substring(2, 4).toInt(16) / 255f,
        blue = hex.substring(4, 6).toInt(16) / 255f
    )
}

object OmniThemePalettes {
    private val Gold = OmniThemePalette(
        accent = Color(0xFFE8C26A),
        accentVariant = Color(0xFFCFA54B),
        background = Color(0xFF000000),
        surface = Color(0xFF12110E),
        surfaceElevated = Color(0xFF1A1813),
        surfaceCard = Color(0xFF221F19),
        border = Color(0xFF28241D)
    )
    private val White = OmniThemePalette(
        accent = Color(0xFFF5F5F5),
        accentVariant = Color(0xFFD4D4D8),
        background = Color(0xFF0D0D0D),
        surface = Color(0xFF141414),
        surfaceElevated = Color(0xFF1C1C1E),
        surfaceCard = Color(0xFF242426),
        border = Color(0xFF2A2A2E)
    )
    private val Crimson = OmniThemePalette(
        accent = Color(0xFFF43F5E),
        accentVariant = Color(0xFFE11D48),
        background = Color(0xFF0D0A0B),
        surface = Color(0xFF161012),
        surfaceElevated = Color(0xFF1F1619),
        surfaceCard = Color(0xFF281C20),
        border = Color(0xFF332127)
    )
    private val Ocean = OmniThemePalette(
        accent = Color(0xFF38BDF8),
        accentVariant = Color(0xFF0284C7),
        background = Color(0xFF080C12),
        surface = Color(0xFF0F151E),
        surfaceElevated = Color(0xFF161E2B),
        surfaceCard = Color(0xFF1C2736),
        border = Color(0xFF243144)
    )
    private val Violet = OmniThemePalette(
        accent = Color(0xFFA855F7),
        accentVariant = Color(0xFF9333EA),
        background = Color(0xFF0C0912),
        surface = Color(0xFF15101E),
        surfaceElevated = Color(0xFF1E172B),
        surfaceCard = Color(0xFF261D36),
        border = Color(0xFF322546)
    )
    private val Emerald = OmniThemePalette(
        accent = Color(0xFF22D37C),
        accentVariant = Color(0xFF0BBF9A),
        background = Color(0xFF08100C),
        surface = Color(0xFF0E1914),
        surfaceElevated = Color(0xFF14241D),
        surfaceCard = Color(0xFF1A2E25),
        border = Color(0xFF223B30)
    )
    private val Amber = OmniThemePalette(
        accent = Color(0xFFFB923C),
        accentVariant = Color(0xFFEA580C),
        background = Color(0xFF0F0C09),
        surface = Color(0xFF18130E),
        surfaceElevated = Color(0xFF211A14),
        surfaceCard = Color(0xFF2A211A),
        border = Color(0xFF352A21)
    )
    private val Rose = OmniThemePalette(
        accent = Color(0xFFEC70A9),
        accentVariant = Color(0xFFDB2777),
        background = Color(0xFF0F090D),
        surface = Color(0xFF191016),
        surfaceElevated = Color(0xFF23161F),
        surfaceCard = Color(0xFF2C1B27),
        border = Color(0xFF382231)
    )
    private val Messenger = OmniThemePalette(
        accent = Color(0xFF168AFF),
        accentVariant = Color(0xFF38BDF8),
        background = Color(0xFF090E15),
        surface = Color(0xFF101823),
        surfaceElevated = Color(0xFF172230),
        surfaceCard = Color(0xFF1D2B3D),
        border = Color(0xFF26384E)
    )
    private val Amethyst = OmniThemePalette(
        accent = Color(0xFF8B5CF6),
        accentVariant = Color(0xFFC084FC),
        background = Color(0xFF0F0B14),
        surface = Color(0xFF181220),
        surfaceElevated = Color(0xFF221A2E),
        surfaceCard = Color(0xFF2B213A),
        border = Color(0xFF362A4A)
    )
    private val Blossom = OmniThemePalette(
        accent = Color(0xFFEC4899),
        accentVariant = Color(0xFFFDA4AF),
        background = Color(0xFF120A10),
        surface = Color(0xFF1C1019),
        surfaceElevated = Color(0xFF261622),
        surfaceCard = Color(0xFF301B2B),
        border = Color(0xFF3E2337)
    )
    private val Lagoon = OmniThemePalette(
        accent = Color(0xFF14B8A6),
        accentVariant = Color(0xFF22D3EE),
        background = Color(0xFF081110),
        surface = Color(0xFF0F1C1B),
        surfaceElevated = Color(0xFF152725),
        surfaceCard = Color(0xFF1B322F),
        border = Color(0xFF24403C)
    )
    private val Sunset = OmniThemePalette(
        accent = Color(0xFFF97316),
        accentVariant = Color(0xFFF43F5E),
        background = Color(0xFF120C09),
        surface = Color(0xFF1C130F),
        surfaceElevated = Color(0xFF261A15),
        surfaceCard = Color(0xFF30211B),
        border = Color(0xFF3D2A22)
    )

    fun resolve(settings: OmniThemeSettings): OmniThemePalette {
        val base = when (settings.theme) {
            OmniAppTheme.GOLD -> Gold
            OmniAppTheme.WHITE -> White
            OmniAppTheme.CRIMSON -> Crimson
            OmniAppTheme.OCEAN -> Ocean
            OmniAppTheme.VIOLET -> Violet
            OmniAppTheme.EMERALD -> Emerald
            OmniAppTheme.AMBER -> Amber
            OmniAppTheme.ROSE -> Rose
            OmniAppTheme.MESSENGER -> Messenger
            OmniAppTheme.AMETHYST -> Amethyst
            OmniAppTheme.BLOSSOM -> Blossom
            OmniAppTheme.LAGOON -> Lagoon
            OmniAppTheme.SUNSET -> Sunset
            OmniAppTheme.CUSTOM -> {
                val c1 = settings.customFirstHex.toThemeColorOrNull() ?: Color(0xFFE8C26A)
                val c2 = settings.customSecondHex.toThemeColorOrNull() ?: Color(0xFF35D6E8)
                val safe1 = if (c1.luminance() < 0.03f) lerp(c1, Color.White, 0.25f) else c1
                val safe2 = if (c2.luminance() < 0.03f) lerp(c2, Color.White, 0.25f) else c2
                OmniThemePalette(
                    accent = safe1,
                    accentVariant = safe2,
                    background = Color(0xFF0B0A0E),
                    surface = lerp(Color(0xFF121116), safe1, 0.06f),
                    surfaceElevated = lerp(Color(0xFF1A181F), safe1, 0.08f),
                    surfaceCard = lerp(Color(0xFF221F28), safe1, 0.10f),
                    border = lerp(Color(0xFF292631), safe1, 0.14f)
                )
            }
        }
        return if (settings.amoledBlack) {
            base.copy(background = Color(0xFF000000))
        } else {
            base
        }
    }

    fun previewBrush(
        theme: OmniAppTheme,
        customFirst: Color,
        customSecond: Color
    ): Brush {
        val dummy = OmniThemeSettings(
            theme = theme,
            amoledBlack = true,
            customFirstHex = customFirst.toThemeHex(),
            customSecondHex = customSecond.toThemeHex()
        )
        val p = resolve(dummy)
        if (!theme.isEnhanced) {
            return Brush.linearGradient(listOf(p.accent, p.accentVariant))
        }
        val colors = when (theme) {
            OmniAppTheme.MESSENGER -> listOf(Color(0xFF006DFF), Color(0xFF168AFF), Color(0xFF38BDF8), Color(0xFF8AD8FF))
            OmniAppTheme.AMETHYST -> listOf(Color(0xFF6D28D9), Color(0xFF8B5CF6), Color(0xFFC084FC), Color(0xFFD8B4FE))
            OmniAppTheme.BLOSSOM -> listOf(Color(0xFFDB2777), Color(0xFFEC4899), Color(0xFFF472B6), Color(0xFFFDA4AF))
            OmniAppTheme.LAGOON -> listOf(Color(0xFF0891B2), Color(0xFF14B8A6), Color(0xFF2DD4BF), Color(0xFF5EEAD4))
            OmniAppTheme.SUNSET -> listOf(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFF97316), Color(0xFFFDBA74))
            OmniAppTheme.CUSTOM -> listOf(
                p.accent,
                lerp(p.accent, p.accentVariant, 0.35f),
                p.accentVariant,
                lerp(p.accentVariant, p.accent, 0.35f)
            )
            else -> listOf(p.accent, p.accentVariant)
        }
        return Brush.sweepGradient(colors + colors.first())
    }
}

object OmniThemeState {
    var accent by mutableStateOf(Color(0xFFE8C26A))
        private set
    var accentVariant by mutableStateOf(Color(0xFFCFA54B))
        private set
    var onAccent by mutableStateOf(Color(0xFF000000))
        private set
    var canvas by mutableStateOf(Color(0xFF000000))
        private set
    var surface by mutableStateOf(Color(0xFF12110E))
        private set
    var surfaceElevated by mutableStateOf(Color(0xFF1A1813))
        private set
    var surfaceCard by mutableStateOf(Color(0xFF221F19))
        private set
    var glassSurface by mutableStateOf(Color(0xCC12110E))
        private set
    var glassBorder by mutableStateOf(Color(0xFF28241D))
        private set

    fun apply(settings: OmniThemeSettings) {
        val p = OmniThemePalettes.resolve(settings)
        accent = p.accent
        accentVariant = p.accentVariant
        onAccent = if (p.accent.luminance() > 0.42f) Color(0xFF000000) else Color(0xFFFFFFFF)
        canvas = p.background
        surface = p.surface
        surfaceElevated = p.surfaceElevated
        surfaceCard = p.surfaceCard
        glassSurface = p.surface.copy(alpha = 0.85f)
        glassBorder = p.border
    }
}
