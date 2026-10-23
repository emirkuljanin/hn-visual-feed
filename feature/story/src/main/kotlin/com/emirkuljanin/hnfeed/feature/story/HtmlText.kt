package com.emirkuljanin.hnfeed.feature.story

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration

/**
 * Hacker News HTML (paragraphs, links, italics, code) as styled text. Links are tappable and open through
 * [onOpenLink]; only http(s) links are honoured. Nothing in the HTML is executed: it only becomes text spans.
 */
@Composable
internal fun HtmlText(
    html: String,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = remember(html, linkColor, onOpenLink) {
        AnnotatedString.fromHtml(
            htmlString = prepareHackerNewsHtml(html),
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { link ->
                val url = (link as? LinkAnnotation.Url)?.url
                if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) onOpenLink(url)
            },
        ).trimEnd()
    }
    Text(text = text, style = style, modifier = modifier)
}

/**
 * Adapts Hacker News HTML to what `fromHtml` (Android's HTML parser) supports. It ignores `<pre>` and collapses
 * whitespace, so code blocks keep their lines and indentation only as `<br>` and non-breaking spaces; it does not
 * style `<code>` but renders `<tt>` monospace.
 */
internal fun prepareHackerNewsHtml(html: String): String = html
    .replace(PreBlock) { block -> block.groupValues[1].replace("\n", "<br>").replace(SpaceOutsideTag, "&nbsp;") }
    .replace("<code>", "<tt>")
    .replace("</code>", "</tt>")

private val PreBlock = Regex("<pre>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)

/** A space that is not inside a tag: HN links URLs in code blocks too, and `<a&nbsp;href=…>` is no tag. */
private val SpaceOutsideTag = Regex(" (?![^<]*>)")

/** `fromHtml` turns the last paragraph's end into trailing newlines, which would add space below the text. */
private fun AnnotatedString.trimEnd(): AnnotatedString {
    val end = text.trimEnd().length
    return if (end == text.length) this else subSequence(0, end)
}
