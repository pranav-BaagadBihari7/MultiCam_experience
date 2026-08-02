package com.multicam.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, vc,
                )
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
