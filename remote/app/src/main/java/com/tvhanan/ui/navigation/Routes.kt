package com.tvhanan.ui.navigation

import com.tvhanan.domain.model.TvDevice

object Routes {
    const val SCAN = "scan"
    const val MANUAL = "manual"
    const val REMOTE = "remote/{ip}/{port}?mac={mac}"
    const val SETTINGS = "settings"

    fun remoteRoute(device: TvDevice) = "remote/${device.ipAddress}/${device.port}?mac=${device.macAddress.orEmpty()}"

    fun remoteRoute(
        ip: String,
        port: Int = 8002,
        mac: String? = null,
    ) = "remote/$ip/$port?mac=${mac.orEmpty()}"
}
