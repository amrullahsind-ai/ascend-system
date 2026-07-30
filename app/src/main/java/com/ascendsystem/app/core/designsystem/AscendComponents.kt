package com.ascendsystem.app.core.designsystem

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object AscendColors {
    val Background = Color(0xFF04070E); val Surface = Color(0xE60A1220)
    val SurfaceRaised = Color(0xFF101C2E); val Cyan = Color(0xFF43D9EE)
    val Violet = Color(0xFF9070E8); val Amber = Color(0xFFF4B95F)
    val Critical = Color(0xFFFF5D6C); val Success = Color(0xFF55E6B5)
    val Text = Color(0xFFE7F4F7); val Muted = Color(0xFF8CA6AF)
}
object AscendSpacing { val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp }
object AscendShapes { val panel = CutCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomEnd = 2.dp, bottomStart = 14.dp) }
object AscendMotion { const val quick = 140; const val standard = 280; const val holdActivationMillis = 3_000L }

@Composable fun AscendBackground(content: @Composable BoxScope.() -> Unit) =
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AscendColors.Background, Color(0xFF07101D)))), content = content)

@Composable fun SystemPanel(modifier: Modifier = Modifier, accent: Color = AscendColors.Cyan, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.border(1.dp, accent.copy(alpha = .45f), AscendShapes.panel), color = AscendColors.Surface, shape = AscendShapes.panel) {
        Column(Modifier.padding(AscendSpacing.md), verticalArrangement = Arrangement.spacedBy(AscendSpacing.sm), content = content)
    }
}
@Composable fun SystemHeader(kicker: String, title: String, subtitle: String? = null) {
    Text(kicker.uppercase(), color = AscendColors.Cyan, style = MaterialTheme.typography.labelSmall)
    Text(title.uppercase(), color = AscendColors.Text, style = MaterialTheme.typography.headlineSmall)
    subtitle?.let { Text(it, color = AscendColors.Muted, style = MaterialTheme.typography.bodyMedium) }
}
@Composable fun SystemStatusIndicator(label: String, online: Boolean) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(if (online) AscendColors.Success else AscendColors.Critical))
        Spacer(Modifier.width(AscendSpacing.sm)); Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
    }
@Composable fun PrimarySystemButton(label: String, enabled: Boolean = true, action: () -> Unit) =
    Button(action, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = AscendShapes.panel) { Text(label.uppercase()) }
@Composable fun SecondarySystemButton(label: String, action: () -> Unit) =
    OutlinedButton(action, modifier = Modifier.fillMaxWidth(), shape = AscendShapes.panel) { Text(label.uppercase()) }
@Composable fun EmergencyOverrideButton(action: () -> Unit) =
    OutlinedButton(action, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, AscendColors.Critical), shape = AscendShapes.panel) {
        Text("EMERGENCY OVERRIDE", color = AscendColors.Critical)
    }
@Composable fun SafetyNoticeCard(text: String) = SystemPanel(accent = AscendColors.Amber) {
    Text("SAFETY NOTICE", color = AscendColors.Amber, style = MaterialTheme.typography.labelMedium)
    Text(text, color = AscendColors.Text)
}
@Composable fun AssessmentProgressIndicator(step: Int, total: Int) {
    Text("CALIBRATION ${step + 1}/$total", color = AscendColors.Muted, style = MaterialTheme.typography.labelSmall)
    LinearProgressIndicator({ (step + 1f) / total }, Modifier.fillMaxWidth(), color = AscendColors.Cyan)
}
@Composable fun AssessmentOptionCard(label: String, selected: Boolean, action: () -> Unit) =
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = action).border(1.dp, if (selected) AscendColors.Cyan else AscendColors.Muted.copy(.3f), AscendShapes.panel),
        color = if (selected) AscendColors.Cyan.copy(.08f) else AscendColors.Surface, shape = AscendShapes.panel
    ) { Text(label.uppercase(), Modifier.padding(AscendSpacing.md), color = if (selected) AscendColors.Cyan else AscendColors.Text) }
@Composable fun ProtocolRuleCard(label: String, value: String, edit: (() -> Unit)? = null) = SystemPanel(Modifier.fillMaxWidth()) {
    Text(label.uppercase(), color = AscendColors.Muted, style = MaterialTheme.typography.labelSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(value, color = AscendColors.Text)
        edit?.let { TextButton(onClick = it) { Text("EDIT") } }
    }
}

@Composable
fun HoldToActivateButton(onActivated: () -> Unit, modifier: Modifier = Modifier) {
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val ticks = 30
        repeat(ticks) {
            delay(AscendMotion.holdActivationMillis / ticks)
            if (!holding) return@LaunchedEffect
            progress = (it + 1f) / ticks
        }
        onActivated(); holding = false
    }
    Surface(
        modifier = modifier.fillMaxWidth().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    holding = event.changes.any { it.pressed }
                }
            }
        },
        color = AscendColors.Cyan.copy(alpha = .12f), shape = AscendShapes.panel,
        border = BorderStroke(1.dp, AscendColors.Cyan)
    ) {
        Column(Modifier.padding(AscendSpacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("HOLD TO ACTIVATE SYSTEM", color = AscendColors.Cyan, fontWeight = FontWeight.Bold)
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(top = AscendSpacing.sm))
        }
    }
}

@Preview @Composable private fun ComponentsPreview() = AscendTheme {
    AscendBackground { Column(Modifier.padding(16.dp)) { SystemHeader("System online", "Protocol status"); SafetyNoticeCard("Emergency access remains available.") } }
}
