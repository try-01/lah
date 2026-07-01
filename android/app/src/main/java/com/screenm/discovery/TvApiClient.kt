package com.screenm.discovery

import android.util.Log
import com.screenm.model.DeviceInfo
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class TvApiClient {

    fun getDeviceInfo(ip: String): DeviceInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("http://$ip:8001/api/v2/").openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                Log.w(TAG, "GET $ip/api/v2/ returned HTTP $responseCode")
                return null
            }
            val response = conn.inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(response)
            val device = json.optJSONObject("device")
            val id = json.optString("id", "")
            val name = device?.optString("name", "")?.ifEmpty { null }
                ?: json.optString("name", "Samsung TV")
            val wifiMac = device?.optString("wifiMac", "") ?: ""
            val modelName = device?.optString("modelName", "") ?: ""
            val type = device?.optString("type", "") ?: ""
            val parseVersion = device?.optString("version", "") ?: ""
            val mac = wifiMac.ifEmpty { id }

            Log.i(TAG, "Found device: $name @ $ip (MAC: $mac, model: $modelName)")

            DeviceInfo(
                id = mac.ifEmpty { ip },
                name = name,
                ipAddress = ip,
                macAddress = mac.ifEmpty { ip },
                modelName = modelName.ifEmpty { type },
                tizenVersion = parseVersion,
                port = 8001
            )
        } catch (e: Exception) {
            Log.d(TAG, "No device at $ip:8001 — ${e.message}")
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    fun launchApp(ip: String, appId: String, metaTag: String? = null): Boolean {
        return try {
            val url = URL("http://$ip:8001/api/v2/applications/$appId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 5000
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("appId", appId)
                if (metaTag != null) {
                    put("meta_tag", metaTag)
                }
            }
            val bodyStr = body.toString()
            Log.d(TAG, "POST $ip/api/v2/applications/$appId body=$bodyStr")
            OutputStreamWriter(conn.outputStream).use { it.write(bodyStr) }

            val code = conn.responseCode
            val responseBody = try {
                conn.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            }
            conn.disconnect()
            Log.i(TAG, "Launch app response: HTTP $code — $responseBody")
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch app $appId on $ip: ${e.message}")
            false
        }
    }

    fun closeApp(ip: String, appId: String): Boolean {
        return try {
            val url = URL("http://$ip:8001/api/v2/applications/$appId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close app $appId on $ip: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "TvApiClient"
    }
}
