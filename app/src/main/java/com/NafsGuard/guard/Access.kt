package com.NafsGuard.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/**
 * অ্যাডমিন সার্ভারে LoginAccess OFF করলে অ্যাপের সব সুরক্ষা বন্ধ হয়ে যায়:
 * সব সুইচ বন্ধ, লক খোলা, Device Owner ও অ্যাডমিন সরানো, VPN বন্ধ, লগআউট।
 * এরপর অ্যাপ কিছুই ব্লক করে না এবং ইউজার সহজে আনইন্সটল করতে পারে।
 */
object Access {
    /**
     * মেয়াদ শেষ হলে ইন্টারনেট ছাড়াই ফোনের সেভ করা মেয়াদ দেখে সব সুরক্ষা বন্ধ করে দেয়।
     * এরপর অ্যাপ Pending পেজে চলে যায়। মেয়াদ শেষ না হলে কিছুই করে না।
     * @return এই কলেই বন্ধ করা হলে true
     */
    @Synchronized
    fun enforceExpiry(c: Context): Boolean {
        if (!Prefs.rawLoggedIn(c) || !Prefs.expired(c)) return false
        disableAll(c)
        try { Push.notify(c, "NafsGuard", "আপনার অ্যাকাউন্টের মেয়াদ শেষ হয়েছে। নবায়নের জন্য অ্যাডমিনের সাথে যোগাযোগ করুন।") } catch (e: Throwable) {}
        try { Remote.onChange?.run() } catch (e: Throwable) {}
        return true
    }

    fun disableAll(c: Context) {
        Prefs.setSafe(c, false)
        Prefs.setFbReel(c, false)
        Prefs.setIgReel(c, false)
        Prefs.setYtShorts(c, false)
        Prefs.setMedia(c, false)
        Prefs.setLinkBlock(c, false)
        Prefs.setVpnAlways(c, false)
        Prefs.clearLock(c)
        Prefs.setUnlock(c, false)
        try { DnsVpnService.stop(c) } catch (e: Exception) {}
        try { Owner.releaseAll(c) } catch (e: Exception) {}
        try {
            val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.removeActiveAdmin(ComponentName(c, AdminReceiver::class.java))
        } catch (e: Exception) {}
        Prefs.logout(c)         // লগআউট হলে GuardService আর কিছু ব্লক করে না
        Session.unlocked = false
    }
}
