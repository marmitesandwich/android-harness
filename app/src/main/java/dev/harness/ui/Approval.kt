package dev.harness.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.agent.ApprovalResponse
import dev.harness.ui.theme.Harness
import dev.harness.ui.theme.MonoFamily
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Plain-language name for what a write tool does; unknown tools fall back to their id. */
internal fun actionTitle(tool: String): String = when (tool) {
    "put_setting" -> "Change a setting"
    "launch_app" -> "Open an app"
    "force_stop" -> "Force-stop an app"
    "run_shell" -> "Run a shell command"
    "tap" -> "Tap the screen"
    "swipe" -> "Swipe the screen"
    "type_text" -> "Type text"
    "press_back" -> "Press Back"
    "press_home" -> "Press Home"
    else -> tool
}

private fun parseArgs(json: String): Map<String, String>? = try {
    (Json.parseToJsonElement(json) as? JsonObject)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: v.toString() }
} catch (_: Exception) {
    null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApprovalSheet(pending: PendingApproval, onRespond: (ApprovalResponse) -> Unit) {
    val c = Harness.colors
    val args = parseArgs(pending.argumentsJson)
    // Dismissing the sheet any way other than a button is a denial.
    ModalBottomSheet(
        onDismissRequest = { onRespond(ApprovalResponse.DENY) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(10.dp).background(c.write))
                Spacer(Modifier.width(10.dp))
                Text(actionTitle(pending.toolName), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "The agent is asking to change something on this phone. Nothing runs until you allow it.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.subdued,
            )

            val command = args?.get("command")?.takeIf { pending.toolName == "run_shell" }
            when {
                command != null -> Text(
                    command,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 20.sp),
                    modifier = Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(8.dp)).padding(12.dp),
                )
                args != null && args.isNotEmpty() -> Column(
                    Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(8.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    args.forEach { (k, v) ->
                        Column {
                            Text(k, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 11.sp), color = c.subdued)
                            Text(v, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 14.sp))
                        }
                    }
                }
                args == null -> Text(
                    pending.argumentsJson,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().background(c.surface2, RoundedCornerShape(8.dp)).padding(12.dp),
                )
            }

            // Deny and Allow get equal weight: neither is the "obvious" tap.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onRespond(ApprovalResponse.DENY) },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("Deny", fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = { onRespond(ApprovalResponse.ALLOW_ONCE) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.write, contentColor = Color.White),
                ) { Text("Allow once", fontWeight = FontWeight.SemiBold) }
            }
            if (pending.canAlwaysAllow) {
                // The permanent option: same size as the others, with the consequence spelled out.
                OutlinedButton(
                    onClick = { onRespond(ApprovalResponse.ALWAYS_ALLOW) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    border = BorderStroke(1.5.dp, c.write),
                ) { Text("Always allow: ${actionTitle(pending.toolName).lowercase()}", fontWeight = FontWeight.SemiBold, color = c.write) }
                Text(
                    "Stops asking for any use of this action, in every chat. Undo it in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.subdued,
                )
            }
        }
    }
}
