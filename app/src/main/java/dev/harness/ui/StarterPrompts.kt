package dev.harness.ui

import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * Example prompts for the empty chat. A mix of read-only questions and ones that need approval,
 * so a tap shows off the approval flow too. Keep each short enough to fit on two lines.
 */
val StarterPrompts = listOf(
    // About the device
    "What's my screen timeout?",
    "Show recent crashes from logcat",
    "Which apps are using the most battery?",
    "How full is my storage?",
    "What's my battery level, and is it charging?",
    "Which app is in the foreground right now?",
    "Am I connected to Wi-Fi?",
    "What notifications do I have?",
    "List the apps I installed myself",
    "What Android version and security patch am I on?",
    "What device model is this?",
    "What's my screen brightness set to?",
    "Is Bluetooth on?",
    "Is Do Not Disturb on?",
    "How long has my phone been up since the last reboot?",
    "Which apps were installed or updated recently?",
    "Which app is using the most storage?",
    "What permissions does the Settings app have?",
    "Which developer options are turned on?",
    "Check whether Shizuku is running",
    // Diagnosing
    "Why is my phone running slow? Check memory and recent errors",
    "Why is my battery draining fast?",
    "Is anything running in the background that shouldn't be?",
    "Show the last 50 error lines in logcat",
    "When did Harness last crash?",
    "Check my network state and DNS settings",
    // The screen
    "What's on my screen right now?",
    "Take a screenshot and tell me where it was saved",
    "Open Settings and find the battery page",
    "Scroll Settings to About phone and tell me the build number",
    "Open the Wi-Fi settings page",
    "Open the Camera app and wait for it to appear",
    "Wait until a new log line mentions Bluetooth, then tell me",
    // Personal data and files (each asks first)
    "What's on my calendar this week?",
    "What's in my Downloads folder?",
    "Read my latest text message",
    "Look up a contact by name",
    // Changing things (each asks first)
    "Set my screen timeout to 2 minutes",
    "Force stop the Clock app and open it again",
    "Put the text hello on my clipboard",
    "Save a note that I'm testing Harness",
    "What's in my notes?",
)

/** A prompt the user has sent before and how many times. */
@Serializable
data class PastPrompt(val text: String, val count: Int = 1)

/** [base] with [tuned] in front, replacing the last pick so the list keeps its length. */
fun withTuned(base: List<String>, tuned: String?): List<String> =
    if (tuned == null || tuned in base) base else listOf(tuned) + base.dropLast(1)

/** Past prompts kept for the empty state. */
const val MAX_PROMPT_HISTORY = 50

/** Past prompts longer than this would not fit on two lines of the empty state. */
private const val MAX_HISTORIC_LENGTH = 120

/**
 * [history] after the user sent [prompt]: a repeat bumps its count and moves it to the front, a new one
 * starts at 1. Blank prompts and prompts from [StarterPrompts] are not kept. Over the cap, the least-used
 * (and among those, the oldest) entry is dropped, so habits outlive one-offs.
 */
fun addToHistory(history: List<PastPrompt>, prompt: String): List<PastPrompt> {
    val text = prompt.trim()
    if (text.isEmpty() || text in StarterPrompts) return history
    val previous = history.firstOrNull { it.text == text }
    val updated = listOf(PastPrompt(text, (previous?.count ?: 0) + 1)) + history.filter { it.text != text }
    if (updated.size <= MAX_PROMPT_HISTORY) return updated
    val dropAt = updated.indexOfLast { it.count == updated.minOf(PastPrompt::count) }
    return updated.filterIndexed { index, _ -> index != dropAt }
}

/** One entry chosen with probability proportional to its count. */
private fun List<PastPrompt>.pickWeighted(random: Random): PastPrompt? {
    if (isEmpty()) return null
    var ticket = random.nextInt(sumOf { it.count })
    for (entry in this) {
        ticket -= entry.count
        if (ticket < 0) return entry
    }
    return last()
}

/**
 * [count] distinct prompts chosen at random, so the empty state looks different each time.
 * When there is [history], one slot is a past prompt, picked more often the more it has been used.
 */
fun pickStarters(
    count: Int = 3,
    from: List<String> = StarterPrompts,
    history: List<PastPrompt> = emptyList(),
    random: Random = Random,
): List<String> {
    val past = if (count > 0) {
        history.filter { it.text !in from && it.text.length <= MAX_HISTORIC_LENGTH && it.count > 0 }
            .pickWeighted(random)?.text
    } else null
    val curated = from.shuffled(random).take(count - (if (past != null) 1 else 0))
    return (curated + listOfNotNull(past)).shuffled(random)
}
