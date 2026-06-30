package com.tvhanan.di

import android.content.Context
import com.tvhanan.data.local.TvPreferences
import com.tvhanan.data.network.SslTrustManager
import com.tvhanan.data.network.TvDiscoveryService
import com.tvhanan.data.network.TvWebSocketClient
import com.tvhanan.data.repository.TvRepositoryImpl
import com.tvhanan.domain.repository.TvRepository

class ServiceLocator(context: Context) {
    private val context: Context = context.applicationContext

    val preferences: TvPreferences by lazy { TvPreferences(context) }
    val discoveryService: TvDiscoveryService by lazy { TvDiscoveryService(context) }
    val sslTrustManager: SslTrustManager by lazy { SslTrustManager(preferences) }
    val webSocketClient: TvWebSocketClient by lazy { TvWebSocketClient(sslTrustManager) }
    val repository: TvRepository by lazy {
        TvRepositoryImpl(discoveryService, webSocketClient, preferences)
    }
}
