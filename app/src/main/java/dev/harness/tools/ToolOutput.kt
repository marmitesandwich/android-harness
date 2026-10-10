package dev.harness.tools

/**
 * Caps tool output so a chatty command (logcat, dumpsys) cannot blow the
 * model's context window. Truncates on a UTF-8 character boundary.
 */
fun truncateOutput(text: String, maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    val bytes = text.toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return text
    var end = maxBytes
    while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
    val head = String(bytes, 0, end, Charsets.UTF_8)
    val dropped = bytes.size - end
    return "$head\n…[truncated $dropped bytes]"
}
