package com.screenm.discovery

import android.content.Context
import com.screenm.model.DeviceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DeviceDiscoveryManager(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ssdpDiscovery = SsdpDiscovery()
    private val subnetScanner = SubnetScanner()
    private val apiClient = TvApiClient()

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private var discoveryJob: Job? = null

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        _devices.value = emptyList()

        discoveryJob = scope.launch {
            val ssdpDeferred = async { ssdpDiscovery.discover(3000) }
            val subnetDeferred = async { subnetScanner.scan(10_000) }

            val ssdpResults = ssdpDeferred.await()
            val subnetResults = subnetDeferred.await()

            val allIps = mutableSetOf<String>()
            ssdpResults.forEach { allIps.add(it.ip) }
            subnetResults.forEach { allIps.add(it.ip) }

            val devices = allIps.mapNotNull { ip -> apiClient.getDeviceInfo(ip) }
                .sortedBy { it.name }

            _devices.value = devices
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    fun destroy() {
        stopDiscovery()
        scope.cancel()
    }
}
