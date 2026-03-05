package org.amnezia.awg.model

import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep

@Keep
data class ServersResponse(
    @SerializedName("configs")
    val configs: List<RemoteConfig>
)

@Keep
data class RemoteConfig(
    @SerializedName("name")
    val name: String,
    @SerializedName("config")
    val configContent: String
)
