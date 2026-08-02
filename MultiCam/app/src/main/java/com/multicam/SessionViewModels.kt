package com.multicam

import android.app.Application
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.content.Context
import android.os.PowerManager
import android.util.Base64
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multicam.capture.CaptureEngine
import com.multicam.sync.AudioExtract
import com.multicam.sync.ClockEstimate
import com.multicam.sync.Xcorr
import com.multicam.sync.TimeSyncClient
import com.multicam.sync.TimeSyncServer
import com.multicam.transport.ControlClient
import com.multicam.transport.ControlServer
import com.multicam.transport.MonitorSender
import com.multicam.transport.MonitorServer
import com.multicam.transport.Msg
import com.multicam.transport.NsdHelper
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Session state for the two roles - S2: the take lifecycle.
 *
 * ROLL semantics: the controller names a session-clock instant T ~3s out;
 * every camera translates T to its local clock (T - offset) and starts
 * recording then. Start latency varies per device (CameraX warmup), so the
 * sidecar records the ACTUAL start in session time. S3's audio correlation
 * seals frame accuracy; the scheduled roll bounds its search window.
 */

class ControllerViewModel(app: Application) : AndroidViewModel(app) {

    data class ConnectedCamera(
        val deviceId: String,
        val name: String,
        val address: String,
    )

    data class TakeReport(val state: String, val detail: String)

    private val _cameras = MutableStateFlow<List<ConnectedCamera>>(emptyList())
    val cameras: StateFlow<List<ConnectedCamera>> = _cameras

    /** deviceId -> latest TAKE_STATUS for the current take */
    private val _reports = MutableStateFlow<Map<String, TakeReport>>(emptyMap())
    val reports: StateFlow<Map<String, TakeReport>> = _reports

    private val _rolling = MutableStateFlow(false)
    val rolling: StateFlow<Boolean> = _rolling

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    val sessionId: String = UUID.randomUUID().toString().take(8)
    private var currentTakeId: String? = null
    private var takeCounter = 0

    private val nsd = NsdHelper(app)
    private val timeSyncServer = TimeSyncServer(viewModelScope)
    private var timeSyncPort = 0

    // S4 live monitor: accepts a JPEG stream per camera, publishes the latest
    // frame per device for the grid. Decode happens off-main inside MonitorServer.
    private val _frames = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    val frames: StateFlow<Map<String, ImageBitmap>> = _frames
    private val monitorServer = MonitorServer(
        viewModelScope,
        onFrame = { id, bmp -> _frames.value = _frames.value + (id to bmp.asImageBitmap()) },
        onStreamEnd = { id -> _frames.value = _frames.value - id },
    )
    private var monitorPort = 0

    private val connToCamera = mutableMapOf<ControlServer.ClientConn, ConnectedCamera>()

    private val server = ControlServer(
        viewModelScope,
        onMessage = { conn, msg ->
            when (msg) {
                is Msg.Hello -> {
                    val cam = ConnectedCamera(msg.deviceId, msg.name, conn.remoteAddress)
                    synchronized(connToCamera) { connToCamera[conn] = cam }
                    _cameras.value = synchronized(connToCamera) { connToCamera.values.toList() }
                    conn.send(Msg.Welcome(sessionId, timeSyncPort, monitorPort))
                    logLine("+ ${cam.name} (${cam.address})")
                }
                is Msg.TakeStatus -> {
                    _reports.value = _reports.value + (msg.deviceId to TakeReport(msg.state, msg.detail))
                    logLine("${msg.state}: ${msg.detail}")
                }
                is Msg.AudioSnippet -> onSnippet(msg)
                else -> Unit
            }
        },
        onDisconnect = { conn ->
            val cam = synchronized(connToCamera) { connToCamera.remove(conn) }
            _cameras.value = synchronized(connToCamera) { connToCamera.values.toList() }
            cam?.let {
                _frames.value = _frames.value - it.deviceId
                logLine("- ${it.name} disconnected")
            }
        },
    )

    /** The controller IS the session clock - its own elapsed time, no offset. */
    fun sessionNanos(): Long = SystemClock.elapsedRealtimeNanos()

    fun start() {
        timeSyncPort = timeSyncServer.start()
        monitorPort = monitorServer.start()
        val controlPort = server.start()
        nsd.advertise(controlPort, Build.MODEL, ::logLine)
        logLine("session $sessionId - control :$controlPort - timesync :$timeSyncPort/udp - monitor :$monitorPort")
    }

