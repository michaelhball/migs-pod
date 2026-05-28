package com.migspod.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink

private val urlRegex = Regex("""https?://[^\s)]+""")
private val trailingPunctuation = setOf('.', ',', ';', ':', '!', '?', ')', ']')

@Composable
fun LinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildLinkified(text, linkColor)
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun buildLinkified(text: String, linkColor: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in urlRegex.findAll(text)) {
        val rawUrl = match.value
        // Trim trailing punctuation that's almost certainly part of the surrounding sentence.
        val trimmedUrl = rawUrl.trimEnd { it in trailingPunctuation }
        val trimmedRange = match.range.first..(match.range.first + trimmedUrl.length - 1)

        if (trimmedRange.first > cursor) {
            append(text.substring(cursor, trimmedRange.first))
        }
        withLink(
            LinkAnnotation.Url(
                url = trimmedUrl,
                styles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                ),
            )
        ) {
            append(trimmedUrl)
        }
        cursor = trimmedRange.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
