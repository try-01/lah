package com.tvhanan.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Meluncurkan app Smart Hub via REST API /api/v2/applications/{appId},
 * BUKAN lewat WebSocket ms.channel.emit. Ditemukan lewat percobaan
 * langsung ke TV: endpoint WebSocket (ed.apps.launch) tidak direspons
 * sama sekali oleh firmware TV ini (N-series 2020 / Tizen 5.0), tapi
 * REST POST ke endpoint applications berhasil membuka app langsung.
 *
 * Endpoint ini tidak butuh token/pairing — sama seperti GET /api/v2/
 * untuk info TV, ini bagian dari REST API publik TV yang tidak melalui
 * jalur otorisasi WebSocket remote.control.
 */
object AppLauncher {
    private const val TAG = "AppLauncher"

    suspend fun launch(
        ip: String,
        appId: String,
    ): Boolean = request(ip, appId, "POST", "launch")

    suspend fun close(
        ip: String,
        appId: String,
    ): Boolean = request(ip, appId, "DELETE", "close")

    private suspend fun request(
        ip: String,
        appId: String,
        method: String,
        label: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val connection = (
                URL("http://$ip:8001/api/v2/applications/$appId")
                    .openConnection() as HttpURLConnection
            )
            try {
                connection.requestMethod = method
                connection.connectTimeout = 4000
                connection.readTimeout = 4000

                val responseCode = connection.responseCode

                Log.d(TAG, "$label($appId) responseCode=$responseCode")
                responseCode in 200..299
            } catch (e: Exception) {
                Log.e(TAG, "$label($appId) failed: ${e.message}")
                false
            } finally {
                connection.disconnect()
            }
        }
}
