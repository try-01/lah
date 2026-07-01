package com.screenm.model

data class DeviceInfo(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String = "",
    val modelName: String = "",
    val tizenVersion: String = "",
    val port: Int = 8001,
)

enum class ConnectionState {
    IDLE,
    CONNECTING,
    STREAMING,
    ERROR,
}
