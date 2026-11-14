package com.omnitrack.app.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.GlassBorder

/**
 * Rich Content Block for Dantotsu & AniList Threaded Comments and Bios.
 * Formats:
 * - Images and animated GIFs: img(url), img300(url), ![alt](url), <img src="url">, or raw image URLs (.gif, .png, .jpg, .webp, tenor, giphy)
 * - Clickable Spoilers: ~!hidden text/media!~ and [spoiler]...[/spoiler]
 * - Quotes: lines starting with >
 * - Clickable Markdown links: [label](url) and bare URLs https://...
 * - Styling: **bold**, *italic*, ~~strikethrough~~, `code`, and @mentions
 */
sealed class CommentBlock {
    data class Text(val text: AnnotatedString) : CommentBlock()
    data class Media(val url: String) : CommentBlock()
    data class Spoiler(val content: String) : CommentBlock()
    data class Quote(val text: String) : CommentBlock()
}

@Composable
fun CommentRichContent(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseCommentContent(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is CommentBlock.Text -> {
                    SelectionContainer {
                        Text(
                            text = block.text,
                            color = Color.White,
                            fontSize = 13.5.sp,
                            lineHeight = 19.5.sp
                        )
                    }
                }
                is CommentBlock.Media -> {
                    CommentMediaBlock(url = block.url)
                }
                is CommentBlock.Spoiler -> {
                    CommentSpoilerBlock(spoilerContent = block.content)
                }
                is CommentBlock.Quote -> {
                    CommentQuoteBlock(quoteText = block.text)
                }
            }
        }
    }
}

@Composable
private fun CommentMediaBlock(url: String) {
    val context = LocalContext.current
    var isError by remember { mutableStateOf(false) }

    if (!isError) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = "Attachment",
            modifier = Modifier
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .heightIn(max = 240.dp),
            contentScale = ContentScale.Fit,
            onError = { isError = true }
        )
    }
}

@Composable
private fun CommentSpoilerBlock(spoilerContent: String) {
    var isRevealed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isRevealed) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (isRevealed) GlassBorder else AccentArctic.copy(alpha = 0.35f),
                RoundedCornerShape(8.dp)
            )
            .clickable { isRevealed = !isRevealed }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null,
                tint = AccentArctic,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isRevealed) "Spoiler (tap to hide)" else "Spoiler (tap to reveal)",
                color = AccentArctic,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = isRevealed,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CommentRichContent(
                content = spoilerContent,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CommentQuoteBlock(quoteText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AccentArctic.copy(alpha = 0.7f))
        )
        Text(
            text = quoteText,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 18.sp
        )
    }
}

fun preprocessAniListHtml(raw: String): String {
    var s = raw
    // Replace HTML entities
    s = s.replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    // Replace <br>, <br/>, <br /> with newlines
    s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")

    // Replace <a> tags: <a href="url">text</a> -> [text](url)
    s = s.replace(Regex("""(?is)<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""")) { m ->
        val url = m.groupValues[1]
        val text = m.groupValues[2].replace(Regex("""<[^>]+>"""), "").trim()
        "[${text.ifEmpty { url }}]($url)"
    }

    // Replace <img> tags: <img src="url"/> -> img(url)
    s = s.replace(Regex("""(?is)<img\s+[^>]*src=["']([^"']+)["'][^>]*>""")) { m ->
        val url = m.groupValues[1]
        "img($url)"
    }

    // Strip <div>, <center>, <span>, <p> opening and closing tags (including <div align="center">, <center>, etc.)
    s = s.replace(Regex("""(?i)</?(?:div|center|span|p|font|header|footer|section)[^>]*>"""), "")

    return s
}

/**
 * Tokenizes AniList & Dantotsu comments into media, spoilers, quotes, and rich markdown text.
 */
