package com.screenm.discovery

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLanUtil {
    fun wake(macAddress: String) {
        try {
            val macBytes =
                parseMac(macAddress) ?: run {
                    Log.w(TAG, "Invalid MAC: $macAddress")
                    return
                }
            val payload = ByteArray(102)
            for (i in 0 until 6) payload[i] = 0xFF.toByte()
            for (i in 0 until 16) System.arraycopy(macBytes, 0, payload, 6 + i * 6, 6)

            val broadcast = InetAddress.getByName("255.255.255.255")
            val ports = intArrayOf(7, 9)

            DatagramSocket().use { sock ->
                sock.broadcast = true
                for (port in ports) {
                    try {
                        sock.send(DatagramPacket(payload, payload.size, broadcast, port))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WOL failed: ${e.message}")
        }
    }

    private fun parseMac(mac: String): ByteArray? {
        return try {
            val hex = mac.replace(":", "").replace("-", "").uppercase()
            if (hex.length != 12) return null
            ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        } catch (_: Exception) {
            null
        }
    }

    private const val TAG = "WakeOnLan"
}
