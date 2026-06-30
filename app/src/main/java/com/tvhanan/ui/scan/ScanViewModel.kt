package com.tvhanan.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.domain.repository.TvRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScanViewModel(
    private val repository: TvRepository
) : ViewModel() {

    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastIp = MutableStateFlow<String?>(null)
    val lastIp: StateFlow<String?> = _lastIp.asStateFlow()

    init {
        viewModelScope.launch {
            _lastIp.value = repository.lastIp.first()
        }
    }

    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _error.value = null

            try {
                val found = repository.discoverDevices()
                _devices.value = found
                if (found.isEmpty()) {
                    _error.value = "TV tidak ditemukan. Coba koneksi manual."
                }
            } catch (e: Exception) {
                _error.value = "Gagal scan: ${e.localizedMessage}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun savePreferredDevice(device: TvDevice) {
        viewModelScope.launch {
            repository.saveLastIp(device.ipAddress)
            repository.saveLastPort(device.port.toString())
            device.macAddress?.let { repository.saveMacAddress(it) }
        }
    }
}
