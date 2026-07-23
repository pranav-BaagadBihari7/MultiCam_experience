package com.multicam.sync

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

/**
 * NTP-style clock agreement over UDP — the spec's §4.5 default sync path,
 * SNTP leg. This is what makes "one shared clock across 4 phones" true.
 *
 * Clock: SystemClock.elapsedRealtimeNanos() everywhere. Monotonic, ticks in
 * deep sleep, immune to the user changing wall-clock time — and it's the same
 * timebase Camera2 stamps frames with on SENSOR_INFO_TIMESTAMP_SOURCE==REALTIME
 * devices, which is exactly why the spec gates multicam on that value.
 *
 * Exchange (all int64 nanos, fixed layout — binary because JSON serialization
 * jitter would pollute the very timestamps we're measuring):
 *   camera  -> controller : [seq][t1]              t1 = camera send time
 *   controller -> camera  : [seq][t1][t2][t3]      t2 = server recv, t3 = server send
 *   camera stamps t4 on receive.
 *
 *   offset = ((t2-t1)+(t3-t4))/2      controllerClock - cameraClock
 *   rtt    = (t4-t1)-(t3-t2)
 *
 * Estimation: keep a sliding window, use only samples whose RTT is within
 * 1.5x the window minimum (asymmetric-delay outliers poison offset), take the
 * median offset. Uncertainty is +-rttMin/2 — the theoretical bound.
 * Clock drift is ~1.2 ms/min against a 33 ms frame, so a continuous 5 Hz
 * exchange keeps us orders of magnitude inside sub-frame territory.
 */
data class ClockEstimate(
    val offsetNanos: Long,        // controllerClock - cameraClock
    val rttMinNanos: Long,        // best observed round trip
    val sampleCount: Int,
) {
    val uncertaintyNanos: Long get() = rttMinNanos / 2
    fun sessionNanos(localElapsedNanos: Long = SystemClock.elapsedRealtimeNanos()): Long =
        localElapsedNanos + offsetNanos
}

private const val PACKET_IN = 16   // [seq][t1]
private const val PACKET_OUT = 32  // [seq][t1][t2][t3]

/** Controller side: echo timestamps. Runs until stop(). */
class TimeSyncServer(private val scope: CoroutineScope) {
    private var socket: DatagramSocket? = null

    /** Binds an ephemeral UDP port and returns it — sent to cameras in WELCOME. */
    fun start(): Int {
        val s = DatagramSocket(0)
        socket = s
        scope.launch(Dispatchers.IO) {
            val buf = ByteArray(PACKET_OUT)
            val packet = DatagramPacket(buf, buf.size)
            while (!s.isClosed) {
                try {
                    s.receive(packet)
                    val t2 = SystemClock.elapsedRealtimeNanos()
                    if (packet.length < PACKET_IN) continue
                    val bb = ByteBuffer.wrap(buf)
                    val seq = bb.getLong(0)
                    val t1 = bb.getLong(8)
                    val out = ByteBuffer.allocate(PACKET_OUT)
                    out.putLong(seq).putLong(t1).putLong(t2)
                        .putLong(SystemClock.elapsedRealtimeNanos()) // t3, stamped last
                    s.send(DatagramPacket(out.array(), PACKET_OUT, packet.address, packet.port))
                } catch (_: Exception) {
                    if (s.isClosed) break
                }
            }
        }
        return s.localPort
    }

    fun stop() = runCatching { socket?.close() }
}

/** Camera side: continuous exchange loop, publishes the running estimate. */
class TimeSyncClient(private val scope: CoroutineScope) {

    private val _estimate = MutableStateFlow<ClockEstimate?>(null)
    val estimate: StateFlow<ClockEstimate?> = _estimate

    private var socket: DatagramSocket? = null
    private val window = ArrayDeque<Pair<Long, Long>>() // (offset, rtt), newest last
    private val windowSize = 64

    fun start(host: InetAddress, port: Int, intervalMs: Long = 200) {
        val s = DatagramSocket().apply { soTimeout = 500 }
        socket = s
        scope.launch(Dispatchers.IO) {
            var seq = 0L
            val inBuf = ByteArray(PACKET_OUT)
            while (!s.isClosed) {
                try {
                    seq++
                    val out = ByteBuffer.allocate(PACKET_IN)
                    out.putLong(seq).putLong(SystemClock.elapsedRealtimeNanos()) // t1
                    s.send(DatagramPacket(out.array(), PACKET_IN, host, port))

                    val reply = DatagramPacket(inBuf, inBuf.size)
                    s.receive(reply)
                    val t4 = SystemClock.elapsedRealtimeNanos()
                    if (reply.length < PACKET_OUT) continue
                    val bb = ByteBuffer.wrap(inBuf)
                    if (bb.getLong(0) != seq) continue // stale reply from a timed-out round
                    val t1 = bb.getLong(8)
                    val t2 = bb.getLong(16)
                    val t3 = bb.getLong(24)

                    val offset = ((t2 - t1) + (t3 - t4)) / 2
                    val rtt = (t4 - t1) - (t3 - t2)
                    addSample(offset, rtt)
                } catch (_: SocketTimeoutException) {
                    // lost packet; keep looping
                } catch (_: Exception) {
                    if (s.isClosed) break
                }
                try {
                    Thread.sleep(intervalMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
    }

    private fun addSample(offset: Long, rtt: Long) {
        window.addLast(offset to rtt)
        if (window.size > windowSize) window.removeFirst()

        val rttMin = window.minOf { it.second }
        // Asymmetric network delay shows up as high RTT; those samples' offsets lie.
        val good = window.filter { it.second <= rttMin + rttMin / 2 }
        if (good.isEmpty()) return
        val median = good.map { it.first }.sorted()[good.size / 2]
        _estimate.value = ClockEstimate(median, rttMin, good.size)
    }

    fun stop() = runCatching { socket?.close() }
}
