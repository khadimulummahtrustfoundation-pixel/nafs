package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L

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

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!Prefs.loggedIn(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

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
