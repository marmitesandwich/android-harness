package dev.harness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.ui.theme.Harness
import dev.harness.ui.theme.MonoFamily

// ponytail: only what models actually emit (paragraphs, bullets, fenced code, tables, rules, **bold**,
// *italic*, `code`). No links, blockquotes or nested lists; add when a real reply needs them.
internal enum class MdAlign { Start, Center, End }

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data class Items(val items: List<Pair<String, String>>) : MdBlock
    data class Table(val header: List<String>, val aligns: List<MdAlign>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

private val ItemStart = Regex("""^\s*([-*]|\d+\.)\s+(.*)$""")
private val RuleLine = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val SeparatorCell = Regex("""^:?-+:?$""")

/** Cells of a `| a | b |` row. Edge pipes are optional and `\|` is a literal pipe. */
internal fun splitRow(line: String): List<String> {
    val s = line.trim()
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        when {
            ch == '\\' && i + 1 < s.length && s[i + 1] == '|' -> { cell.append('|'); i += 2 }
            ch == '|' -> { cells += cell.toString().trim(); cell.clear(); i++ }
            else -> { cell.append(ch); i++ }
        }
    }
    cells += cell.toString().trim()
    if (s.startsWith("|")) cells.removeAt(0) // text before the leading pipe is empty
    if (s.endsWith("|") && !s.endsWith("\\|") && cells.isNotEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

/** Column alignments if [line] is a table separator such as `|:--|:-:|--:|`, else null. */
private fun separatorAligns(line: String): List<MdAlign>? {
    if ('|' !in line) return null
    val cells = splitRow(line)
    if (cells.isEmpty() || !cells.all { SeparatorCell.matches(it) }) return null
    return cells.map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> MdAlign.Center
            cell.endsWith(":") -> MdAlign.End
            else -> MdAlign.Start
        }
    }
}

private fun cleanCell(cell: String) = cell.replace(Regex("(?i)<br\\s*/?>"), " ")

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

    val lines = src.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i++]
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

        // A header row followed by a separator row starts a table; the rows run until a line without a pipe.
        val aligns = if ('|' in line && i < lines.size) separatorAligns(lines[i]) else null
        val header = if (aligns != null) splitRow(line) else null
        if (aligns != null && header != null && header.size == aligns.size) {
            flushPara(); flushItems()
            i++ // the separator
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].isNotBlank() && '|' in lines[i]) {
                val cells = splitRow(lines[i++]).map(::cleanCell)
                rows += List(header.size) { col -> cells.getOrElse(col) { "" } }
            }
            blocks += MdBlock.Table(header.map(::cleanCell), aligns, rows)
            continue
        }

        val item = ItemStart.matchEntire(line)
        when {
            line.isBlank() -> { flushPara(); flushItems() }
            RuleLine.matches(line) -> { flushPara(); flushItems(); blocks += MdBlock.Rule }
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

/** Index of the `*` that closes an italic span opened just before [from], or -1. */
private fun closingItalic(text: String, from: Int): Int {
    var j = from
    while (j < text.length) {
        if (text[j] == '*') {
            if (j + 1 < text.length && text[j + 1] == '*') { j += 2; continue } // a bold marker, not ours
            if (text[j - 1] != ' ') return j
        }
        j++
    }
    return -1
}

/** `**bold**`, `*italic*` and `code` spans. An unclosed marker is shown literally (common mid-stream). */
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
                text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val end = closingItalic(text, i + 1)
                    if (end < 0) { append('*'); i++ } else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                        i = end + 1
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

                is MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))

                is MdBlock.Table -> MarkdownTable(block)
            }
        }
    }
}

/**
 * Laid out column by column so each column is as wide as its widest cell. Cells do not wrap, which keeps
 * every row one line tall and the columns aligned; a table wider than the screen scrolls sideways.
 */
@Composable
private fun MarkdownTable(table: MdBlock.Table) {
    val c = Harness.colors
    val textStyle = MaterialTheme.typography.bodyMedium
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.surface2)
            .horizontalScroll(rememberScrollState()),
    ) {
        table.header.indices.forEach { col ->
            val align = when (table.aligns[col]) {
                MdAlign.Start -> TextAlign.Start
                MdAlign.Center -> TextAlign.Center
                MdAlign.End -> TextAlign.End
            }
            Column(Modifier.width(IntrinsicSize.Max)) {
                @Composable
                fun cell(value: String, header: Boolean) {
                    Text(
                        inlineMarkdown(value.ifEmpty { " " }, c.surface2),
                        style = textStyle,
                        fontWeight = if (header) FontWeight.SemiBold else null,
                        textAlign = align,
                        softWrap = false,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.rule))
                }
                cell(table.header[col], header = true)
                table.rows.forEach { row -> cell(row[col], header = false) }
            }
        }
    }
}
