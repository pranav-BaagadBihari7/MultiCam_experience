package com.multicam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.multicam.CameraViewModel
import com.multicam.ControllerViewModel
import kotlinx.coroutines.delay

/**
 * S2 screens. The controller gains ROLL/STOP and per-angle status; the camera
 * gains a live viewfinder, the ARMED/REC ribbon, and runtime permissions.
 * The shared session clock stays on both screens - it is the product's spine
 * and the demo's proof.
 */

private fun formatSession(nanos: Long): String {
    val totalMs = nanos / 1_000_000
    val m = totalMs / 60_000
    val s = (totalMs % 60_000) / 1000
    val ms = totalMs % 1000
    return "%02d:%02d.%03d".format(m, s, ms)
}

@Composable
private fun BigClock(label: String, size: Int = 54, readNanos: () -> Long?) {
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
            fontSize = size.sp,
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

// ---------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------

@Composable
fun ControllerScreen(vm: ControllerViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.start() }
    val cameras by vm.cameras.collectAsState()
    val reports by vm.reports.collectAsState()
    val rolling by vm.rolling.collectAsState()
    val log by vm.log.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CONTROLLER - session ${vm.sessionId}", fontSize = 14.sp)
        BigClock("SESSION CLOCK (reference)") { vm.sessionNanos() }

        Text(
            if (cameras.isEmpty()) "Waiting for cameras..." else "${cameras.size} camera(s) connected",
            fontSize = 15.sp,
        )
        cameras.forEach { cam ->
            val report = reports[cam.deviceId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cam.name, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text(
                            report?.state ?: "READY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = when (report?.state) {
                                "RECORDING" -> Color(0xFFFF5252)
                                "SAVED" -> Color(0xFF69F0AE)
                                "ERROR" -> Color(0xFFFFAB40)
                                else -> Color.Gray
                            },
                        )
                    }
                    report?.let {
                        Text(it.detail, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { if (rolling) vm.stopTake() else vm.roll() },
            enabled = cameras.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (rolling) Color(0xFFB71C1C) else Color(0xFF1B5E20),
            ),
        ) {
            Text(if (rolling) "STOP" else "ROLL ALL CAMERAS", fontSize = 20.sp)
        }
        EventLog(log)
    }
}

// ---------------------------------------------------------------------------
// Camera
// ---------------------------------------------------------------------------

@Composable
fun CameraScreen(vm: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = grants[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) {
        vm.start()
        if (!permissionsGranted) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    val phase by vm.phase.collectAsState()
    val rec by vm.rec.collectAsState()
    val est by vm.estimate.collectAsState()
    val log by vm.log.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("CAMERA - ${phase.name}", fontSize = 13.sp)
            Text(
                when (rec) {
                    CameraViewModel.RecState.RECORDING -> "REC"
                    CameraViewModel.RecState.ARMED -> "ARMED"
                    CameraViewModel.RecState.IDLE -> ""
                },
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                color = when (rec) {
                    CameraViewModel.RecState.RECORDING -> Color(0xFFFF5252)
                    CameraViewModel.RecState.ARMED -> Color(0xFFFFAB40)
                    else -> Color.Gray
                },
            )
        }

        // Viewfinder: also keeps the CameraX pipeline warm so ROLL fires fast.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (permissionsGranted) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).also { pv ->
                            vm.engine.preview.surfaceProvider = pv.surfaceProvider
                            vm.engine.bind(
                                ctx, lifecycleOwner,
                                onReady = {},
                                onError = {},
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission needed", color = Color.Gray)
                }
            }
        }

        BigClock("SESSION CLOCK (derived)", size = 40) {
            est?.sessionNanos(SystemClock.elapsedRealtimeNanos())
        }

        est?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Telemetry("offset / uncertainty", "%+,d us +/- %,d us".format(it.offsetNanos / 1_000, it.uncertaintyNanos / 1_000))
                    Telemetry("best RTT / samples", "%,d us / %d".format(it.rttMinNanos / 1_000, it.sampleCount))
                }
            }
        }
        EventLog(log)
    }
}

@Composable
private fun Telemetry(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}
