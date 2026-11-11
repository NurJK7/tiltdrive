package dev.tiltdrive.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

private val Bg = Color(0xFF0A0E14)
private val Panel = Color(0xFF131A24)
private val Line = Color(0xFF223044)
private val Accent = Color(0xFFFF7A1A)
private val Gas = Color(0xFF1FD6A0)
private val BrakeCol = Color(0xFFFF4D5A)
private val Dim = Color(0xFF7C8A9E)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Color.Black,
            background = Bg, surface = Panel, onSurface = Color.White, onBackground = Color.White,
        ),
        content = content,
    )
}

@Composable
fun App(vm: DriveViewModel) {
    val state by vm.link.state.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = Bg, contentColor = Color.White) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (state == LinkState.Connected) DriveScreen(vm) else ConnectScreen(vm)
        }
    }
}

// ───────────────────────────── Connect screen ─────────────────────────────

@Composable
fun ConnectScreen(vm: DriveViewModel) {
    val state by vm.link.state.collectAsStateWithLifecycle()
    val error by vm.link.error.collectAsStateWithLifecycle()
    val progress by vm.link.progress.collectAsStateWithLifecycle()
    val searching = state == LinkState.Searching
    val phoneIp = remember { vm.link.phoneIp() }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Wheel(angle = -18f, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(8.dp))
            Text("TiltDrive", fontSize = 34.sp, fontWeight = FontWeight.Black)
            Text("Use your phone as a steering wheel", color = Dim, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
            Step("1", "Phone and PC on the same Wi-Fi")
            Step("2", "In the game: Options → Controls → Hardware → “Use your phone or tablet” = Yes")
            Step("3", "Tap AUTO-CONNECT. Tilt to steer, right half = gas, left half = brake")
            Spacer(Modifier.height(14.dp))
            Text("Dashboard (optional)", color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "In the game: Options → Advanced → Protocols → OutGauge. Address ${phoneIp ?: "(join the Wi-Fi first)"}, " +
                    "port 4445, rate 60. Then reload the car (Ctrl+R).",
                color = Color(0xFFC5CEDB), fontSize = 13.sp,
            )
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = { vm.autoConnect() }, enabled = !searching,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("AUTO-CONNECT", fontSize = 18.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(10.dp))
            if (searching) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Looking for the game… ${(progress * 100).roundToInt()}%", color = Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.link.disconnect() }) { Text("Cancel") }
                }
            } else if (state == LinkState.Failed && error != null) {
                Text(error ?: "", color = BrakeCol, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            Text("Know the 5-digit code? Connect with it", color = Dim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vm.code, onValueChange = { vm.code = it.filter(Char::isDigit).take(5) }, singleLine = true,
                label = { Text("Code") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = vm.host, onValueChange = { vm.host = it }, singleLine = true,
                label = { Text("PC IP address (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { vm.connectManual() }, enabled = !searching && vm.code.length == 5,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Connect") }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(Accent),
            contentAlignment = Alignment.Center,
        ) { Text(n, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Color(0xFFC5CEDB), fontSize = 14.sp)
    }
}

// ───────────────────────────── Drive screen ─────────────────────────────

@Composable
fun DriveScreen(vm: DriveViewModel) {
    val link = vm.link
    val ctx = LocalContext.current
    val view = LocalView.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tel by link.telemetry.collectAsStateWithLifecycle()
    val stale by link.stale.collectAsStateWithLifecycle()

    val tilt = remember {
        TiltSensor(ctx) { view.display?.rotation ?: android.view.Surface.ROTATION_90 }
    }
    DisposableEffect(tilt) {
        tilt.start()
        onDispose {
            tilt.stop()
            link.throttle = 0f
            link.brake = 0f
        }
    }

    var trim by remember { mutableFloatStateOf(0f) }
    var wheelDeg by remember { mutableFloatStateOf(0f) }
    var showSettings by remember { mutableStateOf(false) }
    val cfg by rememberUpdatedState(settings)

    LaunchedEffect(tilt) {
        while (isActive) {
            val s = cfg
            val a = tilt.angleDeg - trim
            val n = (a / s.range).coerceIn(-1f, 1f)
            val dz = s.deadzone / 100f
            val v = if (abs(n) <= dz) 0f else sign(n) * (abs(n) - dz) / (1f - dz)
            val right = if (s.invert) -v else v   // +1 = full right
            link.steerWire = 0.5f - 0.5f * right  // wire: 0 = right, 1 = left, 0.5 = straight
            wheelDeg = a.coerceIn(-s.range, s.range)
            delay(16)
        }
    }

    Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pedal("BRAKE", BrakeCol, settings.analog, settings.haptics, Modifier.weight(0.85f).fillMaxHeight()) {
            link.brake = it
        }
        Column(Modifier.weight(2.3f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val (txt, col) = when {
                    tel == null -> "Connected · dashboard off (set up OutGauge)" to Accent
                    stale -> "No telemetry from the game" to Accent
                    else -> "Connected" to Gas
                }
                Text(txt, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { trim = tilt.angleDeg }) { Text("Center") }
                TextButton(onClick = { showSettings = true }) { Text("Settings") }
                TextButton(onClick = { link.disconnect() }) { Text("Exit", color = BrakeCol) }
            }
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Wheel(angle = wheelDeg, modifier = Modifier.size(150.dp))
                Hud(tel, settings.mph)
            }
        }
        Pedal("GAS", Gas, settings.analog, settings.haptics, Modifier.weight(0.85f).fillMaxHeight()) {
            link.throttle = it
        }
    }

    if (showSettings) {
        SettingsDialog(settings, vm::update) { showSettings = false }
    }
}

