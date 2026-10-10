package dev.harness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.ui.theme.Harness
import dev.harness.ui.theme.MonoFamily

// ponytail: only what models actually emit (paragraphs, bullets, fenced code, **bold**, `code`).
// No tables, links or nested lists; add when a real reply needs them.
internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Items(val items: List<Pair<String, String>>) : MdBlock
}

private val ItemStart = Regex("""^\s*([-*]|\d+\.)\s+(.*)$""")

internal fun parseMarkdown(src: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val para = mutableListOf<String>()
    val items = mutableListOf<Pair<String, String>>()
    val code = mutableListOf<String>()
    var inCode = false

    fun flushPara() {
        if (para.isNotEmpty()) blocks += MdBlock.Paragraph(para.joinToString(" ")); para.clear()
    }
    fun flushItems() {
        if (items.isNotEmpty()) blocks += MdBlock.Items(items.toList()); items.clear()
    }

    for (line in src.lines()) {
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(code.joinToString("\n")); code.clear()
            } else {
                flushPara(); flushItems()
            }
            inCode = !inCode
            continue
        }
        if (inCode) { code += line; continue }

        val item = ItemStart.matchEntire(line)
        when {
            line.isBlank() -> { flushPara(); flushItems() }
            item != null -> {
                flushPara()
                val marker = item.groupValues[1].let { if (it == "-" || it == "*") "–" else it }
                items += marker to item.groupValues[2]
            }
            line.startsWith("#") -> { // headings render as a bold paragraph
                flushPara(); flushItems()
                blocks += MdBlock.Paragraph("**" + line.trimStart('#').trim() + "**")
            }
            else -> { flushItems(); para += line.trim() }
        }
    }
    if (inCode) blocks += MdBlock.Code(code.joinToString("\n")) // unterminated fence while streaming
    flushPara(); flushItems()
    return blocks
}

/** `**bold**` and `code` spans. An unclosed marker is shown literally (common mid-stream). */
internal fun inlineMarkdown(text: String, codeBg: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end < 0) { append(text.substring(i)); i = text.length } else {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                        i = end + 2
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { append(text.substring(i)); i = text.length } else {
                        withStyle(SpanStyle(fontFamily = MonoFamily, fontSize = 13.sp, background = codeBg)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val c = Harness.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parseMarkdown(text).forEach { block ->
            when (block) {
                is MdBlock.Paragraph ->
                    Text(inlineMarkdown(block.text, c.surface2), style = MaterialTheme.typography.bodyLarge)

                is MdBlock.Code -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 12.5.sp, lineHeight = 18.sp),
                    softWrap = false,
                    modifier = Modifier
                        .background(c.surface2, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                )

                is MdBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { (marker, body) ->
                        Row {
                            Text(marker, style = MaterialTheme.typography.bodyLarge, color = c.subdued, modifier = Modifier.width(24.dp))
                            Text(inlineMarkdown(body, c.surface2), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
