package dev.harness.ui

import kotlin.random.Random

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

/** [count] distinct prompts chosen at random, so the empty state looks different each time. */
fun pickStarters(
    count: Int = 3,
    from: List<String> = StarterPrompts,
    random: Random = Random,
): List<String> = from.shuffled(random).take(count)