@Composable
private fun Hud(t: Telemetry?, mph: Boolean) {
    var peak by remember { mutableFloatStateOf(3000f) }
    if (t != null) peak = max(peak, t.rpm)
    val maxRpm = if (peak <= 1.5f) 1f else max(peak, 3000f) * 1.08f
    val rpmFrac = ((t?.rpm ?: 0f) / maxRpm).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                (t?.speed(mph) ?: 0f).roundToInt().toString(),
                fontSize = 64.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(6.dp))
            Text(if (mph) "mph" else "km/h", color = Dim, modifier = Modifier.padding(bottom = 12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Accent, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(t?.gearLabel ?: "N", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Accent) }
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.width(150.dp).height(12.dp)) {
                drawRoundRect(Line, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                val col = when { rpmFrac > 0.9f -> BrakeCol; rpmFrac > 0.75f -> Accent; else -> Gas }
                drawRoundRect(col, size = Size(size.width * rpmFrac, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lamp("◀", t?.lamp(Lights.SIGNAL_L) == true, Gas)
            Lamp("▶", t?.lamp(Lights.SIGNAL_R) == true, Gas)
            Lamp("HI", t?.lamp(Lights.FULLBEAM) == true, Color(0xFF4DA3FF))
            Lamp("ABS", t?.lamp(Lights.ABS) == true, Accent)
            Lamp("TC", t?.lamp(Lights.TC) == true, Accent)
            Lamp("P", t?.lamp(Lights.HANDBRAKE) == true, BrakeCol)
        }
        if (t != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Fuel ${(t.fuel * 100).roundToInt()}%   Temp ${t.engTemp.roundToInt()}°C",
                color = Dim, fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Lamp(text: String, on: Boolean, color: Color) {
    Text(text, color = if (on) color else Color(0xFF2A3547), fontWeight = FontWeight.Bold, fontSize = 15.sp)
}

@Composable
fun Wheel(angle: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.9f
        rotate(angle, pivot = c) {
            drawCircle(Accent, r, c, style = Stroke(width = r * 0.16f))
            drawLine(Accent, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = r * 0.13f, cap = StrokeCap.Round)
            drawLine(Accent, c, Offset(c.x, c.y + r), strokeWidth = r * 0.13f, cap = StrokeCap.Round)
            drawCircle(Accent, r * 0.22f, c)
            drawLine(Color.White, Offset(c.x, c.y - r * 1.04f), Offset(c.x, c.y - r * 0.7f), strokeWidth = r * 0.1f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Pedal(
    label: String, color: Color, analog: Boolean, haptics: Boolean,
    modifier: Modifier, onValue: (Float) -> Unit,
) {
    var value by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    val update by rememberUpdatedState(onValue)
    val hapticsOn by rememberUpdatedState(haptics)
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .clip(shape)
            .background(Panel)
            .border(2.dp, if (value > 0f) color else Line, shape)
            .pointerInput(analog) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun valueAt(y: Float) = if (analog) (1f - y / size.height).coerceIn(0.1f, 1f) else 1f
                    value = valueAt(down.position.y)
                    update(value)
                    if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var tracking = true
                    while (tracking) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed) {
                            tracking = false
                        } else {
                            value = valueAt(ch.position.y)
                            update(value)
                            ch.consume()
                        }
                    }
                    value = 0f
                    update(0f)
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = size.height * value
            drawRect(color.copy(alpha = 0.35f), topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            val n = 9
            for (i in 1 until n) {
                val y = size.height * i / n
                drawLine(Color(0x22FFFFFF), Offset(size.width * 0.15f, y), Offset(size.width * 0.85f, y), strokeWidth = 4f)
            }
        }
        Text(
            label, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp, modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

// ───────────────────────────── Settings ─────────────────────────────

@Composable
private fun SettingsDialog(s: Settings, onChange: (Settings) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Steering range: ${s.range.roundToInt()}°", fontSize = 14.sp)
                Slider(s.range, { onChange(s.copy(range = it)) }, valueRange = 30f..120f)
                Text("Dead zone: ${s.deadzone.roundToInt()}%", fontSize = 14.sp)
                Slider(s.deadzone, { onChange(s.copy(deadzone = it)) }, valueRange = 0f..15f)
                SwitchRow("Reverse steering", s.invert) { onChange(s.copy(invert = it)) }
                SwitchRow("Analog pedals (slide to modulate)", s.analog) { onChange(s.copy(analog = it)) }
                SwitchRow("Vibration", s.haptics) { onChange(s.copy(haptics = it)) }
                SwitchRow("Speed in mph", s.mph) { onChange(s.copy(mph = it)) }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Unofficial app, not affiliated with BeamNG. Protocol info from " +
                        "github.com/BeamNG/remotecontrol (ISC license, © 2014 BeamNG).",
                    color = Dim, fontSize = 11.sp,
                )
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}
