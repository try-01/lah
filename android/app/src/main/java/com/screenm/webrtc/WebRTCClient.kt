package com.screenm.webrtc

import android.content.Context
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.*
import org.webrtc.PeerConnection.*

class WebRTCClient(private val context: Context) {
    private var peerConnection: PeerConnection? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var eglBase: EglBase? = null

    private val _connectionState = MutableStateFlow(State.DISCONNECTED)
    val connectionState: StateFlow<State> = _connectionState.asStateFlow()

    private var sdpCallback: ((SessionDescription) -> Unit)? = null

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setFieldTrials("")
                .createInitializationOptions(),
        )

        eglBase = EglBase.create()

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory =
            PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
    }

    fun createPeerConnection(
        onIceCandidate: (IceCandidate) -> Unit,
        onSdpReady: (SessionDescription) -> Unit,
    ) {
        sdpCallback = onSdpReady

        val iceServers =
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            )

        val config =
            RTCConfiguration(iceServers).apply {
                sdpSemantics = SdpSemantics.UNIFIED_PLAN
                iceTransportsType = IceTransportsType.NOHOST
                bundlePolicy = BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = RtcpMuxPolicy.REQUIRE
                continualGatheringPolicy = ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

        peerConnection =
            peerConnectionFactory?.createPeerConnection(
                config,
                object : Observer {
                    override fun onIceCandidate(candidate: IceCandidate) {
                        onIceCandidate(candidate)
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}

                    override fun onSignalingChange(state: SignalingState) {
                        Log.i(TAG, "Signaling: $state")
                    }

                    override fun onIceConnectionChange(state: IceConnectionState) {
                        Log.i(TAG, "ICE: $state")
                        _connectionState.value =
                            when (state) {
                                IceConnectionState.NEW,
                                IceConnectionState.CHECKING,
                                -> State.CONNECTING
                                IceConnectionState.CONNECTED -> State.CONNECTED
                                IceConnectionState.COMPLETED -> State.CONNECTED
                                IceConnectionState.DISCONNECTED -> State.DISCONNECTED
                                IceConnectionState.FAILED -> State.FAILED
                                IceConnectionState.CLOSED -> State.DISCONNECTED
                            }
                    }

                    override fun onIceGatheringChange(state: IceGatheringState) {}

                    override fun onAddStream(stream: MediaStream) {}

                    override fun onAddTrack(
                        receiver: RtpReceiver,
                        streams: Array<out MediaStream>,
                    ) {}

                    override fun onRemoveStream(stream: MediaStream) {}

                    override fun onDataChannel(channel: DataChannel) {}

                    override fun onRenegotiationNeeded() {}

                    override fun onStandardizedIceConnectionChange(state: IceConnectionState) {}

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                },
            )
    }

    fun startScreenCapture(mediaProjectionPermission: android.content.Intent) {
        val capturer =
            ScreenCapturerAndroid(
                mediaProjectionPermission,
                object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection stopped")
                    }
                },
            )

        videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
        capturer.initialize(
            SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext),
            context,
            videoSource?.capturerObserver,
        )
        capturer.startCapture(1280, 720, 30)
        screenCapturer = capturer

        val videoTrack = peerConnectionFactory?.createVideoTrack("screen_track", videoSource)
        peerConnection?.addTrack(videoTrack)

        startAudioCapture()
    }

    private fun startAudioCapture() {
        val constraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(constraints)
        val audioTrack = peerConnectionFactory?.createAudioTrack("audio_track", audioSource)
        peerConnection?.addTrack(audioTrack)
    }

    fun createOffer() {
        val constraints =
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
            }
        peerConnection?.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(
                        object : SdpObserver {
                            override fun onSetSuccess() {
                                sdpCallback?.invoke(sdp)
                                _connectionState.value = State.CONNECTING
                            }

                            override fun onSetFailure(error: String) {
                                Log.e(TAG, "Set local SDP failed: $error")
                                _connectionState.value = State.FAILED
                            }

                            override fun onCreateSuccess(sdp: SessionDescription) {}

                            override fun onCreateFailure(error: String) {}
                        },
                        sdp,
                    )
                }

                override fun onCreateFailure(error: String) {
                    Log.e(TAG, "Create offer failed: $error")
                    _connectionState.value = State.FAILED
                }

                override fun onSetSuccess() {}

                override fun onSetFailure(error: String) {}
            },
            constraints,
        )
    }

    fun handleRemoteSdp(sdp: SessionDescription) {
        peerConnection?.setRemoteDescription(
            object : SdpObserver {
                override fun onSetSuccess() {}

                override fun onSetFailure(error: String) {
                    Log.e(TAG, "Set remote SDP failed: $error")
                    _connectionState.value = State.FAILED
                }

                override fun onCreateSuccess(sdp: SessionDescription) {}

                override fun onCreateFailure(error: String) {}
            },
            sdp,
        )
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun stop() {
        screenCapturer?.stopCapture()
        screenCapturer?.dispose()
        screenCapturer = null
        videoSource?.dispose()
        videoSource = null
        audioSource?.dispose()
        audioSource = null
        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
        _connectionState.value = State.DISCONNECTED
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED,
    }

    companion object {
        private const val TAG = "WebRTCClient"
    }
}
