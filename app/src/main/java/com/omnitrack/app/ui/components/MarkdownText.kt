package com.omnitrack.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.omnitrack.app.ui.theme.AccentArctic
import com.omnitrack.app.ui.theme.AccentJade
import com.omnitrack.app.ui.theme.TextMuted
import com.omnitrack.app.ui.theme.TextPrimary
import com.omnitrack.app.ui.theme.TextSecondary

/**
 * Lightweight AST-like parser that converts standard GitHub Flavored Markdown into an AnnotatedString.
 * Parses headers (##, ###), bullet lists (- or *), bold (**text**), inline code (`code`), and italics (*text*).
 */
fun parseMarkdownToAnnotatedString(
    markdown: String,
    primaryColor: Color = TextPrimary,
    accentColor: Color = AccentJade,
    codeColor: Color = AccentArctic
): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.lines()
        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // Header 1
                trimmed.startsWith("# ") -> {
                    val content = trimmed.removePrefix("# ").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, color = primaryColor)) {
                        append(content)
                    }
                }
                // Header 2
                trimmed.startsWith("## ") -> {
                    val content = trimmed.removePrefix("## ").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accentColor)) {
                        append(content)
                    }
                }
                // Header 3
                trimmed.startsWith("### ") -> {
                    val content = trimmed.removePrefix("### ").trim()
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp, color = primaryColor)) {
                        append(content)
                    }
                }
                // Bullet item (- or *)
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val content = trimmed.substring(2).trim()
                    withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.Bold)) {
                        append("  •  ")
                    }
                    appendFormattedInline(content, primaryColor, codeColor)
                }
                // Numbered list item
                trimmed.matches(Regex("""^\d+\.\s+.*""")) -> {
                    val prefix = trimmed.substringBefore('.') + ". "
                    val content = trimmed.substringAfter(". ").trim()
                    withStyle(SpanStyle(color = TextMuted, fontWeight = FontWeight.SemiBold)) {
                        append("  $prefix")
                    }
                    appendFormattedInline(content, primaryColor, codeColor)
                }
                // Empty line
                trimmed.isEmpty() -> {
                    // Blank separator
                }
                // Normal paragraph line
                else -> {
                    appendFormattedInline(line, primaryColor, codeColor)
                }
            }

            if (i < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Parses inline formatting: **bold**, `code`, and *italic*.
 */
private fun AnnotatedString.Builder.appendFormattedInline(
    text: String,
    textColor: Color,
    codeColor: Color
) {
    // Regex matching bold, code, and italic tokens
    val regex = Regex("""(\*\*(.+?)\*\*|`([^`]+)`|\*([^*]+)\*)""")
    var lastIndex = 0

    for (match in regex.findAll(text)) {
        val range = match.range
        if (range.first > lastIndex) {
            withStyle(SpanStyle(color = textColor)) {
                append(text.substring(lastIndex, range.first))
            }
        }

        val fullMatch = match.value
        when {
            // Bold: **text**
            fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                val boldContent = fullMatch.removeSurrounding("**")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                    append(boldContent)
                }
            }
            // Code: `code`
            fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                val codeContent = fullMatch.removeSurrounding("`")
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = codeColor,
                        background = Color(0x2AFFFFFF),
                        fontSize = 12.sp
                    )
                ) {
                    append(" $codeContent ")
                }
            }
            // Italic: *text*
            fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                val italicContent = fullMatch.removeSurrounding("*")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = textColor)) {
                    append(italicContent)
                }
            }
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        withStyle(SpanStyle(color = textColor)) {
            append(text.substring(lastIndex))
        }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = 20.sp,
    fontSize: TextUnit = 13.5.sp
) {
    val annotatedString = remember(markdown) { parseMarkdownToAnnotatedString(markdown) }
    Text(
        text = annotatedString,
        modifier = modifier,
        lineHeight = lineHeight,
        fontSize = fontSize
    )
}
