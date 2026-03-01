package org.amnezia.awg.model

import com.google.gson.annotations.SerializedName

data class HandshakeRequest(
    @SerializedName("device_id")
    val deviceId: String
)