private fun parseCommentContent(raw: String): List<CommentBlock> {
    if (raw.isBlank()) return emptyList()

    val clean = preprocessAniListHtml(raw)
    val blocks = mutableListOf<CommentBlock>()
    val lines = clean.trim().split("\n")
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotBlank()) {
            val content = textBuffer.toString().trim()
            val fragmented = fragmentInlineBlocks(content)
            blocks.addAll(fragmented)
            textBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmed = line.trim()

        // 1. Check for Quote
        if (trimmed.startsWith(">")) {
            flushText()
            val quoteContent = trimmed.removePrefix(">").trim()
            blocks.add(CommentBlock.Quote(quoteContent))
            continue
        }

        // 2. Direct media link on its own line
        val isDirectMedia = trimmed.matches(
            Regex("""(?i)^https?://[^\s]+(?:\.(?:gif|png|jpg|jpeg|webp)|tenor\.com/[^\s]+|giphy\.com/[^\s]+)(?:\?[^\s]*)?$""")
        )
        if (isDirectMedia) {
            flushText()
            blocks.add(CommentBlock.Media(trimmed))
            continue
        }

        textBuffer.appendLine(line)
    }

    flushText()
    return blocks.ifEmpty { listOf(CommentBlock.Text(parseMarkdownSpans(raw))) }
}

private fun fragmentInlineBlocks(text: String): List<CommentBlock> {
    val result = mutableListOf<CommentBlock>()
    val pattern = Regex(
        """(?is)(?:img(?:\(\d+\)|\d+)?\((https?://[^\s\)]+)\)|!\[.*?\]\((https?://[^\s\)]+)\)|<img\s+[^>]*src=["'](https?://[^"']+)["'][^>]*>|~!(.*?)!~|\[spoiler\](.*?)\[/spoiler\])"""
    )
    var lastIndex = 0

    for (match in pattern.findAll(text)) {
        if (match.range.first > lastIndex) {
            val preceding = text.substring(lastIndex, match.range.first).trim()
            if (preceding.isNotEmpty()) {
                result.add(CommentBlock.Text(parseMarkdownSpans(preceding)))
            }
        }

        val mediaUrl = match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { match.groupValues[3] }
        val spoilerContent = match.groupValues[4].ifEmpty { match.groupValues[5] }

        if (mediaUrl.isNotEmpty()) {
            result.add(CommentBlock.Media(mediaUrl))
        } else if (spoilerContent.isNotEmpty()) {
            result.add(CommentBlock.Spoiler(spoilerContent))
        }

        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        val remaining = text.substring(lastIndex).trim()
        if (remaining.isNotEmpty()) {
            result.add(CommentBlock.Text(parseMarkdownSpans(remaining)))
        }
    }

    return result
}

/**
 * Parses Markdown bold (**), italic (*), strikethrough (~~), code (`), @mentions, and clickable links into an AnnotatedString.
 */
private fun parseMarkdownSpans(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        // Match:
        // 1 & 2: **bold** or __bold__
        // 3 & 4: *italic* or _italic_
        // 5 & 6: ~~strike~~
        // 7 & 8: `code`
        // 9: @mention
        // 10 & 11: [link](url)
        // 12: bare url (http:// or https://)
        val tokenRegex = Regex(
            """(\*\*|__)(.*?)\1|(\*|_)(.*?)\3|(~~)(.*?)\5|(`)(.*?)\7|(@\w+)|\[(.*?)\]\((https?://[^\s\)]+)\)|(https?://[^\s\)]+)"""
        )

        for (match in tokenRegex.findAll(raw)) {
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }

            when {
                // Bold: **text** or __text__
                match.groupValues[2].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[2])
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White), start, length)
                }
                // Italic: *text* or _text_
                match.groupValues[4].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[4])
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                // Strikethrough: ~~text~~
                match.groupValues[6].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[6])
                    addStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.LineThrough,
                            color = Color.White.copy(alpha = 0.6f)
                        ),
                        start,
                        length
                    )
                }
                // Code: `code`
                match.groupValues[8].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[8])
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.White.copy(alpha = 0.12f),
                            fontSize = 12.5.sp
                        ),
                        start,
                        length
                    )
                }
                // Mention: @username
                match.groupValues[9].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[9])
                    addStyle(
                        SpanStyle(
                            color = AccentArctic,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start,
                        length
                    )
                }
                // Markdown Link: [text](url) -> Clickable!
                match.groupValues[10].isNotEmpty() -> {
                    val label = match.groupValues[10]
                    val url = match.groupValues[11]
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = AccentArctic,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        )
                    ) {
                        append(label)
                    }
                }
                // Bare URL: https://... -> Clickable!
                match.groupValues[12].isNotEmpty() -> {
                    val url = match.groupValues[12]
                    withLink(
                        LinkAnnotation.Url(
                            url = url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = AccentArctic,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(url)
                    }
                }
            }

            cursor = match.range.last + 1
        }

        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }
}
