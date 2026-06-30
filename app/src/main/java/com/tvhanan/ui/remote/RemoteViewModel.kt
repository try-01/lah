package com.tvhanan.ui.remote

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvhanan.domain.model.ConnectionState
import com.tvhanan.domain.model.RemoteKey
import com.tvhanan.domain.repository.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

class RemoteViewModel(
    private val ipAddress: String,
    private val macAddress: String? = null,
    private val repository: TvRepository,
) : ViewModel() {
    private var tokenObserverJob: kotlinx.coroutines.Job? = null
    private val isConnecting = AtomicBoolean(false)

    companion object {
        private const val TAG = "TvHanan"
    }

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastSavedToken = MutableStateFlow<String?>(null)
    val lastSavedToken: StateFlow<String?> = _lastSavedToken.asStateFlow()

    private val _isMacAvailable = MutableStateFlow(false)
    val isMacAvailable: StateFlow<Boolean> = _isMacAvailable.asStateFlow()

    init {
        viewModelScope.launch {
            if (macAddress != null) {
                _isMacAvailable.value = true
            } else {
                repository.macAddress.collect { mac ->
                    _isMacAvailable.value = !mac.isNullOrBlank()
                }
            }
        }
    }

    fun connect() {
        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "connect() skipped: already connecting")
            return
        }
        Log.d(TAG, "connect() called for $ipAddress")
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val savedToken = repository.getToken()
                Log.d(TAG, "savedToken = ${if (savedToken == null) "null" else "exists"}")

                val result = repository.connectWithFallback(ipAddress, savedToken)

                if (result.isSuccess) {
                    Log.d(TAG, "Connection succeeded")
                    if (savedToken == null) {
                        tokenObserverJob?.cancel()
                        tokenObserverJob =
                            launch {
                                val newToken =
                                    withTimeoutOrNull(30_000L) {
                                        repository.tokenReceived
                                            .filterNotNull()
                                            .firstOrNull()
                                    }
                                if (newToken != null) {
                                    repository.saveToken(newToken)
                                    _lastSavedToken.value = newToken
                                    Log.d(TAG, "First token saved: $newToken")
                                } else {
                                    Log.w(TAG, "Token not received within timeout")
                                }
                            }
                    } else {
                        _lastSavedToken.value = savedToken
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Gagal terhubung ke TV"
                    Log.e(TAG, "Connection failed: $errorMsg")
                    _errorMessage.value = errorMsg
                }
            } finally {
                isConnecting.set(false)
            }
        }
    }

    /**
     * Memantau token yang baru tersimpan (baik dari pairing baru maupun
     * yang sudah ada sebelumnya), memanggil [onNewToken] tiap kali nilainya
     * berubah dan tidak null. Dipakai NavGraph untuk sinkronisasi token ke
     * SettingsViewModel tanpa duplikasi logic penyimpanan token.
     */
    suspend fun observeNewToken(onNewToken: suspend (String) -> Unit) {
        _lastSavedToken.filterNotNull().collect { token ->
            onNewToken(token)
        }
    }

    /**
     * Mengirim key ke TV. Catatan: haptic feedback TIDAK dipicu di sini —
     * itu ditangani oleh komponen UI (HapticGlassButton) saat tombol
     * mulai ditekan (onPress), bukan di layer ViewModel. Ini menghindari
     * getar terpicu dua kali (sekali dari UI saat press, sekali lagi
     * dari sini) dan menjaga ViewModel tidak punya concern soal detail
     * presentasi seperti vibration.
     */
    fun sendKey(key: RemoteKey) {
        if (key == RemoteKey.POWER && connectionState.value != ConnectionState.CONNECTED) {
            wakeOnLan()
        } else {
            repository.sendKey(key)
        }
    }

    fun wakeOnLan() {
        viewModelScope.launch {
            val mac = macAddress ?: repository.macAddress.firstOrNull()
            if (!mac.isNullOrBlank()) {
                Log.d(TAG, "Mencoba menyalakan TV via WoL (dengan Retry) ke MAC: $mac")

                val success = repository.wakeOnLan(mac)

                if (success) {
                    _errorMessage.value = "TV sedang dinyalakan, mencoba menghubungkan kembali..."
                    kotlinx.coroutines.delay(8000)

                    repeat(4) { attempt ->
                        if (connectionState.value == ConnectionState.CONNECTED) return@repeat
                        Log.d(TAG, "Auto-reconnect setelah WOL, percobaan ke-${attempt + 1}")
                        connect()
                        while (isConnecting.get()) {
                            kotlinx.coroutines.delay(500)
                        }
                        if (connectionState.value == ConnectionState.CONNECTED) return@repeat
                        if (attempt < 3) {
                            kotlinx.coroutines.delay(4000)
                        }
                    }

                    if (connectionState.value != ConnectionState.CONNECTED) {
                        _errorMessage.value = "TV tidak merespons. Coba nyalakan TV secara manual."
                    }
                }
            } else {
                Log.e(TAG, "Gagal menjalankan WoL: Alamat MAC tidak ditemukan")
            }
        }
    }

    fun launchApp(appId: String) {
        viewModelScope.launch {
            val success = repository.launchApp(ipAddress, appId)
            Log.d(TAG, "launchApp($appId) success=$success")
        }
    }

    fun closeApp(appId: String) {
        viewModelScope.launch {
            val success = repository.closeApp(ipAddress, appId)
            Log.d(TAG, "closeApp($appId) success=$success")
        }
    }

    fun disconnect() {
        repository.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
