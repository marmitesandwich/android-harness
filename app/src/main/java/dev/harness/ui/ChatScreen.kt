package dev.harness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.harness.agent.ApprovalResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Harness") },
                actions = {
                    TextButton(onClick = { viewModel.newChat() }) { Text("New") }
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                },
            )
        },
        bottomBar = { Composer(viewModel) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StatusBanner(viewModel.status, onRequestShizuku, onOpenAccessibility, onOpenSettings)
            MessageList(viewModel.messages, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }

    viewModel.pendingApproval?.let { pending ->
        ApprovalDialog(pending) { response -> viewModel.respondToApproval(response) }
    }
}

@Composable
private fun StatusBanner(
    status: BackendStatus,
    onRequestShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (status.allGood) return

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!status.apiKeySet) {
                BannerRow("API key not set", "Set key", onOpenSettings)
            }
            if (!status.shizukuAvailable) {
                BannerRow("Shizuku: not available", "Pair", onRequestShizuku)
            }
            if (!status.accessibilityEnabled) {
                BannerRow("Accessibility: off", "Enable", onOpenAccessibility)
            }
        }
    }
}

@Composable
private fun BannerRow(label: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun MessageList(messages: List<UiMessage>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(state = state, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(messages, key = { it.id }) { message ->
            MessageRow(message)
        }
    }
}

@Composable
private fun MessageRow(message: UiMessage) {
    val alignment = if (message.kind == UiMessage.Kind.USER) Alignment.CenterEnd else Alignment.CenterStart
    val background = when (message.kind) {
        UiMessage.Kind.USER -> MaterialTheme.colorScheme.primaryContainer
        UiMessage.Kind.ERROR -> MaterialTheme.colorScheme.errorContainer
        UiMessage.Kind.TOOL -> MaterialTheme.colorScheme.secondaryContainer
        UiMessage.Kind.ASSISTANT -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = alignment) {
        Card(
            colors = CardDefaults.cardColors(containerColor = background),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.kind) {
                    UiMessage.Kind.TOOL -> ToolBody(message)
                    UiMessage.Kind.ERROR -> {
                        Text("Error", style = MaterialTheme.typography.labelMedium)
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Text(message.text.ifEmpty { "…" }, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ToolBody(message: UiMessage) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(message.toolName ?: "tool", style = MaterialTheme.typography.labelLarge)
        if (message.running) {
            CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).width(16.dp), strokeWidth = 2.dp)
        }
        if (message.denied) {
            Text("  denied", style = MaterialTheme.typography.labelMedium)
        }
    }
    message.toolArgs?.takeIf { it.isNotBlank() && it != "{}" }?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
    if (message.text.isNotBlank()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(message.text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Composer(viewModel: ChatViewModel) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = viewModel.input,
                onValueChange = { viewModel.input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask the device something…") },
                maxLines = 4,
            )
            if (viewModel.isRunning) {
                Button(onClick = { viewModel.cancel() }, modifier = Modifier.padding(start = 8.dp)) { Text("Stop") }
            } else {
                Button(onClick = { viewModel.send() }, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
            }
        }
    }
}

@Composable
private fun ApprovalDialog(pending: PendingApproval, onRespond: (ApprovalResponse) -> Unit) {
    AlertDialog(
        onDismissRequest = { onRespond(ApprovalResponse.DENY) },
        title = { Text("Approve ${pending.toolName}?") },
        text = {
            Column {
                Text("The agent wants to run a write action.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    pending.argumentsJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small).padding(8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onRespond(ApprovalResponse.ALLOW_ONCE) }) { Text("Allow once") } },
        dismissButton = {
            Row {
                if (pending.canAlwaysAllow) {
                    TextButton(onClick = { onRespond(ApprovalResponse.ALWAYS_ALLOW) }) { Text("Always") }
                }
                TextButton(onClick = { onRespond(ApprovalResponse.DENY) }) { Text("Deny") }
            }
        },
    )
}
