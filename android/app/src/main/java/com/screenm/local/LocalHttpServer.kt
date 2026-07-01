package com.screenm.local

import android.content.Context
import android.util.Log
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class LocalHttpServer(private val context: Context, private val port: Int = 8081) {

    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var isRunning = false

    private val mimeTypes = mapOf(
        ".html" to "text/html; charset=utf-8",
        ".js" to "application/javascript; charset=utf-8",
        ".css" to "text/css; charset=utf-8",
        ".json" to "application/json",
        ".png" to "image/png",
        ".jpg" to "image/jpeg",
        ".svg" to "image/svg+xml"
    )

    val localIp: String? by lazy { findLocalIp() }

    fun start(): Boolean {
        if (isRunning) return true
        return try {
            serverSocket = ServerSocket(port, 10)
            isRunning = true
            executor.submit { acceptLoop() }
            Log.i(TAG, "HTTP server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start HTTP server: ${e.message}")
            false
        }
    }

    private fun acceptLoop() {
        try {
            while (isRunning) {
                val client = serverSocket?.accept() ?: break
                executor.submit { handleClient(client) }
            }
        } catch (_: Exception) {}
    }

    private fun handleClient(client: Socket) {
        try {
            client.use { sock ->
                val request = sock.getInputStream().bufferedReader().use { it.readLine() } ?: return
                if (!request.startsWith("GET ")) return

                val path = URLDecoder.decode(
                    request.split(" ").getOrElse(1) { "/" },
                    "UTF-8"
                ).removePrefix("/").ifEmpty { "receiver.html" }

                val ext = path.substringAfterLast('.', "")
                val contentType = mimeTypes[".$ext"] ?: "application/octet-stream"

                val data = loadFile(path)
                if (data != null) {
                    sendResponse(sock.getOutputStream(), "200 OK", contentType, data)
                } else {
                    sendResponse(sock.getOutputStream(), "404 Not Found", "text/plain", "404".toByteArray())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP client error: ${e.message}")
        }
    }

    private fun loadFile(path: String): ByteArray? {
        val assetPath = "receiver/$path"
        return try {
            context.assets.open(assetPath).use { it.readBytes() }
        } catch (_: Exception) {
            try {
                context.assets.open(path).use { it.readBytes() }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun sendResponse(out: OutputStream, status: String, contentType: String, data: ByteArray) {
        val headers = "HTTP/1.1 $status\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "\r\n"
        out.write(headers.toByteArray())
        out.write(data)
        out.flush()
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        executor.shutdownNow()
    }

    private fun findLocalIp(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            for (network in interfaces) {
                if (network.isLoopback || !network.isUp) continue
                if (network.name.startsWith("ap") || network.name.startsWith("p2p")) continue
                val addresses = network.inetAddresses ?: continue
                for (addr in addresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val ip = addr.hostAddress
                        if (ip != null && ip.startsWith("192.168.")) return ip
                        if (ip != null) return ip
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    companion object {
        private const val TAG = "HttpServer"
    }
}
