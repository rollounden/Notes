package dev.apex.notes.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Renders [source] as lightweight Markdown. Task checkboxes are tappable when [onToggleTask] is set. */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    onToggleTask: ((line: Int) -> Unit)? = null,
) {
    val blocks = remember(source) { parseMarkdown(source) }
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val body = MaterialTheme.typography.bodyLarge

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, codeBg),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp),
                )
                is MdBlock.Paragraph -> Text(inline(block.text, codeBg), style = body)
                is MdBlock.Bullet -> ListRow(depth = block.depth, marker = "•") {
                    Text(inline(block.text, codeBg), style = body)
                }
                is MdBlock.Numbered -> ListRow(depth = block.depth, marker = "${block.number}.") {
                    Text(inline(block.text, codeBg), style = body)
                }
                is MdBlock.Task -> TaskRow(block, codeBg, body, onToggleTask)
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        inline(block.text, codeBg),
                        style = body.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is MdBlock.Code -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBg)
                        .padding(12.dp),
                )
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ListRow(depth: Int, marker: String, content: @Composable () -> Unit) {
    Row(modifier = Modifier.padding(start = (depth * 16).dp)) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(24.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TaskRow(block: MdBlock.Task, codeBg: Color, body: TextStyle, onToggle: ((Int) -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = (block.depth * 16).dp)
            .then(if (onToggle != null) Modifier.clickable { onToggle(block.line) } else Modifier),
    ) {
        Icon(
            if (block.checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (block.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            inline(block.text, codeBg),
            style = body.copy(textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None),
            color = if (block.checked) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
        )
    }
}

/**
 * Inline parser for bold (double star), italic (single star or underscore), inline code and strike.
 * Single-pass scanner with a small stack of open markers; unmatched markers are emitted literally.
 */
internal fun inline(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    var bold = false
    var italic = false
    var strike = false

    fun styleFor(): SpanStyle = SpanStyle(
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        textDecoration = if (strike) TextDecoration.LineThrough else null,
    )

    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            withStyle(styleFor()) { append(plain.toString()) }
            plain.setLength(0)
        }
    }

    fun hasClosing(marker: String, from: Int): Boolean = text.indexOf(marker, from) >= 0

    while (i < text.length) {
        val c = text[i]
        when {
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    flush()
                    withStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    plain.append(c); i++
                }
            }
            text.startsWith("**", i) && (bold || hasClosing("**", i + 2)) -> {
                flush(); bold = !bold; i += 2
            }
            text.startsWith("~~", i) && (strike || hasClosing("~~", i + 2)) -> {
                flush(); strike = !strike; i += 2
            }
            (c == '*' || c == '_') && isEmphasisBoundary(text, i, italic) -> {
                flush(); italic = !italic; i++
            }
            else -> {
                plain.append(c); i++
            }
        }
    }
    flush()
}

private fun isEmphasisBoundary(text: String, i: Int, currentlyOpen: Boolean): Boolean {
    val c = text[i]
    val prev = text.getOrNull(i - 1)
    val next = text.getOrNull(i + 1)
    // Underscores inside words (snake_case) are not emphasis.
    if (c == '_' && prev?.isLetterOrDigit() == true && next?.isLetterOrDigit() == true) return false
    return if (currentlyOpen) {
        prev != null && !prev.isWhitespace()
    } else {
        next != null && !next.isWhitespace() && text.indexOf(c, i + 1) > i
    }
}
