(function() {
    'use strict';

    var video = document.getElementById('remote-video');
    var overlay = document.getElementById('overlay');
    var statusText = document.getElementById('status-text');
    var statusIcon = document.getElementById('status-icon');
    var infoBar = document.getElementById('info-bar');
    var infoText = document.getElementById('info-text');

    var signaling = null;
    var webrtc = null;
    var retryCount = 0;
    var MAX_RETRIES = 3;

    function onSignalingMessage(msg) {
        if (msg && msg.type && webrtc) {
            webrtc.handleMessage(msg.type, msg.data || {});
        }
    }

    function sendSignalingMessage(type, data) {
        if (signaling) {
            signaling.send(type, data);
        }
    }

    function startSession(serverIp, serverPort) {
        console.log('[App] Starting session: ' + serverIp + ':' + (serverPort || 8080));

        setStatus('connecting', 'Connecting...');
        showSpinner(true);

        var wsUrl = 'ws://' + serverIp + ':' + (serverPort || 8080);

        signaling = new SignalingClient(
            wsUrl,
            function onOpen() {
                console.log('[App] Signaling connected');
                setStatus('connecting', 'Negotiating stream...');
            },
            function onMessage(msg) {
                onSignalingMessage(msg);
            },
            function onClose(event) {
                console.log('[App] Signaling closed');
                if (webrtc) {
                    webrtc.stop();
                    webrtc = null;
                }
                setStatus('error', 'Connection lost');
                showSpinner(false);
            },
            function onError(error) {
                console.error('[App] Signaling error:', error);
            }
        );

        webrtc = new WebRTCHandler(video, sendSignalingMessage);

        webrtc.onEvent = function(type, detail) {
            console.log('[App] WebRTC event:', type);
            switch (type) {
                case 'connected':
                    setStatus('connected', 'Streaming...');
                    showSpinner(false);
                    hideOverlayAfter(1000);
                    break;
                case 'streaming':
                    setStatus('connected', 'Receiving...');
                    hideOverlayAfter(500);
                    break;
                case 'disconnected':
                    setStatus('error', 'Disconnected');
                    showOverlay();
                    break;
                case 'error':
                    setStatus('error', 'Error: ' + (detail || 'unknown'));
                    showOverlay();
                    handleRetry(serverIp, serverPort);
                    break;
            }
        };

        webrtc.start();
        signaling.connect();
    }

    function handleRetry(serverIp, serverPort) {
        retryCount++;
        if (retryCount > MAX_RETRIES) return;
        if (webrtc) { webrtc.stop(); webrtc = null; }
        if (signaling) { signaling.disconnect(); signaling = null; }
        setTimeout(function() { startSession(serverIp, serverPort); }, 2000);
    }

    function stopSession() {
        retryCount = 0;
        if (webrtc) { webrtc.stop(); webrtc = null; }
        if (signaling) { signaling.disconnect(); signaling = null; }
        video.src = '';
        setStatus('idle', 'Disconnected');
        showOverlay();
        showSpinner(false);
    }

    function setStatus(state, text) {
        statusText.textContent = text || '';
        statusIcon.className = 'status';
        switch (state) {
            case 'connecting': statusIcon.classList.add('connecting'); break;
            case 'connected': statusIcon.classList.add('connected'); break;
            case 'error': statusIcon.classList.add('error'); break;
        }
    }

    function showSpinner(show) {
        var spinner = document.getElementById('spinner');
        if (show) {
            if (!spinner) {
                spinner = document.createElement('div');
                spinner.id = 'spinner';
                spinner.className = 'spinner';
                statusIcon.parentNode.insertBefore(spinner, statusIcon);
            }
            spinner.style.display = 'block';
        } else {
            if (spinner) { spinner.style.display = 'none'; }
        }
    }

    function showOverlay() { overlay.classList.remove('hidden'); }
    function hideOverlay() { overlay.classList.add('hidden'); }
    function hideOverlayAfter(ms) { setTimeout(function() { hideOverlay(); }, ms); }

    function showInfo(text) { infoText.textContent = text; infoBar.classList.remove('hidden'); }
    function hideInfo() { infoBar.classList.add('hidden'); }

    function getUrlParams() {
        var params = {};
        var query = window.location.search.substring(1);
        if (!query) return params;
        var pairs = query.split('&');
        for (var i = 0; i < pairs.length; i++) {
            var pair = pairs[i].split('=');
            params[decodeURIComponent(pair[0])] = decodeURIComponent(pair[1] || '');
        }
        return params;
    }

    setStatus('idle', 'Waiting for connection...');
    showSpinner(false);

    var params = getUrlParams();
    var serverIp = params.serverIp || params.server;
    var serverPort = parseInt(params.serverPort || params.port || '8080', 10);

    if (serverIp) {
        startSession(serverIp, serverPort);
    } else {
        setStatus('error', 'No server IP provided');
        showInfo('Launch from screenM Android app');
    }

    document.addEventListener('keydown', function(event) {
        var keyCode = event.keyCode;
        if (keyCode === 27) { event.preventDefault(); stopSession(); }
    });

    window.screenM = {
        start: function(serverIp, serverPort) { startSession(serverIp, serverPort); },
        stop: function() { stopSession(); }
    };

    console.log('[App] screenM Browser Receiver ready');
})();
