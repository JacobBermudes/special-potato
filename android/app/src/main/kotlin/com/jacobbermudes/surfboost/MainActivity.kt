package com.jacobbermudes.surfboost

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import org.amnezia.awg.backend.Backend
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config

class MainActivity : FlutterActivity() {
    private val VPN_CHANNEL = "com.jacobbermudes.surfboost/vpn_channel"
    private lateinit var channel: MethodChannel
    
    private lateinit var backend: Backend
    
    private val myTunnel = object : Tunnel {
        override fun getName() = "AmneziaSimple"
        override fun onStateChange(newState: Tunnel.State) {
            val statusStr = if (newState == Tunnel.State.UP) "Подключено" else "Отключено"
            runOnUiThread {
                channel.invokeMethod("onVpnStatusChanged", statusStr)
            }
        }
    }

    private var pendingConfig: String? = null
    private val VPN_REQUEST_CODE = 1001

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        backend = GoBackend(applicationContext)
        
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, VPN_CHANNEL)
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startVpn" -> {
                    val configString = call.argument<String>("config")
                    if (configString != null) {
                        connectToVpn(configString)
                        result.success(null)
                    } else {
                        result.error("ERR", "Нет конфига", null)
                    }
                }
                "stopVpn" -> {
                    disconnectVpn()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun connectToVpn(configStr: String) {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingConfig = configStr
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            startAmneziaEngine(configStr)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && pendingConfig != null) {
                startAmneziaEngine(pendingConfig!!)
                pendingConfig = null
            } else {
                channel.invokeMethod("onVpnStatusChanged", "В доступе отказано")
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun startAmneziaEngine(configStr: String) {
        try {
            val inputStream = configStr.byteInputStream()
            val config = Config.parse(inputStream)

            Thread {
                try {
                    backend.setState(myTunnel, Tunnel.State.UP, config)
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThread { channel.invokeMethod("onVpnStatusChanged", "Ошибка: ${e.message}") }
                }
            }.start()

        } catch (e: Exception) {
             channel.invokeMethod("onVpnStatusChanged", "Ошибка парсинга конфига")
        }
    }

    private fun disconnectVpn() {
         Thread {
             try {
                 backend.setState(myTunnel, Tunnel.State.DOWN, null)
             } catch (e: Exception) {
                 e.printStackTrace()
             }
         }.start()
    }
}