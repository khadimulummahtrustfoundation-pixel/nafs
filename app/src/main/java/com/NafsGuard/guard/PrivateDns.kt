package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

// Android এর Private DNS এ CleanBrowsing Family ফিল্টার সেট করে (অ্যাডাল্ট ব্লক + সেইফ সার্চ)
object PrivateDns {
    const val HOST = "family-filter-dns.cleanbrowsing.org"

    fun supported() = Build.VERSION.SDK_INT >= 28

    // WRITE_SECURE_SETTINGS অনুমতি একবার adb দিয়ে দিলে true হবে
    fun canWrite(c: Context) = supported() &&
        c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isOn(c: Context): Boolean {
        if (!supported()) return false
        return try {
            val m = Settings.Global.getString(c.contentResolver, "private_dns_mode")
            val h = Settings.Global.getString(c.contentResolver, "private_dns_specifier")
            m == "hostname" && h.equals(HOST, true)
        } catch (e: Exception) { false }
    }

    fun apply(c: Context): Boolean {
        if (!canWrite(c)) return false
        return try {
            Settings.Global.putString(c.contentResolver, "private_dns_specifier", HOST)
            Settings.Global.putString(c.contentResolver, "private_dns_mode", "hostname")
            true
        } catch (e: Exception) { false }
    }

    fun off(c: Context): Boolean {
        if (!canWrite(c)) return false
        return try {
            Settings.Global.putString(c.contentResolver, "private_dns_mode", "opportunistic")
            true
        } catch (e: Exception) { false }
    }

    // Private DNS পেজ খোলার চেষ্টা; না পেলে নেটওয়ার্ক সেটিংস, তাও না পেলে মূল সেটিংস
    fun openSettings(a: Activity) {
        val tries = listOf(
            "android.settings.PRIVATE_DNS_SETTINGS",
            Settings.ACTION_WIRELESS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (act in tries) {
            try { a.startActivity(Intent(act)); return } catch (e: Exception) {}
        }
    }
}
