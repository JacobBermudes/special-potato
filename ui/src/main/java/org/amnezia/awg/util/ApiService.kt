package org.amnezia.awg.util

import org.amnezia.awg.model.HandshakeRequest
import org.amnezia.awg.model.HandshakeResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("handshake")
    suspend fun sendHandshake(@Body request: HandshakeRequest): Response<HandshakeResponse>
}
