package com.tvhanan.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.domain.repository.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Status sebuah aksi koneksi (reconnect/scan) yang ditampilkan sbg modal di SettingsScreen. */
sealed interface ConnectionActionState {
    data object Idle : ConnectionActionState
    data object Loading : ConnectionActionState
    data class ReconnectSuccess(val device: TvDevice) : ConnectionActionState
    data class ScanResult(val devices: List<TvDevice>) : ConnectionActionState
    data class Failed(val message: String) : ConnectionActionState
}

/**
 * Preferensi tampilan remote. Nilai [remoteSize] dikonsumsi oleh
 * RemoteScreen lewat [NavGraph] sebagai [scaleFactor], yang
 * mengalikan ukuran dp tombol-tombol remote.
 */
data class RemoteUiPreferences(
    val hapticEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val meshBackgroundEnabled: Boolean = true,
    val remoteSize: RemoteSize = RemoteSize.FIT
)

enum class RemoteSize(val scaleFactor: Float) {
    COMPACT(0.86f),
    FIT(1.0f),
    LARGE(1.14f)
}

class SettingsViewModel(
    private val repository: TvRepository
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _tvDevice = MutableStateFlow<TvDevice?>(null)
    val tvDevice: StateFlow<TvDevice?> = _tvDevice.asStateFlow()

    private val _isActuallyConnected = MutableStateFlow(false)
    val isActuallyConnected: StateFlow<Boolean> = _isActuallyConnected.asStateFlow()

    private val _uiPreferences = MutableStateFlow(RemoteUiPreferences())
    val uiPreferences: StateFlow<RemoteUiPreferences> = _uiPreferences.asStateFlow()

    private val _actionState = MutableStateFlow<ConnectionActionState>(ConnectionActionState.Idle)
    val actionState: StateFlow<ConnectionActionState> = _actionState.asStateFlow()

    init {
        loadCurrentDevice()
    }

    private fun loadCurrentDevice() {
        viewModelScope.launch {
            val ip = repository.lastIp.first()
            val port = repository.lastPort.first()?.toIntOrNull() ?: 8002
            val mac = repository.macAddress.first()
            if (ip != null) {
                _tvDevice.value = TvDevice(ipAddress = ip, port = port, macAddress = mac)
            }
        }
    }

/** Dipanggil RemoteScreen begitu IP/port/mac aktif diketahui, supaya
     * TvInfoCard di Settings langsung akurat tanpa menunggu DataStore. */
    fun setActiveDevice(ipAddress: String, port: Int, macAddress: String?, token: String? = null, isConnected: Boolean = false) {
        _tvDevice.value = TvDevice(ipAddress = ipAddress, port = port, macAddress = macAddress, token = token)
        _isActuallyConnected.value = isConnected
    }

    fun setHapticEnabled(enabled: Boolean) {
        _uiPreferences.value = _uiPreferences.value.copy(hapticEnabled = enabled)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _uiPreferences.value = _uiPreferences.value.copy(keepScreenOn = enabled)
    }

    fun setMeshBackgroundEnabled(enabled: Boolean) {
        _uiPreferences.value = _uiPreferences.value.copy(meshBackgroundEnabled = enabled)
    }

    fun setRemoteSize(size: RemoteSize) {
        _uiPreferences.value = _uiPreferences.value.copy(remoteSize = size)
    }

    fun reconnect() {
        val device = _tvDevice.value ?: return
        viewModelScope.launch {
            _actionState.value = ConnectionActionState.Loading
            try {
                val reachable = repository.isHostReachable(device.ipAddress, device.port)
                _actionState.value = if (reachable) {
                    ConnectionActionState.ReconnectSuccess(device)
                } else {
                    ConnectionActionState.Failed("TV tidak merespons di ${device.ipAddress}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reconnect probe failed", e)
                _actionState.value = ConnectionActionState.Failed(e.message ?: "Gagal menghubungkan ulang")
            }
        }
    }

    fun scanForOtherTvs() {
        viewModelScope.launch {
            _actionState.value = ConnectionActionState.Loading
            try {
                val devices = repository.discoverDevices()
                _actionState.value = ConnectionActionState.ScanResult(devices)
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed", e)
                _actionState.value = ConnectionActionState.Failed(e.message ?: "Gagal memindai jaringan")
            }
        }
    }

    fun forgetTv() {
        viewModelScope.launch {
            repository.clearPreferences()
            _tvDevice.value = null
            _actionState.value = ConnectionActionState.Idle
        }
    }

    fun wakeTv() {
        val device = _tvDevice.value ?: return
        val mac = device.macAddress ?: return
        viewModelScope.launch {
            repository.wakeOnLan(mac)
        }
    }

    fun resetActionState() {
        _actionState.value = ConnectionActionState.Idle
    }
}
