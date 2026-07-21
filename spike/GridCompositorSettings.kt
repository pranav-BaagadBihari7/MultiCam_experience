package com.spike.decoder

import androidx.media3.common.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

/**
 * 2x2 grid layout for four composited inputs.
 *
 * Lifted from Media3's own `demos/composition` sample at tag 1.10.1 so our geometry matches the
 * reference implementation exactly — if the grid looks wrong, that's a decoder or pipeline problem,
 * not our arithmetic.
 *
 * Coordinates are normalised device coordinates: the frame spans -1..1 on both axes and **y is
 * up-positive**, so +0.5 is the top half. Output is the size of input 0 (1920x1080), and each
 * quadrant renders at half scale (960x540).
 *
 * `inputId` maps to the sequence index in the order sequences are passed to `Composition.Builder`.
 *
 * Media3 carries an in-source `TODO(b/417362922)` conceding this implementation isn't fully
 * accurate. It's good enough to answer a go/no-go on decode throughput, which is all we need.
 */
@OptIn(UnstableApi::class)
class GridCompositorSettings : VideoCompositorSettings {

    override fun getOutputSize(inputSizes: List<Size>): Size = inputSizes[0]

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        val quadrant = when (inputId) {
            0 -> -0.5f to 0.5f    // top-left
            1 -> 0.5f to 0.5f     // top-right
            2 -> -0.5f to -0.5f   // bottom-left
            3 -> 0.5f to -0.5f    // bottom-right
            else -> return StaticOverlaySettings.Builder().build()
        }
        return StaticOverlaySettings.Builder()
            .setScale(0.5f, 0.5f)
            .setOverlayFrameAnchor(0f, 0f) // anchor at the overlay's own centre
            .setBackgroundFrameAnchor(quadrant.first, quadrant.second)
            .build()
    }
}
