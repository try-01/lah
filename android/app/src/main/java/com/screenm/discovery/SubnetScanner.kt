package com.screenm.discovery

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class SubnetScanner {

    data class ScanResult(val ip: String, val openPorts: List<Int>)

    private val concurrencyLimit = Semaphore(50)

    suspend fun scan(timeoutMs: Long = 10_000): List<ScanResult> = coroutineScope {
        val baseIp = getBaseIp()
        if (baseIp == null) {
            Log.w(TAG, "Could not determine local network base IP — subnet scan skipped")
            return@coroutineScope emptyList()
        }
        Log.i(TAG, "Starting subnet scan on $baseIp/24 (ports: 8001, 8002)")
        val prefix = baseIp.substringBeforeLast('.') + "."
        val ports = listOf(8001, 8002)

        val deferred = (1..254).map { octet ->
            async(Dispatchers.IO) {
                concurrencyLimit.withPermit {
                    val ip = "$prefix$octet"
                    val openPorts = ports.filter { port ->
                        try {
                            withTimeout(200) {
                                Socket().use { sock ->
                                    sock.connect(InetSocketAddress(ip, port), 150)
                                    true
                                }
                            }
                        } catch (_: Exception) { false }
                    }
                    if (openPorts.isNotEmpty()) {
                        Log.d(TAG, "Open port(s) at $ip: $openPorts")
                        ScanResult(ip, openPorts)
                    } else null
                }
            }
        }

        try {
            withTimeout(timeoutMs) {
                deferred.awaitAll().filterNotNull()
            }
        } catch (_: Exception) {
            val completed = deferred.filter { it.isCompleted }.mapNotNull {
                try { it.getCompleted() } catch (_: Exception) { null }
            }
            Log.w(TAG, "Subnet scan timed out after ${completed.size} completed checks")
            completed
        }
    }

    private fun getBaseIp(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { ni ->
                Log.d(TAG, "Checking interface: ${ni.name} (up=${ni.isUp}, loopback=${ni.isLoopback})")
                if (ni.isLoopback || !ni.isUp) return@forEach
                ni.inetAddresses?.asSequence()?.forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: return@forEach
                        Log.i(TAG, "Local IP: $ip on ${ni.name}")
                        return ip.substringBeforeLast('.') + ".0"
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    companion object {
        private const val TAG = "SubnetScanner"
    }
}
