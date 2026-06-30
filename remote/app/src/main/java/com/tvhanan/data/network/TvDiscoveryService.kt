package com.tvhanan.data.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.tvhanan.domain.model.TvDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException

class TvDiscoveryService(context: Context) {
    private val context: Context = context.applicationContext

    companion object {
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_TIMEOUT = 4000L
        private const val SCAN_TIMEOUT = 300
        private const val TAG = "TvDiscoveryService"
    }

    suspend fun discoverDevices(): List<TvDevice> =
        withContext(Dispatchers.IO) {
            val ssdpResults = discoverSSDP()
            if (ssdpResults.isNotEmpty()) return@withContext ssdpResults

            val subnet = getLocalIpPrefix() ?: return@withContext emptyList()
            scanSubnet(subnet)
        }

/**
     * Ambil info dasar TV (nama model, MAC wifi asli) lewat endpoint
     * HTTP /api/v2/ yang tidak butuh pairing/token sama sekali — berguna
     * untuk menampilkan nama TV yang sebenarnya di hasil scan, bukan
     * generik "Samsung TV". Dipanggil setelah port terbuka terdeteksi.
     */
    private suspend fun fetchDeviceInfo(ip: String): Pair<String, String?>? =
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("http://$ip:8001/api/v2/")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.requestMethod = "GET"

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                    connection.disconnect()
                    return@withContext null
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = org.json.JSONObject(body)
                val device = json.optJSONObject("device") ?: return@withContext null
                val name = device.optString("name", "Samsung TV").removePrefix("[TV] ")
                val mac = device.optString("wifiMac", device.optString("mac", "")).ifBlank { null }

                name to mac
            } catch (e: Exception) {
                Log.e(TAG, "fetchDeviceInfo failed for $ip: ${e.message}")
                null
            }
        }

    /**
     * Cek apakah sebuah host:port bisa dijangkau (TCP connect singkat).
     * Dipakai untuk "Hubungkan ulang TV" di Settings — verifikasi cepat
     * sebelum RemoteScreen mencoba membuka WebSocket sesungguhnya.
     */
    suspend fun isHostReachable(
        ip: String,
        port: Int,
    ): Boolean =
        withContext(Dispatchers.IO) {
            isPortOpen(ip, port)
        }

    private suspend fun discoverSSDP(): List<TvDevice> {
        return withContext(Dispatchers.IO) {
            val multicastLock = acquireMulticastLock()

            try {
                val results = mutableListOf<TvDevice>()
                DatagramSocket().use { socket ->
                    socket.soTimeout = SSDP_TIMEOUT.toInt()

                    val ssdpRequest =
                        buildString {
                            append("M-SEARCH * HTTP/1.1\r\n")
                            append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                            append("MAN: \"ssdp:discover\"\r\n")
                            append("ST: urn:samsung.com:device:RemoteControlReceiver:1\r\n")
                            append("MX: 3\r\n")
                            append("\r\n")
                        }

                    val sendPacket =
                        DatagramPacket(
                            ssdpRequest.toByteArray(),
                            ssdpRequest.length,
                            InetAddress.getByName(SSDP_ADDR),
                            SSDP_PORT,
                        )
                    socket.send(sendPacket)

                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < SSDP_TIMEOUT) {
                        try {
                            val buffer = ByteArray(1024)
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)

                            val response = String(packet.data, 0, packet.length)
                            val ip = parseLocationIp(response)
                            if (ip != null && !results.any { it.ipAddress == ip }) {
                                val info = fetchDeviceInfo(ip)
                                results.add(
                                    TvDevice(ipAddress = ip, name = info?.first ?: "Samsung TV", macAddress = info?.second),
                                )
                            }
                        } catch (_: SocketTimeoutException) {
                            break
                        } catch (_: Exception) {
                            continue
                        }
                    }
                }
                results
            } finally {
                releaseMulticastLock(multicastLock)
            }
        }
    }

    private suspend fun scanSubnet(prefix: String): List<TvDevice> =
        coroutineScope {
            val semaphore = Semaphore(20)
            (1..254).map { octet ->
                async {
                    semaphore.withPermit {
                        val ip = "$prefix.$octet"
                        val openPort =
                            when {
                                isPortOpen(ip, 8002) -> 8002
                                isPortOpen(ip, 8001) -> 8001
                                else -> null
                            }
                        if (openPort != null) {
                            val info = fetchDeviceInfo(ip)
                            TvDevice(
                                ipAddress = ip,
                                name = info?.first ?: "Samsung TV",
                                macAddress = info?.second,
                                port = openPort,
                            )
                        } else {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }

    private fun isPortOpen(
        ip: String,
        port: Int,
    ): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), SCAN_TIMEOUT)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getLocalIpPrefix(): String? {
        return try {
            // Berikan penanganan null-safety jika sistem mengembalikan nilai null saat tidak ada interface aktif
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()

            // Filter interface aktif, lalu prioritaskan Wi-Fi (wlan0, wlan1, dst) di urutan pertama
            val activeInterfaces =
                interfaces
                    .filter { !it.isLoopback && it.isUp }
                    .sortedByDescending { it.name.startsWith("wlan") }

            for (networkInterface in activeInterfaces) {
                val addresses = networkInterface.inetAddresses.toList()
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        return ip.substringBeforeLast(".") // Berhasil mengunci prefix jaringan Wi-Fi
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        return try {
            val wifi =
                context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
            val lock = wifi.createMulticastLock("tvhanan_ssdp")
            lock.setReferenceCounted(false)
            lock.acquire()
            lock
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseMulticastLock(lock: WifiManager.MulticastLock?) {
        try {
            lock?.release()
        } catch (_: Exception) {
        }
    }

    private fun parseLocationIp(response: String): String? {
        val locationHeader =
            response.lines().firstOrNull {
                it.startsWith("LOCATION:", ignoreCase = true)
            } ?: return null

        val url = locationHeader.substringAfter(":").trim()
        return try {
            val host =
                InetAddress.getByName(
                    url.removePrefix("http://").removePrefix("https://")
                        .substringBefore("/").substringBefore(":"),
                )
            host.hostAddress
        } catch (_: Exception) {
            null
        }
    }
}
