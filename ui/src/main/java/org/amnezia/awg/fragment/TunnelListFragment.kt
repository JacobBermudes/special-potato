/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.databinding.TunnelListFragmentBinding
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.AutoConnector
import org.amnezia.awg.util.HardcodedConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simplified fragment for connecting to hardcoded AmneziaWG tunnels with custom image button.
 */
class TunnelListFragment : BaseFragment() {
    private var binding: TunnelListFragmentBinding? = null
    private var currentProfileIndex = 0
    
    private val vpnPermissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lifecycleScope.launch {
            val success = AutoConnector.connectWithHardcodedConfig(HardcodedConfig.PROFILES[currentProfileIndex])
            if (success) {
                showSnackbar("Connected to VPN")
            } else {
                showSnackbar("Failed to connect")
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupProfileSpinner()
        
        lifecycleScope.launch {
            try {
                val tunnelManager = Application.getTunnelManager()
                
                // Initialize profiles if needed
                HardcodedConfig.PROFILES.forEach { profile ->
                    val tunnels = tunnelManager.getTunnels()
                    if (tunnels.find { it.name == profile.name } == null) {
                        AutoConnector.connectWithHardcodedConfig(profile)
                        val tunnel = tunnelManager.getTunnels().find { it.name == profile.name }
                        if (tunnel != null && tunnel.state != Tunnel.State.DOWN) {
                            tunnelManager.setTunnelState(tunnel, Tunnel.State.DOWN)
                        }
                    }
                }

                while (isAdded) {
                    val currentProfile = HardcodedConfig.PROFILES[currentProfileIndex]
                    val tunnels = tunnelManager.getTunnels()
                    val tunnel = tunnels.find { it.name == currentProfile.name }
                    if (tunnel != null) {
                        updateStatusUi(tunnel)
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring tunnel state", e)
            }
        }
    }

    private fun setupProfileSpinner() {
        val binding = binding ?: return
        val profileNames = HardcodedConfig.PROFILES.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, profileNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.profileSpinner.adapter = adapter
        
        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentProfileIndex = position
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelListFragmentBinding.inflate(inflater, container, false)
        binding?.apply {
            vpnButton.setOnClickListener {
                lifecycleScope.launch {
                    val currentProfile = HardcodedConfig.PROFILES[currentProfileIndex]
                    val tunnels = Application.getTunnelManager().getTunnels()
                    val tunnel = tunnels.find { it.name == currentProfile.name }
                    
                    if (tunnel != null && tunnel.state == Tunnel.State.UP) {
                        try {
                            AutoConnector.disconnectHardcodedConfig(currentProfile.name)
                        } catch (e: Throwable) {
                            showSnackbar("Failed to disconnect")
                        }
                    } else {
                        // Disconnect others
                        tunnels.forEach {
                            if (it.state == Tunnel.State.UP) {
                                Application.getTunnelManager().setTunnelState(it, Tunnel.State.DOWN)
                            }
                        }

                        val activity = requireActivity()
                        if (Application.getBackend() is GoBackend) {
                            try {
                                val intent = GoBackend.VpnService.prepare(activity)
                                if (intent != null) {
                                    vpnPermissionActivityResultLauncher.launch(intent)
                                    return@launch
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "VPN Prepare error", e)
                            }
                        }
                        try {
                            AutoConnector.connectWithHardcodedConfig(currentProfile)
                        } catch (e: Throwable) {
                            showSnackbar("Failed to connect")
                        }
                    }
                }
            }
        }
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {}

    private fun showSnackbar(message: CharSequence) {
        val binding = binding
        if (binding != null)
            Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG)
                .setAnchorView(binding.profileSpinner)
                .show()
    }

    private fun updateStatusUi(tunnel: ObservableTunnel) {
        val binding = binding ?: return
        val (statusTxt, alpha) = when {
            tunnel.state == Tunnel.State.UP && tunnel.connectionStatus == ObservableTunnel.ConnectionStatus.CONNECTED -> 
                Pair("Connected", 1.0f)
            tunnel.state == Tunnel.State.UP -> 
                Pair("Connecting...", 0.5f)
            else -> 
                Pair("Disconnected", 0.3f)
        }
        
        binding.statusText.text = statusTxt
        // Эффект нажатия/активности через прозрачность картинки
        binding.vpnButton.alpha = alpha
    }

    companion object {
        private const val TAG = "AmneziaWG/TunnelListFragment"
    }
}
