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
    // Back off out of the middle of a multi-byte character.
    while (end > 0 && (bytes[end].toInt() and 0xC0) == 0x80) end--
    var head = String(bytes, 0, end, Charsets.UTF_8)
    // A cut at a lead byte still yields an incomplete tail, decoded as U+FFFD.
    if (head.endsWith('\uFFFD')) head = head.dropLast(1)
    val kept = head.toByteArray(Charsets.UTF_8).size
    return "$head\n…[truncated ${bytes.size - kept} bytes]"
}
