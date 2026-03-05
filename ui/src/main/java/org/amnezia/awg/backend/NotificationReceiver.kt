package org.amnezia.awg.backend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (GoBackend.VpnService.ACTION_DISCONNECT == intent.action) {
            val stopIntent = Intent(context, GoBackend.VpnService::class.java)
            stopIntent.action = GoBackend.VpnService.ACTION_DISCONNECT
            context.startService(stopIntent)
        }
    }
}
