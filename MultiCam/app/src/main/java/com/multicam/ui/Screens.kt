package com.multicam.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multicam.CameraViewModel
import com.multicam.ControllerViewModel
import kotlinx.coroutines.delay

/**
 * S1 screens. The star of both is the SESSION CLOCK — the same number ticking
 * on every device. Filming two screens side by side IS the S1 demo.
 */

private fun formatSession(nanos: Long): String {
    val totalMs = nanos / 1_000_000
    val m = totalMs / 60_000
    val s = (totalMs % 60_000) / 1000
    val ms = totalMs % 1000
    return "%02d:%02d.%03d".format(m, s, ms)
}

@Composable
private fun BigClock(label: String, readNanos: () -> Long?) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            now = readNanos() ?: -1L
            delay(16) // ~60 fps clock repaint
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(
            if (now < 0) "--:--.---" else formatSession(now),
            fontSize = 64.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun EventLog(lines: List<String>) {
    Column(Modifier.fillMaxWidth()) {
        lines.forEach {
            Text(it, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
        }
    }
}

@Composable
fun ControllerScreen(vm: ControllerViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.start() }
    val cameras by vm.cameras.collectAsState()
    val log by vm.log.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CONTROLLER — session ${vm.sessionId}", fontSize = 14.sp)
        BigClock("SESSION CLOCK (reference)") { vm.sessionNanos() }

        Text(
            if (cameras.isEmpty()) "Waiting for cameras…" else "${cameras.size} camera(s) locked on",
            fontSize = 16.sp,
        )
        cameras.forEach { cam ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(cam.name, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text(cam.address, fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        EventLog(log)
    }
}

@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.start() }
    val phase by vm.phase.collectAsState()
    val est by vm.estimate.collectAsState()
    val log by vm.log.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CAMERA — ${phase.name}", fontSize = 14.sp)
        BigClock("SESSION CLOCK (derived)") {
            est?.sessionNanos(SystemClock.elapsedRealtimeNanos())
        }

        est?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Telemetry("clock offset", "%+,d µs".format(it.offsetNanos / 1_000))
                    Telemetry("uncertainty", "± %,d µs".format(it.uncertaintyNanos / 1_000))
                    Telemetry("best RTT", "%,d µs".format(it.rttMinNanos / 1_000))
                    Telemetry("samples", "${it.sampleCount}")
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        EventLog(log)
    }
}

@Composable
private fun Telemetry(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}
