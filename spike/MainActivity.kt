package com.spike.decoder

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class, ExperimentalApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SpikeScreen() }
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalApi::class)
@Composable
fun SpikeScreen(vm: SpikeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().padding(12.dp)) {

        // The grid. No seek bar, deliberately — seeking crashes in grid mode (androidx/media#2439).
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 8.dp)
        ) {
            AndroidView(
                factory = { c -> PlayerView(c).apply { useController = false } },
                update = { it.player = vm.player },
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1515))) {
                Text(it, Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("elapsed", "${state.elapsedSec}s / 60s")
            Stat("dropped", "${state.droppedFrames}", alarm = state.droppedFrames > 60)
            Stat("thermal", state.thermal, alarm = state.thermal !in listOf("NONE", "LIGHT", "-"))
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start() }, enabled = state.status != "running") {
                Text(if (state.finished) "Run again" else "Start 60s run")
            }
            OutlinedButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("spike", vm.results()))
                },
                enabled = state.finished,
            ) { Text("Copy results") }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            if (state.finished) vm.results() else state.capabilities,
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String, alarm: Boolean = false) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = if (alarm) Color(0xFF4A1515) else Color(0xFF1C1C1E)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
