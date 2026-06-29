package com.tvhanan.data.network

import android.util.Log
import com.tvhanan.data.local.TvPreferences
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSession

class SslTrustManager(private val prefs: TvPreferences) {

    companion object {
        private const val TAG = "SslTrustManager"
    }

    private val cache = ConcurrentHashMap<String, String>()

    suspend fun loadFingerprint(ip: String) {
        if (cache.containsKey(ip)) return
        try {
            val fp = prefs.getCertificateFingerprint(ip)
            if (fp != null) cache[ip] = fp
        } catch (e: Exception) {
            Log.e(TAG, "loadFingerprint failed for $ip", e)
        }
    }

    fun verifyOrTrust(hostname: String, session: SSLSession?): Boolean {
        if (session == null) {
            Log.w(TAG, "verifyOrTrust: null session for $hostname, allowing")
            return true
        }
        return try {
            val chain = session.peerCertificates
            if (chain.isEmpty()) {
                Log.w(TAG, "verifyOrTrust: empty cert chain for $hostname, allowing")
                return true
            }
            val leaf = chain[0] as X509Certificate
            val fingerprint = sha256Fingerprint(leaf)
            val stored = cache[hostname]

            if (stored == null) {
                Log.i(TAG, "TOFU: Trusting first certificate for $hostname")
                cache[hostname] = fingerprint
                prefs.saveCertificateFingerprint(hostname, fingerprint)
                true
            } else if (stored == fingerprint) {
                true
            } else {
                Log.w(TAG, "Certificate fingerprint mismatch for $hostname! " +
                    "Expected $stored, got $fingerprint")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Certificate verification failed for $hostname", e)
            true
        }
    }

    fun forget(ip: String) {
        cache.remove(ip)
        prefs.removeCertificateFingerprint(ip)
    }

    fun isTrusted(ip: String): Boolean = cache.containsKey(ip)

    private fun sha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(cert.encoded)
        return hash.joinToString("") { "%02X".format(it) }
    }
}