    fun roll() {
        takeCounter++
        val takeId = "take-%03d".format(takeCounter)
        currentTakeId = takeId
        _reports.value = emptyMap()
        _rolling.value = true
        val startAt = sessionNanos() + 3_000_000_000L // T = now + 3s, every camera fires at T
        broadcast(Msg.Roll(takeId, startAt))
        logLine("ROLL $takeId - T minus 3s")
    }

    fun stopTake() {
        val takeId = currentTakeId ?: return
        _rolling.value = false
        broadcast(Msg.Stop(takeId))
        logLine("STOP $takeId")
    }

    private fun broadcast(msg: Msg) {
        val conns = synchronized(connToCamera) { connToCamera.keys.toList() }
        viewModelScope.launch(Dispatchers.IO) {
            conns.forEach { runCatching { it.send(msg) } }
        }
    }

    // ------------------------------------------------------------------
    // S3: audio alignment. Fingerprints arrive per take; the first device
    // (lowest deviceId, deterministic) is the reference; every other angle
    // is pre-aligned by session timestamps, then cross-correlated. The
    // residual IS gate G2's number: it is how far the clock was from the
    // truth the sound proves. < 33 ms = sub-frame.
    // ------------------------------------------------------------------

    private class Snippet(val startSessionNanos: Long, val pcm: ShortArray)

    private val snippets = mutableMapOf<String, MutableMap<String, Snippet>>() // takeId -> deviceId -> snippet

    private fun onSnippet(msg: Msg.AudioSnippet) {
        val bytes = try {
            Base64.decode(msg.pcmBase64, Base64.NO_WRAP)
        } catch (_: Exception) {
            return
        }
        val pcm = ShortArray(bytes.size / 2)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer().get(pcm)
        val perTake = synchronized(snippets) {
            snippets.getOrPut(msg.takeId) { mutableMapOf() }.also {
                it[msg.deviceId] = Snippet(msg.startSessionNanos, pcm)
            }.toMap()
        }
        logLine("fingerprint from ${msg.deviceId} (${perTake.size} total)")
        if (perTake.size >= 2) alignTake(msg.takeId, perTake)
    }

    private fun alignTake(takeId: String, perTake: Map<String, Snippet>) {
        viewModelScope.launch(Dispatchers.Default) {
            val refId = perTake.keys.min()
            val ref = perTake.getValue(refId)
            _reports.value = _reports.value + (refId to TakeReport("ALIGNED", "reference angle"))
            for ((devId, snip) in perTake) {
                if (devId == refId) continue
                // Trim both to a common session start so xcorr measures pure residual.
                val commonStart = maxOf(ref.startSessionNanos, snip.startSessionNanos)
                val rate = AudioExtract.TARGET_RATE
                val refSkip = ((commonStart - ref.startSessionNanos) * rate / 1_000_000_000L).toInt()
                val otherSkip = ((commonStart - snip.startSessionNanos) * rate / 1_000_000_000L).toInt()
                if (refSkip >= ref.pcm.size || otherSkip >= snip.pcm.size) continue
                val result = Xcorr.align(
                    ref.pcm.copyOfRange(refSkip, ref.pcm.size),
                    snip.pcm.copyOfRange(otherSkip, snip.pcm.size),
                )
                val residualMs = result.shiftMicros / 1000.0
                val subFrame = kotlin.math.abs(residualMs) <= 33.0
                val verdict = if (subFrame) "SUB-FRAME" else "OFF BY >1 FRAME"
                _reports.value = _reports.value +
                    (devId to TakeReport("ALIGNED", "residual %+.1f ms vs ref - %s (corr %.2f)".format(residualMs, verdict, result.peak)))
                logLine("$takeId $devId: %+.1f ms, corr %.2f".format(residualMs, result.peak))
            }
        }
    }

    private fun logLine(s: String) {
        _log.value = (_log.value + s).takeLast(8)
    }

    override fun onCleared() {
        nsd.stop()
        server.stop()
        timeSyncServer.stop()
        monitorServer.stop()
        super.onCleared()
    }
}

class CameraViewModel(app: Application) : AndroidViewModel(app) {

    enum class Phase { SEARCHING, CONNECTING, SYNCING, LOCKED }
    enum class RecState { IDLE, ARMED, RECORDING }

    private val _phase = MutableStateFlow(Phase.SEARCHING)
    val phase: StateFlow<Phase> = _phase

    private val _rec = MutableStateFlow(RecState.IDLE)
    val rec: StateFlow<RecState> = _rec

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    val engine = CaptureEngine()
    private val deviceId = UUID.randomUUID().toString().take(8)

    private val timeSyncClient = TimeSyncClient(viewModelScope)
    val estimate: StateFlow<ClockEstimate?> = timeSyncClient.estimate

