package com.screenm.discovery

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

class SsdpDiscovery(private val context: Context) {
    data class DiscoveredDevice(val ip: String, val location: String?, val server: String?)

    fun discover(timeoutMs: Long = 3000): List<DiscoveredDevice> {
        val results = mutableListOf<DiscoveredDevice>()
        val multicastGroup = InetAddress.getByName(MULTICAST_ADDR)
        val sock = DatagramSocket(null)
        val networkIps = getLocalIps()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = wifiManager?.createMulticastLock("screenM_ssdp_lock")
        multicastLock?.setReferenceCounted(false)
        multicastLock?.acquire()

        try {
            sock.reuseAddress = true
            sock.bind(InetSocketAddress(MULTICAST_PORT))
            sock.soTimeout = timeoutMs.toInt()

            val searchMessage = buildMSearch()
            Log.i(TAG, "Sending M-SEARCH from ${networkIps.size} interfaces, target=$SEARCH_TARGET")

            for (localIp in networkIps) {
                val packet =
                    DatagramPacket(
                        searchMessage,
                        searchMessage.size,
                        multicastGroup,
                        MULTICAST_PORT,
                    )
                packet.address = multicastGroup
                sock.send(packet)
                Log.d(TAG, "M-SEARCH sent from ${localIp.hostAddress}")
            }

            var count = 0
            while (true) {
                try {
                    val buf = ByteArray(4096)
                    val receivePacket = DatagramPacket(buf, buf.size)
                    sock.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)

                    if (isSamsungResponse(response)) {
                        val ip = receivePacket.address.hostAddress ?: continue
                        count++
                        Log.i(TAG, "SSDP response #$count from $ip")
                        results.add(
                            DiscoveredDevice(
                                ip = ip,
                                location = extractHeader(response, "LOCATION"),
                                server = extractHeader(response, "SERVER"),
                            ),
                        )
                    }
                } catch (_: SocketTimeoutException) {
                    Log.d(TAG, "SSDP listening timed out, got $count Samsung responses")
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSDP error: ${e.message}", e)
        } finally {
            try {
                if (multicastLock?.isHeld == true) multicastLock.release()
            } catch (_: Exception) {
            }
            try {
                sock.close()
            } catch (_: Exception) {
            }
        }

        return results.distinctBy { it.ip }
    }

    private fun buildMSearch(): ByteArray {
        return (
            "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: $MULTICAST_ADDR:$MULTICAST_PORT\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: $SEARCH_TARGET\r\n" +
                "USER-AGENT: screenM/1.0 Android\r\n" +
                "\r\n"
        ).toByteArray()
    }

    private fun isSamsungResponse(response: String): Boolean {
        val server = extractHeader(response, "SERVER")
        val userAgent = extractHeader(response, "USER-AGENT")
        val containsSamsung =
            server?.uppercase()?.contains("SAMSUNG") == true ||
                userAgent?.uppercase()?.contains("SAMSUNG") == true
        if (!containsSamsung) {
            val st = extractHeader(response, "ST")
            val location = extractHeader(response, "LOCATION")
            if (st?.contains(SEARCH_TARGET, ignoreCase = true) == true) {
                Log.d(TAG, "Samsung device found via ST match (no Samsung in headers): $location")
                return true
            }
            return false
        }
        val st = extractHeader(response, "ST")
        return st == null || st.contains(SEARCH_TARGET, ignoreCase = true)
    }

    private fun extractHeader(
        response: String,
        name: String,
    ): String? {
        val regex = Regex("$name:\\s*(.+)", RegexOption.IGNORE_CASE)
        return regex.find(response)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun getLocalIps(): List<InetAddress> {
        val ips = mutableListOf<InetAddress>()
        try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()?.forEach { ni ->
                if (ni.isLoopback || !ni.isUp) return@forEach
                ni.inetAddresses?.asSequence()?.forEach { addr ->
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        ips.add(addr)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return ips
    }

    companion object {
        private const val TAG = "SsdpDiscovery"
        private const val MULTICAST_ADDR = "239.255.255.250"
        private const val MULTICAST_PORT = 1900
        private const val SEARCH_TARGET = "urn:samsung.com:device:RemoteControlReceiver:1"
    }
}
