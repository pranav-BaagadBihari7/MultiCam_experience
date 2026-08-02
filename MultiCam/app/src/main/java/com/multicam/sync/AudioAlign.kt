package com.multicam.sync

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File

/**
 * S3: seal frame-accuracy with sound.
 *
 * The clock (S1) and scheduled roll (S2) get every take aligned to within
 * milliseconds; this module measures the RESIDUAL error by cross-correlating
 * the takes' audio. Sidecar timestamps pre-align the signals, so the search
 * is a narrow +-500 ms - the spec's "SNTP-primed" GCC-style pass - and the
 * result is the exact per-clip correction plus a confidence score.
 *
 * Footage never crosses the network: each camera ships only a 12 s mono
 * 16 kHz fingerprint (~380 KB) extracted from its own file.
 */
object AudioExtract {

    const val TARGET_RATE = 16_000

    /** Decode the file's audio track to mono 16 kHz PCM, at most [maxSeconds]. */
    fun extractMono16k(file: File, maxSeconds: Int = 12): ShortArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIdx = i; format = f; break
                }
            }
            if (trackIdx < 0 || format == null) return null
            extractor.selectTrack(trackIdx)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val maxUs = maxSeconds * 1_000_000L

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var pcm = ShortArray(0)
            var pcmLen = 0
            fun append(chunk: ShortArray, len: Int) {
                if (pcmLen + len > pcm.size) pcm = pcm.copyOf(maxOf(pcm.size * 2, pcmLen + len))
                System.arraycopy(chunk, 0, pcm, pcmLen, len)
                pcmLen += len
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0 || extractor.sampleTime > maxUs) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val out = codec.getOutputBuffer(outIdx)!!
                        val shorts = ShortArray(info.size / 2)
                        out.position(info.offset)
                        out.asShortBuffer().get(shorts, 0, shorts.size)
                        append(shorts, shorts.size)
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            codec.stop(); codec.release()

            // Downmix to mono, resample to 16 kHz by stepped nearest-sample pick.
            val frames = pcmLen / channels
            if (frames == 0) return null
            val step = rate.toDouble() / TARGET_RATE
            val outLen = (frames / step).toInt()
            val mono = ShortArray(outLen)
            var pos = 0.0
            for (i in 0 until outLen) {
                val f = pos.toInt().coerceAtMost(frames - 1)
                var acc = 0
                for (c in 0 until channels) acc += pcm[f * channels + c]
                mono[i] = (acc / channels).toShort()
                pos += step
            }
            return mono
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }
}

object Xcorr {

    data class Result(
        val shiftMicros: Long,   // positive = other's audio is LATE relative to ref
        val peak: Double,        // normalized correlation at the peak, 0..1
    )

    /**
     * Coarse-to-fine normalized cross-correlation at 16 kHz.
     * Coarse: 1 ms steps over +-maxShiftMs on 16x-decimated signals.
     * Fine: 62.5 us steps in a +-2 ms window around the coarse peak.
     */
    fun align(ref: ShortArray, other: ShortArray, maxShiftMs: Int = 500): Result {
        val decim = 16
        val refC = decimate(ref, decim)
        val otherC = decimate(other, decim)
        val coarseRate = AudioExtract.TARGET_RATE / decim // 1000 Hz -> 1 sample = 1 ms

        var bestShift = 0
        var bestScore = -2.0
        for (s in -maxShiftMs..maxShiftMs) {
            val score = ncc(refC, otherC, s * coarseRate / 1000)
            if (score > bestScore) { bestScore = score; bestShift = s }
        }

        // Fine pass at full rate around the coarse peak.
        val center = bestShift * AudioExtract.TARGET_RATE / 1000
        var bestFine = center
        var bestFineScore = -2.0
        for (s in (center - 32)..(center + 32)) {
            val score = ncc(ref, other, s)
            if (score > bestFineScore) { bestFineScore = score; bestFine = s }
        }

        return Result(
            shiftMicros = bestFine * 1_000_000L / AudioExtract.TARGET_RATE,
            peak = bestFineScore,
        )
    }

    private fun decimate(x: ShortArray, factor: Int): ShortArray {
        val out = ShortArray(x.size / factor)
        for (i in out.indices) {
            var acc = 0
            val base = i * factor
            for (j in 0 until factor) acc += x[base + j]
            out[i] = (acc / factor).toShort()
        }
        return out
    }

    /** Normalized cross-correlation of ref vs other shifted by [shift] samples. */
    private fun ncc(ref: ShortArray, other: ShortArray, shift: Int): Double {
        val start = maxOf(0, shift)
        val end = minOf(ref.size, other.size + shift)
        val n = end - start
        if (n < AudioExtract.TARGET_RATE / 16) return -2.0 // <62ms overlap: meaningless
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in start until end) {
            val a = ref[i].toDouble()
            val b = other[i - shift].toDouble()
            dot += a * b; na += a * a; nb += b * b
        }
        if (na == 0.0 || nb == 0.0) return -2.0
        return dot / Math.sqrt(na * nb)
    }
}
