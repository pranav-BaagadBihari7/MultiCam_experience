package com.multicam.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor

/**
 * CameraX capture: Preview (viewfinder, also keeps the pipeline warm so ROLL
 * start latency is low) + VideoCapture/Recorder writing full quality locally.
 *
 * Spec-honest note: recording is LOCAL ONLY — nothing streams. 1080p here;
 * HLG10 and manual controls come later via Camera2Interop per the tech spec.
 * The spec's stream-combo spike governs that upgrade, not this file.
 */
class CaptureEngine {

    val preview: Preview = Preview.Builder().build()
    private var videoCapture: VideoCapture<Recorder>? = null

    val isReady: Boolean get() = videoCapture != null

    /**
     * The S4 monitor use case — a low-res YUV stream, downscaled + JPEG'd + shipped
     * to the controller. KEEP_ONLY_LATEST so it can never stall the pipeline.
     * Bound LAST and dropped first: it is the only use case sacrificed if a device
     * rejects the three-surface combination — the master is never the thing dropped.
     */
    val imageAnalysis: ImageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                    )
                ).build()
        )
        .build()

    /** True once bind() succeeded WITH imageAnalysis; false if we fell back to master-only. */
    var monitorSupported: Boolean = false
        private set

    /** True if the local viewfinder (Preview) is bound; false if it was dropped to fit the monitor. */
    var previewBound: Boolean = true
        private set

    fun setMonitorAnalyzer(executor: Executor, analyzer: ImageAnalysis.Analyzer) =
        imageAnalysis.setAnalyzer(executor, analyzer)

    fun clearMonitorAnalyzer() = imageAnalysis.clearAnalyzer()

    fun bind(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            Quality.FHD,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD),
                        )
                    )
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                provider.unbindAll()
                val sel = CameraSelector.DEFAULT_BACK_CAMERA
                // Ladder, in priority order. The MASTER (vc) is in every rung — never dropped.
                //   1. all three: local viewfinder + master + monitor
                //   2. drop the LOCAL VIEWFINDER, keep master + monitor (the tablet is the
                //      viewfinder, so the monitor feed matters more than the phone's own preview;
                //      2-stream PRIV(RECORD)+YUV binds on virtually every device)
                //   3. master only (some device rejects even that pair) — records, no feed
                try {
                    provider.bindToLifecycle(lifecycleOwner, sel, preview, vc, imageAnalysis)
                    monitorSupported = true; previewBound = true
                } catch (e: Exception) {
                    Log.w("mcnet", "3-usecase rejected, dropping local viewfinder: ${e.message}")
                    provider.unbindAll()
                    try {
                        provider.bindToLifecycle(lifecycleOwner, sel, vc, imageAnalysis)
                        monitorSupported = true; previewBound = false
                    } catch (e2: Exception) {
                        Log.w("mcnet", "monitor+record rejected too, master-only: ${e2.message}")
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, sel, preview, vc)
                        monitorSupported = false; previewBound = true
                    }
                }
                videoCapture = vc
                onReady()
            } catch (e: Exception) {
                onError("camera bind failed: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Must be called on the main thread. Audio rides along when permitted. */
    @SuppressLint("MissingPermission") // audio permission is checked right here
    fun startRecording(
        context: Context,
        file: File,
        onEvent: (VideoRecordEvent) -> Unit,
    ): Recording? {
        val vc = videoCapture ?: return null
        val pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build())
        val withAudio = if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) pending.withAudioEnabled() else pending
        return withAudio.start(ContextCompat.getMainExecutor(context), onEvent)
    }
}
