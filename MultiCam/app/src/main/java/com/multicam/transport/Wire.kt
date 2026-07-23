package com.multicam.transport

import org.json.JSONObject

/**
 * The wire protocol, in full. Newline-delimited JSON over TCP for control;
 * fixed-layout binary over UDP for time sync (see TimeSync.kt — text is too
 * slow to stamp accurately there).
 *
 * Deliberately tiny: every message type the tech spec's session state machine
 * needs for S1. ROLL/STOP arrive in S2 as scheduled-time commands.
 */
const val SERVICE_TYPE = "_multicam._tcp."

sealed class Msg {
    /** camera -> controller, first message after connect */
    data class Hello(val deviceId: String, val name: String) : Msg()

    /** controller -> camera, reply to Hello. Carries the UDP time-sync port. */
    data class Welcome(val sessionId: String, val timeSyncPort: Int) : Msg()

    fun toJson(): String = when (this) {
        is Hello -> JSONObject().put("type", "HELLO")
            .put("deviceId", deviceId).put("name", name)
        is Welcome -> JSONObject().put("type", "WELCOME")
            .put("sessionId", sessionId).put("timeSyncPort", timeSyncPort)
    }.toString()

    companion object {
        fun parse(line: String): Msg? = try {
            val o = JSONObject(line)
            when (o.getString("type")) {
                "HELLO" -> Hello(o.getString("deviceId"), o.getString("name"))
                "WELCOME" -> Welcome(o.getString("sessionId"), o.getInt("timeSyncPort"))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
