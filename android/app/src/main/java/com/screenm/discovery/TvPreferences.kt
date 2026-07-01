package com.screenm.discovery

import android.content.Context
import android.content.SharedPreferences

class TvPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val crypto = CryptoUtil()

    fun saveToken(mac: String, token: String) {
        val encrypted = crypto.encrypt(token)
        prefs.edit().putString("token_${mac}", encrypted).apply()
    }

    fun getToken(mac: String): String? {
        val encrypted = prefs.getString("token_${mac}", null) ?: return null
        return crypto.decrypt(encrypted)
    }

    fun saveFingerprint(mac: String, fingerprint: String) {
        prefs.edit().putString("fingerprint_${mac}", fingerprint).apply()
    }

    fun getFingerprint(mac: String): String? {
        return prefs.getString("fingerprint_${mac}", null)
    }

    fun removeDevice(mac: String) {
        prefs.edit()
            .remove("token_${mac}")
            .remove("fingerprint_${mac}")
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "screenm_tv_preferences"
    }
}
