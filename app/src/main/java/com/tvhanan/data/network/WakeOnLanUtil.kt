package com.tvhanan.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import android.util.Log

object WakeOnLanUtil {

    private const val TAG = "WakeOnLanUtil"
    private val WOL_PORTS = listOf(9, 7)

    suspend fun sendWakeOnLan(macAddress: String, broadcastIp: String = "255.255.255.255"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val macBytes = parseMacAddress(macAddress)
                val packetBytes = ByteArray(6 + 16 * macBytes.size)

                for (i in 0 until 6) {
                    packetBytes[i] = 0xFF.toByte()
                }
                for (i in 0 until 16) {
                    System.arraycopy(macBytes, 0, packetBytes, 6 + i * macBytes.size, macBytes.size)
                }

                val targetIp = if (broadcastIp == "255.255.255.255") {
                    getRealSubnetBroadcast()
                } else {
                    broadcastIp
                }

                val address = InetAddress.getByName(targetIp)
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    WOL_PORTS.forEach { port ->
                        val packet = DatagramPacket(packetBytes, packetBytes.size, address, port)
                        socket.send(packet)
                    }
                }
                Log.d(TAG, "Magic packet sent to $macAddress via $targetIp")
                true
            } catch (e: Exception) {
                Log.e(TAG, "sendWakeOnLan failed: ${e.message}")
                false
            }
        }

    suspend fun sendWakeOnLanWithRetry(
        macAddress: String,
        broadcastIp: String = "255.255.255.255",
        attempts: Int = 5,
        intervalMillis: Long = 2000
    ): Boolean = withContext(Dispatchers.IO) {
        repeat(attempts) { attempt ->
            val sent = sendWakeOnLan(macAddress, broadcastIp)
            if (sent) {
                Log.d(TAG, "Attempt ${attempt + 1}/$attempts sent")
                return@withContext true
            }
            if (attempt < attempts - 1) {
                kotlinx.coroutines.delay(intervalMillis)
            }
        }
        false
    }

    private fun getRealSubnetBroadcast(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (networkInterface in interfaces) {
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                for (interfaceAddress in networkInterface.interfaceAddresses) {
                    val broadcast = interfaceAddress.broadcast
                    if (broadcast != null && broadcast is Inet4Address) {
                        return broadcast.hostAddress ?: "255.255.255.255"
                    }
                }
            }
        } catch (_: Exception) {}
        return "255.255.255.255"
    }

    private fun parseMacAddress(mac: String): ByteArray {
        val hex = mac.replace(":", "").replace("-", "").replace(" ", "").uppercase()
        if (hex.length != 12) {
            throw IllegalArgumentException("Alamat MAC harus terdiri dari 12 karakter heksadesimal")
        }
        return ByteArray(6) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
