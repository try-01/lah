package com.tvhanan.domain.model

data class TvDevice(
    val ipAddress: String,
    val name: String = "Samsung TV",
    val macAddress: String? = null,
    // Prioritaskan 8002 sebagai port bawaan
    val port: Int = 8002,
    val token: String? = null,
)
