package com.screenm.local

import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenm.ScreenCaptureService
import com.screenm.discovery.TvApiClient
import com.screenm.model.ConnectionState
import com.screenm.model.DeviceInfo
import com.screenm.signaling.LocalSignalingServer
import com.screenm.webrtc.WebRTCClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SessionManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val signalingServer = LocalSignalingServer()
    private val httpServer = LocalHttpServer(context)
    private val apiClient = TvApiClient()
    private val webRtcClient = WebRTCClient(context)

    private var signalingJob: Job? = null
    private var isStopping = false

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun startSession(
        mediaProjection: Intent,
        device: DeviceInfo,
    ) {
        if (isStopping) return
        _connectionState.value = ConnectionState.CONNECTING
        broadcastState(ConnectionState.CONNECTING)

        scope.launch {
            try {
                webRtcClient.initialize()

                val wsStarted = signalingServer.start()
                if (!wsStarted) {
                    failWith("Failed to start signaling server")
                    return@launch
                }

                val httpStarted = httpServer.start()
                if (!httpStarted) {
                    failWith("Failed to start HTTP server")
                    return@launch
                }

                val localIp =
                    httpServer.localIp ?: signalingServer.localIp ?: run {
                        failWith("No network IP found")
                        return@launch
                    }

                val wsPort = 8080
                val receiverUrl = "http://$localIp:8081/receiver.html?serverIp=$localIp&port=$wsPort"

                // Launch TV browser — try all possible methods
                val launched = launchBrowserOnTv(device.ipAddress, receiverUrl)

                if (launched) {
                    Log.i(TAG, "Browser launched on TV. Waiting for WS connection...")
                    Log.i(TAG, "If browser doesn't navigate automatically, open manually:\n$receiverUrl")
                    // Broadcast the URL so Activity can show it
                    broadcastUrl(receiverUrl)
                } else {
                    Log.w(TAG, "Could not launch browser automatically.\nOpen this URL on TV:\n$receiverUrl")
                    broadcastUrl(receiverUrl)
                }

                // Wait for TV to connect to our WebSocket
                withTimeout(60_000L) {
                    signalingServer.tvConnected.first { c -> c }
                }
                Log.i(TAG, "TV connected to WS!")

                webRtcClient.createPeerConnection(
                    onIceCandidate = { candidate ->
                        signalingServer.send(
                            "ice_candidate",
                            JSONObject().apply {
                                put("sdpMid", candidate.sdpMid)
                                put("sdpMLineIndex", candidate.sdpMLineIndex)
                                put("candidate", candidate.sdp)
                            },
                        )
                    },
                    onSdpReady = { sdp ->
                        signalingServer.send(
                            "sdp_offer",
                            JSONObject().apply {
                                put("type", sdp.type.canonicalForm())
                                put("sdp", sdp.description)
                            },
                        )
                    },
                )

                webRtcClient.startScreenCapture(mediaProjection)

                signalingJob = launch { collectSignaling() }

                launch {
                    webRtcClient.connectionState.collect { state ->
                        when (state) {
                            WebRTCClient.State.CONNECTED -> {
                                _connectionState.value = ConnectionState.STREAMING
                                broadcastState(ConnectionState.STREAMING)
                            }
                            WebRTCClient.State.FAILED -> {
                                failWith("WebRTC connection failed")
                            }
                            else -> {}
                        }
                    }
                }

                webRtcClient.createOffer()
            } catch (e: TimeoutCancellationException) {
                failWith(
                    "Timeout — open browser on TV and navigate to:\n${
                        "http://${httpServer.localIp ?: "192.168.123.40"}:8081"
                    }",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Session error: ${e.message}", e)
                failWith(e.message)
            }
        }
    }

    private suspend fun launchBrowserOnTv(
        ip: String,
        url: String,
    ): Boolean {
        // Method 1: meta_tag with action_type DEEP_LINK (various formats)
        val formats =
            listOf(
                "url=$url",
                url,
                """{"url":"$url"}""",
                "uri=$url",
            )

        val browserIds =
            listOf(
                "org.tizen.browser",
                "com.samsung.app.internet",
                "org.tizen.tizenbrowser",
            )

        for (appId in browserIds) {
            // Try each meta_tag format
            for (metaTag in formats) {
                val ok = apiClient.launchApp(ip, appId, metaTag)
                if (ok) {
                    Log.i(TAG, "Launched $appId with meta_tag=$metaTag")
                    return true
                }
            }
            // Try with uri as separate parameter
            val ok = apiClient.launchAppWithUri(ip, appId, url)
            if (ok) {
                Log.i(TAG, "Launched $appId with uri=$url")
                return true
            }
        }
        return false
    }

    private fun broadcastUrl(url: String) {
        val intent =
            Intent(ScreenCaptureService.BROADCAST_STATE).apply {
                putExtra("state", ConnectionState.CONNECTING.name)
                putExtra("url", url)
            }
        context.sendBroadcast(intent)
    }

    private fun failWith(message: String?) {
        if (isStopping) return
        _connectionState.value = ConnectionState.ERROR
        broadcastState(ConnectionState.ERROR, message ?: "Unknown error")
        stopSession()
    }

    private suspend fun collectSignaling() {
        signalingServer.messages
            .catch { e -> Log.w(TAG, "Signaling flow error: ${e.message}") }
            .collect { msg ->
                when (msg.type) {
                    "sdp_answer" -> handleSdpAnswer(msg.data)
                    "ice_candidate" -> handleIceCandidate(msg.data)
                }
            }
    }

    private fun handleSdpAnswer(data: JSONObject?) {
        if (data == null) return
        val type = data.optString("type", "")
        val sdp = data.optString("sdp", "")
        if (type.isEmpty() || sdp.isEmpty()) return
        try {
            webRtcClient.handleRemoteSdp(
                SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp),
            )
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Invalid SDP type: $type")
        }
    }

    private fun handleIceCandidate(data: JSONObject?) {
        val sdpMid = data?.optString("sdpMid", "") ?: return
        val sdpMLineIndex = data.optInt("sdpMLineIndex", 0)
        val candidate = data.optString("candidate", "") ?: return
        if (candidate.isEmpty()) return
        webRtcClient.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
    }

    fun stopSession() {
        if (isStopping) return
        isStopping = true
        signalingJob?.cancel()
        signalingJob = null
        scope.coroutineContext.cancelChildren()
        webRtcClient.stop()
        signalingServer.stop()
        httpServer.stop()
        _connectionState.value = ConnectionState.IDLE
        broadcastState(ConnectionState.IDLE)
        isStopping = false
    }

    private fun broadcastState(
        state: ConnectionState,
        error: String? = null,
    ) {
        val intent =
            Intent(ScreenCaptureService.BROADCAST_STATE).apply {
                putExtra("state", state.name)
                error?.let { putExtra("error", it) }
            }
        context.sendBroadcast(intent)
    }

    fun destroy() {
        stopSession()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SessionManager"
    }
}
