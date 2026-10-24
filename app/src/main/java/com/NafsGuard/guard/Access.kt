package com.NafsGuard.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * অ্যাডমিন Google Sheet-এ LoginAccess OFF করলে অ্যাপের সব সুরক্ষা বন্ধ হয়ে যায়:
 * সব সুইচ বন্ধ, লক খোলা, Device Owner ও অ্যাডমিন সরানো, VPN বন্ধ, লগআউট।
 * এরপর অ্যাপ কিছুই ব্লক করে না এবং ইউজার সহজে আনইন্সটল করতে পারে।
 */
object Access {
    fun disableAll(c: Context) {
        Prefs.setSafe(c, false)
        Prefs.setFbReel(c, false)
        Prefs.setIgReel(c, false)
        Prefs.setMedia(c, false)
        Prefs.clearLock(c)
        Prefs.setUnlock(c, false)
        try { DnsVpnService.stop(c) } catch (e: Exception) {}
        try { Owner.releaseAll(c) } catch (e: Exception) {}
        try {
            val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.removeActiveAdmin(ComponentName(c, AdminReceiver::class.java))
        } catch (e: Exception) {}
        Prefs.logout(c)          // লগআউট হলে GuardService আর কিছু ব্লক করে না
        Session.unlocked = false
    }
}
