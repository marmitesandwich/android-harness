package dev.harness.ui

import android.app.Application
import dev.harness.backend.AccessibilityOverlayBackend
import dev.harness.backend.AccessibilityUiBackend
import dev.harness.backend.AndroidClipboardBackend
import dev.harness.backend.AndroidRunKeeper
import dev.harness.backend.ClipboardBackend
import dev.harness.backend.FileNotesStore
import dev.harness.backend.HttpFetcher
import dev.harness.backend.NotesStore
import dev.harness.backend.OverlayBackend
import dev.harness.backend.OkHttpFetcher
import dev.harness.backend.RunKeeper
import dev.harness.backend.ShizukuShellBackend
import dev.harness.backend.ShellBackend
import dev.harness.backend.UiBackend

/** Wires the process-wide singletons. */
class HarnessApp : Application() {

    lateinit var settings: SettingsStore
        private set

    lateinit var shell: ShellBackend
        private set

    lateinit var uiBackend: UiBackend
        private set

    lateinit var overlay: OverlayBackend
        private set

    lateinit var notes: NotesStore
        private set

    lateinit var clipboard: ClipboardBackend
        private set

    lateinit var http: HttpFetcher
        private set

    lateinit var runKeeper: RunKeeper
        private set

    lateinit var transcripts: TranscriptStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        shell = ShizukuShellBackend(this)
        uiBackend = AccessibilityUiBackend(screenshotDir = { cacheDir })
        overlay = AccessibilityOverlayBackend()
        clipboard = AndroidClipboardBackend(this)
        http = OkHttpFetcher()
        runKeeper = AndroidRunKeeper(this)
        transcripts = FileTranscriptStore { java.io.File(filesDir, "transcript.json") }
        notes = FileNotesStore { java.io.File(filesDir, "notes.txt") }
    }

    companion object {
        fun from(application: Application): HarnessApp = application as HarnessApp
    }
}
