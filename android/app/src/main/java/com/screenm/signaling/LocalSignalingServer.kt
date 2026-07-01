package com.screenm.signaling

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface

class LocalSignalingServer(private val port: Int = 8080) {
    private var server: WebSocketServer? = null
    private var tvConnection: WebSocket? = null

    private val _messages = Channel<SignalingMessage>(Channel.BUFFERED)
    val messages: Flow<SignalingMessage> = _messages.receiveAsFlow()

    private val _tvConnected = MutableStateFlow(false)
    val tvConnected: StateFlow<Boolean> = _tvConnected.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val localIp: String? by lazy { findLocalIp() }

    suspend fun start(): Boolean =
        withContext(Dispatchers.IO) {
            if (server != null) return@withContext true
            try {
                server =
                    object : WebSocketServer(InetSocketAddress(port)) {
                        override fun onOpen(
                            conn: WebSocket,
                            handshake: ClientHandshake,
                        ) {
                            Log.i(TAG, "TV connected: ${conn.remoteSocketAddress}")
                            if (tvConnection != null) {
                                tvConnection?.close()
                            }
                            tvConnection = conn
                            _tvConnected.value = true
                        }

                        override fun onMessage(
                            conn: WebSocket,
                            message: String,
                        ) {
                            try {
                                val json = JSONObject(message)
                                val type = json.optString("type", "")
                                val data = json.optJSONObject("data")
                                _messages.trySend(SignalingMessage(type, data))
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid message: ${e.message}")
                            }
                        }

                        override fun onClose(
                            conn: WebSocket,
                            code: Int,
                            reason: String,
                            remote: Boolean,
                        ) {
                            Log.i(TAG, "TV disconnected: $reason")
                            tvConnection = null
                            _tvConnected.value = false
                        }

                        override fun onError(
                            conn: WebSocket?,
                            ex: Exception,
                        ) {
                            Log.e(TAG, "WS error: ${ex.message}")
                        }

                        override fun onStart() {
                            Log.i(TAG, "WS server started on port $port")
                            _isRunning.value = true
                        }
                    }
                server?.setReuseAddr(true)
                server?.start()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WS server: ${e.message}")
                false
            }
        }

    fun send(
        type: String,
        data: JSONObject? = null,
    ) {
        val msg =
            JSONObject().apply {
                put("type", type)
                data?.let { put("data", it) }
            }
        tvConnection?.send(msg.toString())
    }

    fun stop() {
        try {
            server?.stop(1000)
        } catch (_: Exception) {
        }
        server = null
        tvConnection = null
        _tvConnected.value = false
        _isRunning.value = false
    }

    private fun findLocalIp(): String? {
        try {
            var candidate: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (network in interfaces) {
                if (network.isLoopback || !network.isUp) continue
                val addresses = network.inetAddresses ?: continue
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && ip.startsWith("192.168.")) return ip
                        if (candidate == null) candidate = ip
                    }
                }
            }
            return candidate
        } catch (_: Exception) {
        }
        return null
    }

    companion object {
        private const val TAG = "SignalingServer"
    }
}

data class SignalingMessage(
    val type: String,
    val data: JSONObject? = null,
)
