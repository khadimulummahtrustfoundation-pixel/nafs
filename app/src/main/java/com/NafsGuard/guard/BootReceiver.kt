package com.NafsGuard.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.loggedIn(context) && Prefs.safe(context) && VpnService.prepare(context) == null) {
            try { DnsVpnService.start(context) } catch (e: Exception) {}
        }
    }
}
