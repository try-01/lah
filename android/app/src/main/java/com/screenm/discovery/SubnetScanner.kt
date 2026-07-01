package com.screenm.discovery

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
        val baseIp = getBaseIp() ?: return@coroutineScope emptyList()
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
                    if (openPorts.isNotEmpty()) ScanResult(ip, openPorts) else null
                }
            }
        }

        try {
            withTimeout(timeoutMs) {
                deferred.awaitAll().filterNotNull()
            }
        } catch (_: Exception) {
            deferred.filter { it.isCompleted }.mapNotNull {
                try { it.getCompleted() } catch (_: Exception) { null }
            }
        }
    }

    private fun getBaseIp(): String? {
        try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { ni ->
                if (ni.isLoopback || !ni.isUp) return@forEach
                ni.inetAddresses?.asSequence()?.forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress ?: return@forEach
                        return ip.substringBeforeLast('.') + ".0"
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
