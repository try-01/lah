package com.tvhanan.data.network

import com.tvhanan.domain.model.RemoteKey
import org.json.JSONObject

object SamsungKeyMapper {

    private const val REMOTE_CONTROL_CHANNEL = "ms.remote.control"

    fun createKeyPressPayload(key: RemoteKey): String {
        return JSONObject().apply {
            put("method", REMOTE_CONTROL_CHANNEL)
            put("params", JSONObject().apply {
                put("Cmd", "Click")
                put("DataOfCmd", key.keyCode)
                put("Option", "false")
                put("TypeOfRemote", "SendRemoteKey")
            })
        }.toString()
    }
}
