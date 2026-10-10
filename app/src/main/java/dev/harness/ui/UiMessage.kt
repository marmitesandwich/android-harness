package dev.harness.ui

/** A rendered chat entry. Tools and errors render differently from prose. */
data class UiMessage(
    val id: String,
    val kind: Kind,
    val text: String,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val denied: Boolean = false,
    val running: Boolean = false,
    val streaming: Boolean = false,
) {
    enum class Kind { USER, ASSISTANT, TOOL, ERROR }
}

/** A write tool waiting for the user's decision. */
data class PendingApproval(
    val toolName: String,
    val argumentsJson: String,
    val canAlwaysAllow: Boolean,
)
