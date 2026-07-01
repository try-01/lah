package com.screenm.local

import android.content.Context
import android.content.Intent
import android.util.Log
import com.screenm.ScreenCaptureService
import com.screenm.model.ConnectionState
import com.screenm.signaling.LocalSignalingServer
import com.screenm.webrtc.WebRTCClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class SessionManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val signalingServer = LocalSignalingServer()
    private val webRtcClient = WebRTCClient(context)

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var signalingJob: Job? = null
    private var isStopping = false

    fun startSession(mediaProjection: Intent, targetIp: String) {
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

                // Wait for TV to connect to our WebSocket
                withTimeout(30_000L) {
                    signalingServer.tvConnected.first { it }
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

                // Tell TV our IP so it can start the WebRTC flow
                signalingServer.send("start", JSONObject().apply {
                    put("serverIp", localIp)
                    put("serverPort", 8080)
                })

                // Collect signaling messages in background
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

                // Create the SDP offer
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
        val type = data?.optString("type") ?: return
        val sdp = data.optString("sdp") ?: return
        webRtcClient.handleRemoteSdp(
            SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
        )
    }

    private fun handleIceCandidate(data: JSONObject?) {
        val sdpMid = data?.optString("sdpMid") ?: return
        val sdpMLineIndex = data.optInt("sdpMLineIndex")
        val candidate = data.optString("candidate") ?: return
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
        scope.cancel()
        stopSession()
    }

    companion object {
        private const val TAG = "SessionManager"
    }
}
