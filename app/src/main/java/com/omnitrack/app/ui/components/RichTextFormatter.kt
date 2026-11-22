package com.omnitrack.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentGold
import com.omnitrack.app.ui.theme.TextPrimary

/**
 * Cleans Open Library / Wikipedia / Goodreads markdown & HTML quirks:
 * - Resolves `[label][ref]` + `[ref]: https://...` and `[label](https://...)` into tappable gold links.
 * - Converts `<i>`, `<em>`, `*italic*` to italics and `<b>`, `<strong>`, `**bold**` to bold.
 * - Strips horizontal rules (`----`), trailing `([Source][1])` / `Source: ...` / `(from wikipedia)` footers,
 *   and bare `http(s)://...` URLs.
 * - Moves `See also:` / `Contains:` / `Also contained in:` lines cleanly to the bottom.
 */
fun parseCleanRichText(raw: String, linkColor: Color = AccentGold): AnnotatedString {
    if (raw.isBlank()) return AnnotatedString("")

    var normalized = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</p\s*>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<p[^>]*>""", RegexOption.IGNORE_CASE), "")

    // Convert <a> tags into markdown links before generic HTML tag stripping
    val htmlLinkRegex = Regex("""<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    normalized = htmlLinkRegex.replace(normalized) { m ->
        val url = m.groupValues[1].trim()
        val label = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
        if (label.isNotBlank() && url.startsWith("http", ignoreCase = true)) "[$label]($url)" else label
    }

    // Collect reference-style link definitions: `[1]: https://...` or `  [id]: http://... "Title"`
    val refDefs = mutableMapOf<String, String>()
    val refDefRegex = Regex("""^\s*\[([^\]]+)\]:\s*(\S+)(?:\s+["'(][^"')]*["')])?\s*$""")
    val bodyLines = mutableListOf<String>()
    val trailingNotes = mutableListOf<String>()

    for (line in normalized.lines()) {
        val trimmed = line.trim()
        val refMatch = refDefRegex.matchEntire(line)
        if (refMatch != null) {
            val id = refMatch.groupValues[1].trim().lowercase()
            val url = refMatch.groupValues[2].trim().trimEnd(')', ']', '.')
            if (url.startsWith("http", ignoreCase = true)) {
                refDefs[id] = url
            }
            continue
        }
        // Drop horizontal rule lines (`----`, `===`, `___`)
        if (trimmed.matches(Regex("""^[-=_*]{3,}$"""))) {
            continue
        }
        // Drop standalone source footers like `(from wikipedia)`, `([Source][1])`, `Source: Open Library`
        if (trimmed.matches(Regex("""^\(?\[?source\]?(\[\d+\]|\([^)]+\))?\)?\.?$""", RegexOption.IGNORE_CASE)) ||
            trimmed.startsWith("Source:", ignoreCase = true) ||
            trimmed.equals("(from wikipedia)", ignoreCase = true) ||
            trimmed.equals("from wikipedia", ignoreCase = true)
        ) {
            continue
        }
        // Move "See also:" / "Contains:" / "Also contained in:" to the bottom
        if (trimmed.startsWith("See also:", ignoreCase = true) ||
            trimmed.startsWith("Contains:", ignoreCase = true) ||
            trimmed.startsWith("Also contained in:", ignoreCase = true)
        ) {
            trailingNotes.add(trimmed)
            continue
        }
        bodyLines.add(line)
    }

    var text = bodyLines.joinToString("\n")
    // Strip trailing inline `([Source][1])` or `(Source: ...)`
    text = text
        .replace(Regex("""\(\s*\[Source\]\[[^\]]+\]\s*\)""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[Source\]\[[^\]]+\]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\(\s*Source:\s*[^)]+\)""", RegexOption.IGNORE_CASE), "")

    if (trailingNotes.isNotEmpty()) {
        val cleanedBody = text.trim()
        text = buildString {
            if (cleanedBody.isNotBlank()) {
                append(cleanedBody)
                append("\n\n")
            }
            append(trailingNotes.joinToString("\n"))
        }
    }

    // Resolve `[label][refId]` into inline `[label](url)` (or bare `label` if missing/source)
    val refLinkRegex = Regex("""\[([^\]]+)\]\[([^\]]*)\]""")
    text = refLinkRegex.replace(text) { m ->
        val label = m.groupValues[1].trim()
        val refKey = m.groupValues[2].ifBlank { label }.trim().lowercase()
        val url = refDefs[refKey]
        if (label.equals("source", ignoreCase = true)) {
            ""
        } else if (url != null) {
            "[$label]($url)"
        } else {
            label
        }
    }

    // Strip any bare http(s) URLs that aren't wrapped inside markdown link parens `](https://...)`
    text = text.replace(Regex("""(?<!\]\()https?://\S+"""), "")
    // Convert HTML emphasis/strong tags into simple markers before generic HTML tag removal
    text = text
        .replace(Regex("""<\s*(em|i)\s*>""", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("""</\s*(em|i)\s*>""", RegexOption.IGNORE_CASE), "*")
        .replace(Regex("""<\s*(b|strong)\s*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""</\s*(b|strong)\s*>""", RegexOption.IGNORE_CASE), "**")
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    val tokenRegex = Regex("""\[([^\]]+)\]\((https?://[^)\s]+)\)|\*\*([^*]+)\*\*|\*([^*\n]+)\*""")
    return buildAnnotatedString {
        var cursor = 0
        for (match in tokenRegex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            val linkText = match.groups[1]?.value
            val linkUrl = match.groups[2]?.value
            val boldText = match.groups[3]?.value
            val italicText = match.groups[4]?.value

            when {
                linkText != null && linkUrl != null -> {
                    withLink(
                        LinkAnnotation.Url(
                            url = linkUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        )
                    ) {
                        append(linkText.replace("*", ""))
                    }
                }
                boldText != null -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }
                italicText != null -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

@Composable
fun FormattedRichText(
    rawText: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    linkColor: Color = AccentGold,
    fontSize: TextUnit = 13.5.sp,
    lineHeight: TextUnit = 20.sp,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null
) {
    val annotated = remember(rawText, linkColor) { parseCleanRichText(rawText, linkColor) }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout ?: {},
        modifier = modifier
    )
}
