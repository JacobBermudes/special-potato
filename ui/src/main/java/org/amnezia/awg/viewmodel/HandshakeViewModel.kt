package org.amnezia.awg.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.amnezia.awg.Application as AwgApplication
import org.amnezia.awg.config.Config
import org.amnezia.awg.model.HandshakeRequest
import org.amnezia.awg.model.RemoteConfig
import org.amnezia.awg.util.DeviceUtils
import org.amnezia.awg.util.RetrofitClient
import org.amnezia.awg.util.UserKnobs
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.delay

class HandshakeViewModel(application: Application) : AndroidViewModel(application) {

    private val _handshakeResult = MutableLiveData<Boolean>()
    val handshakeResult: LiveData<Boolean> get() = _handshakeResult

    fun performHandshake() {
        val deviceId = DeviceUtils.getDeviceId(getApplication())
        val request = HandshakeRequest(deviceId)

        viewModelScope.launch {

            val maxAttempts = 4
            val delayMillis = 500L

            for (attempt in 1..maxAttempts) {
                try {
                    // Step 1: POST /handshake
                    val handshakeResponse = RetrofitClient.apiService.sendHandshake(request)

                    if (handshakeResponse.isSuccessful) {
                        val token = handshakeResponse.body()?.token

                        if (token != null) {
                            UserKnobs.setAuthToken(token)

                            // Step 2: GET /servers
                            val serversResponse = RetrofitClient.apiService.getServers("Bearer $token")

                            if (serversResponse.isSuccessful && serversResponse.body() != null) {
                                processConfigs(serversResponse.body()!!.configs)
                                _handshakeResult.postValue(true)
                                return@launch
                            }
                        }
                    }
                    Log.w("HandshakeViewModel", "Attempt $attempt failed")
                } catch (e: Exception) {
                    Log.e("HandshakeViewModel", "Network error on attempt $attempt", e)
                }

                if (attempt < maxAttempts) {
                    delay(delayMillis)
                }
            }

            _handshakeResult.postValue(false)

        }
    }

    private suspend fun processConfigs(remoteConfigs: List<RemoteConfig>) {
        withContext(Dispatchers.IO) {
            val tunnelManager = AwgApplication.getTunnelManager()
            val existingTunnels = tunnelManager.getTunnels()

            // 1. Identify which tunnels to remove
            // We remove tunnels that are currently in the app but NOT in the new list from the server
            val remoteNames = remoteConfigs.map { it.name }.toSet()
            val tunnelsToRemove = existingTunnels.filter { !remoteNames.contains(it.name) }

            tunnelsToRemove.forEach { tunnel ->
                try {
                    Log.d("HandshakeViewModel", "Removing old tunnel: ${tunnel.name}")
                    tunnelManager.delete(tunnel)
                } catch (e: Exception) {
                    Log.e("HandshakeViewModel", "Failed to delete old tunnel: ${tunnel.name}", e)
                }
            }

            // 2. Update or create tunnels from the new list
            remoteConfigs.forEach { remote ->
                try {
                    val configStream = ByteArrayInputStream(remote.configContent.toByteArray(StandardCharsets.UTF_8))
                    val config = Config.parse(configStream)
                    
                    if (existingTunnels.containsKey(remote.name)) {
                        val tunnel = existingTunnels[remote.name]
                        if (tunnel != null) {
                            Log.d("HandshakeViewModel", "Updating existing tunnel: ${remote.name}")
                            tunnelManager.setTunnelConfig(tunnel, config)
                        }
                    } else {
                        Log.d("HandshakeViewModel", "Creating new tunnel: ${remote.name}")
                        tunnelManager.create(remote.name, config)
                    }
                } catch (e: Exception) {
                    Log.e("HandshakeViewModel", "Failed to parse or save config: ${remote.name}", e)
                }
            }
        }
    }
}
