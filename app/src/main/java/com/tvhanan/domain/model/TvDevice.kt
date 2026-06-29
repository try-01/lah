package com.tvhanan.domain.model

data class TvDevice(
    val ipAddress: String,
    val name: String = "Samsung TV",
    val macAddress: String? = null,
    val port: Int = 8002, // Prioritaskan 8002 sebagai port bawaan
    val token: String? = null
)