    // S4 monitor: JPEG compression runs on its own thread, off main and off the
    // encoder threads, so it cannot contend with the master recording pipeline.
    private val monitorExecutor = Executors.newSingleThreadExecutor()
    private val monitorSender = MonitorSender(viewModelScope, deviceId)
    private val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        // Shed the monitor first; the master recording is never in this ladder.
        when {
            status >= PowerManager.THERMAL_STATUS_SEVERE ->
                monitorSender.apply { intervalMs = 500; maxWidth = 384; quality = 40 } // ~2 fps
            status >= PowerManager.THERMAL_STATUS_MODERATE ->
                monitorSender.apply { intervalMs = 200; maxWidth = 384; quality = 45 } // ~5 fps
        }
    }

    private val nsd = NsdHelper(app)
    private var connectedHost: java.net.InetAddress? = null
    private var connectedPort = 0
    private var stopped = false

    private var currentRecording: Recording? = null

    // Sidecar fields for the take in flight.
    private var takeId: String? = null
    private var scheduledStartSession = 0L
    private var actualStartLocal = 0L
    private var offsetAtStart: Long? = null
    private var uncertaintyAtStart = 0L

    private val client = ControlClient(
        viewModelScope,
        onMessage = { msg ->
            when (msg) {
                is Msg.Welcome -> {
                    logLine("session ${msg.sessionId} - locking clock...")
                    _phase.value = Phase.SYNCING
                    connectedHost?.let { host ->
                        timeSyncClient.start(host, msg.timeSyncPort)
                        // Start the live monitor feed once we know the port — but
                        // only if the device accepted the 3-use-case bind. If not,
                        // the master still records; there's just no feed.
                        if (engine.monitorSupported) {
                            engine.setMonitorAnalyzer(monitorExecutor, monitorSender.analyzer)
                            monitorSender.start(host, msg.monitorPort)
                            logLine("monitor streaming")
                        } else {
                            logLine("monitor unsupported here (recording unaffected)")
                        }
                    }
                }
                is Msg.Roll -> handleRoll(msg)
                is Msg.Stop -> currentRecording?.stop()
                else -> Unit
            }
        },
        onDisconnect = {
            timeSyncClient.stop()
            _phase.value = Phase.SEARCHING
            // Reconnect directly to the known controller rather than waiting for
            // NSD to re-announce it (mDNS only fires onServiceFound for NEW
            // services, so a dropped camera could otherwise hang in SEARCHING).
            val host = connectedHost
            if (!stopped && host != null) {
                logLine("controller lost - reconnecting...")
                viewModelScope.launch {
                    delay(600)
                    if (!stopped && _phase.value == Phase.SEARCHING) doConnect(host, connectedPort)
                }
            } else {
                logLine("controller lost - searching again")
            }
        },
    )

    private fun doConnect(host: java.net.InetAddress, port: Int) {
        if (_phase.value != Phase.SEARCHING) return
        _phase.value = Phase.CONNECTING
        connectedHost = host
        connectedPort = port
        client.connect(host, port) {
            client.send(Msg.Hello(deviceId, Build.MODEL))
        }
    }

    fun start() {
        runCatching { power.addThermalStatusListener(thermalListener) }
        nsd.discover(
            onFound = { host, port -> doConnect(host, port) },
            onEvent = ::logLine,
        )
        viewModelScope.launch {
            estimate.collect { est ->
                if (est != null && est.sampleCount >= 8 && _phase.value == Phase.SYNCING) {
                    _phase.value = Phase.LOCKED
                    logLine("clock locked: offset ${est.offsetNanos / 1_000}us +/- ${est.uncertaintyNanos / 1_000}us")
                }
            }
        }
    }

    private fun handleRoll(roll: Msg.Roll) {
        if (!engine.isReady) {
            client.send(Msg.TakeStatus(deviceId, roll.takeId, "ERROR", "camera not ready on $deviceId"))
            return
        }
        val est = estimate.value
        val offset = est?.offsetNanos ?: 0L
        takeId = roll.takeId
        scheduledStartSession = roll.startAtSessionNanos
        offsetAtStart = est?.offsetNanos
        uncertaintyAtStart = est?.uncertaintyNanos ?: -1L
        _rec.value = RecState.ARMED
        logLine("ARMED ${roll.takeId} - firing at T")

        viewModelScope.launch {
            // Translate the shared instant to this device's clock and wait for it.
            val targetLocal = roll.startAtSessionNanos - offset
            val waitMs = (targetLocal - SystemClock.elapsedRealtimeNanos()) / 1_000_000
            if (waitMs > 0) delay(waitMs)
            startTake(offset)
        }
    }

    /** Runs on main (viewModelScope) - CameraX requires it. */
    private fun startTake(offset: Long) {
        val app = getApplication<Application>()
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: app.filesDir
        val safeModel = Build.MODEL.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(dir, "${takeId}_${safeModel}.mp4")

        currentRecording = engine.startRecording(app, file) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    actualStartLocal = SystemClock.elapsedRealtimeNanos()
                    val actualSession = actualStartLocal + offset
                    val deltaMs = (actualSession - scheduledStartSession) / 1_000_000
                    _rec.value = RecState.RECORDING
                    // Ease the monitor off while the master encoder is the heat source:
                    // presence over smoothness during a take.
                    monitorSender.apply { intervalMs = 143; maxWidth = 384; quality = 45 } // ~7 fps
                    logLine("REC - started %+d ms vs T".format(deltaMs))
                    client.send(
                        Msg.TakeStatus(deviceId, takeId ?: "?", "RECORDING", "$safeModel %+d ms vs T".format(deltaMs))
                    )
                }
                is VideoRecordEvent.Finalize -> {
                    _rec.value = RecState.IDLE
                    monitorSender.apply { intervalMs = 100; maxWidth = 480; quality = 55 } // back to ~10 fps
                    if (event.hasError()) {
                        logLine("record error ${event.error}")
                        client.send(Msg.TakeStatus(deviceId, takeId ?: "?", "ERROR", "code ${event.error} on $safeModel"))
                    } else {
                        writeSidecar(file, offset)
                        logLine("SAVED ${file.name}")
                        client.send(Msg.TakeStatus(deviceId, takeId ?: "?", "SAVED", file.name))
                        sendAudioFingerprint(file, offset)
                    }
                }
                else -> Unit
            }
        }
        if (currentRecording == null) {
            client.send(Msg.TakeStatus(deviceId, takeId ?: "?", "ERROR", "startRecording returned null"))
        }
    }

    /**
     * S3: decode this take's first seconds of audio to a mono 16 kHz
     * fingerprint and ship it to the controller for cross-correlation.
     * ~380 KB over the LAN; the footage itself never leaves the device.
     */
    private fun sendAudioFingerprint(videoFile: File, offset: Long) {
        val tid = takeId ?: return
        val startSession = actualStartLocal + offset
        viewModelScope.launch(Dispatchers.Default) {
            val pcm = AudioExtract.extractMono16k(videoFile)
            if (pcm == null || pcm.size < AudioExtract.TARGET_RATE) {
                client.send(Msg.TakeStatus(deviceId, tid, "NO_AUDIO", "no usable audio track on ${Build.MODEL}"))
                return@launch
            }
            val bytes = ByteArray(pcm.size * 2)
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .asShortBuffer().put(pcm)
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            client.send(Msg.AudioSnippet(deviceId, tid, AudioExtract.TARGET_RATE, startSession, b64))
            logLine("audio fingerprint sent (${pcm.size / AudioExtract.TARGET_RATE}s)")
        }
    }

    /**
     * The take's sync metadata, written next to the MP4. This file is S3's
     * input: the audio-xcorr search window comes from these numbers.
     */
    private fun writeSidecar(videoFile: File, offset: Long) {
        val stopLocal = SystemClock.elapsedRealtimeNanos()
        val json = JSONObject()
            .put("takeId", takeId)
            .put("deviceId", deviceId)
            .put("deviceModel", Build.MODEL)
            .put("scheduledStartSessionNanos", scheduledStartSession)
            .put("actualStartLocalNanos", actualStartLocal)
            .put("actualStartSessionNanos", actualStartLocal + offset)
            .put("stopSessionNanos", stopLocal + offset)
            .put("clockOffsetNanos", offsetAtStart ?: JSONObject.NULL)
            .put("clockUncertaintyNanos", uncertaintyAtStart)
            .put("clockLocked", offsetAtStart != null)
        File(videoFile.parentFile, videoFile.nameWithoutExtension + ".sync.json")
            .writeText(json.toString(2))
    }

    private fun logLine(s: String) {
        _log.value = (_log.value + s).takeLast(8)
    }

    override fun onCleared() {
        stopped = true
        currentRecording?.stop()
        nsd.stop()
        client.close()
        timeSyncClient.stop()
        monitorSender.stop()
        engine.clearMonitorAnalyzer()
        monitorExecutor.shutdown()
        runCatching { power.removeThermalStatusListener(thermalListener) }
        super.onCleared()
    }
}
