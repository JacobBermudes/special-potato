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
import org.amnezia.awg.util.DeviceUtils
import org.amnezia.awg.util.RetrofitClient
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class HandshakeViewModel(application: Application) : AndroidViewModel(application) {

    private val _handshakeResult = MutableLiveData<Boolean>()
    val handshakeResult: LiveData<Boolean> get() = _handshakeResult

    fun performHandshake() {
        val deviceId = DeviceUtils.getDeviceId(getApplication())
        val request = HandshakeRequest(deviceId)

        viewModelScope.launch {
            try {
                val response = RetrofitClient.apiService.sendHandshake(request)
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null) {
                        processConfigs(body.configs)
                    }
                    _handshakeResult.postValue(true)
                } else {
                    _handshakeResult.postValue(false)
                }
            } catch (e: Exception) {
                Log.e("HandshakeViewModel", "Error during handshake", e)
                _handshakeResult.postValue(false)
            }
        }
    }

    private suspend fun processConfigs(remoteConfigs: List<org.amnezia.awg.model.RemoteConfig>) {
        withContext(Dispatchers.IO) {
            val tunnelManager = AwgApplication.getTunnelManager()
            val existingTunnels = tunnelManager.getTunnels()

            remoteConfigs.forEach { remote ->
                try {
                    val configStream = ByteArrayInputStream(remote.configContent.toByteArray(StandardCharsets.UTF_8))
                    val config = Config.parse(configStream)
                    
                    if (existingTunnels.containsKey(remote.name)) {
                        val tunnel = existingTunnels[remote.name]
                        if (tunnel != null) {
                            tunnelManager.setTunnelConfig(tunnel, config)
                        }
                    } else {
                        tunnelManager.create(remote.name, config)
                    }
                } catch (e: Exception) {
                    Log.e("HandshakeViewModel", "Failed to parse or save config: ${remote.name}", e)
                }
            }
        }
    }
}
