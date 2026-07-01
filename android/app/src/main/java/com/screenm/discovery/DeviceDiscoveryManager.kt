package com.screenm.discovery

import android.content.Context
import android.util.Log
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
    private val ssdpDiscovery = SsdpDiscovery(context)
    private val subnetScanner = SubnetScanner()
    private val apiClient = TvApiClient()

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private var discoveryJob: Job? = null

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) {
            Log.d(TAG, "Discovery already running, skipping")
            return
        }
        _devices.value = emptyList()
        Log.i(TAG, "Starting device discovery (SSDP + subnet scan)")

        discoveryJob =
            scope.launch {
                val ssdpDeferred =
                    async {
                        val t0 = System.currentTimeMillis()
                        val results = ssdpDiscovery.discover(3000)
                        Log.i(TAG, "SSDP found ${results.size} devices in ${System.currentTimeMillis() - t0}ms: ${results.map { it.ip }}")
                        results
                    }
                val subnetDeferred =
                    async {
                        val t0 = System.currentTimeMillis()
                        val results = subnetScanner.scan(10_000)
                        Log.i(TAG, "Subnet scan found ${results.size} devices in ${System.currentTimeMillis() - t0}ms: ${results.map { it.ip }}")
                        results
                    }

                val ssdpResults = ssdpDeferred.await()
                val subnetResults = subnetDeferred.await()

                val allIps = mutableSetOf<String>()
                ssdpResults.forEach { allIps.add(it.ip) }
                subnetResults.forEach { allIps.add(it.ip) }
                Log.i(TAG, "Total unique IPs to probe: ${allIps.size} — $allIps")

                val devices =
                    allIps.mapNotNull { ip ->
                        Log.d(TAG, "Probing $ip via REST API")
                        apiClient.getDeviceInfo(ip)
                    }.sortedBy { it.name }

                Log.i(TAG, "Discovery complete: ${devices.size} device(s) found")
                devices.forEach { Log.i(TAG, "  → ${it.name} @ ${it.ipAddress} (MAC: ${it.macAddress})") }

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

    companion object {
        private const val TAG = "DeviceDiscovery"
    }
}
