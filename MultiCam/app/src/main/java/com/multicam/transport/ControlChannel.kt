package com.multicam.transport

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
 */

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
                val conn = ClientConn(socket)
                launch(Dispatchers.IO) {
                    try {
                        while (true) {
                            val line = conn.reader.readLine() ?: break
                            Msg.parse(line)?.let { onMessage(conn, it) }
                        }
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
                val s = Socket(host, port).apply { tcpNoDelay = true }
                socket = s
                writer = BufferedWriter(OutputStreamWriter(s.getOutputStream()))
                onConnected()
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                while (true) {
                    val line = reader.readLine() ?: break
                    Msg.parse(line)?.let { onMessage(it) }
                }
            } catch (_: Exception) {
                // fall through to disconnect
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
            }
        }
    }

    fun close() = runCatching { socket?.close() }
}
