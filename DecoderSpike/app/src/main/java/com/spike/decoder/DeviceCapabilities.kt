package com.spike.decoder

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * The four values that decide what this product can be, read off the actual silicon.
 *
 * Constants for camera values live on [CameraMetadata], not [CameraCharacteristics] — the keys and
 * the values are on different classes. Referencing them explicitly rather than relying on inherited
 * Java statics resolving through Kotlin.
 */
object DeviceCapabilities {

    data class CodecInstances(val codecName: String, val mime: String, val hardware: Boolean, val maxInstances: Int)

    data class Report(
        val device: String,
        val soc: String,
        val androidVersion: String,
        val hardwareLevel: String,
        val tenBitCapable: Boolean,
        val timestampSource: String,
        val timestampSourceIsRealtime: Boolean,
        val codecs: List<CodecInstances>,
    )

    fun collect(context: Context): Report {
        val cam = readCamera(context)
        return Report(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "unknown (needs API 31+)",
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            hardwareLevel = cam.first,
            tenBitCapable = cam.second,
            timestampSource = cam.third,
            timestampSourceIsRealtime = cam.third == "REALTIME",
            codecs = readCodecs(),
        )
    }

    /** Returns (hardwareLevel, tenBitCapable, timestampSource) for the first back-facing camera. */
    private fun readCamera(context: Context): Triple<String, Boolean, String> {
        return try {
            val mgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                    CameraMetadata.LENS_FACING_BACK
            } ?: mgr.cameraIdList.firstOrNull() ?: return Triple("no camera", false, "no camera")

            val ch = mgr.getCameraCharacteristics(id)

            // NB: the numeric ordering is NOT a quality ranking — LEGACY is 2, above FULL at 1.
            val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
                CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
                else -> "unknown"
            }

            // Ten-bit is a capability *value* inside an int[], not a key of its own.
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            val tenBit = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT)

            val ts = when (ch.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE)) {
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME -> "REALTIME"
                CameraMetadata.SENSOR_INFO_TIMESTAMP_SOURCE_UNKNOWN -> "UNKNOWN"
                else -> "unreadable"
            }

            Triple(level, tenBit, ts)
        } catch (t: Throwable) {
            Triple("error: ${t.message}", false, "error")
        }
    }

    /**
     * Advertised decoder instance ceilings. Treat as an upper bound only — hardware shares a decoder
     * budget across instances and resolution, so the real concurrent-1080p limit is routinely lower.
     * Measuring the actual limit is what the grid playback is for.
     */
    private fun readCodecs(): List<CodecInstances> = try {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
            .filter { !it.isEncoder && (Build.VERSION.SDK_INT < 29 || !it.isAlias) }
            .flatMap { info ->
                info.supportedTypes
                    .filter {
                        it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) ||
                            it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, true)
                    }
                    .mapNotNull { mime ->
                        runCatching {
                            CodecInstances(
                                codecName = info.name,
                                mime = mime,
                                hardware = Build.VERSION.SDK_INT >= 29 && info.isHardwareAccelerated,
                                maxInstances = info.getCapabilitiesForType(mime).maxSupportedInstances,
                            )
                        }.getOrNull()
                    }
            }
            .sortedByDescending { it.hardware }
    } catch (t: Throwable) {
        emptyList()
    }

    fun Report.format(): String = buildString {
        appendLine("DEVICE CAPABILITIES")
        appendLine("  device            : $device")
        appendLine("  soc               : $soc")
        appendLine("  android           : $androidVersion")
        appendLine("  camera hw level   : $hardwareLevel${if (hardwareLevel != "FULL" && hardwareLevel != "LEVEL_3") "   <-- manual ISO/shutter NOT guaranteed" else ""}")
        appendLine("  10-bit (HLG10)    : $tenBitCapable${if (!tenBitCapable) "   <-- no 10-bit HDR path on this device" else ""}")
        appendLine("  timestamp source  : $timestampSource${if (!timestampSourceIsRealtime) "   <-- FATAL: cross-device sync impossible here" else ""}")
        appendLine()
        appendLine("DECODER CEILINGS (advertised — upper bound, not a guarantee)")
        if (codecs.isEmpty()) appendLine("  none readable")
        codecs.forEach {
            appendLine("  ${it.codecName}  ${it.mime}  hw=${it.hardware}  maxInstances=${it.maxInstances}")
        }
    }
}
