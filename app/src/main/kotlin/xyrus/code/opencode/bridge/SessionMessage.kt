package com.xyruscode.opencode.bridge

import org.json.JSONObject

/**
 * Data class representing a WebSocket message between
 * mobile companion app and desktop opencode plugin.
 */
data class SessionMessage(
    val type: String,
    val content: String? = null,
    val command: String? = null,
    val status: String? = null,
    val message: String? = null,
    val serverTime: String? = null
) {
    companion object {
        /**
         * Parse a SessionMessage from JSON string.
         */
        fun fromJson(json: String): SessionMessage {
            val map = JSONObject(json)
            return SessionMessage(
                type = map["type"]?.toString() ?: "",
                content = map["content"]?.toString(),
                command = map["command"]?.toString(),
                status = map["status"]?.toString(),
                message = map["message"]?.toString(),
                serverTime = map["serverTime"]?.toString()
            )
        }

        /**
         * Convert SessionMessage to JSON string.
         */
        fun toJson(): String {
            val map = mutableMapOf<String, Any>()
            map["type"] = type
            content?.let { map["content"] = it }
            command?.let { map["command"] = it }
            status?.let { map["status"] = it }
            message?.let { map["message"] = it }
            serverTime?.let { map["serverTime"] = it }
            return JSONObject(map).toString()
        }
    }
}