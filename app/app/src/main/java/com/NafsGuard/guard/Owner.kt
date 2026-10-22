package com.NafsGuard.guard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

// Device Owner হলে সিস্টেম নিজেই আনইন্সটল/Force stop/Safe mode আটকে দেয়
object Owner {
    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private fun cn(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context): Boolean = try { dpm(c).isDeviceOwnerApp(c.packageName) } catch (e: Exception) { false }

    fun applyLock(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c); val a = cn(c)
        try { d.setUninstallBlocked(a, c.packageName, true) } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= 30) {
            try { d.setUserControlDisabledPackages(a, listOf(c.packageName)) } catch (e: Exception) {}
        }
        try { d.addUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT) } catch (e: Exception) {}
        // Android 10+: Private DNS নিজে সেট হয় এবং ইউজার বদলাতে পারে না
        if (Build.VERSION.SDK_INT >= 29) {
            try { d.setGlobalPrivateDnsModeSpecifiedHost(a, "family-filter-dns.cleanbrowsing.org") } catch (e: Exception) {}
        }
    }

    // সব বিধিনিষেধ তুলে Device Owner বন্ধ করে। সফল হলে true
    fun releaseAll(c: Context): Boolean {
        if (!isOwner(c)) return true
        val d = dpm(c); val a = cn(c)
        try { d.setUninstallBlocked(a, c.packageName, false) } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= 30) {
            try { d.setUserControlDisabledPackages(a, emptyList()) } catch (e: Exception) {}
        }
        try { d.clearUserRestriction(a, UserManager.DISALLOW_SAFE_BOOT) } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= 29) {
            try { d.setGlobalPrivateDnsModeOpportunistic(a) } catch (e: Exception) {}
        }
        return try { d.clearDeviceOwnerApp(c.packageName); true } catch (e: Exception) { false }
    }
}
