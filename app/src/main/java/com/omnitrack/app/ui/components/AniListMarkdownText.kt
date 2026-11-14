package com.omnitrack.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Normalizes AniList raw description (Markdown + HTML hybrid) and renders it as an
 * unboxed Compose AnnotatedString with clickable links, bold/italics, and tap-to-reveal spoilers.
 */
fun parseAniListMarkdownToAnnotatedString(
    rawText: String,
    primaryColor: Color = TextSecondary,
    linkColor: Color = AccentArctic,
    revealSpoilers: Boolean = false
): AnnotatedString {
    // 1. Decode HTML entities
    val decoded = rawText
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&ndash;", "–")
        .replace("&mdash;", "—")
        .replace("&nbsp;", " ")
        .replace("&eacute;", "é")
        .replace("&rsquo;", "'")
        .replace("&lsquo;", "'")
        .replace("&ldquo;", "\"")
        .replace("&rdquo;", "\"")

    // 2. Clean out media tags (images/webm/youtube) that clutter descriptions
    var text = decoded
        // AniList image syntax: img(url), img300(url), etc.
        .replace(Regex("""(img\d*|webm|youtube)\((https?://[^\s)]+)\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<img[^>]*>""", RegexOption.IGNORE_CASE), "")
        // HTML line breaks
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        // HTML bold
        .replace(Regex("""<(b|strong)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "**${it.groupValues[2]}**" }
        // HTML italics
        .replace(Regex("""<(i|em)>(.*?)</\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { "*${it.groupValues[2]}*" }
        // HTML links: <a href="...">label</a>
        .replace(Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            val href = it.groupValues[1].trim()
            val anchor = it.groupValues[2].replace(Regex("""<.*?>"""), "").trim()
            "[$anchor]($href)"
        }
        // AniList HTML spoilers <span class="markdown_spoiler">...</span>
        .replace(Regex("""<span[^>]*class=['"]?markdown_spoiler['"]?[^>]*>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            "~!${it.groupValues[1]}!~"
        }
        // Headings (#, ##, ###) -> **Heading**
        .replace(Regex("""(?m)^#{1,6}\s*(.+)$""")) { "**${it.groupValues[1].trim()}**" }
        // Horizontal dividers
        .replace(Regex("""(?m)^[_\-*]{3,}\s*$"""), "\n")
        // Underline bold __text__ -> **text**
        .replace(Regex("""__(.*?)__""", RegexOption.DOT_MATCHES_ALL)) { "**${it.groupValues[1]}**" }
        // Strip any remaining unhandled HTML tags
        .replace(Regex("""<.*?>"""), "")
        // Normalize consecutive newlines
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    // 3. Tokenize inline tokens:
    // - Markdown links: [Label](url) where url can be absolute or relative (/character/...)
    // - Spoilers: ~!...~! (including multiline)
    // - Bold: **...**
    // - Italics: *...*
    // - Bare URLs: https://... or http://...
    val tokenRegex = Regex(
        pattern = """(~![\s\S]*?!~|\[([^\]]+)\]\(([^)\s]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*|(https?://[^\s)]+))"""
    )

    return buildAnnotatedString {
        var lastIdx = 0
        for (match in tokenRegex.findAll(text)) {
            val range = match.range
            if (range.first > lastIdx) {
                withStyle(SpanStyle(color = primaryColor)) {
                    append(text.substring(lastIdx, range.first))
                }
            }

            val fullMatch = match.value
            when {
                // Spoiler: ~!content!~
                fullMatch.startsWith("~!") && fullMatch.endsWith("!~") -> {
                    val spoilerContent = fullMatch.removeSurrounding("~!", "!~").trim()
                    if (revealSpoilers) {
                        withStyle(SpanStyle(color = primaryColor, fontStyle = FontStyle.Italic)) {
                            append(spoilerContent)
                        }
                    } else {
                        withStyle(
                            SpanStyle(
                                color = TextMuted,
                                background = Color(0x2EFFFFFF),
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(" [Spoiler] ")
                        }
                    }
                }

                // Markdown Link: [Label](url)
                fullMatch.startsWith("[") && fullMatch.contains("](") && fullMatch.endsWith(")") -> {
                    val label = fullMatch.substringAfter("[").substringBefore("](")
                    val rawUrl = fullMatch.substringAfter("](").removeSuffix(")").trim()
                    val resolvedUrl = when {
                        rawUrl.startsWith("/") -> "https://anilist.co$rawUrl"
                        rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
                        else -> "https://$rawUrl"
                    }

                    withLink(
                        LinkAnnotation.Url(
                            url = resolvedUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        )
                    ) {
                        append(label)
                    }
                }

                // Bold: **content**
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val boldContent = fullMatch.removeSurrounding("**")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary)) {
                        append(boldContent)
                    }
                }

                // Italics: *content*
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val italicContent = fullMatch.removeSurrounding("*")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = primaryColor)) {
                        append(italicContent)
                    }
                }

                // Bare URL
                fullMatch.startsWith("http://") || fullMatch.startsWith("https://") -> {
                    withLink(
                        LinkAnnotation.Url(
                            url = fullMatch,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(fullMatch)
                    }
                }
            }
            lastIdx = range.last + 1
        }

        if (lastIdx < text.length) {
            withStyle(SpanStyle(color = primaryColor)) {
                append(text.substring(lastIdx))
            }
        }
    }
}

/**
 * Modern unboxed AniList description composable with expandable text and clickable hyperlinks.
 */
@Composable
fun AniListMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 4,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 19.sp,
    primaryColor: Color = TextSecondary
) {
    var expanded by remember { mutableStateOf(false) }
    var revealSpoilers by remember { mutableStateOf(false) }
    val hasSpoilers = remember(text) { text.contains("~!") || text.contains("markdown_spoiler") }

    val annotatedString = remember(text, revealSpoilers) {
        parseAniListMarkdownToAnnotatedString(
            rawText = text,
            primaryColor = primaryColor,
            linkColor = AccentArctic,
            revealSpoilers = revealSpoilers
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = annotatedString,
            fontSize = fontSize,
            lineHeight = lineHeight,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.animateContentSize()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (text.length > 180) {
                Text(
                    text = if (expanded) "Show Less" else "Read More",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentArctic,
                    modifier = Modifier.clickable {
                        expanded = !expanded
                        if (expanded) revealSpoilers = true
                    }
                )
            }

            if (hasSpoilers) {
                Text(
                    text = if (revealSpoilers) "Hide Spoilers" else "Reveal Spoilers",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                    modifier = Modifier.clickable {
                        revealSpoilers = !revealSpoilers
                    }
                )
            }
        }
    }
}
