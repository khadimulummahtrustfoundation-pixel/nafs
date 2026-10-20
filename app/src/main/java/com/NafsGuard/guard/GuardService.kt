package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L
    private var lastToast = 0L
    private var lastNag = 0L
    private var lastBlock = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val poll = object : Runnable {
        override fun run() {
            try { pollOnce() } catch (e: Exception) {}
            handler.postDelayed(this, 600)
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

    // যেসব অ্যাপে NafsGuard-এর সেটিংস/আনইন্সটল পেজ খুলতে পারে
    private val sensitiveParts = listOf(
        "settings", "installer", "permissioncontroller", "launcher",
        "securitycenter", "safecenter", "phonemaster", "appmanager"
    )
    // ক্লিক ধরার জন্য (লঞ্চার বাদ, নইলে হোম স্ক্রিনে অ্যাপ খুলতেই আটকাবে)
    private val clickParts = listOf("settings", "installer", "securitycenter", "safecenter", "phonemaster", "appmanager")

    // NafsGuard-এর "বিস্তারিত" পেজ চেনার শব্দ (শুধু তালিকা পেজে এগুলো থাকে না)
    private val detailWords = listOf(
        "force stop", "uninstall", "deactivate", "clear data", "clear storage",
        "use nafsguard", "stop nafsguard", "forget vpn", "disconnect",
        "ফোর্স স্টপ", "আনইনস্টল", "আনইন্সটল", "ডিঅ্যাক্টিভেট", "ডেটা মুছুন", "স্টোরেজ মুছুন"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Prefs.loggedIn(this) && Prefs.safe(this) && !DnsVpnService.active &&
            VpnService.prepare(this) == null) {
            try { DnsVpnService.start(this) } catch (e: Exception) {}
        }
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    private fun pollOnce() {
        if (!Prefs.loggedIn(this) || !Prefs.locked(this)) return
        // লক চলাকালীন অ্যাডমিন বন্ধ হয়ে গেলে আবার চালুর অনুরোধ
        val cn = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val now = System.currentTimeMillis()
        if (!dpm.isAdminActive(cn) && now - lastNag > 10000) {
            lastNag = now
            try {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {}
        }
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        protect(pkg, root)
    }

    private fun has(root: AccessibilityNodeInfo, t: String): Boolean {
        val l = root.findAccessibilityNodeInfosByText(t)
        return l != null && l.isNotEmpty()
    }

    private fun block() {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 1200) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        if (now - lastToast > 2500) {
            lastToast = now
            toast("🔒 লক চালু, NafsGuard-এর সেটিংস বদলানো যাবে না")
        }
    }

    // শুধু NafsGuard-এর নিজের পেজ/ডায়ালগ খুললে পেছনে পাঠায়; অন্য সেটিংস স্বাভাবিক থাকে
    private fun protect(pkg: String, root: AccessibilityNodeInfo): Boolean {
        if (pkg == packageName || !Prefs.locked(this)) return false
        val p = pkg.lowercase()
        if (sensitiveParts.none { p.contains(it) }) return false
        if (!has(root, "NafsGuard")) return false
        // আনইন্সটল কনফার্ম ডায়ালগ: নামটি থাকলেই যথেষ্ট
        val isInstaller = p.contains("installer")
        if (!isInstaller && detailWords.none { has(root, it) }) return false
        if (p.contains("launcher") && !has(root, "uninstall") && !has(root, "আনইন্সটল") && !has(root, "আনইনস্টল")) return false
        block()
        return true
    }

    private fun clickedOurName(e: AccessibilityEvent): Boolean {
        val inText = e.text?.any { it?.toString()?.contains("NafsGuard", true) == true } == true
        if (inText) return true
        val src = e.source ?: return false
        return has(src, "NafsGuard")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!Prefs.loggedIn(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> { checkInput(e.source); return }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // সেটিংসে "NafsGuard" লেখায় ক্লিক করলে কিছুক্ষণ পর পেছনে পাঠানো (ভাষা যা-ই হোক)
                val p = pkg.lowercase()
                if (Prefs.locked(this) && clickParts.any { p.contains(it) } && clickedOurName(e)) {
                    handler.postDelayed({ block() }, 450)
                }
                return
            }
        }

        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: pkg
        if (protect(rootPkg, root)) return

        val now = System.currentTimeMillis()
        if (now - last < 300) return
        last = now
        urlBars[rootPkg]?.forEach { id ->
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
            toast("🚫 নিষিদ্ধ শব্দ, লেখা মুছে ফেলা হয়েছে")
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onInterrupt() {}
}
