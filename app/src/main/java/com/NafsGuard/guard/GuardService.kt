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

    private var lastReelScan = 0L
    private var lastReelBlock = 0L
    private var lastReelToast = 0L
    private var reelStreak = 0
    private val fbPkgs = setOf("com.facebook.katana", "com.facebook.lite")
    private val igPkgs = setOf("com.instagram.android", "com.instagram.lite")
    private val reelWords = listOf("reel", "রিল", "রীল")
    private val reelExact = setOf("reels", "reel", "রিলস", "রিল", "রীলস", "রীল")
    private val igReelIds = listOf("clips", "reel")
    private val inboxIds = listOf("direct_inbox", "inbox_refreshable", "row_inbox", "inbox_container",
        "direct_thread", "thread_list", "message_list", "row_thread")
    private val listClasses = listOf("recyclerview", "listview", "scrollview")
    private var weakHits = 0
    private var reelIntentUntil = 0L
    private var ocrPkg = ""
    private var videoModeUntil = 0L
    private var videoSince = 0L
    private var upNextName = ""
    private var changeSeen = 0
    private var videoIntentUntil = 0L
    private val fbMain = "com.facebook.katana"
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

    private fun shouldScan(pkg: String): Boolean {
        if (pkg == packageName || pkg == "android") return false
        val p = pkg.lowercase()
        return !(p.contains("systemui") || p.contains("launcher") ||
            p.contains("inputmethod") || p.contains("keyboard"))
    }

    private val scanLoop = object : Runnable {
        override fun run() {
            try { scanOnce() } catch (e: Exception) {}
            try { vpnWatch() } catch (e: Exception) {}
            handler.postDelayed(this, 1500)
        }
    }

    private var lastVpnTry = 0L
    private fun vpnWatch() {
        if (!Prefs.loggedIn(this) || !Prefs.safe(this) || DnsVpnService.active) return
        val now = System.currentTimeMillis()
        if (now - lastVpnTry < 5000) return
        lastVpnTry = now
        if (VpnService.prepare(this) == null) DnsVpnService.start(this)
    }

    private val sensitiveParts = listOf(
        "settings", "installer", "permissioncontroller", "launcher",
        "securitycenter", "safecenter", "phonemaster", "appmanager"
    )
    private val clickParts = listOf("settings", "installer", "securitycenter", "safecenter", "phonemaster", "appmanager")

    private val detailWords = listOf(
        "force stop", "uninstall", "deactivate", "clear data", "clear storage",
        "use nafsguard", "stop nafsguard", "forget vpn", "disconnect",
        "ফোর্স স্টপ", "আনইনস্টল", "আনইন্সটল", "ডিঅ্যাক্টিভেট", "ডেটা মুছুন", "স্টোরেজ মুছুন"
    )
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
            toast("🔒 Lok chalu, NafsGuard-er settings bodlano jabe na")
        }
    }

    private fun deviceAdminGuard(root: AccessibilityNodeInfo): Boolean {
        if (!Prefs.locked(this)) return false
        if (!lastWinPkg.lowercase().contains("settings")) return false
        if (!lastWinClass.lowercase().contains("deviceadmin")) return false
        if (root.packageName?.toString() != lastWinPkg) return false
        if (!has(root, "NafsGuard")) return false
        if (adminActive()) {
            block()
            return true
        }
        val btns = root.findAccessibilityNodeInfosByViewId("com.android.settings:id/action_button")
        if (btns != null && btns.isNotEmpty()) {
            btns[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        return false
    }

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
                    blockReels(tw)
                }
                if (pkg == fbMain && Prefs.fbReel(this) && tw < videoIntentUntil && cls != prevCls) {
                    videoIntentUntil = 0L
                    blockReels(tw)
                }
                if (reelEnabled(pkg)) {
                    val tNow = System.currentTimeMillis()
                    val cl = cls.lowercase()
                    val skip = listOf("share", "direct", "message", "inbox", "compos", "create", "upload", "camera", "story")
                    if (cl.contains("reel") && skip.none { cl.contains(it) }) {
                        blockReels(tNow)
                    }
                }
            }
        }
        if (pkg == packageName) return

        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> { checkInput(e.source, pkg); return }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val tn = System.currentTimeMillis()
                if (pkg in fbPkgs && Prefs.fbReel(this) && tn < videoModeUntil && tn - videoSince > 1200) {
                    val dy = if (Build.VERSION.SDK_INT >= 28) Math.abs(e.scrollDeltaY) else 100
                    if (dy >= 40 || (e.fromIndex >= 0 && e.toIndex >= 0 && e.fromIndex != e.toIndex)) {
                        videoModeUntil = 0L; changeSeen = 0; upNextName = ""
                        blockReels(tn)
                    }
                }
                return
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val csrc = e.source
                if (csrc != null && clickExcluded(csrc)) {
                    reelIntentUntil = 0L; videoIntentUntil = 0L
                } else {
                    if (pkg == fbMain && Prefs.fbReel(this) && clickedVideo(csrc)) {
                        videoIntentUntil = System.currentTimeMillis() + 2500
                    }
                    if (reelEnabled(pkg) && clickedReel(csrc)) {
                        reelIntentUntil = System.currentTimeMillis() + 2500
                    }
                }
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
    }

    private fun checkInput(n: AccessibilityNodeInfo?, pkg: String) {
        n ?: return
        if (n.isPassword || !n.isEditable) return
        val t = n.text?.toString() ?: return
        if (Guard.isBad(this, t)) {
            var clean = Guard.removeBad(this, t)
            if (Guard.isBad(this, clean)) clean = ""
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, clean)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            showWarning(pkg)
        }
    }

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

    private fun reelSignal(root: AccessibilityNodeInfo, pkg: String): String? {
        val isIg = pkg in igPkgs
        val isLite = pkg == "com.facebook.lite"
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
            if (hit != null && inbox) return
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
            
            val sel = isSelectedNode(n)
            val interesting = idl.contains("video") || idl.contains("reel") || idl.contains("clips")
            var bounds = false
            if (visible && interesting) { n.getBoundsInScreen(rect); bounds = true }

            if (hit == null && visible) {
                if (isIg && sel && idl.endsWith("clips_tab")) hit = "tab:clips_tab"
                else if (interesting && bounds && rect.height() >= bigH && rect.height() > rect.width() &&
                    isIg && igReelIds.any { idl.contains(it) }) {
                    hit = "big:" + idl.substringAfter(":id/")
                }
                else for (l in labels) {
                    if (l.length > 40) continue
                    if (reelWords.none { l.startsWith(it) }) continue
                    val selected = sel || l.contains("selected") || l.contains("নির্বাচিত")
                    var tabLike = idl.contains("tab") || l.contains("tab") || l.contains("ট্যাব")
                    if (selected && !tabLike) {
                        if (!bounds) { n.getBoundsInScreen(rect); bounds = true }
                        tabLike = rect.height() in 1 until (h * 12 / 100) &&
                            (rect.top > h * 80 / 100 || rect.bottom < h * 20 / 100)
                    }
                    if (selected && tabLike) { hit = "sel:$l"; break }
                    if (!nowInList && l in reelExact) {
                        if (!bounds) { n.getBoundsInScreen(rect); bounds = true }
                        if (rect.height() > 0 && rect.top < topLimit) { hit = "top:$l"; break }
                    }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1, nowInList)
        }
        walk(root, 0, false)
        if (inbox) return null
        return hit
    }

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
            toast("🚫 NafsGuard: Reels block kora ache")
        }
    }

    private fun checkReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!reelEnabled(pkg)) return false
        val now = System.currentTimeMillis()
        if (now - lastReelScan < 350) return false
        lastReelScan = now

        if (pkg == fbMain && now < videoIntentUntil && fullscreenVideo(root)) {
            videoIntentUntil = 0L
            blockReels(now)
            return true
        }

        val hit = reelSignal(root, pkg)
        if (hit == null) {
            weakHits = 0
            if (now - lastReelBlock > 8000) reelStreak = 0
            return false
        }
        if (hit.startsWith("top:")) {
            if (now - lastWeakAt > 2500) weakHits = 0
            lastWeakAt = now
            weakHits++
            if (weakHits < 2) return false
        }
        blockReels(now)
        return true
    }

    private fun looksVideo(n: AccessibilityNodeInfo): Boolean {
        val idl = n.viewIdResourceName?.lowercase() ?: ""
        val cl = n.className?.toString()?.lowercase() ?: ""
        if (idl.contains("video") || cl.contains("video")) return true
        val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        val b = n.text?.toString()?.lowercase()?.trim() ?: ""
        for (l in listOf(a, b)) {
            if (l.isNotEmpty() && l.length <= 100 && l.contains("video") &&
                !l.contains("tab") && !l.contains("ট্যাব")) return true
        }
        return false
    }

    private fun bigClick(src: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        src.getBoundsInScreen(r)
        return r.height() >= resources.displayMetrics.heightPixels * 15 / 100
    }

    private val clickSkipWords = listOf("story", "stories", "স্টোরি", "messenger", "message", "মেসেজ", "মেসেঞ্জার", "chat", "inbox")
    private fun clickExcluded(src: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = src
        var i = 0
        while (n != null && i < 6) {
            val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val b = n.text?.toString()?.lowercase()?.trim() ?: ""
            val idl = n.viewIdResourceName?.lowercase() ?: ""
            for (l in listOf(a, b, idl)) {
                if (l.isNotEmpty() && l.length <= 120 && clickSkipWords.any { l.contains(it) }) return true
            }
            n = n.parent; i++
        }
        return false
    }

    private fun clickedVideo(src: AccessibilityNodeInfo?): Boolean {
        src ?: return false
        var n: AccessibilityNodeInfo? = src
        var i = 0
        while (n != null && i < 6) {
            if (looksVideo(n)) return true
            n = n.parent; i++
        }
        if (!bigClick(src)) return false
        fun down(x: AccessibilityNodeInfo?, d: Int): Boolean {
            if (x == null || d > 2) return false
            if (looksVideo(x)) return true
            for (k in 0 until x.childCount) if (down(x.getChild(k), d + 1)) return true
            return false
        }
        return down(src, 0)
    }

    private fun fullscreenVideo(root: AccessibilityNodeInfo): Boolean {
        val h = resources.displayMetrics.heightPixels
        val rect = Rect()
        val cnt = intArrayOf(0)
        var found = false
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || found || cnt[0] > 700 || d > 40) return
            cnt[0]++
            if (n.isVisibleToUser) {
                val idl = n.viewIdResourceName?.lowercase() ?: ""
                val cl = n.className?.toString()?.lowercase() ?: ""
                if (idl.contains("video") || idl.contains("player") || cl.contains("video") || cl.contains("player")) {
                    n.getBoundsInScreen(rect)
                    if (rect.height() >= h * 68 / 100) { found = true; return }
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return found
    }

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
        if (!bigClick(src)) return false
        fun down(x: AccessibilityNodeInfo?, d: Int): Boolean {
            if (x == null || d > 2) return false
            if (labelIsReel(x)) return true
            for (k in 0 until x.childCount) if (down(x.getChild(k), d + 1)) return true
            return false
        }
        return down(src, 0)
    }

    private var shotBusy = false
    private var liteSeen = 0
    private var liteLastTop = -1
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val liteLoop = object : Runnable {
        override fun run() {
            try { liteTick() } catch (e: Exception) {}
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
                    if (bmp == null) { shotBusy = false; return }
                    recognizer.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { t ->
                            shotBusy = false
                            try { liteEvaluate(t, bmp.width, bmp.height) } catch (e: Exception) {}
                        }
                        .addOnFailureListener { shotBusy = false }
                } catch (e: Exception) { shotBusy = false }
            }
            override fun onFailure(code: Int) { shotBusy = false }
        })
    }

    private fun liteEvaluate(t: com.google.mlkit.vision.text.Text, w: Int, h: Int) {
        val now = System.currentTimeMillis()
        var found = false
        var foundTop = -1
        for (b in t.textBlocks) for (ln in b.lines) {
            val box = ln.boundingBox ?: continue
            val txt = ln.text.trim().lowercase()
            if (txt in reelExact && box.top < h * 14 / 100 && box.left < w / 2 && box.width() < w / 2) {
                found = true; foundTop = box.top
            }
        }
        if (!found) { liteSeen = 0; return }
        if (liteSeen > 0 && Math.abs(foundTop - liteLastTop) < h * 2 / 100) liteSeen++ else liteSeen = 1
        liteLastTop = foundTop
        if (liteSeen >= 2) {
            liteSeen = 0
            reelIntentUntil = 0L
            blockReels(now)
        }
    }

    private fun scanOnce() {
        if (!Prefs.loggedIn(this) || overlay != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
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

    private fun scanPageText(root: AccessibilityNodeInfo, pkg: String) {
        val sb = StringBuilder()
        val cnt = intArrayOf(0)
        collectText(root, sb, 0, cnt)
        val bad = Guard.badCount(this, sb.toString())
        if (bad >= 2) showWarning(pkg)
    }

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
        card.addView(Ui.text(c, "Allah ke voy korun.", 22f, true, Color.parseColor("#C62828"))
            .apply { gravity = Gravity.CENTER })
        card.addView(Ui.text(c, "Apatot phone ta rekhe ektu pani khan.", 16f)
            .apply { gravity = Gravity.CENTER })
        card.addView(Ui.button(c, "Bondho korun") { dismissWarning(pkg) })

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
            toast("🚫 Nishiddho shobdo")
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
