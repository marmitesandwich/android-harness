package dev.harness.tools.ui

import dev.harness.backend.UiBackend

/** One line of the `ui_tree` dump, parsed back into something searchable. */
data class UiNode(
    val cls: String,
    val id: String?,
    val text: String?,
    val desc: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val visible: Boolean get() = right > left && bottom > top

    /** Case-insensitive substring match on text, content description or resource id. */
    fun matches(query: String): Boolean = listOfNotNull(text, desc, id).any { it.contains(query, ignoreCase = true) }

    fun render(): String = buildString {
        append(cls)
        id?.let { append(" #").append(it) }
        text?.let { append(" text=\"").append(it).append('"') }
        desc?.let { append(" desc=\"").append(it).append('"') }
        append(" center=(").append(centerX).append(", ").append(centerY).append(')')
        if (clickable) append(" clickable")
        if (editable) append(" editable")
        if (scrollable) append(" scrollable")
    }
}

// Mirrors AccessibilityUiBackend.dump(): `Class #id text="..." desc="..." [l,t-r,b] flags`.
private val NODE_LINE =
    Regex("""^\s*(\S+?)(?: #(\S+))?(?: text="(.*?)")?(?: desc="(.*?)")? \[(-?\d+),(-?\d+)-(-?\d+),(-?\d+)](.*)$""")

/** Parses a `ui_tree` dump; lines that are not nodes (errors, blanks) are skipped. */
fun parseUiTree(tree: String): List<UiNode> = tree.lineSequence().mapNotNull { line ->
    val m = NODE_LINE.matchEntire(line) ?: return@mapNotNull null
    val g = m.groupValues
    val flags = g[9]
    UiNode(
        cls = g[1],
        id = m.groups[2]?.value,
        text = m.groups[3]?.value,
        desc = m.groups[4]?.value,
        left = g[5].toInt(),
        top = g[6].toInt(),
        right = g[7].toInt(),
        bottom = g[8].toInt(),
        clickable = " clickable" in flags,
        editable = " editable" in flags,
        scrollable = " scrollable" in flags,
    )
}.toList()

/** Visible nodes on the current screen whose text, description or id contains [query]. */
internal suspend fun UiBackend.find(query: String): List<UiNode> =
    parseUiTree(windowTree()).filter { it.visible && it.matches(query) }
