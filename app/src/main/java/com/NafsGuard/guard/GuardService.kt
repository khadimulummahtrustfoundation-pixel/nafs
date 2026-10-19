package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.net.VpnService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L

    companion object {
        @Volatile private var navUntil = 0L
        @Volatile private var navClicks = 0
        @Volatile private var navScrolls = 0
        private val hopped = mutableSetOf<String>()

        // Private DNS পেজে পৌঁছানোর জন্য ৪৫ সেকেন্ড অটো-নেভিগেশন চালু
        fun startDnsNav() {
            navUntil = System.currentTimeMillis() + 45_000L
            navClicks = 0; navScrolls = 0; hopped.clear()
        }
    }

    private val dnsWords = listOf("private dns", "প্রাইভেট ডিএনএস", "প্রাইভেট dns")
    private val hostWords = listOf("hostname", "হোস্টনেম")
    private val hopWords = listOf(
        "more connection settings", "connection & sharing", "connections & sharing",
        "advanced", "আরও সংযোগ সেটিংস", "উন্নত"
    )

    private fun firstNode(root: AccessibilityNodeInfo, w: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByText(w).firstOrNull()

    private fun clickUp(n: AccessibilityNodeInfo): Boolean {
        var x: AccessibilityNodeInfo? = n
        var i = 0
        while (x != null && i < 6) {
            if (x.isClickable) return x.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            x = x.parent; i++
        }
        return false
    }

    private fun scrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val r = scrollable(c)
            if (r != null) return r
        }
        return null
    }

    // সেটিংসে "Private DNS" খুঁজে ক্লিক; হোস্টনেম অপশন দেখা গেলে থেমে যায় (পেস্ট ইউজার নিজে করবে)
    private fun dnsAssist(root: AccessibilityNodeInfo) {
        if (System.currentTimeMillis() > navUntil || navClicks >= 8) return
        if (hostWords.any { firstNode(root, it) != null }) { navUntil = 0L; return }
        for (w in dnsWords) {
            val n = firstNode(root, w) ?: continue
            if (clickUp(n)) { navClicks++; return }
        }
        for (w in hopWords) {
            if (w in hopped) continue
            val n = firstNode(root, w) ?: continue
            if (clickUp(n)) { hopped.add(w); navClicks++; return }
        }
        if (navScrolls < 10) {
            val s = scrollable(root)
            if (s != null && s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) navScrolls++
        }
    }

    private val urlBars = mapOf(
        "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
        "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
        "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
        "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
        "com.sec.android.app.sbrowser" to listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "org.mozilla.firefox:id/url_bar_title"),
        "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput")
    )

    private val protectedPkgs = setOf(
        "com.android.settings", "com.google.android.packageinstaller",
        "com.android.packageinstaller", "com.samsung.android.packageinstaller"
    )

    // সেটআপের সময় এই অ্যাপগুলোতে গেলে সেশন থাকবে; অন্য কোথাও গেলে সেশন শেষ
    private val tripPkgs = setOf(
        "com.android.settings", "com.android.systemui", "com.android.vpndialogs",
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.samsung.android.app.settings"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Prefs.loggedIn(this) && Prefs.dnsAuto(this) && !PrivateDns.isOn(this)) {
            PrivateDns.apply(this)
        }
        if (Prefs.loggedIn(this) && Prefs.safe(this) && !DnsVpnService.active &&
            VpnService.prepare(this) == null) {
            try { DnsVpnService.start(this) } catch (e: Exception) {}
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!Prefs.loggedIn(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        // সেটিংস থেকে Home/অন্য অ্যাপে গেলে অ্যাপের লগইন সেশন শেষ
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            Session.keepUntil != 0L && pkg !in tripPkgs) {
            Session.unlocked = false
            Session.keepUntil = 0L
        }

        if (e.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            checkInput(e.source)
            return
        }
        val now = System.currentTimeMillis()
        if (now - last < 300) return
        last = now
        val root = rootInActiveWindow ?: return
        if (Prefs.locked(this) && pkg in protectedPkgs &&
            root.findAccessibilityNodeInfosByText("NafsGuard").isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            toast("লক চালু — NafsGuard সেটিংস বন্ধ")
            return
        }
        if (pkg.contains("settings")) dnsAssist(root)
        urlBars[pkg]?.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).forEach { n ->
                val t = n.text?.toString() ?: return@forEach
                if (Guard.isBad(this, t)) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    toast("🚫 এই সাইট ব্লক করা হয়েছে")
                    return
                }
            }
        }
    }

    private fun checkInput(n: AccessibilityNodeInfo?) {
        n ?: return
        if (n.isPassword) return
        val t = n.text?.toString() ?: return
        if (Guard.isBad(this, t)) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            toast("🚫 নিষিদ্ধ শব্দ — লেখা মুছে ফেলা হয়েছে")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onInterrupt() {}
}
