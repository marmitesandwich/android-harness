package dev.harness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.ui.theme.Harness
import dev.harness.ui.theme.MonoFamily

@Composable
fun SettingsScreen(store: SettingsStore, onDone: () -> Unit) {
    var apiKey by remember { mutableStateOf(store.apiKey) }
    var model by remember { mutableStateOf(store.model) }
    var baseUrl by remember { mutableStateOf(store.baseUrl) }
    val field = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 14.sp)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))
            .verticalScroll(rememberScrollState()),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDone) { Text("Back", fontWeight = FontWeight.SemiBold) }
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API key") },
                textStyle = field,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                textStyle = field,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                textStyle = field,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Use a chat-completions model. The key is stored encrypted on this device only.",
                style = MaterialTheme.typography.bodyMedium,
                color = Harness.colors.subdued,
            )
            Button(
                onClick = {
                    store.apiKey = apiKey
                    store.model = model
                    store.baseUrl = baseUrl
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Save changes", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
