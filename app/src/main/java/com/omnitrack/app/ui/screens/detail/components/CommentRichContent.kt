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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.GlassBorder

/**
 * Rich Content Block for Dantotsu & AniList Threaded Comments.
 * Formats:
 * - Images and animated GIFs: img(url), ![alt](url), or raw image URLs (.gif, .png, .jpg, .webp, tenor, giphy)
 * - Clickable Spoilers: ~!hidden text/media!~ and [spoiler]...[/spoiler]
 * - Quotes: lines starting with >
 * - Markdown: **bold**, *italic*, ~~strikethrough~~, `code`, @mentions, and [links](url)
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
        modifier = modifier,
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
                            lineHeight = 19.sp
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
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = "Comment attachment",
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .heightIn(max = 240.dp),
        contentScale = ContentScale.Fit
    )
}

@Composable
private fun CommentSpoilerBlock(spoilerContent: String) {
    var isRevealed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.95f)
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
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            lineHeight = 18.sp
        )
    }
}

/**
 * Tokenizes AniList & Dantotsu comments into media, spoilers, quotes, and markdown spans.
 */
private fun parseCommentContent(raw: String): List<CommentBlock> {
    if (raw.isBlank()) return emptyList()

    val blocks = mutableListOf<CommentBlock>()
    var remaining = raw.trim()

    // Regular expressions for AniList/Dantotsu syntax
    // img(...) or img200(...) or img(200)(...)
    val imgRegex = Regex("""(?i)img(?:\(\d+\)|\d+)?\((https?://[^\s\)]+)\)""")
    // Markdown image: ![alt](url)
    val mdImgRegex = Regex("""!\[.*?\]\((https?://[^\s\)]+)\)""")
    // AniList spoiler: ~!...text...!~ or [spoiler]...[/spoiler]
    val spoilerRegex = Regex("""(?s)~!(.*?)!~|\[spoiler\](.*?)\[/spoiler\]""")

    // Split by lines to check for quotes or standalone media URLs
    val lines = remaining.split("\n")
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotBlank()) {
            val parsedSpans = parseMarkdownSpans(textBuffer.toString().trim())
            blocks.add(CommentBlock.Text(parsedSpans))
            textBuffer.clear()
        }
    }

    for (line in lines) {
        val trimmedLine = line.trim()

        // 1. Check for Quote
        if (trimmedLine.startsWith(">")) {
            flushText()
            val quoteContent = trimmedLine.removePrefix(">").trim()
            blocks.add(CommentBlock.Quote(quoteContent))
            continue
        }

        // 2. Check for Standalone Image / GIF URL on its own line
        val isDirectMediaUrl = trimmedLine.matches(
            Regex("""(?i)https?://[^\s]+(?:\.(?:gif|png|jpg|jpeg|webp)|tenor\.com/[^\s]+|giphy\.com/[^\s]+)(?:\?[^\s]*)?""")
        )
        if (isDirectMediaUrl) {
            flushText()
            blocks.add(CommentBlock.Media(trimmedLine))
            continue
        }

        // 3. Check for img(...) or ![...](...) tag on line
        val imgMatch = imgRegex.find(trimmedLine) ?: mdImgRegex.find(trimmedLine)
        if (imgMatch != null && trimmedLine == imgMatch.value.trim()) {
            flushText()
            val url = imgMatch.groupValues[1]
            blocks.add(CommentBlock.Media(url))
            continue
        }

        // 4. Check for full-line Spoiler ~!...text...!~
        val spoilerMatch = spoilerRegex.find(trimmedLine)
        if (spoilerMatch != null && trimmedLine == spoilerMatch.value.trim()) {
            flushText()
            val spoilerBody = spoilerMatch.groupValues[1].ifEmpty { spoilerMatch.groupValues[2] }
            blocks.add(CommentBlock.Spoiler(spoilerBody))
            continue
        }

        // Otherwise append to text buffer
        textBuffer.appendLine(line)
    }

    flushText()

    // If textBuffer had embedded img() or spoiler tags inside a multiline block, parse them
    val finalBlocks = mutableListOf<CommentBlock>()
    for (b in blocks) {
        if (b is CommentBlock.Text) {
            val textStr = b.text.text
            if (imgRegex.containsMatchIn(textStr) || spoilerRegex.containsMatchIn(textStr)) {
                // Fragment further
                finalBlocks.addAll(fragmentInlineBlocks(textStr))
            } else {
                finalBlocks.add(b)
            }
        } else {
            finalBlocks.add(b)
        }
    }

    return finalBlocks.ifEmpty { listOf(CommentBlock.Text(parseMarkdownSpans(raw))) }
}

private fun fragmentInlineBlocks(text: String): List<CommentBlock> {
    val result = mutableListOf<CommentBlock>()
    val pattern = Regex("""(?s)(?i)img(?:\(\d+\)|\d+)?\((https?://[^\s\)]+)\)|!\[.*?\]\((https?://[^\s\)]+)\)|~!(.*?)!~|\[spoiler\](.*?)\[/spoiler\]""")
    var lastIndex = 0

    for (match in pattern.findAll(text)) {
        if (match.range.first > lastIndex) {
            val preceding = text.substring(lastIndex, match.range.first).trim()
            if (preceding.isNotEmpty()) {
                result.add(CommentBlock.Text(parseMarkdownSpans(preceding)))
            }
        }

        val mediaUrl = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val spoilerContent = match.groupValues[3].ifEmpty { match.groupValues[4] }

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
 * Parses Markdown bold (**), italic (*), strikethrough (~~), code (`), and @mentions into an AnnotatedString.
 */
private fun parseMarkdownSpans(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val tokenRegex = Regex("""(\*\*|__)(.*?)\1|(\*|_)(.*?)\3|(~~)(.*?)\5|(`)(.*?)\7|(@\w+)|\[(.*?)\]\((https?://[^\s\)]+)\)""")

        for (match in tokenRegex.findAll(raw)) {
            // Append plain text before match
            if (match.range.first > cursor) {
                append(raw.substring(cursor, match.range.first))
            }

            when {
                // Bold: **text** or __text__
                match.groupValues[2].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[2])
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
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
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.White.copy(alpha = 0.6f)), start, length)
                }
                // Code: `code`
                match.groupValues[8].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[8])
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.White.copy(alpha = 0.1f),
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
                // Markdown Link: [text](url)
                match.groupValues[10].isNotEmpty() -> {
                    val start = length
                    append(match.groupValues[10])
                    addStyle(
                        SpanStyle(
                            color = AccentArctic,
                            textDecoration = TextDecoration.Underline
                        ),
                        start,
                        length
                    )
                }
            }

            cursor = match.range.last + 1
        }

        // Append trailing plain text
        if (cursor < raw.length) {
            append(raw.substring(cursor))
        }
    }
}
