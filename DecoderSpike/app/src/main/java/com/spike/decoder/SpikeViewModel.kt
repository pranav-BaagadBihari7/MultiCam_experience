package com.spike.decoder

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MultipleInputVideoGraph
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import com.spike.decoder.DeviceCapabilities.format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The spike.
 *
 * Plays four 1080p clips in a 2x2 grid via CompositionPlayer for 60s and records what happens.
 * Deliberately shaped around three open Media3 bugs — see spike/README.md before "fixing" anything:
 *   - no seeking            (androidx/media#2439: crashes in grid mode)
 *   - one item per sequence (androidx/media#2742: compositor deadlock on transitions)
 *   - video track only      (androidx/media#2854: freeze when audio declared but absent)
 */
@OptIn(UnstableApi::class, ExperimentalApi::class)
class SpikeViewModel(app: Application) : AndroidViewModel(app) {

    data class State(
        val status: String = "idle",
        val elapsedSec: Int = 0,
        val droppedFrames: Int = 0,
        val dropEvents: Int = 0,
        val thermal: String = "-",
        val worstThermal: String = "NONE",
        val error: String? = null,
        val capabilities: String = "",
        val finished: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    var player: CompositionPlayer? = null
        private set

    private val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    init {
        _state.value = _state.value.copy(
            capabilities = with(DeviceCapabilities) { DeviceCapabilities.collect(app).format() }
        )
    }

    fun start() {
        val app = getApplication<Application>()
        val dir = app.getExternalFilesDir(null)
        val clips = (1..4).map { File(dir, "clip$it.mp4") }
        val missing = clips.filterNot { it.exists() }
        if (missing.isNotEmpty()) {
            _state.value = _state.value.copy(
                error = "Missing ${missing.size} clip(s). Push them with:\n" +
                    "adb push clip.mp4 ${dir?.absolutePath}/clip1.mp4   (and clip2..4)",
            )
            return
        }

        release()

        // Four sequences, one clip each. inputId in GridCompositorSettings maps to this order.
        // Video track only: isolates decode throughput and dodges the no-audio freeze.
        val sequences = clips.map { file ->
            EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_VIDEO))
                .addItem(EditedMediaItem.Builder(MediaItem.fromUri(file.toURI().toString())).build())
                .build()
        }

        val composition = Composition.Builder(sequences)
            .setVideoCompositorSettings(GridCompositorSettings())
            .build()

        val p = CompositionPlayer.Builder(app)
            // MANDATORY for multi-sequence compositing. Without it you silently get the
            // single-input graph and the grid never composites.
            .setVideoGraphFactory(MultipleInputVideoGraph.Factory())
            .build()

        p.addAnalyticsListener(object : AnalyticsListener {
            // b/451741691: drops from all four sequences are aggregated here. We cannot attribute a
            // drop to a specific angle — fine for go/no-go, a real limitation later.
            override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                _state.value = _state.value.copy(
                    droppedFrames = _state.value.droppedFrames + droppedFrames,
                    dropEvents = _state.value.dropEvents + 1,
                )
            }
        })

        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _state.value = _state.value.copy(
                    error = "${error.errorCodeName}: ${error.message}",
                    status = "FAILED",
                    finished = true,
                )
            }
        })

        player = p
        p.setComposition(composition)
        p.prepare()
        p.play()

        _state.value = _state.value.copy(status = "running", error = null, droppedFrames = 0, dropEvents = 0, finished = false)
        runClock()
    }

    private fun runClock() = viewModelScope.launch {
        val order = listOf("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
        for (sec in 1..60) {
            delay(1000)
            if (_state.value.finished) return@launch
            val t = thermalName()
            val worst = if (order.indexOf(t) > order.indexOf(_state.value.worstThermal)) t else _state.value.worstThermal
            _state.value = _state.value.copy(elapsedSec = sec, thermal = t, worstThermal = worst)
        }
        player?.pause()
        _state.value = _state.value.copy(status = "complete", finished = true)
    }

    private fun thermalName(): String = when (powerManager.currentThermalStatus) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "unknown"
    }

    /** 60s x 30fps x 4 inputs = 7200 frames. Drops as a share of that is the number that matters. */
    fun results(): String {
        val s = _state.value
        val expected = 60 * 30 * 4
        val pct = if (expected > 0) s.droppedFrames * 100.0 / expected else 0.0
        val green = s.error == null && pct < 1.0 && s.worstThermal in listOf("NONE", "LIGHT")
        return buildString {
            appendLine("=== DECODER SPIKE: 4x 1080p H.264, 2x2 grid, CompositionPlayer, Media3 1.10.1 ===")
            appendLine()
            appendLine("RESULT            : ${if (s.error != null) "FAILED" else if (green) "GREEN" else "RED"}")
            appendLine("  ran for         : ${s.elapsedSec}s of 60s")
            appendLine("  dropped frames  : ${s.droppedFrames} (${"%.2f".format(pct)}% of ~$expected expected)")
            appendLine("  drop events     : ${s.dropEvents}")
            appendLine("  worst thermal   : ${s.worstThermal}")
            s.error?.let { appendLine("  error           : $it") }
            appendLine()
            appendLine(s.capabilities)
            appendLine("Note: drop counts are aggregated across all 4 inputs (b/451741691).")
        }
    }

    fun release() {
        player?.release()
        player = null
    }

    override fun onCleared() {
        release()
        super.onCleared()
    }
}
