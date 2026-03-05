/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import org.amnezia.awg.R
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.viewmodel.HandshakeViewModel

/**
 * Simplified main entry point to the SurfBoost application.
 */
class MainActivity : BaseActivity() {

    private val handshakeViewModel: HandshakeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, notifications can be shown
        } else {
            Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        checkNotificationPermission()

        // Observe the handshake result
        handshakeViewModel.handshakeResult.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Handshake successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Handshake failed", Toast.LENGTH_SHORT).show()
            }
        }

        // Trigger the handshake
        handshakeViewModel.performHandshake()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ): Boolean {
        return false
    }
}
