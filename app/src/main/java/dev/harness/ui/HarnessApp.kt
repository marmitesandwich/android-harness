package dev.harness.ui

import android.app.Application
import dev.harness.backend.AccessibilityUiBackend
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

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        shell = ShizukuShellBackend(this)
        uiBackend = AccessibilityUiBackend(screenshotDir = { cacheDir })
    }

    companion object {
        fun from(application: Application): HarnessApp = application as HarnessApp
    }
}
