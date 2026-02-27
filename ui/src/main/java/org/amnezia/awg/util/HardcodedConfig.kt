/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

/**
 * Hardcoded VPN configuration for automatic connection
 */
object HardcodedConfig {
    // CONFIGURE THESE PARAMETERS WITH YOUR VPN SERVER DETAILS
    const val TUNNEL_NAME = "Server"
    
    const val HARDCODED_CONFIG = """
[Interface]
PrivateKey = WX0HK/GMC0B0dUU6J3g5xQFyw4bFAweb3Opd3hNEgvc=
Address = 10.8.1.76/32
DNS = 172.29.172.254, 1.0.0.1

Jc = 4
Jmin = 10
Jmax = 50

S1 = 31
S2 = 108

H1 = 519435434
H2 = 1678390573
H3 = 1728303485
H4 = 705095975

[Peer]
PublicKey = VM6Nis6pCFbcq+/I1oz+uklXmMnq8/f/B5cUxeJH8WE=
PreSharedKey = F8ei/fsU+OJKx1AgRbzMMXwPZBYYbfFHrsU+h2JRT/o=
Endpoint = 194.164.216.213:40017
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
"""

    fun isConfigured(): Boolean {
        return !HARDCODED_CONFIG.contains("YOUR_") && HARDCODED_CONFIG.trim().isNotEmpty()
    }
}
