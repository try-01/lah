package com.tvhanan.data.repository

import com.tvhanan.data.local.TvPreferences
import com.tvhanan.data.network.AppLauncher
import com.tvhanan.data.network.TvDiscoveryService
import com.tvhanan.data.network.TvWebSocketClient
import com.tvhanan.data.network.WakeOnLanUtil
import com.tvhanan.domain.model.ConnectionState
import com.tvhanan.domain.model.RemoteKey
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.domain.repository.TvRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class TvRepositoryImpl(
    private val discoveryService: TvDiscoveryService,
    private val webSocketClient: TvWebSocketClient,
    private val preferences: TvPreferences
) : TvRepository {

    override val lastIp: Flow<String?> = preferences.lastIp
    override val lastPort: Flow<String?> = preferences.lastPort
    override val macAddress: Flow<String?> = preferences.macAddress

    override val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState
    override val tokenReceived: Flow<String?> = webSocketClient.tokenReceived

    override suspend fun getToken(): String? = preferences.getToken()

    override suspend fun saveToken(token: String) = preferences.saveToken(token)

    override suspend fun saveLastIp(ip: String) = preferences.saveLastIp(ip)

    override suspend fun saveLastPort(port: String) = preferences.saveLastPort(port)

    override suspend fun saveMacAddress(mac: String) = preferences.saveMacAddress(mac)

    override suspend fun clearPreferences() = preferences.clear()

    override suspend fun discoverDevices(): List<TvDevice> = discoveryService.discoverDevices()

    override suspend fun isHostReachable(ip: String, port: Int): Boolean =
        discoveryService.isHostReachable(ip, port)

    override suspend fun connectWithFallback(ip: String, token: String?): Result<Unit> {
        return webSocketClient.connectWithFallback(ip, token).map { }
    }

    override fun sendKey(key: RemoteKey): Boolean = webSocketClient.sendKey(key)

    override fun disconnect() = webSocketClient.disconnect()

    override suspend fun wakeOnLan(mac: String, broadcastIp: String): Boolean =
        WakeOnLanUtil.sendWakeOnLanWithRetry(mac, broadcastIp)

    override suspend fun launchApp(ip: String, appId: String): Boolean =
        AppLauncher.launch(ip, appId)

    override suspend fun closeApp(ip: String, appId: String): Boolean =
        AppLauncher.close(ip, appId)
}
