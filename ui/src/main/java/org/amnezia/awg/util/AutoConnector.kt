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

    suspend fun connectWithHardcodedConfig(profile: HardcodedConfig.VpnProfile = HardcodedConfig.PROFILES[0]): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
            Log.d(TAG, "Starting connection with profile: ${profile.name}")
            
            val tunnelManager = Application.getTunnelManager()
            val tunnels = tunnelManager.getTunnels()

            // Check if tunnel already exists
            var tunnel = tunnels.find { it.name == profile.name }

            // Create tunnel if it doesn't exist
            if (tunnel == null) {
                Log.d(TAG, "Creating tunnel: ${profile.name}")
                try {
                    val config = parseConfig(profile.config)
                    tunnel = tunnelManager.create(profile.name, config)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse or create config", e)
                    return@withContext false
                }
            }

            // Connect the tunnel
            if (tunnel.state != Tunnel.State.UP) {
                Log.d(TAG, "Connecting tunnel: ${profile.name}")
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to set tunnel state", e)
                    return@withContext false
                }
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect with hardcoded config", e)
            return@withContext false
        }
    }

    suspend fun disconnectHardcodedConfig(profileName: String): Boolean = withContext(Dispatchers.Main.immediate) {
        try {
            val tunnelManager = Application.getTunnelManager()
            val tunnels = tunnelManager.getTunnels()
            val tunnel = tunnels.find { it.name == profileName }

            if (tunnel != null && tunnel.state == Tunnel.State.UP) {
                Log.d(TAG, "Disconnecting tunnel: $profileName")
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
