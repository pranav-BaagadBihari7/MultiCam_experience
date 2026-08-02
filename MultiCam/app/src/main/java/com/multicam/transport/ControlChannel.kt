package com.multicam.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP control channel: newline-delimited JSON messages (Wire.kt).
 * Reliable and ordered — the property UDP lacks and commands require.
 * Time-critical data does NOT travel here; that's TimeSync's UDP path.
 *
 * SO_KEEPALIVE is on so a camera whose screen sleeps (Wi-Fi power-save can
 * silently half-drop the socket) is detected rather than lingering as a
 * zombie; disconnect causes are logged, never swallowed (tag "mcnet").
 */
const val NET_TAG = "mcnet"

/** Controller side: accepts N camera connections. */
class ControlServer(
    private val scope: CoroutineScope,
    private val onMessage: (ClientConn, Msg) -> Unit,
    private val onDisconnect: (ClientConn) -> Unit,
) {
    class ClientConn internal constructor(private val socket: Socket) {
        val remoteAddress: String = socket.inetAddress?.hostAddress ?: "?"
        private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
        internal val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        @Synchronized
        fun send(msg: Msg) {
            writer.write(msg.toJson())
            writer.newLine()
            writer.flush()
        }

        fun close() = runCatching { socket.close() }
    }

    private var server: ServerSocket? = null

    /** Binds an ephemeral port and returns it — this is what NSD advertises. */
    fun start(): Int {
        val ss = ServerSocket(0)
        server = ss
        scope.launch(Dispatchers.IO) {
            while (!ss.isClosed) {
                val socket = runCatching { ss.accept() }.getOrNull() ?: break
                socket.tcpNoDelay = true
                runCatching { socket.keepAlive = true }
                val conn = ClientConn(socket)
                Log.i(NET_TAG, "server: accepted ${conn.remoteAddress}")
                launch(Dispatchers.IO) {
                    try {
                        while (true) {
                            val line = conn.reader.readLine() ?: break
                            Msg.parse(line)?.let { onMessage(conn, it) }
                        }
                        Log.i(NET_TAG, "server: ${conn.remoteAddress} closed stream (EOF)")
                    } catch (e: Exception) {
                        Log.w(NET_TAG, "server: ${conn.remoteAddress} read loop ended: ${e.javaClass.simpleName}: ${e.message}")
                    } finally {
                        conn.close()
                        onDisconnect(conn)
                    }
                }
            }
        }
        return ss.localPort
    }

    fun stop() = runCatching { server?.close() }
}

/** Camera side: one connection to the controller. */
class ControlClient(
    private val scope: CoroutineScope,
    private val onMessage: (Msg) -> Unit,
    private val onDisconnect: () -> Unit,
) {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    fun connect(host: InetAddress, port: Int, onConnected: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket(host, port).apply {
                    tcpNoDelay = true
                    runCatching { keepAlive = true }
                }
                socket = s
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                Log.i(NET_TAG, "client: connected to ${host.hostAddress}:$port")
                onConnected()
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    Msg.parse(line)?.let { onMessage(it) }
                }
                Log.i(NET_TAG, "client: controller closed stream (EOF)")
            } catch (e: Exception) {
                Log.w(NET_TAG, "client: read loop ended: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                runCatching { socket?.close() }
                onDisconnect()
            }
        }
    }

    fun send(msg: Msg) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                writer?.apply { write(msg.toJson()); newLine(); flush() }
            }.onFailure { Log.w(NET_TAG, "client: send failed: ${it.message}") }
        }
    }

    fun close() = runCatching { socket?.close() }
}
