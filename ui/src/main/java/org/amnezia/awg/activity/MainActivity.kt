/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import org.amnezia.awg.R
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.viewmodel.HandshakeViewModel

/**
 * Simplified main entry point to the AmneziaWG application.
 */
class MainActivity : BaseActivity() {

    private val handshakeViewModel: HandshakeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

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

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ): Boolean {
        return false
    }
}
