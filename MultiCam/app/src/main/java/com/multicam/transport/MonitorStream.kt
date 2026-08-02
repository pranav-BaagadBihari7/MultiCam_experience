package com.multicam.transport

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * S4 — the live multi-view monitor transport. A SEPARATE low-bitrate JPEG
 * stream per camera, on its own TCP socket. It exists only so the director can
 * judge framing; it NEVER touches the master Preview+VideoCapture path.
 *
 * Design guarantees the monitor cannot disturb recording:
 *  - camera side runs ImageAnalysis with STRATEGY_KEEP_ONLY_LATEST, hands frames
 *    to a CONFLATED channel — a slow/lossy network drops frames, never applies
 *    backpressure to the camera pipeline or the encoders;
 *  - JPEG compression runs on a dedicated executor, never main, never an encoder
 *    thread; the socket read/decode on the controller runs off the main thread.
 *
 * Wire format (big-endian via Data{In,Out}putStream = network byte order):
 *   handshake once:  [int32 idLen][idLen bytes deviceId UTF-8]
 *   then per frame:  [int32 jpegLen][jpegLen bytes JPEG]
 */
private const val MAX_FRAME = 2_000_000

// ─────────────────────────── CAMERA SIDE ───────────────────────────

/** Compresses ImageAnalysis frames to JPEG and streams them over one TCP socket. */
class MonitorSender(
    private val scope: CoroutineScope,
    private val deviceId: String,
) {
    // Tunables flipped by record state / thermal. Volatile: read on the analyzer
    // thread, written from the VM / thermal callback.
    @Volatile var intervalMs: Long = 100   // ~10 fps standby
    @Volatile var maxWidth: Int = 480
    @Volatile var quality: Int = 55

    private val frames = Channel<ByteArray>(Channel.CONFLATED) // drop-old, never block camera
    private var lastAtNanos = 0L
    private var socket: Socket? = null

    /** Attach via CaptureEngine.setMonitorAnalyzer(executor, analyzer). Runs on that executor. */
    val analyzer = ImageAnalysis.Analyzer { image ->
        try {
            val now = SystemClock.elapsedRealtimeNanos()
            if (now - lastAtNanos < intervalMs * 1_000_000L) return@Analyzer
            lastAtNanos = now
            val jpeg = encode(image) ?: return@Analyzer
            frames.trySend(jpeg) // conflated: overwrites any unsent frame
        } finally {
            image.close() // MANDATORY — or STRATEGY_KEEP_ONLY_LATEST starves the pipeline
        }
    }

    private fun encode(image: ImageProxy): ByteArray? = runCatching {
        val src = image.toBitmap() // camera-core 1.5 YUV_420_888 -> ARGB
        val scale = maxWidth.toFloat() / src.width
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        ByteArrayOutputStream().apply { out.compress(Bitmap.CompressFormat.JPEG, quality, this) }
            .toByteArray()
    }.getOrNull()

    fun start(host: InetAddress, port: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                val s = Socket(host, port).apply { tcpNoDelay = true }
                socket = s
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                val id = deviceId.toByteArray(Charsets.UTF_8)
                out.writeInt(id.size); out.write(id); out.flush() // handshake
                Log.i(NET_TAG, "monitor: streaming as $deviceId")
                for (jpeg in frames) { // sender loop drains the conflated channel
                    out.writeInt(jpeg.size); out.write(jpeg); out.flush()
                }
            } catch (e: Exception) {
                Log.w(NET_TAG, "monitor: sender ended: ${e.message}")
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun stop() {
        runCatching { socket?.close() }
        frames.close()
    }
}

// ─────────────────────────── CONTROLLER SIDE ───────────────────────────

/** Accepts N monitor streams; hands decoded (deviceId, Bitmap) to the VM off the main thread. */
class MonitorServer(
    private val scope: CoroutineScope,
    private val onFrame: (deviceId: String, bmp: Bitmap) -> Unit,
    private val onStreamEnd: (deviceId: String) -> Unit,
) {
    private var server: ServerSocket? = null

    /** Ephemeral port advertised in WELCOME.monitorPort — mirrors TimeSyncServer.start(). */
    fun start(): Int {
        val ss = ServerSocket(0)
        server = ss
        scope.launch(Dispatchers.IO) {
            while (!ss.isClosed) {
                val sock = runCatching { ss.accept() }.getOrNull() ?: break
                sock.tcpNoDelay = true
                launch(Dispatchers.IO) { readLoop(sock) } // decode off main thread
            }
        }
        return ss.localPort
    }

    private fun readLoop(sock: Socket) {
        var deviceId = "?"
        try {
            val din = DataInputStream(BufferedInputStream(sock.getInputStream()))
            val idLen = din.readInt()
            if (idLen !in 1..256) return
            val idb = ByteArray(idLen); din.readFully(idb)
            deviceId = String(idb, Charsets.UTF_8)
            Log.i(NET_TAG, "monitor: $deviceId connected")
            while (true) {
                val len = din.readInt()
                if (len !in 1..MAX_FRAME) break
                val buf = ByteArray(len); din.readFully(buf)
                val bmp = BitmapFactory.decodeByteArray(buf, 0, len) ?: continue
                onFrame(deviceId, bmp)
            }
        } catch (e: Exception) {
            Log.w(NET_TAG, "monitor: $deviceId ended: ${e.message}")
        } finally {
            runCatching { sock.close() }
            onStreamEnd(deviceId)
        }
    }

    fun stop() = runCatching { server?.close() }
}
