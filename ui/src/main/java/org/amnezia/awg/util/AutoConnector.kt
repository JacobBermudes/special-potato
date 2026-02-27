/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

import android.util.Log
import org.amnezia.awg.Application
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.StringReader

/**
 * Utility for automatic VPN connection using hardcoded configuration
 */
object AutoConnector {
    private const val TAG = "AmneziaWG/AutoConnector"

    suspend fun connectWithHardcodedConfig(): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!HardcodedConfig.isConfigured()) {
            Log.w(TAG, "Hardcoded config not properly configured - contains placeholder values")
            return@withContext false
        }

        try {
            Log.d(TAG, "Starting connection with hardcoded config")
            Log.d(TAG, "Config: ${HardcodedConfig.HARDCODED_CONFIG}")
            
            val tunnelManager = Application.getTunnelManager()
            Log.d(TAG, "Got tunnel manager")
            
            val tunnels = tunnelManager.getTunnels()
            Log.d(TAG, "Got tunnels list, count: ${tunnels.size}")

            // Check if tunnel already exists
            var tunnel = tunnels.find { it.name == HardcodedConfig.TUNNEL_NAME }
            Log.d(TAG, "Existing tunnel found: ${tunnel != null}")

            // Create tunnel if it doesn't exist
            if (tunnel == null) {
                Log.d(TAG, "Creating tunnel: ${HardcodedConfig.TUNNEL_NAME}")
                try {
                    val config = parseConfig(HardcodedConfig.HARDCODED_CONFIG)
                    Log.d(TAG, "Config parsed successfully")
                    tunnel = tunnelManager.create(HardcodedConfig.TUNNEL_NAME, config)
                    Log.d(TAG, "Tunnel created successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse or create config", e)
                    return@withContext false
                }
            }

            // Connect the tunnel
            if (tunnel.state != Tunnel.State.UP) {
                Log.d(TAG, "Connecting tunnel: ${HardcodedConfig.TUNNEL_NAME}, current state: ${tunnel.state}")
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                    Log.d(TAG, "Tunnel state change requested")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set tunnel state", e)
                    return@withContext false
                }
            } else {
                Log.d(TAG, "Tunnel already UP")
            }

            Log.d(TAG, "Successfully connected to hardcoded VPN")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect with hardcoded config", e)
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun disconnectHardcodedConfig(): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
            val tunnelManager = Application.getTunnelManager()
            val tunnels = tunnelManager.getTunnels()
            val tunnel = tunnels.find { it.name == HardcodedConfig.TUNNEL_NAME }

            if (tunnel != null && tunnel.state == Tunnel.State.UP) {
                Log.d(TAG, "Disconnecting tunnel: ${HardcodedConfig.TUNNEL_NAME}")
                tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect hardcoded config", e)
            return@withContext false
        }
    }

    private suspend fun parseConfig(configText: String): Config = withContext(Dispatchers.IO) {
        Config.parse(BufferedReader(StringReader(configText)))
    }
}
