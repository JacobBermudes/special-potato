package org.amnezia.awg.model

import com.google.gson.annotations.SerializedName

data class HandshakeResponse(
    @SerializedName("configs")
    val configs: List<RemoteConfig>
)

data class RemoteConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("config")
    val configContent: String
)
