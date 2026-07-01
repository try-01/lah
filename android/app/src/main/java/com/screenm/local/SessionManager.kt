package com.screenm.local

import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenm.ScreenCaptureService
import com.screenm.discovery.TvApiClient
import com.screenm.model.ConnectionState
import com.screenm.model.DeviceInfo
import com.screenm.signaling.LocalSignalingServer
import com.screenm.signaling.SignalingMessage
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
    private val apiClient = TvApiClient()
    private val webRtcClient = WebRTCClient(context)

    private var signalingJob: Job? = null
    private var isStopping = false

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun startSession(mediaProjection: Intent, device: DeviceInfo) {
        if (isStopping) return
        _connectionState.value = ConnectionState.CONNECTING
        broadcastState(ConnectionState.CONNECTING)

        scope.launch {
            try {
                webRtcClient.initialize()

                val serverStarted = signalingServer.start()
                if (!serverStarted) {
                    failWith("Failed to start signaling server")
                    return@launch
                }

                val localIp = signalingServer.localIp ?: run {
                    failWith("No network IP found")
                    return@launch
                }

                // Launch Tizen app via REST API with Android IP
                val launched = apiClient.launchApp(device.ipAddress, TIZEN_APP_ID, JSONObject().apply {
                    put("serverIp", localIp)
                    put("serverPort", 8080)
                })
                if (!launched) {
                    Log.w(TAG, "REST launch failed, TV app may need manual launch")
                }

                // Wait for TV to connect to our WebSocket
                withTimeout(30_000L) {
                    signalingServer.tvConnected.first { c -> c }
                }

                webRtcClient.createPeerConnection(
                    onIceCandidate = { candidate ->
                        signalingServer.send("ice_candidate", JSONObject().apply {
                            put("sdpMid", candidate.sdpMid)
                            put("sdpMLineIndex", candidate.sdpMLineIndex)
                            put("candidate", candidate.sdp)
                        })
                    },
                    onSdpReady = { sdp ->
                        signalingServer.send("sdp_offer", JSONObject().apply {
                            put("type", sdp.type.canonicalForm())
                            put("sdp", sdp.description)
                        })
                    }
                )

                webRtcClient.startScreenCapture(mediaProjection)

                signalingJob = launch { collectSignaling() }

                // Monitor WebRTC connection state
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
                failWith("TV connection timeout")
            } catch (e: Exception) {
                Log.e(TAG, "Session error: ${e.message}", e)
                failWith(e.message)
            }
        }
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
                SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
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
        _connectionState.value = ConnectionState.IDLE
        broadcastState(ConnectionState.IDLE)
        isStopping = false
    }

    private fun broadcastState(state: ConnectionState, error: String? = null) {
        val intent = Intent(ScreenCaptureService.BROADCAST_STATE).apply {
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
        private const val TIZEN_APP_ID = "screenm.Receiver"
    }
}
