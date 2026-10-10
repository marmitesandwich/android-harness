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
