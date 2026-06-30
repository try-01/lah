package com.tvhanan.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tvhanan.util.CryptoUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_settings")

class TvPreferences(context: Context) {
    private val context: Context = context.applicationContext
    private val cryptoUtil = CryptoUtil()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Helper untuk menangkap error I/O agar app tidak Force Close
    private val Flow<Preferences>.safeData: Flow<Preferences>
        get() =
            this.catch { exception ->
                if (exception is IOException) emit(emptyPreferences()) else throw exception
            }

    companion object {
        private val KEY_LAST_IP = stringPreferencesKey("last_ip")
        private val KEY_LAST_PORT = stringPreferencesKey("last_port")
        private val KEY_TOKEN = stringPreferencesKey("token")
        private val KEY_MAC_ADDRESS = stringPreferencesKey("mac_address")

        private fun certFingerprintKey(ip: String) = stringPreferencesKey("cert_fp_$ip")
    }

    val lastIp: Flow<String?> = context.dataStore.data.safeData.map { it[KEY_LAST_IP] }
    val lastPort: Flow<String?> = context.dataStore.data.safeData.map { it[KEY_LAST_PORT] }
    val token: Flow<String?> = context.dataStore.data.safeData.map { it[KEY_TOKEN] }
    val macAddress: Flow<String?> = context.dataStore.data.safeData.map { it[KEY_MAC_ADDRESS] }

    suspend fun saveLastIp(ip: String) {
        context.dataStore.edit { it[KEY_LAST_IP] = ip }
    }

    suspend fun saveLastPort(port: String) {
        context.dataStore.edit { it[KEY_LAST_PORT] = port }
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = cryptoUtil.encrypt(token) }
    }

    suspend fun saveMacAddress(mac: String) {
        context.dataStore.edit { it[KEY_MAC_ADDRESS] = mac }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.safeData.first()[KEY_TOKEN]?.let { cryptoUtil.decrypt(it) }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    // ===== Certificate Fingerprint (TOFU SSL) =====

    // Suspend version — dipanggil dari context coroutine (SslTrustManager.loadFingerprint)
    suspend fun getCertificateFingerprint(ip: String): String? {
        return context.dataStore.data.safeData.first()[certFingerprintKey(ip)]
    }

    // Fire-and-forget version — dipanggil dari OkHttp thread (hostnameVerifier, non-suspend)
    // In-memory cache sudah diupdate sebelum fungsi ini dipanggil, jadi async save aman.
    fun saveCertificateFingerprint(
        ip: String,
        fingerprint: String,
    ) {
        scope.launch {
            context.dataStore.edit { it[certFingerprintKey(ip)] = fingerprint }
        }
    }

    fun removeCertificateFingerprint(ip: String) {
        scope.launch {
            context.dataStore.edit { it.remove(certFingerprintKey(ip)) }
        }
    }
}
