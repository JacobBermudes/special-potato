package org.amnezia.awg.model

import com.google.gson.annotations.SerializedName
import androidx.annotation.Keep

@Keep
data class HandshakeResponse(
    @SerializedName("token")
    val token: String
)
