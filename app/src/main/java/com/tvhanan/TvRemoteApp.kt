package com.tvhanan

import android.app.Application
import com.tvhanan.di.ServiceLocator
import com.tvhanan.util.HapticUtil

class TvRemoteApp : Application() {

    lateinit var serviceLocator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        serviceLocator = ServiceLocator(this)
        HapticUtil.init(this)
    }
}
