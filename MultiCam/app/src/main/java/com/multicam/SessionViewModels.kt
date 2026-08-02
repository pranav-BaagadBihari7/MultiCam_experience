package com.multicam

import android.app.Application
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multicam.capture.CaptureEngine
import com.multicam.sync.ClockEstimate
import com.multicam.sync.TimeSyncClient
import com.multicam.sync.TimeSyncServer
import com.multicam.transport.ControlClient
import com.multicam.transport.ControlServer
import com.multicam.transport.Msg
import com.multicam.transport.NsdHelper
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

    private val connToCamera = mutableMapOf<ControlServer.ClientConn, ConnectedCamera>()

    private val server = ControlServer(
        viewModelScope,
        onMessage = { conn, msg ->
            when (msg) {
                is Msg.Hello -> {
                    val cam = ConnectedCamera(msg.deviceId, msg.name, conn.remoteAddress)
                    synchronized(connToCamera) { connToCamera[conn] = cam }
                    _cameras.value = synchronized(connToCamera) { connToCamera.values.toList() }
                    conn.send(Msg.Welcome(sessionId, timeSyncPort))
                    logLine("+ ${cam.name} (${cam.address})")
                }
                is Msg.TakeStatus -> {
                    _reports.value = _reports.value + (msg.deviceId to TakeReport(msg.state, msg.detail))
                    logLine("${msg.state}: ${msg.detail}")
                }
                else -> Unit
            }
        },
        onDisconnect = { conn ->
            val cam = synchronized(connToCamera) { connToCamera.remove(conn) }
            _cameras.value = synchronized(connToCamera) { connToCamera.values.toList() }
            cam?.let { logLine("- ${it.name} disconnected") }
        },
    )

    /** The controller IS the session clock - its own elapsed time, no offset. */
    fun sessionNanos(): Long = SystemClock.elapsedRealtimeNanos()

    fun start() {
        timeSyncPort = timeSyncServer.start()
        val controlPort = server.start()
        nsd.advertise(controlPort, Build.MODEL, ::logLine)
        logLine("session $sessionId - control :$controlPort - timesync :$timeSyncPort/udp")
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

    private fun logLine(s: String) {
        _log.value = (_log.value + s).takeLast(8)
    }

    override fun onCleared() {
        nsd.stop()
        server.stop()
        timeSyncServer.stop()
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

    private val nsd = NsdHelper(app)
    private var connectedHost: java.net.InetAddress? = null

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
                    connectedHost?.let { timeSyncClient.start(it, msg.timeSyncPort) }
                }
                is Msg.Roll -> handleRoll(msg)
                is Msg.Stop -> currentRecording?.stop()
                else -> Unit
            }
        },
        onDisconnect = {
            logLine("controller lost - searching again")
            _phase.value = Phase.SEARCHING
        },
    )

    fun start() {
        nsd.discover(
            onFound = { host, port ->
                if (_phase.value != Phase.SEARCHING) return@discover
                _phase.value = Phase.CONNECTING
                connectedHost = host
                client.connect(host, port) {
                    client.send(Msg.Hello(deviceId, Build.MODEL))
                }
            },
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
                    logLine("REC - started %+d ms vs T".format(deltaMs))
                    client.send(
                        Msg.TakeStatus(deviceId, takeId ?: "?", "RECORDING", "$safeModel %+d ms vs T".format(deltaMs))
                    )
                }
                is VideoRecordEvent.Finalize -> {
                    _rec.value = RecState.IDLE
                    if (event.hasError()) {
                        logLine("record error ${event.error}")
                        client.send(Msg.TakeStatus(deviceId, takeId ?: "?", "ERROR", "code ${event.error} on $safeModel"))
                    } else {
                        writeSidecar(file, offset)
                        logLine("SAVED ${file.name}")
                        client.send(Msg.TakeStatus(deviceId, takeId ?: "?", "SAVED", file.name))
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
        currentRecording?.stop()
        nsd.stop()
        client.close()
        timeSyncClient.stop()
        super.onCleared()
    }
}
