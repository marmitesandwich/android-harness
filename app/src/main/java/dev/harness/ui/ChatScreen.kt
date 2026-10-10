package dev.harness.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.harness.ui.theme.Harness
import dev.harness.ui.theme.MonoFamily
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val AiStart = 44.dp // the AI column sits on the right: indented from the left edge
private val Gutter = 50.dp // x of the trace rail
private val ToolInset = 68.dp // where tool text starts, right of the rail

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenSettings: () -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)),
    ) {
        TopBar(viewModel, onOpenSettings)
        StatusStrip(viewModel.status, onRequestShizuku, onOpenAccessibility, onOpenSettings)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (viewModel.messages.isEmpty()) {
                EmptyState(viewModel)
            } else {
                val last = viewModel.messages.lastOrNull()
                // Busy, but nothing visibly streaming or running: the model is thinking.
                val thinking = viewModel.isRunning && viewModel.pendingApproval == null &&
                    !(last?.kind == UiMessage.Kind.ASSISTANT && last.streaming) &&
                    !(last?.kind == UiMessage.Kind.TOOL && last.running)
                MessageList(viewModel.messages, thinking)
            }
        }
        Composer(viewModel)
    }

    viewModel.pendingApproval?.let { pending ->
        ApprovalSheet(pending) { response -> viewModel.respondToApproval(response) }
    }
}

// ---- top bar ---------------------------------------------------------------

@Composable
private fun TopBar(viewModel: ChatViewModel, onOpenSettings: () -> Unit) {
    val c = Harness.colors
    val status = viewModel.status
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            HarnessMark(Modifier.size(30.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text("Harness", style = MaterialTheme.typography.titleLarge)
                Text(
                    viewModel.modelName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 11.sp),
                    color = c.subdued,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        StatusDots(status)
        IconButton(
            onClick = { viewModel.newChat() },
            modifier = Modifier.semantics { contentDescription = "New chat" },
        ) { PlusIcon(MaterialTheme.colorScheme.onBackground) }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.semantics { contentDescription = "Settings" },
        ) { SlidersIcon(MaterialTheme.colorScheme.onBackground, MaterialTheme.colorScheme.background) }
    }
}

