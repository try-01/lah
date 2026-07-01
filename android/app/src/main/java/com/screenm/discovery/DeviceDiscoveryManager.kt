package com.screenm.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.screenm.model.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceDiscoveryManager(private val context: Context) {

    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val devices: StateFlow<List<DeviceInfo>> = _devices.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isRunning = false

    fun startDiscovery() {
        if (isRunning) return
        isRunning = true
        _devices.value = emptyList()

        acquireWifiLock()
        startNsdDiscovery()
    }

    fun stopDiscovery() {
        if (!isRunning) return
        isRunning = false
        stopNsdDiscovery()
        releaseWifiLock()
    }

    private fun acquireWifiLock() {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "screenM:discovery"
        )
        wifiLock?.setReferenceCounted(false)
        wifiLock?.acquire()
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock = null
    }

    private fun startNsdDiscovery() {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager?.let { manager ->
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val name = info.serviceName
                            val host = info.host?.hostAddress ?: return
                            val txt = info.attributes

                            val device = DeviceInfo(
                                id = "$host:${info.port}",
                                name = name.removeSuffix("._tizen-mirror._tcp.local."),
                                ipAddress = host,
                                tizenVersion = String(txt["tizenVersion"] ?: ByteArray(0)),
                                port = info.port
                            )

                            val current = _devices.value.toMutableList()
                            if (current.none { it.id == device.id }) {
                                current.add(device)
                                _devices.value = current
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val current = _devices.value.filter {
                    it.port != serviceInfo.port
                }
                _devices.value = current
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        discoveryListener?.let {
            nsdManager?.discoverServices(
                "_tizen-mirror._tcp",
                NsdManager.PROTOCOL_DNS_SD,
                it
            )
        }
    }

    private fun stopNsdDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        } catch (_: Exception) {}
        discoveryListener = null
        nsdManager = null
    }

    fun destroy() {
        stopDiscovery()
    }
}
