/**
 * WebRTC receiver for Tizen 4.0+ (Chromium M56+)
 * ES5-compatible — no let/const/arrow/Promise/class
 * Pure callback-style. Does NOT patch signaling — main.js routes messages.
 */

var WebRTCHandler = function(videoElement, sendMessage) {
    this.video = videoElement;
    this.sendMessage = sendMessage; // callback: function(type, data)
    this.pc = null;
    this.isConnected = false;
    this._destroyed = false;

    // Prefix handling for Tizen 4.0 (Chromium M56)
    this.RTCPeerConnection = window.RTCPeerConnection
        || window.webkitRTCPeerConnection
        || window.mozRTCPeerConnection;

    this.RTCSessionDescription = window.RTCSessionDescription
        || window.webkitRTCSessionDescription
        || window.mozRTCSessionDescription;

    this.RTCIceCandidate = window.RTCIceCandidate
        || window.webkitRTCIceCandidate
        || window.mozRTCIceCandidate;
};

WebRTCHandler.prototype.start = function() {
    if (this._destroyed) return;

    if (!this.RTCPeerConnection) {
        this._dispatch('error', 'WebRTC not supported');
        return;
    }

    var self = this;

    var servers = {
        iceServers: [
            { urls: 'stun:stun.l.google.com:19302' }
        ],
        iceTransportPolicy: 'all',
        bundlePolicy: 'max-bundle',
        rtcpMuxPolicy: 'require'
    };

    try {
        this.pc = new this.RTCPeerConnection(servers);
    } catch (e) {
        this._dispatch('error', 'Failed to create PC: ' + e.message);
        return;
    }

    this.pc.onicecandidate = function(event) {
        if (event.candidate && self.sendMessage) {
            self.sendMessage('ice_candidate', {
                sdpMid: event.candidate.sdpMid,
                sdpMLineIndex: event.candidate.sdpMLineIndex,
                candidate: event.candidate.candidate
            });
        }
    };

    this.pc.oniceconnectionstatechange = function() {
        var state = self.pc.iceConnectionState;
        if (state === 'connected' || state === 'completed') {
            self.isConnected = true;
            self._dispatch('connected');
        } else if (state === 'disconnected' || state === 'failed' || state === 'closed') {
            self.isConnected = false;
            self._dispatch('disconnected');
        }
    };

    this.pc.ontrack = function(event) {
        if (!event.track) return;
        if ('srcObject' in self.video) {
            self.video.srcObject = event.streams[0];
        } else {
            self.video.src = window.URL.createObjectURL(event.streams[0]);
        }
        self.video.play();
        self._dispatch('streaming');
    };
};

// Called by main.js when signaling messages arrive
WebRTCHandler.prototype.handleMessage = function(type, data) {
    if (this._destroyed || !this.pc) return;

    switch (type) {
        case 'sdp_offer':
            this._handleOffer(data);
            break;
        case 'ice_candidate':
            this._handleIceCandidate(data);
            break;
        case 'start':
            this._dispatch('started');
            break;
    }
};

WebRTCHandler.prototype._handleOffer = function(data) {
    if (!data || !data.sdp) return;
    var self = this;

    var desc = new this.RTCSessionDescription({
        type: 'offer',
        sdp: data.sdp
    });

    this.pc.setRemoteDescription(desc, function() {
        self._createAnswer();
    }, function(error) {
        self._dispatch('error', 'setRemoteDescription: ' + error);
    });
};

WebRTCHandler.prototype._createAnswer = function() {
    var self = this;

    this.pc.createAnswer(function(desc) {
        self.pc.setLocalDescription(desc, function() {
            if (self.sendMessage) {
                self.sendMessage('sdp_answer', {
                    type: desc.type,
                    sdp: desc.sdp
                });
            }
        }, function(error) {
            self._dispatch('error', 'setLocalDescription: ' + error);
        });
    }, function(error) {
        self._dispatch('error', 'createAnswer: ' + error);
    });
};

WebRTCHandler.prototype._handleIceCandidate = function(data) {
    if (!data || !data.candidate) return;

    try {
        var candidate = new this.RTCIceCandidate({
            sdpMid: data.sdpMid,
            sdpMLineIndex: data.sdpMLineIndex,
            candidate: data.candidate
        });
        this.pc.addIceCandidate(candidate);
    } catch (e) {
        console.warn('[WebRTC] ICE candidate error:', e.message);
    }
};

WebRTCHandler.prototype.stop = function() {
    this._destroyed = true;
    if (this.pc) {
        this.pc.close();
        this.pc = null;
    }
    this.video.src = '';
    this.isConnected = false;
};

WebRTCHandler.prototype._dispatch = function(type, detail) {
    if (this.onEvent) {
        this.onEvent(type, detail);
    }
};
