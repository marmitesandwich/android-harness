package dev.harness.tools.shell

/**
 * Where the file tools may roam. Shell uid can read far more than the user would expect an
 * agent to touch (other apps' data, system files), so stay on shared storage and /data/local/tmp.
 */
internal object FilePaths {
    private val ALLOWED_PREFIXES = listOf("/sdcard/", "/storage/emulated/0/", "/data/local/tmp/")

    /** Returns [path] when it is inside an allowed root and has no traversal; throws otherwise. */
    fun check(path: String): String {
        val trimmed = path.trim()
        require('\n' !in trimmed && '\u0000' !in trimmed) { "invalid path" }
        require(ALLOWED_PREFIXES.any { trimmed.startsWith(it) }) {
            "path must start with ${ALLOWED_PREFIXES.joinToString(" or ")}"
        }
        require(trimmed.split('/').none { it == ".." }) { "path must not contain '..'" }
        return trimmed
    }
}