/** API key, Shizuku, Accessibility: filled when ready, hollow when something is missing. */
@Composable
private fun StatusDots(status: BackendStatus) {
    val c = Harness.colors
    val summary = "API key ${if (status.apiKeySet) "set" else "missing"}, " +
        "Shizuku ${if (status.shizukuAvailable) "ready" else "unavailable"}, " +
        "Accessibility ${if (status.accessibilityEnabled) "on" else "off"}"
    Row(
        Modifier.padding(end = 4.dp).semantics(mergeDescendants = true) { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(status.apiKeySet, status.shizukuAvailable, status.accessibilityEnabled).forEach { ok ->
            Canvas(Modifier.size(8.dp)) {
                if (ok) drawCircle(c.read) else drawCircle(c.write, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun StatusStrip(
    status: BackendStatus,
    onRequestShizuku: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (status.allGood) return
    Column {
        if (!status.apiKeySet) StatusRow("No API key", "Add key", onOpenSettings)
        if (!status.shizukuAvailable) StatusRow("Shizuku isn't running", "Pair", onRequestShizuku)
        if (!status.accessibilityEnabled) StatusRow("Accessibility service is off", "Enable", onOpenAccessibility)
    }
}

@Composable
private fun StatusRow(label: String, action: String, onClick: () -> Unit) {
    val c = Harness.colors
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 6.dp).background(c.write))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
        TextButton(onClick = onClick) { Text(action, fontWeight = FontWeight.SemiBold) }
    }
}

// ---- empty state -----------------------------------------------------------

private val Starters = listOf(
    "What's my screen timeout?",
    "Show recent crashes from logcat",
    "Which apps are using the most battery?",
)

@Composable
private fun EmptyState(viewModel: ChatViewModel) {
    val c = Harness.colors
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
        Text("Ask about this phone", style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp, lineHeight = 36.sp))
        Text(
            "Settings, logs, installed apps and what's on screen. Changes always ask first.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.subdued,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        Starters.forEach { prompt ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.input = prompt; viewModel.send() }
                    .drawBehind {
                        drawLine(c.rule, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    .padding(vertical = 16.dp),
            ) { Text(prompt, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

// ---- conversation ------------------------------------------------------------

@Composable
private fun MessageList(messages: List<UiMessage>, thinking: Boolean) {
    val state = rememberLazyListState()
    val tail = messages.lastOrNull()
    LaunchedEffect(messages.size, tail?.text?.length, thinking) {
        val count = messages.size + if (thinking) 1 else 0
        if (count > 0) state.scrollToItem(count - 1)
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            when (message.kind) {
                UiMessage.Kind.USER -> UserBlock(message)
                UiMessage.Kind.ASSISTANT -> Prose(message)
                UiMessage.Kind.ERROR -> ErrorBlock(message)
                UiMessage.Kind.TOOL -> ToolRow(
                    message,
                    connectUp = messages.getOrNull(index - 1)?.kind == UiMessage.Kind.TOOL,
                    connectDown = messages.getOrNull(index + 1)?.kind == UiMessage.Kind.TOOL,
                )
            }
        }
        if (thinking) {
            item(key = "thinking") { ThinkingRow(connectUp = tail?.kind == UiMessage.Kind.TOOL) }
        }
    }
}

/** Spinner node on the trace rail plus elapsed seconds; shown while the model is working silently. */
@Composable
private fun ThinkingRow(connectUp: Boolean) {
    val c = Harness.colors
    val seconds by produceState(0) {
        while (true) { delay(1000); value += 1 }
    }
    val angle by rememberInfiniteTransition(label = "thinking").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "angle",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Thinking, $seconds seconds" }
            .drawBehind {
                val x = Gutter.toPx()
                val y = 17.dp.toPx()
                if (connectUp) drawLine(c.rule, Offset(x, 0f), Offset(x, y), 1.5.dp.toPx())
                val r = 5.dp.toPx()
                // Faint full ring with a bright 100-degree arc sweeping round it.
                drawCircle(c.rule, r, Offset(x, y), style = Stroke(2.dp.toPx()))
                drawArc(
                    color = c.read,
                    startAngle = angle,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(x - r, y - r),
                    size = Size(2 * r, 2 * r),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            .padding(start = ToolInset, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Thinking" + if (seconds > 0) " ${seconds}s" else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 12.sp),
            color = c.subdued,
        )
    }
}

@Composable
private fun UserBlock(message: UiMessage) {
    Box(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 56.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SelectionContainer {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .background(Harness.colors.surface2, RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Prose(message: UiMessage) {
    Box(Modifier.fillMaxWidth().padding(start = AiStart, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
        SelectionContainer {
            Markdown(if (message.streaming) message.text + " ▍" else message.text.ifEmpty { "…" })
        }
    }
}

@Composable
private fun ErrorBlock(message: UiMessage) {
    val c = Harness.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = AiStart, end = 20.dp, top = 8.dp, bottom = 8.dp)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(c.danger))
        SelectionContainer {
            Text(message.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

// ---- trace rail --------------------------------------------------------------

private const val MaxExpandedLines = 40

/** One tool call: a node on the rail, name + args, and a result that expands on tap. */
@Composable
private fun ToolRow(message: UiMessage, connectUp: Boolean, connectDown: Boolean) {
    val c = Harness.colors
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val failed = message.text.startsWith("ERROR")
    val accent = when {
        failed -> c.danger
        message.denied -> c.subdued
        message.write -> c.write
        else -> c.read
    }
    val pulse = if (message.running) {
        rememberInfiniteTransition(label = "running").animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        1f
    }
    val hasResult = !message.running && message.text.isNotBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = Gutter.toPx()
                val y = 17.dp.toPx()
                if (connectUp) drawLine(c.rule, Offset(x, 0f), Offset(x, y), 1.5.dp.toPx())
                if (connectDown) drawLine(c.rule, Offset(x, y), Offset(x, size.height), 1.5.dp.toPx())
                val nodeColor = accent.copy(alpha = pulse)
                val r = 5.dp.toPx()
                if (message.write) {
                    drawRect(nodeColor, Offset(x - r, y - r), Size(2 * r, 2 * r))
                } else {
                    drawCircle(nodeColor, r, Offset(x, y), style = Stroke(2.dp.toPx()))
                }
            }
            .clickable(enabled = hasResult, onClickLabel = if (expanded) "Collapse result" else "Expand result") {
                expanded = !expanded
            }
            .padding(start = ToolInset, end = 20.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message.toolName ?: "tool",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                textDecoration = if (message.denied) TextDecoration.LineThrough else null,
            )
            val args = summarizeArgs(message.toolArgs)
            if (args.isNotEmpty()) {
                Text(
                    args,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 12.sp),
                    color = c.subdued,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
                )
            }
            if (message.denied) {
                Text("denied", style = MaterialTheme.typography.bodySmall, color = c.subdued, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (hasResult) {
            if (expanded) {
                val lines = message.text.lines()
                val shown = lines.take(MaxExpandedLines).joinToString("\n")
                SelectionContainer {
                    Text(
                        if (lines.size > MaxExpandedLines) shown + "\n… ${lines.size - MaxExpandedLines} more lines" else shown,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 17.sp),
                        color = if (failed) c.danger else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth().background(c.surface2, RoundedCornerShape(6.dp)).padding(8.dp),
                    )
                }
            } else {
                Text(
                    resultPreview(message.text),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily, fontSize = 12.sp),
                    color = if (failed) c.danger else c.subdued,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** `{"ns":"system","key":"x"}` -> `system x`. Raw text if it isn't a JSON object. */
private fun summarizeArgs(json: String?): String {
    if (json.isNullOrBlank() || json == "{}") return ""
    return try {
        (Json.parseToJsonElement(json) as? JsonObject)?.values
            ?.joinToString(" ") { (it as? JsonPrimitive)?.content ?: it.toString() }
            ?: json
    } catch (_: Exception) {
        json
    }
}

private fun resultPreview(text: String): String {
    val lines = text.lines().filter { it.isNotBlank() }
    val first = lines.firstOrNull().orEmpty().trim()
    return if (lines.size > 1) "$first  +${lines.size - 1} lines" else first
}

// ---- composer ----------------------------------------------------------------

@Composable
private fun Composer(viewModel: ChatViewModel) {
    val c = Harness.colors
    val running = viewModel.isRunning
    val canSend = running || viewModel.input.isNotBlank()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(c.surface2, RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f).heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = viewModel.input,
                onValueChange = { viewModel.input = it },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (viewModel.input.isEmpty()) {
                            Text("Ask the device…", style = MaterialTheme.typography.bodyLarge, color = c.subdued)
                        }
                        inner()
                    }
                },
            )
        }
        val onPrimary = MaterialTheme.colorScheme.onPrimary
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) MaterialTheme.colorScheme.primary else c.rule)
                .semantics { contentDescription = if (running) "Stop" else "Send" }
                .clickable(enabled = canSend, role = Role.Button) {
                    if (running) viewModel.cancel() else viewModel.send()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(20.dp)) {
                val stroke = 2.dp.toPx()
                if (running) {
                    drawRoundRect(onPrimary, Offset(size.width * 0.2f, size.height * 0.2f), Size(size.width * 0.6f, size.height * 0.6f), CornerRadius(2.dp.toPx()))
                } else {
                    val cx = size.width / 2
                    drawLine(onPrimary, Offset(cx, size.height * 0.85f), Offset(cx, size.height * 0.15f), stroke, StrokeCap.Round)
                    drawLine(onPrimary, Offset(cx, size.height * 0.15f), Offset(cx - 6.dp.toPx(), size.height * 0.15f + 6.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(onPrimary, Offset(cx, size.height * 0.15f), Offset(cx + 6.dp.toPx(), size.height * 0.15f + 6.dp.toPx()), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

// ---- icons (drawn, no icon dependency) ----------------------------------------

@Composable
private fun PlusIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = 2.dp.toPx()
        drawLine(color, Offset(size.width / 2, 2.dp.toPx()), Offset(size.width / 2, size.height - 2.dp.toPx()), s, StrokeCap.Round)
        drawLine(color, Offset(2.dp.toPx(), size.height / 2), Offset(size.width - 2.dp.toPx(), size.height / 2), s, StrokeCap.Round)
    }
}

@Composable
private fun SlidersIcon(color: Color, background: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = 2.dp.toPx()
        listOf(0.2f to 0.3f, 0.5f to 0.7f, 0.8f to 0.45f).forEach { (y, knob) ->
            val py = size.height * y
            drawLine(color, Offset(1.dp.toPx(), py), Offset(size.width - 1.dp.toPx(), py), s, StrokeCap.Round)
            drawCircle(background, 3.5.dp.toPx(), Offset(size.width * knob, py))
            drawCircle(color, 3.5.dp.toPx(), Offset(size.width * knob, py), style = Stroke(s))
        }
    }
}

/** The app mark (same shapes as the launcher icon): a READ ring and a WRITE square on one trace line. */
@Composable
private fun HarnessMark(modifier: Modifier = Modifier) {
    val c = Harness.colors
    Canvas(modifier) {
        val k = size.width / 52f // icon geometry lives in a 52-unit box
        drawLine(c.subdued, Offset(14 * k, 22 * k), Offset(14 * k, 44 * k), 3.5f * k, StrokeCap.Round)
        drawLine(c.subdued, Offset(14 * k, 44 * k), Offset(32 * k, 44 * k), 3.5f * k, StrokeCap.Round)
        drawCircle(c.read, 10 * k, Offset(14 * k, 12 * k), style = Stroke(5 * k))
        drawRect(c.write, Offset(32 * k, 36 * k), Size(16 * k, 16 * k))
    }
}
