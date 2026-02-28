/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.util

/**
 * Hardcoded VPN configurations for automatic connection
 */
object HardcodedConfig {
    
    data class VpnProfile(val name: String, val config: String)

    val PROFILES = listOf(
        VpnProfile("London1", """
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
""".trimIndent()),
        VpnProfile("London2", """
[Interface]
Address = 10.8.1.79/32
DNS = 172.29.172.254, 1.0.0.1
PrivateKey = beTToV1COiBwz+oFp2BuAeudbu1PmAwzbETA6wvxfLk=
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
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = 194.164.216.213:40017
PersistentKeepalive = 25
""".trimIndent())
    )

    fun isConfigured(): Boolean = true
}
