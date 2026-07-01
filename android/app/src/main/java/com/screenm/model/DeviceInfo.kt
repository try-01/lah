package com.screenm.model

data class DeviceInfo(
    val id: String,
    val name: String,
    val ipAddress: String,
    val tizenVersion: String = "",
    val port: Int = 0
) {
    val isGoogleCastCapable: Boolean
        get() = tizenVersion.isNotEmpty() && parseVersion(tizenVersion) >= 8

    private fun parseVersion(v: String): Int {
        return v.split(".").firstOrNull()?.toIntOrNull() ?: 0
    }
}

enum class ConnectionState {
    IDLE,
    CONNECTING,
    STREAMING,
    ERROR
}
