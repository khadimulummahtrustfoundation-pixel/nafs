package com.NafsGuard.guard

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.Service
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
    private val igReelIds = listOf("clips")
    private val inboxIds = listOf("direct_inbox", "inbox_refreshable", "row_inbox", "inbox_container",
        "direct_thread", "thread_list", "message_list", "row_thread")
    private val listClasses = listOf("recyclerview", "listview", "scrollview")
    private var weakHits = 0
    private var lastWeakAt = 0L
    private var ocrPkg = ""
    private var viewerSeen = 0
    private var warnBusy = false
    private val numRegex = Regex("^[0-9]+([.,][0-9])?[KM]?$")
    private val notPlayerLines = listOf("what's on your mind", "search", "friend request", "people you may know",
        "notifications", "marketplace", "chats", "messages", "your story", "create story", "send message",
        "reply", "type a message", "active now", "add friend", "confirm", "delete",
        "add to story")
    private var liteGraceUntil = 0L
    private var awaitTouch = false
    private var awaitSince = 0L
    private var lastRulesTry = 0L
    private fun awaiting(now: Long) = awaitTouch && now - awaitSince < 6000

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
            try { syncAccess() } catch (e: Exception) {}
            try { keepAliveTick() } catch (e: Throwable) {}
            handler.postDelayed(this, 1500)
        }
    }

    private val mediaWords = setOf("images", "image", "videos", "video", "short videos", "ছবি", "ছবিগুলো", "ভিডিও", "ভিডিওগুলো")
    private val siblingWords = setOf("all", "news", "web", "shopping", "maps", "সব", "খবর", "সংবাদ", "ওয়েব")
    private var lastMediaScan = 0L
    private var lastMediaBlock = 0L
    private var mediaStreak = 0

    private fun isBrowser(pkg: String): Boolean {
        if (pkg in urlBars) return true
        val p = pkg.lowercase()
        return listOf("browser", "chrome", "firefox", "fenix", "opera", "brave", "vivaldi", "duckduckgo", "phoenix", "ucmobile")
            .any { p.contains(it) }
    }

    private fun isMediaUrl(u0: String): Boolean {
        val u = u0.lowercase()
        if (u.contains("google.")) {
            if (u.contains("tbm=isch") || u.contains("tbm=vid") || u.contains("udm=2") || u.contains("udm=7") ||
                u.contains("udm=39") || u.contains("/imghp") || u.contains("images.google") || u.contains("google.com/images") ||
                u.contains("/videohp")) return true
        }
        if (u.contains("bing.com") && (u.contains("/images") || u.contains("/videos"))) return true
        if (u.contains("duckduckgo.com") && (u.contains("iax=images") || u.contains("iax=videos") ||
                u.contains("ia=images") || u.contains("ia=videos"))) return true
        if (u.contains("images.search.yahoo") || u.contains("video.search.yahoo")) return true
        if (u.contains("yandex.") && (u.contains("/images") || u.contains("/video"))) return true
        if (u.contains("search.brave.com") && (u.contains("/images") || u.contains("/videos"))) return true
        if (u.contains("startpage.com") && (u.contains("cat=pics") || u.contains("cat=video"))) return true
        return false
    }

    private fun mediaTabSelected(root: AccessibilityNodeInfo): Boolean {
        var tabSel = false
        var sibling = false
        val cnt = intArrayOf(0)
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || cnt[0] > 2500 || d > 70 || (tabSel && sibling)) return
            cnt[0]++
            val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val b = n.text?.toString()?.lowercase()?.trim() ?: ""
            for (l0 in listOf(a, b)) {
                if (l0.isEmpty() || l0.length > 40) continue
                val l = l0.removeSuffix(", selected").removeSuffix(" selected").trim()
                if (l in siblingWords) sibling = true
                if (l in mediaWords && (isSelectedNode(n) || l0.contains("selected") || l0.contains("current"))) tabSel = true
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return tabSel && sibling
    }

    private fun clickedMediaTab(src: AccessibilityNodeInfo?): Boolean {
        var n: AccessibilityNodeInfo? = src
        var i = 0
        while (n != null && i < 4) {
            val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
            val b = n.text?.toString()?.lowercase()?.trim() ?: ""
            if (a in mediaWords || b in mediaWords) return true
            n = n.parent; i++
        }
        return false
    }

    private fun blockMedia(now: Long) {
        if (now - lastMediaBlock < 1500) return
        if (now - lastMediaBlock > 10000) mediaStreak = 0
        lastMediaBlock = now
        mediaStreak++
        Diag.lastBlock = "media-search"; Diag.lastBlockAt = now
        if (mediaStreak >= 3) { mediaStreak = 0; performGlobalAction(GLOBAL_ACTION_HOME) }
        else performGlobalAction(GLOBAL_ACTION_BACK)
        toast("🚫 NafsGuard: ছবি ও ভিডিও সার্চ বন্ধ আছে")
    }

    private fun checkMedia(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!Prefs.media(this) || !isBrowser(pkg)) return false
        val now = System.currentTimeMillis()
        if (now - lastMediaScan < 600) return false
        lastMediaScan = now
        if (mediaTabSelected(root)) { blockMedia(now); return true }
        return false
    }

    private var lastSync = 0L
    private var syncBusy = false
    private fun syncAccess() {
        if (!Prefs.loggedIn(this) || syncBusy) return
        val now = System.currentTimeMillis()
        if (now - lastSync < 120000) return
        val phone = Prefs.phone(this)
        val pass = Prefs.pass(this)
        if (phone.isEmpty() || pass.isEmpty()) return
        lastSync = now
        syncBusy = true
        Thread {
            try {
                if (Rules.due(this)) Rules.refresh(this)
                val r = Api.login(phone, pass)
                if (r.off) {
                    handler.post {
                        Access.disableAll(this)
                        toast("অ্যাডমিন অ্যাক্সেস বন্ধ করেছেন, NafsGuard এখন নিষ্ক্রিয়")
                    }
                } else if (r.ok) {
                    Prefs.applyServer(this, r)
                }
            } catch (e: Exception) {
            } finally { syncBusy = false }
        }.start()
    }

    private var keepOn = false
    private fun keepAliveTick() {
        val want = Prefs.loggedIn(this)
        if (want && !keepOn) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(android.app.NotificationChannel(
                        "guard_keep", "NafsGuard সুরক্ষা", android.app.NotificationManager.IMPORTANCE_MIN))
                }
                val open = android.app.PendingIntent.getActivity(
                    this, 0, Intent(this, LoginActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
                val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, "guard_keep")
                        else android.app.Notification.Builder(this)
                val n = b.setContentTitle("NafsGuard চালু আছে")
                    .setContentText("আপনার সুরক্ষা সক্রিয়")
                    .setSmallIcon(android.R.drawable.ic_lock_lock)
                    .setOngoing(true)
                    .setContentIntent(open)
                    .build()
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(4021, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(4021, n)
                }
                keepOn = true
            } catch (e: Throwable) {}
        } else if (!want && keepOn) {
            try { stopForeground(Service.STOP_FOREGROUND_REMOVE) } catch (e: Throwable) {}
            keepOn = false
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
        try { keepAliveTick() } catch (e: Throwable) {}
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
            toast("🔒 লক চালু, NafsGuard-এর সেটিংস বদলানো যাবে না")
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

        when (e.eventType) {
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START, AccessibilityEvent.TYPE_TOUCH_INTERACTION_END,
            AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_VIEW_CLICKED ->
                if (System.currentTimeMillis() - awaitSince > 500) awaitTouch = false
        }

        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = e.className?.toString() ?: ""
            if (cls.isNotEmpty() && !cls.startsWith("android")) {
                lastWinPkg = pkg
                lastWinClass = cls
                if (reelEnabled(pkg)) {
                    val tNow = System.currentTimeMillis()
                    Diag.reelWin = cls
                    val cl = cls.lowercase()
                    val skip = listOf("share", "direct", "message", "messag", "inbox", "chat", "thread",
                        "story", "stories", "compos", "create", "upload", "camera")
                    if (cl.contains("reel") && skip.none { cl.contains(it) } && !awaiting(tNow)) {
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
                if (pkg == "com.facebook.lite") {
                    val src = e.source
                    if (src != null) {
                        val r = Rect(); src.getBoundsInScreen(r)
                        val dmx = resources.displayMetrics
                        val cy = (r.top + r.bottom) / 2
                        val label = ((src.contentDescription?.toString() ?: "") + " " + (src.text?.toString() ?: "")).lowercase()
                        val wide = r.width() > dmx.widthPixels * 40 / 100
                        val reelTap = !wide && (label.contains("reel") || label.contains("রিল") || label.contains("video"))
                        if (cy < dmx.heightPixels * 20 / 100 && !reelTap) liteGraceUntil = System.currentTimeMillis() + 5000
                    }
                }
                if (Prefs.media(this) && isBrowser(pkg) && clickedMediaTab(e.source)) {
                    handler.postDelayed({ blockMedia(System.currentTimeMillis()) }, 500)
                }
                val p = pkg.lowercase()
                if (Prefs.locked(this) && clickParts.any { p.contains(it) } && clickedOurName(e)) {
                    handler.postDelayed({ block() }, 450)
                }
                return
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED, AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> return
        }

        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: pkg
        if (deviceAdminGuard(root)) return
        if (protect(rootPkg, root)) return
        if (checkMedia(root, rootPkg)) return
        if (checkReels(root, rootPkg)) return

        val now = System.currentTimeMillis()
        if (now - last < 300) return
        last = now
        urlBars[rootPkg]?.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id).forEach { n ->
                val t = n.text?.toString() ?: return@forEach
                if (Prefs.media(this) && isMediaUrl(t)) {
                    blockMedia(System.currentTimeMillis())
                    return
                }
                if (Guard.isBad(this, t)) {
                    showWarning(rootPkg)
                    return
                }
            }
        }
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
            val sel = Bundle()
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, clean.length)
            sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, clean.length)
            n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
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
            if (n == null || cnt[0] > 3000 || depth > 70) return
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
            if (dump != null && dump.size < 40 && visible) {
                val sid = id.substringAfter(":id/")
                var s2 = sid + (if (labels.isNotEmpty()) "[" + labels[0].take(18) + "]" else "") + (if (sel) "*" else "")
                if (interesting && bounds && h > 0) s2 += "@" + (rect.top * 100 / h) + "-" + (rect.bottom * 100 / h)
                if (s2.isNotEmpty()) dump.add(s2)
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1, nowInList)
        }
        val quick = quickTab(root, isIg, h)
        if (quick != null) return quick
        walk(root, 0, false)
        if (dump != null) Diag.reelDump = dump.joinToString("  ")
        if (inbox) return null
        return hit
    }

    private fun quickTab(root: AccessibilityNodeInfo, isIg: Boolean, h: Int): String? {
        if (isIg) {
            val ids = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
            if (ids != null) for (n in ids) if (n.isVisibleToUser && isSelectedNode(n)) return "tab:clips_tab"
        }
        val rect = Rect()
        for (q in listOf("reel", "রিল")) {
            val list = root.findAccessibilityNodeInfosByText(q) ?: continue
            var i = 0
            for (n in list) {
                if (i++ > 40) break
                if (!n.isVisibleToUser) continue
                val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val b = n.text?.toString()?.lowercase()?.trim() ?: ""
                val l = if (a.isNotEmpty()) a else b
                if (l.isEmpty() || l.length > 40 || reelWords.none { l.startsWith(it) }) continue
                val selected = isSelectedNode(n) || l.contains("selected") || l.contains("নির্বাচিত")
                if (!selected) continue
                val idl = n.viewIdResourceName?.lowercase() ?: ""
                var tabLike = idl.contains("tab") || l.contains("tab") || l.contains("ট্যাব")
                if (!tabLike) {
                    n.getBoundsInScreen(rect)
                    tabLike = rect.height() in 1 until (h * 12 / 100) &&
                        (rect.top > h * 80 / 100 || rect.bottom < h * 20 / 100)
                }
                if (tabLike) return "sel:$l"
            }
        }
        return null
    }

    private val actWords = listOf("like", "comment", "share", "save", "more", "send",
        "লাইক", "মন্তব্য", "কমেন্ট", "শেয়ার", "সেভ")

    private fun actionColumn(root: AccessibilityNodeInfo): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val rect = Rect()
        val pts = ArrayList<IntArray>()
        for (q in actWords) {
            val list = root.findAccessibilityNodeInfosByText(q) ?: continue
            var i = 0
            for (n in list) {
                if (i++ > 25) break
                if (!n.isVisibleToUser) continue
                val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val b = n.text?.toString()?.lowercase()?.trim() ?: ""
                val l = if (a.isNotEmpty()) a else b
                if (l.isEmpty() || l.length > 40 || !l.startsWith(q)) continue
                n.getBoundsInScreen(rect)
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                if (cx > w * 78 / 100 && cy > h * 30 / 100 && cy < h * 97 / 100 && rect.width() < w * 25 / 100) {
                    pts.add(intArrayOf(cx, cy))
                }
            }
        }
        if (pts.size < 3) return false
        pts.sortBy { it[1] }
        var distinct = 1
        var lastY = pts[0][1]
        var minX = pts[0][0]
        var maxX = pts[0][0]
        for (i in 1 until pts.size) {
            if (pts[i][1] - lastY >= h * 5 / 100) { distinct++; lastY = pts[i][1] }
            if (pts[i][0] < minX) minX = pts[i][0]
            if (pts[i][0] > maxX) maxX = pts[i][0]
        }
        return distinct >= 3 && (maxX - minX) <= w * 8 / 100
    }

    private fun storyOrInbox(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (pkg !in fbPkgs) return false
        val storyIds = listOf("story_viewer", "stories_viewer", "story_tray", "viewer_progress", "story_reaction", "stories_pager")
        val inboxIdsFb = listOf("inbox", "thread", "messenger", "message_list", "chat")
        val words = listOf("your story", "create story", "add to story", "pause story", "send message",
            "chats", "messages", "type a message", "active now", "আপনার স্টোরি", "মেসেজ", "চ্যাট")
        val cnt = intArrayOf(0)
        var hit = false
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || hit || cnt[0] > 1800 || d > 40) return
            cnt[0]++
            if (n.isVisibleToUser) {
                val id = n.viewIdResourceName?.lowercase() ?: ""
                if (storyIds.any { id.contains(it) } || inboxIdsFb.any { id.contains(it) }) { hit = true; return }
                val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val b = n.text?.toString()?.lowercase()?.trim() ?: ""
                if (words.any { w -> (a.isNotEmpty() && a.startsWith(w)) || (b.isNotEmpty() && b.startsWith(w)) }) {
                    hit = true; return
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return hit
    }

    private fun ruleSignal(root: AccessibilityNodeInfo, pkg: String): String? {
        val r = Rules.forPkg(this, pkg) ?: return null
        for (id in r.skipIds) {
            val l = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (l != null && l.any { it.isVisibleToUser }) return "skip"
        }
        for (id in r.reelIds) {
            val l = root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            if (l != null && l.any { it.isVisibleToUser }) return "id:$id"
        }
        for (d in r.reelDesc) {
            val l = root.findAccessibilityNodeInfosByText(d) ?: continue
            for (n in l) {
                if (!n.isVisibleToUser) continue
                val a = n.contentDescription?.toString()?.lowercase()?.trim() ?: ""
                val b = n.text?.toString()?.lowercase()?.trim() ?: ""
                if (a.startsWith(d) || b.startsWith(d)) return "desc:" + d.take(12)
            }
        }
        return null
    }

    private fun geomColumn(root: AccessibilityNodeInfo): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val rect = Rect()
        val pts = ArrayList<IntArray>()
        val cnt = intArrayOf(0)
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || cnt[0] > 3500 || d > 70) return
            cnt[0]++
            if (n.isVisibleToUser && (n.isClickable || n.isFocusable || n.isLongClickable ||
                    n.contentDescription != null)) {
                n.getBoundsInScreen(rect)
                val bw = rect.width()
                val bh = rect.height()
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                if (cx > w * 78 / 100 && cy > h * 30 / 100 && cy < h * 96 / 100 &&
                    bw >= w * 5 / 100 && bw <= w * 20 / 100 && bh >= w * 4 / 100 && bh <= w * 20 / 100) {
                    pts.add(intArrayOf(cx, cy))
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        if (pts.size < 4) return false
        pts.sortBy { it[1] }
        val col = ArrayList<IntArray>()
        for (p in pts) {
            if (col.isNotEmpty() && p[1] - col.last()[1] < h * 25 / 1000) continue
            col.add(p)
        }
        var start = 0
        while (start < col.size) {
            var end = start
            while (end + 1 < col.size) {
                val gap = col[end + 1][1] - col[end][1]
                val dx = Math.abs(col[end + 1][0] - col[start][0])
                if (gap in (h * 3 / 100)..(h * 13 / 100) && dx <= w * 8 / 100) end++ else break
            }
            val n = end - start + 1
            if (n >= 4 && n <= 8) {
                val gaps = ArrayList<Int>()
                for (i in start until end) gaps.add(col[i + 1][1] - col[i][1])
                val mn = gaps.minOrNull() ?: 1
                val mx = gaps.maxOrNull() ?: 1
                val span = col[end][1] - col[start][1]
                if (mx * 100 >= mn * 125 && span <= h * 50 / 100) return true
            }
            start = end + 1
        }
        return false
    }

    private fun blockReels(now: Long) {
        if (now - lastReelBlock < 900) return
        lastReelBlock = now
        awaitTouch = true; awaitSince = now
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

    private fun checkReels(root: AccessibilityNodeInfo, pkg: String): Boolean {
        if (!reelEnabled(pkg)) return false
        val now = System.currentTimeMillis()
        if (now - lastReelScan < 350) return false
        lastReelScan = now
        if (awaiting(now)) return false
        if (now < liteGraceUntil) return false
        if (storyOrInbox(root, pkg)) return false
        val rs = ruleSignal(root, pkg)
        if (rs == "skip") return false
        val col = actionColumn(root) || (pkg in igPkgs && geomColumn(root))
        val hit: String? = rs ?: reelSignal(root, pkg) ?: (if (col) "col:viewer" else null)
        Diag.reelPkg = pkg; Diag.reelHit = hit ?: "-"; Diag.reelAt = now
        if (hit == null) {
            weakHits = 0
            if (now - lastReelBlock > 8000) reelStreak = 0
            return false
        }
        if (hit.startsWith("top:") || hit.startsWith("col:")) {
            if (now - lastWeakAt > 2500) weakHits = 0
            lastWeakAt = now
            weakHits++
            if (weakHits < 2) return false
        }
        Diag.lastBlock = pkg.substringAfterLast('.') + " " + hit; Diag.lastBlockAt = now
        blockReels(now)
        return true
    }

    private var shotBusy = false
    private var liteSeen = 0
    private var liteLastTop = -1
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private val liteLoop = object : Runnable {
        override fun run() {
            try { liteTick() } catch (e: Exception) { Diag.liteErr = "e:" + (e.message ?: "").take(30) }
            handler.postDelayed(this, 800)
        }
    }

    private fun liteTick() {
        if (Build.VERSION.SDK_INT < 30 || shotBusy || warnBusy) return
        if (!Prefs.loggedIn(this)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val pkg = rootInActiveWindow?.packageName?.toString()
        val on = pkg != null && ((pkg in fbPkgs && Prefs.fbReel(this)) ||
            (pkg == "com.instagram.lite" && Prefs.igReel(this)))
        if (!on) { liteSeen = 0; viewerSeen = 0; return }
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
        var moreEnd = false
        var follow = false
        var upNext = false
        var addComment = false
        var numRightCount = 0
        var notPlayer = false
        for (b in t.textBlocks) for (ln in b.lines) {
            val box = ln.boundingBox ?: continue
            val raw = ln.text.trim()
            val txt = raw.lowercase()
            if (notPlayerLines.any { txt.startsWith(it) }) notPlayer = true
            if (box.top < h * 25 / 100 && topLines.size < 8) topLines.add(txt.take(14))
            if (txt in reelExact && box.top < h * 14 / 100 && box.left < w / 2 && box.width() < w / 2) {
                found = true; foundTop = box.top
            }
            if (box.top > h * 55 / 100) {
                if (!txt.contains("see more") && (txt == "more" || txt.endsWith("... more") ||
                        txt.endsWith("… more") || txt.endsWith("...more"))) moreEnd = true
                if (txt.startsWith("follow") && box.left > w * 40 / 100) follow = true
                if (txt.startsWith("up next")) upNext = true
                if (txt.startsWith("add a comment")) addComment = true
            }
            if (box.left > w * 80 / 100 && box.top > h * 40 / 100 && raw.length <= 5 && numRegex.matches(raw)) numRightCount++
        }
        val viewer = !notPlayer && moreEnd &&
            ((numRightCount >= 2) || upNext || (addComment && numRightCount >= 1) || (follow && numRightCount >= 1))
        Diag.liteOcr = (if (viewer) "[VIEWER] " else "") + (if (topLines.isEmpty()) "-" else topLines.joinToString("|"))
        Diag.liteOcrAt = now
        if (now < liteGraceUntil || awaiting(now)) { liteSeen = 0; viewerSeen = 0; return }
        if (viewer) {
            viewerSeen++
            if (viewerSeen >= 2) {
                viewerSeen = 0
                liteSeen = 0
                Diag.reelPkg = ocrPkg; Diag.reelHit = "ocr:viewer"; Diag.reelAt = now
                Diag.lastBlock = ocrPkg.substringAfterLast('.') + " ocr:viewer"; Diag.lastBlockAt = now
                blockReels(now)
                return
            }
        } else viewerSeen = 0
        if (!found) { liteSeen = 0; return }
        if (liteSeen > 0 && Math.abs(foundTop - liteLastTop) < h * 2 / 100) liteSeen++ else liteSeen = 1
        liteLastTop = foundTop
        if (liteSeen >= 2) {
            liteSeen = 0
            Diag.reelPkg = ocrPkg; Diag.reelHit = "ocr:reels"; Diag.reelAt = now
            Diag.lastBlock = ocrPkg.substringAfterLast('.') + " ocr:reels"; Diag.lastBlockAt = now
            blockReels(now)
        }
    }

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

    private fun scanPageText(root: AccessibilityNodeInfo, pkg: String) {
        val sb = StringBuilder()
        val cnt = intArrayOf(0)
        collectText(root, sb, 0, cnt)
        val bad = Guard.badCount(this, sb.toString())
        Diag.textNodes = cnt[0]; Diag.textChars = sb.length; Diag.textBad = bad; Diag.textAt = System.currentTimeMillis()
        if (bad >= 2) showWarning(pkg)
    }

    private fun blurBitmap(src: Bitmap): Bitmap {
        val sw = maxOf(1, src.width / 28)
        val sh = maxOf(1, src.height / 28)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val tiny = Bitmap.createScaledBitmap(small, maxOf(1, sw / 2), maxOf(1, sh / 2), true)
        return Bitmap.createScaledBitmap(tiny, maxOf(1, src.width / 4), maxOf(1, src.height / 4), true)
    }

    private fun showWarning(pkg: String) {
        if (overlay != null || warnBusy) return
        if (Build.VERSION.SDK_INT >= 30) {
            warnBusy = true
            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                        var blurred: Bitmap? = null
                        try {
                            val hb = r.hardwareBuffer
                            val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                            val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                            hb.close()
                            if (bmp != null) blurred = blurBitmap(bmp)
                        } catch (e: Exception) {}
                        warnBusy = false
                        buildWarning(pkg, blurred)
                    }
                    override fun onFailure(code: Int) { warnBusy = false; buildWarning(pkg, null) }
                })
            } catch (e: Exception) {
                warnBusy = false
                buildWarning(pkg, null)
            }
        } else {
            buildWarning(pkg, null)
        }
    }

    private fun buildWarning(pkg: String, blurred: Bitmap?) {
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
            isClickable = true
            if (blurred != null) {
                setBackgroundColor(Color.BLACK)
                addView(ImageView(c).apply {
                    scaleType = ImageView.ScaleType.FIT_XY
                    setImageBitmap(blurred)
                }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(View(c).apply { setBackgroundColor(Color.parseColor("#59000000")) },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } else {
                setBackgroundColor(Color.parseColor("#E6101010"))
            }
            addView(card, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
            ).apply { setMargins(m, 0, m, 0) })
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
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
