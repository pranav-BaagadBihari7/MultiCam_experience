package com.multicam.transport

import org.json.JSONObject

/**
 * The wire protocol, in full. Newline-delimited JSON over TCP for control;
 * fixed-layout binary over UDP for time sync (see TimeSync.kt).
 *
 * S2 adds the take lifecycle: ROLL carries a *future* start instant in
 * session-clock nanos — every camera translates it to its local clock via its
 * learned offset and fires at the same physical moment. STOP is immediate.
 * TAKE_STATUS flows camera -> controller so the director sees every angle's
 * actual behavior (how far from T it started, where the file landed).
 */
const val SERVICE_TYPE = "_multicam._tcp."

sealed class Msg {
    /** camera -> controller, first message after connect */
    data class Hello(val deviceId: String, val name: String) : Msg()

    /** controller -> camera, reply to Hello. Carries the UDP time-sync port and the TCP monitor port. */
    data class Welcome(val sessionId: String, val timeSyncPort: Int, val monitorPort: Int) : Msg()

    /** controller -> all cameras: start recording at this session-clock instant. */
    data class Roll(val takeId: String, val startAtSessionNanos: Long) : Msg()

    /** controller -> all cameras: stop the named take now. */
    data class Stop(val takeId: String) : Msg()

    /** camera -> controller: RECORDING / SAVED / ERROR + human-readable detail. */
    data class TakeStatus(
        val deviceId: String,
        val takeId: String,
        val state: String,
        val detail: String,
    ) : Msg()

    /**
     * camera -> controller: mono 16 kHz PCM fingerprint of the take's first
     * seconds (base64 LE shorts), stamped with the session-time of its first
     * sample. S3's cross-correlation input — footage itself never ships.
     */
    data class AudioSnippet(
        val deviceId: String,
        val takeId: String,
        val sampleRate: Int,
        val startSessionNanos: Long,
        val pcmBase64: String,
    ) : Msg()

    fun toJson(): String = when (this) {
        is Hello -> JSONObject().put("type", "HELLO")
            .put("deviceId", deviceId).put("name", name)
        is Welcome -> JSONObject().put("type", "WELCOME")
            .put("sessionId", sessionId).put("timeSyncPort", timeSyncPort).put("monitorPort", monitorPort)
        is Roll -> JSONObject().put("type", "ROLL")
            .put("takeId", takeId).put("startAtSessionNanos", startAtSessionNanos)
        is Stop -> JSONObject().put("type", "STOP").put("takeId", takeId)
        is TakeStatus -> JSONObject().put("type", "TAKE_STATUS")
            .put("deviceId", deviceId).put("takeId", takeId)
            .put("state", state).put("detail", detail)
        is AudioSnippet -> JSONObject().put("type", "AUDIO_SNIPPET")
            .put("deviceId", deviceId).put("takeId", takeId)
            .put("sampleRate", sampleRate).put("startSessionNanos", startSessionNanos)
            .put("pcmBase64", pcmBase64)
    }.toString()

    companion object {
        fun parse(line: String): Msg? = try {
            val o = JSONObject(line)
            when (o.getString("type")) {
                "HELLO" -> Hello(o.getString("deviceId"), o.getString("name"))
                "WELCOME" -> Welcome(o.getString("sessionId"), o.getInt("timeSyncPort"), o.getInt("monitorPort"))
                "ROLL" -> Roll(o.getString("takeId"), o.getLong("startAtSessionNanos"))
                "STOP" -> Stop(o.getString("takeId"))
                "TAKE_STATUS" -> TakeStatus(
                    o.getString("deviceId"), o.getString("takeId"),
                    o.getString("state"), o.getString("detail"),
                )
                "AUDIO_SNIPPET" -> AudioSnippet(
                    o.getString("deviceId"), o.getString("takeId"),
                    o.getInt("sampleRate"), o.getLong("startSessionNanos"),
                    o.getString("pcmBase64"),
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
