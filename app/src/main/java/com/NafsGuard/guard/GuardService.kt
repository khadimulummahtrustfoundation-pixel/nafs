package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class GuardService : AccessibilityService() {

    private var last = 0L
    private var lastToast = 0L
    private var lastNag = 0L
    private var lastBlock = 0L
    private var overlay: View? = null
    private var lastWinPkg = ""
    private var lastWinClass = ""
    private val handler = Handler(Looper.getMainLooper())

    // ---- Reels ব্লক ----
    private var lastReelScan = 0L
    private var lastReelBlock = 0L
    private var lastReelToast = 0L
    private var reelStreak = 0
    private val fbPkgs = setOf("com.facebook.katana", "com.facebook.lite")
    private val igPkgs = setOf("com.instagram.android", "com.instagram.lite")
    // Reels ট্যাব/স্ক্রিন চেনার শব্দ (ইংরেজি ও বাংলা UI দুটোতেই)
    private val reelWords = listOf("reel", "রিল", "রীল")
    // স্ক্রিনের ওপরের অংশে ঠিক এই লেখা থাকলে Reels স্ক্রিন ধরা হয়
    // Facebook-এর "Video" ট্যাব ব্লক করতে না চাইলে এই তালিকা খালি করুন: listOf()
    private val fbTabWords = listOf("video", "ভিডিও")
    private val reelExact = setOf("reels", "reel", "রিলস", "রিল", "রীলস", "রীল")
    // Instagram-এর Reels স্ক্রিনের ভেতরের id-র অংশ (অ্যাপ আপডেটে বদলাতে পারে, Diag দেখে ঠিক করা যাবে)
    private val igReelIds = listOf("clips_viewer")

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

    // ---- পেজের লেখা পাহারা: যেকোনো অ্যাপে চলে (শুধু নিচের সিস্টেম অংশগুলো বাদ) ----
    private fun shouldScan(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android") return false
        val p = pkg.lowercase()
        return !(p.contains("systemui") || p.contains("launcher") ||
            p.contains("inputmethod") || p.contains("keyboard"))
    }

    private val scanLoop = object : Runnable {
        override fun run() {
            try { scanOnce() } catch (e: Exception) {}
            handler.postDelayed(this, 1500)
        }
    }

    // যেসব অ্যাপে NafsGuard-এর সেটিংস/আনইন্সটল পেজ খুলতে পারে
    private val sensitiveParts = listOf(
        "settings", "installer", "permissioncontroller", "launcher",
        "securitycenter", "safecenter", "phonemaster", "appmanager"
    )
    // ক্লিক ধরার জন্য (লঞ্চার বাদ, নইলে হোম স্ক্রিনে অ্যাপ খুলতেই আটকাবে)
    private val clickParts = listOf("settings", "installer", "securitycenter", "safecenter", "phonemaster", "appmanager")

    // NafsGuard-এর "বিস্তারিত" পেজ চেনার শব্দ
    private val detailWords = listOf(
        "force stop", "uninstall", "deactivate", "clear data", "clear storage",
        "use nafsguard", "stop nafsguard", "forget vpn", "disconnect",
        "ফোর্স স্টপ", "আনইনস্টল", "আনইন্সটল", "ডিঅ্যাক্টিভেট", "ডেটা মুছুন", "স্টোরেজ মুছুন"
    )
    // ডিভাইস অ্যাডমিন পেজ চেনার শব্দ (অ্যাডমিন চালু থাকলে এগুলোতেও আটকাবে)
    private val adminWords = listOf("device admin", "device administrator", "ডিভাইস অ্যাডমিন")

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Prefs.loggedIn(this) && Prefs.safe(this) && !DnsVpnService.active &&
            VpnService.prepare(this) == null) {
            try { DnsVpnService.start(this) } catch (e: Exception) {}
        }
        handler.removeCallbacks(poll)
        handler.post(poll)
        handler.removeCallbacks(scanLoop)
        handler.postDelayed(scanLoop, 3000)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        return super.onUnbind(intent)
    }

    private fun adminActive(): Boolean {
        val cn = ComponentName(this, AdminReceiver::class.java)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isAdminActive(cn)
    }

    private fun pollOnce() {
        if (!Prefs.loggedIn(this) || !Prefs.locked(this)) return
        val now = System.currentTimeMillis()
        // লক চলাকালীন অ্যাডমিন বন্ধ হয়ে গেলে আবার চালুর অনুরোধ
        if (!adminActive() && now - lastNag > 4000) {
            lastNag = now
            try {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@GuardService, AdminReceiver::class.java))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {}
        }
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (deviceAdminGuard(root)) return
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

    // ডিভাইস অ্যাডমিন পেজ: ভাষা যা-ই হোক, স্ক্রিনের ক্লাস নাম দেখে চেনা হয়
    private fun deviceAdminGuard(root: AccessibilityNodeInfo): Boolean {
        if (!Prefs.locked(this)) return false
        if (!lastWinPkg.lowercase().contains("settings")) return false
        if (!lastWinClass.lowercase().contains("deviceadmin")) return false
        if (root.packageName?.toString() != lastWinPkg) return false
        if (!has(root, "NafsGuard")) return false
        if (adminActive()) {
            block()      // অ্যাডমিন চালু: বন্ধ করার পেজ খুলতে দেবে না
            return true
        }
        // অ্যাডমিন বন্ধ হয়ে গেছে: অ্যাক্টিভেশন পেজে নিজে থেকে "Activate" চেপে দেওয়া
        val btns = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/action_button")
        if (btns != null && btns.isNotEmpty()) {
            btns[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

    // শুধু NafsGuard-এর নিজের পেজ/ডায়ালগ খুললে পেছনে পাঠায়; অন্য সেটিংস স্বাভাবিক থাকে
    private fun protect(pkg: String, root: AccessibilityNodeInfo): Boolean {
        if (pkg == packageName || !Prefs.locked(this)) return false
        val p = pkg.lowercase()
        if (sensitiveParts.none { p.contains(it) }) return false
        if (!has(root, "NafsGuard")) return false
        val isInstaller = p.contains("installer")
        if (!isInstaller) {
            val hit = detailWords.any { has(root, it) } ||
                (adminActive() && adminWords.any { has(root, it) })
            if (!hit) return false
        }
        if (p.contains("launcher") && !has(root, "uninstall") && !has(root, "আনইন্সটল") && !has(root, "আনইনস্টল")) return false
        block()
        return true
    }

    private fun clickedOurName(e: AccessibilityEvent): Boolean {
        val words = listOf("nafsguard")
        val joined = (e.text ?: emptyList<CharSequence>()).joinToString(" ") { cs -> cs.toString().lowercase() }
        if (words.any { joined.contains(it) }) return true
        val src = e.source ?: return false
        return has(src, "NafsGuard")
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!Prefs.loggedIn(this)) return
        val pkg = e.packageName?.toString() ?: return

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = e.className?.toString() ?: ""
            if (cls.isNotEmpty() && !cls.startsWith("android")) {
                lastWinPkg = pkg
                lastWinClass = cls
                if (reelEnabled(pkg)) {
                    val tNow = System.currentTimeMillis()
                    Diag.reelWin = cls
                    if (cls.lowercase().contains("reel")) {
                        Diag.reelPkg = pkg; Diag.reelHit = "win:" + cls.substringAfterLast('.'); Diag.reelAt = tNow
                        blockReels(tNow)
                    }
                }
            }
        }
        if (pkg == packageName) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> { checkInput(e.source, pkg); return }
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
        if (deviceAdminGuard(root)) return
        if (protect(rootPkg, root)) return
        if (checkReels(root, rootPkg)) return

        val now = System.currentTimeMillis()
        if (now - last < 300) return
        last = now
        urlBars[rootPkg]?.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).forEach { n ->
                val t = n.text?.toString() ?: return@forEach
                if (Guard.isBad(this, t)) {
                    showWarning(rootPkg)
                    return
                }
            }
        }
    }

    // খারাপ শব্দ লিখলে শুধু সেই শব্দটুকু মুছে বাকি লেখা রেখে দেওয়া, তারপর সতর্কবার্তা
    private fun checkInput(n: AccessibilityNodeInfo?, pkg: String) {
        n ?: return
        if (n.isPassword || !n.isEditable) return
        val t = n.text?.toString() ?: return
        if (Guard.isBad(this, t)) {
            var clean = Guard.removeBad(this, t)
            if (Guard.isBad(this, clean)) clean = ""      // নিরাপত্তা: তবুও থেকে গেলে পুরোটা মুছবে
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, clean)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            val sel = Bundle()
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, clean.length)
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, clean.length)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
            showWarning(pkg)
        }
    }

    // ---------- Reels ব্লক ----------
    private fun reelEnabled(pkg: String): Boolean = when {
        pkg in fbPkgs -> Prefs.fbReel(this)
        pkg in igPkgs -> Prefs.igReel(this)
        else -> false
    }

    private fun isSelectedNode(n: AccessibilityNodeInfo): Boolean {
        if (n.isSelected || n.isChecked) return true
        try { if (n.collectionItemInfo?.isSelected == true) return true } catch (e: Exception) {}
        if (Build.VERSION.SDK_INT >= 30) {
            val sd = n.stateDescription?.toString()?.lowercase() ?: ""
            if (sd.contains("selected") || sd.contains("নির্বাচিত")) return true
        }
        return false
    }

    // স্ক্রিনে Reels আছে কিনা দেখে; পেলে কোন সংকেতে ধরল সেটা ফেরত দেয়, না পেলে null
    private fun reelSignal(root: AccessibilityNodeInfo, pkg: String): String? {
        val isIg = pkg in igPkgs
        val dump = if (Diag.show) LinkedHashSet<String>() else null
        val topLimit = resources.displayMetrics.heightPixels * 18 / 100
        val rect = Rect()
        var hit: String? = null
        val cnt = intArrayOf(0)

        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || (hit != null && dump == null) || cnt[0] > 700 || depth > 40) return
            cnt[0]++
            val id = n.viewIdResourceName ?: ""
            val visible = n.isVisibleToUser
            val labels = ArrayList<String>(3)
            n.contentDescription?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            n.text?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            if (Build.VERSION.SDK_INT >= 28) {
                n.paneTitle?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            }
            val sel = isSelectedNode(n)

            if (hit == null) {
                if (isIg && visible && igReelIds.any { id.contains(it) }) hit = "id:" + id.substringAfter(":id/")
                else if (isIg && sel && id.endsWith("clips_tab")) hit = "tab:clips_tab"
                else for (l in labels) {
                    if (l.length > 40) continue
                    // Facebook-এ Reels এখন অনেক সময় "Video" ট্যাবের ভেতরে থাকে: "Video, tab 2 of 6" নির্বাচিত হলে ধরা হয়
                    if (!isIg && pkg in fbPkgs && (sel || l.contains("selected")) && l.contains("tab") &&
                        fbTabWords.any { l.contains(it) }) { hit = "fbtab:$l"; break }
                    val hasReel = reelWords.any { l.contains(it) }
                    if (!hasReel) continue
                    // নির্বাচিত ট্যাব (লেখায় "selected" থাকলেও ধরা হয়)
                    if (sel || l.contains("selected") || l.contains("নির্বাচিত")) { hit = "sel:$l"; break }
                    // স্ক্রিনের ওপরের শিরোনামে ঠিক "Reels" লেখা
                    if (visible && l in reelExact) {
                        n.getBoundsInScreen(rect)
                        if (rect.height() > 0 && rect.top < topLimit) { hit = "top:$l"; break }
                    }
                }
            }
            if (dump != null && dump.size < 36 && visible) {
                val sid = id.substringAfter(":id/")
                val s = sid + (if (labels.isNotEmpty()) "[" + labels[0].take(18) + "]" else "") + (if (sel) "*" else "")
                if (s.isNotEmpty()) dump.add(s)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        if (dump != null) Diag.reelDump = dump.joinToString("  ")
        return hit
    }

    // Back (বারবার ফিরে এলে হোম) এবং ছোট বার্তা
    private fun blockReels(now: Long) {
        if (now - lastReelBlock < 900) return
        lastReelBlock = now
        reelStreak++
        if (reelStreak >= 3) {
            reelStreak = 0
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        if (now - lastReelToast > 2500) {
            lastReelToast = now
            toast("🚫 NafsGuard: Reels ব্লক করা আছে")
        }
    }

    // Facebook/Instagram (ও Lite)-এ Reels খুললে ব্লক
    private fun checkReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!reelEnabled(pkg)) return false
        val now = System.currentTimeMillis()
        if (now - lastReelScan < 350) return false
        lastReelScan = now

        val hit = reelSignal(root, pkg)
        Diag.reelPkg = pkg; Diag.reelHit = hit ?: "-"; Diag.reelAt = now
        if (hit == null) {
            if (now - lastReelBlock > 8000) reelStreak = 0
            return false
        }
        blockReels(now)
        return true
    }

    // ---------- পেজের লেখা যাচাই ----------
    private fun scanOnce() {
        if (!Prefs.loggedIn(this) || overlay != null) return
        Diag.loopAt = System.currentTimeMillis()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val root = rootInActiveWindow
        if (root == null) { Diag.rootNull++; return }
        val pkg = root.packageName?.toString() ?: return
        if (pkg != packageName) { Diag.pkg = pkg; Diag.watched = shouldScan(pkg) }
        if (checkReels(root, pkg)) return
        if (!shouldScan(pkg)) return
        scanPageText(root, pkg)
    }

    private fun collectText(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int, count: IntArray) {
        if (n == null || count[0] > 700 || depth > 40) return
        count[0]++
        if (n.isVisibleToUser && !n.isPassword && !n.isEditable) {
            n.text?.let { sb.append(it).append('\n') }
            n.contentDescription?.let { sb.append(it).append('\n') }
        }
        for (i in 0 until n.childCount) collectText(n.getChild(i), sb, depth + 1, count)
    }

    // পেজে ২টি বা বেশি খারাপ শব্দ থাকলে সতর্কবার্তা (একটা শব্দে ভুল ব্লক এড়াতে)
    private fun scanPageText(root: AccessibilityNodeInfo, pkg: String) {
        val sb = StringBuilder()
        val cnt = intArrayOf(0)
        collectText(root, sb, 0, cnt)
        val bad = Guard.badCount(this, sb.toString())
        Diag.textNodes = cnt[0]; Diag.textChars = sb.length; Diag.textBad = bad; Diag.textAt = System.currentTimeMillis()
        if (bad >= 2) showWarning(pkg)
    }

    // ---------- সতর্কবার্তা পপ-আপ (সব অ্যাপের ওপরে দেখায়) ----------
    private fun showWarning(pkg: String) {
        if (overlay != null) return
        val c: Context = this
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = Ui.dp(c, 24); setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = Ui.dp(c, 20).toFloat()
            }
        }
        card.addView(ImageView(c).apply {
            setImageResource(android.R.drawable.ic_dialog_alert)
            setColorFilter(Color.parseColor("#C62828"))
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 72))
        })
        card.addView(Ui.text(c, "আল্লাহকে ভয় করুন।", 22f, true, Color.parseColor("#C62828"))
            .apply { gravity = Gravity.CENTER })
        card.addView(Ui.text(c, "আপাতত ফোনটা রেখে একটু পানি খান বা হাঁটাচলা করুন।", 16f)
            .apply { gravity = Gravity.CENTER })
        card.addView(Ui.button(c, "বন্ধ করুন") { dismissWarning(pkg) })

        val m = Ui.dp(c, 28)
        val root = FrameLayout(c).apply {
            setBackgroundColor(Color.parseColor("#B3000000"))
            isClickable = true
            addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ).apply { setMargins(m, 0, m, 0) })
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            overlay = root
        } catch (e: Exception) {
            toast("🚫 নিষিদ্ধ শব্দ, আল্লাহকে ভয় করুন")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        try { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) {}
    }

    private fun canKill(pkg: String): Boolean {
        val p = pkg.lowercase()
        return pkg != packageName && pkg != "android" && !p.contains("launcher") &&
            !p.contains("systemui") && !p.contains("inputmethod") && !p.contains("settings")
    }

    // "বন্ধ করুন": ওভারলে সরিয়ে হোমে গিয়ে ঐ অ্যাপের প্রসেস বন্ধ করে দেয়।
    // ইউজার আবার অ্যাপ খুললে সেটা নতুন করে (শুরু থেকে) চালু হবে।
    // কোনো অ্যাপ নিজে থেকে আবার চালু হয়ে গেলে তাই কয়েকবার চেষ্টা করা হয়।
    private fun dismissWarning(pkg: String) {
        removeOverlay()
        performGlobalAction(GLOBAL_ACTION_HOME)
        if (!canKill(pkg)) return
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (delay in longArrayOf(700, 1500, 2500)) {
            handler.postDelayed({
                try { am.killBackgroundProcesses(pkg) } catch (e: Exception) {}
            }, delay)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onInterrupt() {}
}
