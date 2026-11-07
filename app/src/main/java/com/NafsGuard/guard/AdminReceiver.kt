package com.NafsGuard.guard

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        if (Prefs.locked(context)) "লক চালু আছে — মেয়াদ শেষ না হওয়া পর্যন্ত বন্ধ করা যাবে না।"
        else "অ্যাডমিন বন্ধ করলে NafsGuard আনইন্সটল করা যাবে।"
}
