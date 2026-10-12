package dev.harness.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.harness.ui.theme.HarnessTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val app: HarnessApp get() = application as HarnessApp

    private val viewModel: ChatViewModel by viewModels { ChatViewModel.factory(app) }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> viewModel.refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        enableEdgeToEdge()
        setContent {
            HarnessTheme {
                LaunchedEffect(Unit) { viewModel.refreshStatus() }
                var showSettings by remember { mutableStateOf(false) }

                if (showSettings) {
                    SettingsScreen(app.settings) {
                        showSettings = false
                        viewModel.refreshStatus()
                    }
                } else {
                    ChatScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                        onRequestShizuku = ::requestShizuku,
                        onOpenAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppVisible(true)
    }

    override fun onStop() {
        viewModel.setAppVisible(false)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        super.onDestroy()
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Install and start Shizuku first")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                viewModel.refreshStatus()
                return
            }
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (t: Throwable) {
            toast(t.message ?: "Shizuku unavailable")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val SHIZUKU_REQUEST_CODE = 0xC0DE
    }
}
