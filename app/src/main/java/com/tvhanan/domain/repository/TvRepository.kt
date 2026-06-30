package com.tvhanan.domain.repository

import com.tvhanan.domain.model.ConnectionState
import com.tvhanan.domain.model.RemoteKey
import com.tvhanan.domain.model.TvDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TvRepository {
    val lastIp: Flow<String?>
    val lastPort: Flow<String?>
    val macAddress: Flow<String?>

    val connectionState: StateFlow<ConnectionState>
    val tokenReceived: Flow<String?>

    suspend fun getToken(): String?
    suspend fun saveToken(token: String)
    suspend fun saveLastIp(ip: String)
    suspend fun saveLastPort(port: String)
    suspend fun saveMacAddress(mac: String)
    suspend fun clearPreferences()

    suspend fun discoverDevices(): List<TvDevice>
    suspend fun isHostReachable(ip: String, port: Int): Boolean

    suspend fun connectWithFallback(ip: String, token: String? = null): Result<Unit>
    fun sendKey(key: RemoteKey): Boolean
    fun disconnect()

    suspend fun wakeOnLan(mac: String, broadcastIp: String = "255.255.255.255"): Boolean
    suspend fun launchApp(ip: String, appId: String): Boolean
    suspend fun closeApp(ip: String, appId: String): Boolean
}
