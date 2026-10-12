package dev.harness.backend

import dev.harness.agent.ApprovalResponse

/** What an approval card says. Plain strings so the overlay does not depend on tool internals. */
data class ApprovalCard(val title: String, val details: String, val canAlwaysAllow: Boolean)

/**
 * Things drawn on top of other apps through the accessibility service: an outline that points at
 * something on screen, and the approval card, so the agent can ask permission while it is showing
 * the user another app and Harness itself is out of sight.
 */
interface OverlayBackend {
    val available: Boolean

    /** Outline the given screen rectangle, with an optional short label. Gone after [seconds] or when the screen changes. */
    suspend fun highlight(left: Int, top: Int, right: Int, bottom: Int, label: String?, seconds: Int)

    /** Show the approval card over whatever is on screen. Call from the main thread. */
    fun showApproval(card: ApprovalCard, onResponse: (ApprovalResponse) -> Unit)

    fun dismissApproval()
}

/** Used when no overlay can be drawn; tools report the accessibility message instead. */
class UnavailableOverlayBackend : OverlayBackend {
    override val available = false
    override suspend fun highlight(left: Int, top: Int, right: Int, bottom: Int, label: String?, seconds: Int): Unit =
        error("accessibility service is not enabled")

    override fun showApproval(card: ApprovalCard, onResponse: (ApprovalResponse) -> Unit) = Unit
    override fun dismissApproval() = Unit
}
