package dev.harness.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {

    @Test
    fun splitsParagraphsListsAndFences() {
        val blocks = parseMarkdown("Intro line\ncontinued\n\n- one\n- two\n\n```\nlogcat -d\n```\nDone")
        assertEquals(
            listOf(
                MdBlock.Paragraph("Intro line continued"),
                MdBlock.Items(listOf("–" to "one", "–" to "two")),
                MdBlock.Code("logcat -d"),
                MdBlock.Paragraph("Done"),
            ),
            blocks,
        )
    }

    @Test
    fun unterminatedFenceWhileStreamingStillRendersAsCode() {
        assertEquals(listOf(MdBlock.Code("partial")), parseMarkdown("```\npartial"))
    }

    @Test
    fun boldAndCodeAreStyledAndUnclosedMarkersStayLiteral() {
        val closed = inlineMarkdown("a **b** `c`", Color.Gray)
        assertEquals("a b c", closed.text)
        assertEquals(2, closed.spanStyles.size)
        assertEquals("a **b", inlineMarkdown("a **b", Color.Gray).text)
    }
}

class MarkdownTableTest {

    private val table = "| Date | App |\n|---|:-:|\n| Oct 10 | dev.harness |\n| Oct 9 | Canta |"

    @Test
    fun aTableBecomesHeaderAlignmentsAndRows() {
        assertEquals(
            listOf(
                MdBlock.Table(
                    header = listOf("Date", "App"),
                    aligns = listOf(MdAlign.Start, MdAlign.Center),
                    rows = listOf(listOf("Oct 10", "dev.harness"), listOf("Oct 9", "Canta")),
                ),
            ),
            parseMarkdown(table),
        )
    }

    @Test
    fun theReplyFromTheScreenshotNowRendersAsTwoTables() {
        val reply = "**Newly installed:**\n\n| Date | App |\n|---|---|\n| Oct 10 | dev.harness |\n\n" +
            "**Updated:**\n\n| Updated | App |\n|---|---|\n| Oct 12 | dev.nightwatch.android |"
        val blocks = parseMarkdown(reply)
        assertEquals(4, blocks.size)
        assertEquals(2, blocks.count { it is MdBlock.Table })
    }

    @Test
    fun textAroundATableStaysSeparate() {
        val blocks = parseMarkdown("Before\n$table\nAfter")
        assertEquals(MdBlock.Paragraph("Before"), blocks[0])
        assertEquals(true, blocks[1] is MdBlock.Table)
        assertEquals(MdBlock.Paragraph("After"), blocks[2])
    }

    @Test
    fun alignmentColonsAndOptionalEdgePipes() {
        val blocks = parseMarkdown("a | b | c\n:-- | :-: | --:\n1 | 2 | 3")
        val t = blocks.single() as MdBlock.Table
        assertEquals(listOf(MdAlign.Start, MdAlign.Center, MdAlign.End), t.aligns)
        assertEquals(listOf(listOf("1", "2", "3")), t.rows)
    }

    @Test
    fun shortAndLongRowsAreFittedToTheHeader() {
        val t = parseMarkdown("| a | b |\n|---|---|\n| only |\n| x | y | z |").single() as MdBlock.Table
        assertEquals(listOf(listOf("only", ""), listOf("x", "y")), t.rows)
    }

    @Test
    fun escapedPipesAndBreakTagsInsideCells() {
        val t = parseMarkdown("| a | b |\n|---|---|\n| x \\| y | one<br>two |").single() as MdBlock.Table
        assertEquals(listOf(listOf("x | y", "one two")), t.rows)
    }

    @Test
    fun aHeaderWithoutASeparatorYetIsJustTextWhileStreaming() {
        assertEquals(listOf(MdBlock.Paragraph("| Date | App |")), parseMarkdown("| Date | App |"))
    }

    @Test
    fun aTableCutOffMidRowStillRenders() {
        val t = parseMarkdown("| a | b |\n|---|---|\n| 1 | 2 |\n| 3 |").single() as MdBlock.Table
        assertEquals(listOf(listOf("1", "2"), listOf("3", "")), t.rows)
    }

    @Test
    fun aSeparatorWithADifferentColumnCountIsNotATable() {
        assertEquals(false, parseMarkdown("| a | b |\n|---|\n| 1 | 2 |").any { it is MdBlock.Table })
    }

    @Test
    fun splitRowKeepsInnerEmptyCells() {
        assertEquals(listOf("a", "", "c"), splitRow("| a | | c |"))
    }
}

class MarkdownInlineTest {

    @Test
    fun italicIsStyledAndMathAsterisksAreNot() {
        val italic = inlineMarkdown("an *emphasised* word", Color.Gray)
        assertEquals("an emphasised word", italic.text)
        assertEquals(1, italic.spanStyles.size)
        assertEquals("2 * 3 * 4", inlineMarkdown("2 * 3 * 4", Color.Gray).text)
        assertEquals("a *b", inlineMarkdown("a *b", Color.Gray).text)
    }

    @Test
    fun boldInsideItalicTextIsNotMistakenForItalic() {
        val text = inlineMarkdown("**bold** and *it*", Color.Gray)
        assertEquals("bold and it", text.text)
        assertEquals(2, text.spanStyles.size)
    }

    @Test
    fun horizontalRulesBecomeRules() {
        assertEquals(
            listOf(MdBlock.Paragraph("a"), MdBlock.Rule, MdBlock.Paragraph("b")),
            parseMarkdown("a\n\n---\n\nb"),
        )
    }
}
