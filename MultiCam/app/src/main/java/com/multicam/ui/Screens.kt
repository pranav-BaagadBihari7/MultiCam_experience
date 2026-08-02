package com.multicam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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

/** One live camera tile: the monitor feed + name + status badge. */
@Composable
private fun CameraTile(
    name: String,
    report: ControllerViewModel.TakeReport?,
    frame: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Box(Modifier.fillMaxSize()) {
            if (frame != null) {
                Image(
                    bitmap = frame,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("no feed", fontSize = 12.sp, color = Color.Gray)
                }
            }
            // Name, bottom-left.
            Text(
                name,
                Modifier.align(Alignment.BottomStart).padding(6.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color.White,
            )
            // Status badge, top-right.
            Text(
                report?.state ?: "READY",
                Modifier.align(Alignment.TopEnd).padding(6.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = when (report?.state) {
                    "RECORDING" -> Color(0xFFFF5252)
                    "SAVED" -> Color(0xFF69F0AE)
                    "ALIGNED" -> Color(0xFF40C4FF)
                    "ERROR", "NO_AUDIO" -> Color(0xFFFFAB40)
                    else -> Color.LightGray
                },
            )
        }
    }
}

@Composable
fun ControllerScreen(vm: ControllerViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.start() }
    val cameras by vm.cameras.collectAsState()
    val reports by vm.reports.collectAsState()
    val rolling by vm.rolling.collectAsState()
    val frames by vm.frames.collectAsState()
    val log by vm.log.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CONTROLLER - session ${vm.sessionId}", fontSize = 14.sp)
        BigClock("SESSION CLOCK (reference)", size = 40) { vm.sessionNanos() }

        Text(
            if (cameras.isEmpty()) "Waiting for cameras..." else "${cameras.size} camera(s) connected",
            fontSize = 15.sp,
        )

        // Live multi-view grid — 2-up rows of camera tiles, each showing the
        // low-bitrate monitor feed + name + status. Takes the free vertical space.
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cameras.chunked(2).forEach { rowCams ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowCams.forEach { cam ->
                        CameraTile(cam.name, reports[cam.deviceId], frames[cam.deviceId], Modifier.weight(1f).fillMaxHeight())
                    }
                    if (rowCams.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

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
