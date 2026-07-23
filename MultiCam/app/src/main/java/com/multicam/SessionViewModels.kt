package com.multicam

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multicam.sync.ClockEstimate
import com.multicam.sync.TimeSyncClient
import com.multicam.sync.TimeSyncServer
import com.multicam.transport.ControlClient
import com.multicam.transport.ControlServer
import com.multicam.transport.Msg
import com.multicam.transport.NsdHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Session state for the two roles. One APK, role chosen at runtime — the
 * spec's §1.1 shape. S1 scope: discovery, connection, clock lock, live
 * telemetry. ROLL/STOP land here in S2.
 */

class ControllerViewModel(app: Application) : AndroidViewModel(app) {

    data class ConnectedCamera(
        val deviceId: String,
        val name: String,
        val address: String,
        val connectedAtElapsed: Long,
    )

    private val _cameras = MutableStateFlow<List<ConnectedCamera>>(emptyList())
    val cameras: StateFlow<List<ConnectedCamera>> = _cameras

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    val sessionId: String = UUID.randomUUID().toString().take(8)

    private val nsd = NsdHelper(app)
    private val timeSyncServer = TimeSyncServer(viewModelScope)
    private var timeSyncPort = 0

    private val connToCamera = mutableMapOf<ControlServer.ClientConn, ConnectedCamera>()

    private val server = ControlServer(
        viewModelScope,
        onMessage = { conn, msg ->
            when (msg) {
                is Msg.Hello -> {
                    val cam = ConnectedCamera(
                        deviceId = msg.deviceId,
                        name = msg.name,
                        address = conn.remoteAddress,
                        connectedAtElapsed = SystemClock.elapsedRealtimeNanos(),
                    )
                    synchronized(connToCamera) { connToCamera[conn] = cam }
                    _cameras.value = synchronized(connToCamera) { connToCamera.values.toList() }
                    conn.send(Msg.Welcome(sessionId, timeSyncPort))
                    logLine("+ ${cam.name} (${cam.address})")
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

    /** The controller IS the session clock — its own elapsed time, no offset. */
    fun sessionNanos(): Long = SystemClock.elapsedRealtimeNanos()

    fun start() {
        timeSyncPort = timeSyncServer.start()
        val controlPort = server.start()
        nsd.advertise(controlPort, Build.MODEL, ::logLine)
        logLine("session $sessionId · control :$controlPort · timesync :$timeSyncPort/udp")
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

    private val _phase = MutableStateFlow(Phase.SEARCHING)
    val phase: StateFlow<Phase> = _phase

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val timeSyncClient = TimeSyncClient(viewModelScope)
    val estimate: StateFlow<ClockEstimate?> = timeSyncClient.estimate

    private val nsd = NsdHelper(app)
    private var connectedHost: java.net.InetAddress? = null

    private val client = ControlClient(
        viewModelScope,
        onMessage = { msg ->
            when (msg) {
                is Msg.Welcome -> {
                    logLine("session ${msg.sessionId} · locking clock…")
                    _phase.value = Phase.SYNCING
                    connectedHost?.let { timeSyncClient.start(it, msg.timeSyncPort) }
                }
                else -> Unit
            }
        },
        onDisconnect = {
            logLine("controller lost — searching again")
            _phase.value = Phase.SEARCHING
        },
    )

    fun start() {
        val deviceId = UUID.randomUUID().toString().take(8)
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
        // Promote SYNCING -> LOCKED once the estimate is trustworthy.
        viewModelScope.launch {
            estimate.collect { est ->
                if (est != null && est.sampleCount >= 8 && _phase.value == Phase.SYNCING) {
                    _phase.value = Phase.LOCKED
                    logLine("clock locked: offset ${est.offsetNanos / 1_000}us ± ${est.uncertaintyNanos / 1_000}us")
                }
            }
        }
    }

    private fun logLine(s: String) {
        _log.value = (_log.value + s).takeLast(8)
    }

    override fun onCleared() {
        nsd.stop()
        client.close()
        timeSyncClient.stop()
        super.onCleared()
    }
}
