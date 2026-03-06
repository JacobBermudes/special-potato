package org.amnezia.awg.util

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object RetrofitClient {
    private const val HMAC_KEY = "abcd"
    private const val BASE_URL = "http://82.38.71.36:9090/"
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private class HmacInterceptor(private val context: Context, private val key: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val deviceId = DeviceUtils.getDeviceId(context)
            

            val dataToSign = timestamp + deviceId
            val signature = calculateHmac(dataToSign, key)

            val requestBuilder = original.newBuilder()
                .header("X-Timestamp", timestamp)
                .header("X-Device-Id", deviceId)
                .header("X-Signature", signature)
                .method(original.method, original.body)

            return chain.proceed(requestBuilder.build())
        }

        private fun calculateHmac(data: String, key: String): String {
            val sha256Hmac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
            sha256Hmac.init(secretKey)
            return sha256Hmac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor(HmacInterceptor(appContext, HMAC_KEY))
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
            .create(ApiService::class.java)
    }
}
