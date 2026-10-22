package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.Display
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

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
    // স্ক্রিনের ওপরের শিরোনামে ঠিক এই লেখা থাকলে (এবং লিস্টের ভেতরে না হলে) Reels স্ক্রিন ধরা হয়
    private val reelExact = setOf("reels", "reel", "রিলস", "রিল", "রীলস", "রীল")
    // Instagram-এর Reels স্ক্রিনের ভেতরের id-র অংশ (বড় আকারে দেখা গেলে তবেই ধরা হয়)
    private val igReelIds = listOf("clips", "reel")
    // মেসেজ/ইনবক্স স্ক্রিন চেনার id: এগুলো থাকলে Reels ভেবে ভুল ব্লক করবে না
    private val inboxIds = listOf("direct_inbox", "inbox_refreshable", "row_inbox", "inbox_container",
        "direct_thread", "thread_list", "message_list", "row_thread", "messenger")
    // ফিডের লিস্ট চেনার ক্লাস (ফিডের ভেতরের "Reels" শিরোনাম ব্লক করবে না)
    private val listClasses = listOf("recyclerview", "listview", "scrollview")
    private var weakHits = 0
    // ফিডের Reels কার্ডে ক্লিক করলে কিছুক্ষণ "খোলার অপেক্ষা" মোড: এর মধ্যে নতুন স্ক্রিন খুললে পেছনে পাঠানো হবে
    private var reelIntentUntil = 0L
    private var ocrPkg = ""
    private var lastWeakAt = 0L

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
        handler.removeCallbacks(liteLoop)
        handler.postDelayed(liteLoop, 4000)
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
                val prevCls = lastWinClass
                lastWinPkg = pkg
                lastWinClass = cls
                val tw = System.currentTimeMillis()
                if (reelEnabled(pkg) && tw < reelIntentUntil && cls != prevCls) {
                    reelIntentUntil = 0L
                    Diag.reelPkg = pkg; Diag.reelHit = "click+win"; Diag.reelAt = tw
                    Diag.lastBlock = pkg.substringAfterLast('.') + " click+win:" + cls.substringAfterLast('.'); Diag.lastBlockAt = tw
                    blockReels(tw)
                }
                if (reelEnabled(pkg)) {
                    val tNow = System.currentTimeMillis()
                    Diag.reelWin = cls
                    val cl = cls.lowercase()
                    // মেসেজ/শেয়ার/তৈরির স্ক্রিনের ক্লাসে reel শব্দ থাকলেও ব্লক করবে না
                    val skip = listOf("share", "direct", "message", "inbox", "compos", "create", "upload", "camera", "story")
                    if (cl.contains("reel") && skip.none { cl.contains(it) }) {
                        Diag.reelPkg = pkg; Diag.reelHit = "win:" + cls.substringAfterLast('.'); Diag.reelAt = tNow
                        Diag.lastBlock = pkg.substringAfterLast('.') + " " + Diag.reelHit; Diag.lastBlockAt = tNow
                        blockReels(tNow)
                    }
                }
            }
        }
        if (pkg == packageName) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> { checkInput(e.source, pkg); return }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (reelEnabled(pkg) && clickedReel(e.source)) {
                    reelIntentUntil = System.currentTimeMillis() + 2500
                    Diag.lastBlock = pkg.substringAfterLast('.') + " reel-click"; Diag.lastBlockAt = System.currentTimeMillis()
                }
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
    // নিয়ম: নিউজ ফিডের ভিডিও/রিলস কার্ড, সাধারণ ভিডিও ও ইনবক্স ব্লক হবে না।
    // ব্লক হবে শুধু (১) Reels ট্যাব নির্বাচিত, (২) ফুল-স্ক্রিন Reels প্লেয়ার খুললে।
    private fun reelSignal(root: AccessibilityNodeInfo, pkg: String): String? {
        val isIg = pkg in igPkgs
        val isLite = pkg == "com.facebook.lite"
        val dump = if (Diag.show) LinkedHashSet<String>() else null
        val dm = resources.displayMetrics
        val h = dm.heightPixels
        val topLimit = h * 18 / 100
        val bigH = h * 68 / 100
        val rect = Rect()
        var hit: String? = null
        var inbox = false
        val cnt = intArrayOf(0)

        fun walk(n: AccessibilityNodeInfo?, depth: Int, inList: Boolean) {
            if (n == null || cnt[0] > 700 || depth > 40) return
            if (hit != null && dump == null && inbox) return
            cnt[0]++
            val id = n.viewIdResourceName ?: ""
            val visible = n.isVisibleToUser
            val cls = n.className?.toString()?.lowercase() ?: ""
            val nowInList = inList || listClasses.any { cls.contains(it) }
            val idl = id.lowercase()
            if (visible && inboxIds.any { idl.contains(it) }) inbox = true

            val labels = ArrayList<String>(3)
            n.contentDescription?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            n.text?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            if (Build.VERSION.SDK_INT >= 28) {
                n.paneTitle?.toString()?.lowercase()?.trim()?.let { if (it.isNotEmpty()) labels.add(it) }
            }
            val sel = isSelectedNode(n)
            val interesting = idl.contains("video") || idl.contains("reel") || idl.contains("clips")
            var bounds = false
            if (visible && (interesting || (dump != null))) { n.getBoundsInScreen(rect); bounds = true }

            if (isLite && visible && bounds && idl.endsWith("video_view") && h > 0) {
                Diag.liteVideo = (rect.top * 100 / h).toString() + "-" + (rect.bottom * 100 / h) + "% চওড়া " +
                    (rect.width() * 100 / dm.widthPixels) + "%"
                Diag.liteVideoAt = System.currentTimeMillis()
            }
            if (hit == null && visible) {
                if (isIg && sel && idl.endsWith("clips_tab")) hit = "tab:clips_tab"
                // ফুল-স্ক্রিন Reels প্লেয়ার: Instagram-এর clips_viewer, বা Facebook/Lite-এর reel নামের বড় ভিউ
                else if (interesting && bounds && rect.height() >= bigH && rect.height() > rect.width() &&
                    isIg && igReelIds.any { idl.contains(it) }) {
                    hit = "big:" + idl.substringAfter(":id/")
                }
                // Facebook Lite: পুরো স্ক্রিন জোড়া খাড়া video_view = Reels প্লেয়ার (ফিডের ছোট ভিডিও নয়)
                else if (isLite && bounds && idl.endsWith("video_view") &&
                    rect.height() >= bigH && rect.height() > rect.width()) {
                    hit = "lite:video_view"
                }
                else for (l in labels) {
                    if (l.length > 40) continue
                    // লেখা Reels দিয়েই শুরু হতে হবে ("sent a reel" ইত্যাদি ধরবে না)
                    if (reelWords.none { l.startsWith(it) }) continue
                    val selected = sel || l.contains("selected") || l.contains("নির্বাচিত")
                    var tabLike = idl.contains("tab") || l.contains("tab") || l.contains("ট্যাব")
                    if (selected && !tabLike) {
                        // Instagram Lite ইত্যাদিতে লেখায় "tab" থাকে না: নিচের/ওপরের ছোট বারের ভেতরে থাকলে ট্যাব ধরা হয়
                        if (!bounds) { n.getBoundsInScreen(rect); bounds = true }
                        tabLike = rect.height() in 1 until (h * 12 / 100) &&
                            (rect.top > h * 80 / 100 || rect.bottom < h * 20 / 100)
                    }
                    if (selected && tabLike) { hit = "sel:$l"; break }
                    // ফুল-স্ক্রিন Reels পেজের ওপরের শিরোনাম (ফিডের লিস্টের ভেতরের শিরোনাম বাদ)
                    if (!nowInList && l in reelExact) {
                        if (!bounds) { n.getBoundsInScreen(rect); bounds = true }
                        if (rect.height() > 0 && rect.top < topLimit) { hit = "top:$l"; break }
                    }
                }
            }
            if (dump != null && dump.size < 40 && visible) {
                val sid = id.substringAfter(":id/")
                var s2 = sid + (if (labels.isNotEmpty()) "[" + labels[0].take(18) + "]" else "") + (if (sel) "*" else "")
                if (interesting && bounds && h > 0) s2 += "@" + (rect.top * 100 / h) + "-" + (rect.bottom * 100 / h)
                if (s2.isNotEmpty()) dump.add(s2)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1, nowInList)
        }
        walk(root, 0, false)
        if (dump != null) Diag.reelDump = dump.joinToString("  ")
        if (inbox) return null
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
            weakHits = 0
            if (now - lastReelBlock > 8000) reelStreak = 0
            return false
        }
        // দুর্বল সংকেত (শুধু শিরোনাম): পরপর ২ বার দেখা গেলে তবেই ব্লক, নইলে ফিডে স্ক্রল করার সময় ভুল ব্লক হতে পারে
        if (hit.startsWith("top:")) {
            if (now - lastWeakAt > 2500) weakHits = 0
            lastWeakAt = now
            weakHits++
            if (weakHits < 2) return false
        }
        Diag.lastBlock = pkg.substringAfterLast('.') + " " + hit; Diag.lastBlockAt = now
        blockReels(now)
        return true
    }

    // ক্লিক করা অংশ বা তার ওপরের/ভেতরের লেখা "Reel..." দিয়ে শুরু হলে সেটা Reels কার্ড
    private fun labelIsReel(n: AccessibilityNodeInfo): Boolean {
        val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        val b = n.text?.toString()?.lowercase()?.trim() ?: ""
        return (a.isNotEmpty() && a.length <= 80 && reelWords.any { a.startsWith(it) }) ||
            (b.isNotEmpty() && b.length <= 80 && reelWords.any { b.startsWith(it) })
    }

    private fun clickedReel(src: AccessibilityNodeInfo?): Boolean {
        src ?: return false
        var n: AccessibilityNodeInfo? = src
        var i = 0
        while (n != null && i < 8) {
            if (labelIsReel(n)) return true
            n = n.parent; i++
        }
        fun down(x: AccessibilityNodeInfo?, d: Int): Boolean {
            if (x == null || d > 2) return false
            if (labelIsReel(x)) return true
            for (k in 0 until x.childCount) if (down(x.getChild(k), d + 1)) return true
            return false
        }
        return down(src, 0)
    }

    // ---------- Facebook Lite: স্ক্রিনশট থেকে লেখা পড়ে Reels পেজ চেনা ----------
    // Lite নিজের স্ক্রিনের লেখা অ্যাক্সেসিবিলিটিতে দেয় না, তাই ছবি তুলে ফোনের ভেতরেই লেখা পড়া হয়।
    private var shotBusy = false
    private var liteSeen = 0
    private var liteLastTop = -1
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val liteLoop = object : Runnable {
        override fun run() {
            try { liteTick() } catch (e: Exception) { Diag.liteErr = "e:" + (e.message ?: "").take(30) }
            handler.postDelayed(this, 1000)
        }
    }

    private fun liteTick() {
        if (Build.VERSION.SDK_INT < 30 || shotBusy) return
        if (!Prefs.loggedIn(this)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val pkg = rootInActiveWindow?.packageName?.toString()
        val on = pkg != null && ((pkg in fbPkgs && Prefs.fbReel(this)) ||
            (pkg == "com.instagram.lite" && Prefs.igReel(this)))
        if (!on) { liteSeen = 0; return }
        ocrPkg = pkg!!
        shotBusy = true
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                try {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    if (bmp == null) { shotBusy = false; Diag.liteErr = "bmp null"; return }
                    recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { t ->
                            shotBusy = false
                            try { liteEvaluate(t, bmp.width, bmp.height) } catch (e: Exception) {}
                        }
                        .addOnFailureListener { e ->
                            shotBusy = false
                            Diag.liteErr = "ocr:" + (e.message ?: "").take(30)
                        }
                } catch (e: Exception) {
                    shotBusy = false
                    Diag.liteErr = "shot:" + (e.message ?: "").take(30)
                }
            }
            override fun onFailure(code: Int) { shotBusy = false; Diag.liteErr = "shot-fail:$code" }
        })
    }

    private fun liteEvaluate(t: com.google.mlkit.vision.text.Text, w: Int, h: Int) {
        val now = System.currentTimeMillis()
        var found = false
        var foundTop = -1
        val topLines = ArrayList<String>()
        for (b in t.textBlocks) for (ln in b.lines) {
            val box = ln.boundingBox ?: continue
            val txt = ln.text.trim().lowercase()
            if (box.top < h * 25 / 100 && topLines.size < 8) topLines.add(txt.take(14))
            // ওপরের বাঁ দিকে ছোট "Reels" শিরোনাম = Reels প্লেয়ার/পেজ
            if (txt in reelExact && box.top < h * 14 / 100 && box.left < w / 2 && box.width() < w / 2) {
                found = true; foundTop = box.top
            }
        }
        Diag.liteOcr = if (topLines.isEmpty()) "-" else topLines.joinToString("|")
        Diag.liteOcrAt = now
        if (!found) { liteSeen = 0; return }
        // একই জায়গায় স্থির থাকলে তবেই (ফিড স্ক্রল করার সময় শিরোনাম সরে যায়)
        if (liteSeen > 0 && Math.abs(foundTop - liteLastTop) < h * 2 / 100) liteSeen++ else liteSeen = 1
        liteLastTop = foundTop
        val need = if (now < reelIntentUntil) 1 else 2
        if (liteSeen >= need) {
            liteSeen = 0
            reelIntentUntil = 0L
            Diag.reelPkg = ocrPkg; Diag.reelHit = "ocr:reels"; Diag.reelAt = now
            Diag.lastBlock = ocrPkg.substringAfterLast('.') + " ocr:reels"; Diag.lastBlockAt = now
            blockReels(now)
        }
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
