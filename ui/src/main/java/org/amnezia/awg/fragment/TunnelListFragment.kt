/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.ObservableList
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.databinding.TunnelListFragmentBinding
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.viewmodel.HandshakeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Simplified fragment for connecting to AmneziaWG tunnels.
 */
class TunnelListFragment : BaseFragment() {
    private var binding: TunnelListFragmentBinding? = null
    private var currentTunnelName: String? = null
    private val tunnelNames = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private val handshakeViewModel: HandshakeViewModel by viewModels()
    
    private val vpnPermissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val name = currentTunnelName ?: return@registerForActivityResult
        lifecycleScope.launch {
            val tunnelManager = Application.getTunnelManager()
            val tunnels = tunnelManager.getTunnels()
            val tunnel = tunnels.find { it.name == name }
            if (tunnel != null) {
                try {
                    tunnelManager.setTunnelState(tunnel, Tunnel.State.UP)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect", e)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupProfileSpinner()
        
        handshakeViewModel.handshakeResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Server list updated!", Toast.LENGTH_SHORT).show()
                updateSpinnerItems()
            } else {
                Toast.makeText(requireContext(), "Server list update fail", Toast.LENGTH_SHORT).show()
            }
            binding?.refreshButton?.isEnabled = true
            binding?.refreshButton?.alpha = 1.0f
        }

        lifecycleScope.launch {
            try {
                val tunnelManager = Application.getTunnelManager()
                val tunnels = tunnelManager.getTunnels()
                
                // Observe tunnel list changes
                tunnels.addOnListChangedCallback(object : ObservableList.OnListChangedCallback<ObservableList<ObservableTunnel>>() {
                    override fun onChanged(sender: ObservableList<ObservableTunnel>?) {
                        updateSpinnerItems()
                    }
                    override fun onItemRangeChanged(sender: ObservableList<ObservableTunnel>?, positionStart: Int, itemCount: Int) {
                        updateSpinnerItems()
                    }
                    override fun onItemRangeInserted(sender: ObservableList<ObservableTunnel>?, positionStart: Int, itemCount: Int) {
                        updateSpinnerItems()
                    }
                    override fun onItemRangeMoved(sender: ObservableList<ObservableTunnel>?, fromPosition: Int, toPosition: Int, itemCount: Int) {
                        updateSpinnerItems()
                    }
                    override fun onItemRangeRemoved(sender: ObservableList<ObservableTunnel>?, positionStart: Int, itemCount: Int) {
                        updateSpinnerItems()
                    }
                })

                updateSpinnerItems()

                while (isAdded) {
                    val name = currentTunnelName
                    if (name != null) {
                        val tunnel = tunnels.find { it.name == name }
                        if (tunnel != null) {
                            updateStatusUi(tunnel)
                        } else {
                            binding?.statusText?.text = "Unknown"
                        }
                    } else {
                        binding?.statusText?.text = "No tunnels"
                    }
                    delay(500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring tunnel state", e)
            }
        }
    }

    private fun updateSpinnerItems() {
        lifecycleScope.launch {
            val tunnels = Application.getTunnelManager().getTunnels()
            val oldSelection = currentTunnelName
            
            tunnelNames.clear()
            tunnels.forEach { tunnelNames.add(it.name) }
            
            if (tunnelNames.isEmpty()) {
                tunnelNames.add("No tunnels available")
            }
            
            adapter.notifyDataSetChanged()
            
            val newPosition = if (oldSelection != null) {
                val index = tunnelNames.indexOf(oldSelection)
                if (index != -1) index else 0
            } else 0
            
            if (tunnelNames.isNotEmpty() && tunnelNames[0] != "No tunnels available") {
                binding?.profileSpinner?.setSelection(newPosition)
                currentTunnelName = tunnelNames[newPosition]
            }
        }
    }

    private fun setupProfileSpinner() {
        val binding = binding ?: return
        adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, tunnelNames)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.profileSpinner.adapter = adapter
        
        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position >= 0 && position < tunnelNames.size) {
                    val selected = tunnelNames[position]
                    if (selected != "No tunnels available") {
                        currentTunnelName = selected
                    }
                }
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
            refreshButton.setOnClickListener {
                it.isEnabled = false
                it.alpha = 0.5f
                handshakeViewModel.performHandshake()
            }

            vpnButton.setOnClickListener {
                val name = currentTunnelName ?: return@setOnClickListener
                lifecycleScope.launch {
                    val tunnels = Application.getTunnelManager().getTunnels()
                    val tunnel = tunnels.find { it.name == name }
                    
                    if (tunnel != null && tunnel.state == Tunnel.State.UP) {
                        try {
                            Application.getTunnelManager().setTunnelState(tunnel, Tunnel.State.DOWN)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Failed to disconnect", e)
                        }
                    } else if (tunnel != null) {
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
                            Application.getTunnelManager().setTunnelState(tunnel, Tunnel.State.UP)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Failed to connect", e)
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
        binding.vpnButton.alpha = alpha
    }

    companion object {
        private const val TAG = "SurfBoost/TunnelListFragment"
    }
}
