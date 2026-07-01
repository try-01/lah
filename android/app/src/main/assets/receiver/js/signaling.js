/**
 * WebSocket signaling client for Tizen 4.0+
 * ES5-compatible — no let/const/arrow/Promise
 */

var SignalingClient = function(url, onOpen, onMessage, onClose, onError) {
    this.url = url;
    this.ws = null;
    this.reconnectTimer = null;
    this.isConnected = false;

    this._onOpen = onOpen || function() {};
    this._onMessage = onMessage || function(msg) {};
    this._onClose = onClose || function() {};
    this._onError = onError || function() {};
};

SignalingClient.prototype.connect = function() {
    try {
        this.ws = new WebSocket(this.url);
        var self = this;

        this.ws.onopen = function() {
            self.isConnected = true;
            console.log('[Signaling] Connected to ' + self.url);
            if (self._onOpen) self._onOpen();
        };

        this.ws.onmessage = function(event) {
            try {
                var msg = JSON.parse(event.data);
                console.log('[Signaling] Received:', msg.type);
                if (self._onMessage) self._onMessage(msg);
            } catch (e) {
                console.warn('[Signaling] Invalid message:', e.message);
            }
        };

        this.ws.onclose = function(event) {
            self.isConnected = false;
            console.log('[Signaling] Disconnected (code: ' + event.code + ')');
            if (self._onClose) self._onClose(event);
            if (self.reconnectTimer === null) {
                self.reconnectTimer = setTimeout(function() {
                    self.reconnectTimer = null;
                    console.log('[Signaling] Reconnecting...');
                    self.connect();
                }, 3000);
            }
        };

        this.ws.onerror = function(error) {
            console.error('[Signaling] Error:', error);
            if (self._onError) self._onError(error);
        };
    } catch (e) {
        console.error('[Signaling] Connection failed:', e.message);
        if (this._onError) this._onError(e);
    }
};

SignalingClient.prototype.send = function(type, data) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        console.warn('[Signaling] Not connected, cannot send');
        return false;
    }
    var payload = { type: type, data: data || {} };
    this.ws.send(JSON.stringify(payload));
    return true;
};

SignalingClient.prototype.disconnect = function() {
    if (this.reconnectTimer !== null) {
        clearTimeout(this.reconnectTimer);
        this.reconnectTimer = null;
    }
    if (this.ws) {
        this.ws.onclose = null;
        this.ws.close();
        this.ws = null;
    }
    this.isConnected = false;
};
