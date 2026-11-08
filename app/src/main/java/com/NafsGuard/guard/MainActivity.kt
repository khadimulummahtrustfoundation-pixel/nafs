package com.NafsGuard.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_VPN = 77

    private val admin get() = ComponentName(this, AdminReceiver::class.java)
    private val dpm get() = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // নোটিফিকেশন থেকে ক্লিক করে আসলে নোটিশ ডায়ালগ ওপেন করা
        if (intent?.action == "OPEN_NOTICES" || intent?.getStringExtra("open_page") == "notices") {
            // একটু দেরি করে ডায়ালগ ওপেন করা যাতে UI লোড হতে সমস্যা না হয়
            Handler(Looper.getMainLooper()).postDelayed({
                showNoticesDialog()
            }, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        Session.keep = false
        Update.onFound = Runnable { runOnUiThread { Update.showIfNeeded(this) } }
        Access.enforceExpiry(this)       // মেয়াদ শেষ হলে সব বন্ধ করে Pending পেজে পাঠাবে
        UpdateCheck.checkOnOpen(this)
        if (!Prefs.loggedIn(this) || !Session.unlocked) { goLogin(); return }
        expiryTick.removeCallbacksAndMessages(null)
        expiryTick.postDelayed(expiryRun, 1000)
        build()
        recheck()
        ensureSafe()
        if (Prefs.locked(this)) Owner.applyLock(this)
        askBattery()
        Update.showIfNeeded(this)
    }

    // অ্যাপ খোলা থাকা অবস্থায় মেয়াদ শেষ হলেও সাথে সাথে বন্ধ হবে (ইন্টারনেট লাগে না)
    private val expiryTick = Handler(Looper.getMainLooper())
    private val expiryRun = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (Access.enforceExpiry(this@MainActivity)) { goLogin(); return }
            expiryTick.postDelayed(this, 1000)
        }
    }

    override fun onStop() {
        expiryTick.removeCallbacksAndMessages(null)
        super.onStop()
        if (!Session.keep) Session.unlocked = false
        Remote.onChange = null
        Update.onFound = null
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }

    private fun openExternal(i: Intent) {
        Session.keep = true
        try { startActivity(i) } catch (e: Exception) { Session.keep = false }
    }

    private fun stopEverything() {
        Prefs.logout(this)
        Prefs.setSafe(this, false)
        try { DnsVpnService.stop(this) } catch (e: Exception) {}
        Session.unlocked = false
    }

    private fun recheck() {
        Remote.onChange = Runnable {
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (!Prefs.loggedIn(this)) goLogin()
                else if (Session.unlocked) build()
            }
        }
    }

    private var battDialogShown = false
    private fun askBattery() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName) || battDialogShown || isFinishing) return
        val now = System.currentTimeMillis()
        if (now - Prefs.battAskedAt(this) < 6L * 3600 * 1000) return
        battDialogShown = true
        Prefs.setBattAskedAt(this, now)
        AlertDialog.Builder(this)
            .setTitle("সবসময় চালু রাখার অনুমতি")
            .setMessage("ফোন ব্যাকগ্রাউন্ডে NafsGuard বন্ধ করে দিলে সুরক্ষা থেমে যায়। পরের পর্দায় \"Allow\" চাপলে ব্যাটারি সেভার আর NafsGuard-কে বন্ধ করবে না।")
            .setPositiveButton("চালু করুন") { _, _ ->
                openExternal(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")))
            }
            .setNegativeButton("পরে", null)
            .show()
    }

    private fun openAutoStart() {
        val list = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
            "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity"
        )
        for ((pk, cl) in list) {
            val i = Intent().setComponent(ComponentName(pk, cl))
            if (packageManager.resolveActivity(i, 0) != null) {
                Session.keep = true
                try { startActivity(i); return } catch (e: Exception) { Session.keep = false }
            }
        }
        openExternal(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName")))
        toast("এখানে \"Battery\" বা \"Autostart\" খুঁজে সবসময় চালু / Unrestricted করুন")
    }

    private fun ensureSafe() {
        if ((Prefs.safe(this) || Prefs.linkBlock(this)) && !DnsVpnService.active && VpnService.prepare(this) == null) {
            try { DnsVpnService.start(this) } catch (e: Exception) {}
            refreshLater()
        }
    }

    private fun refreshLater() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && Session.unlocked) build()
        }, 800)
    }

    private fun a11yOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/${GuardService::class.java.name}", true) ||
            s.contains("$packageName/.GuardService", true)
    }

    private var pendingLink = false

    private fun enableLinkBlock() {
        pendingLink = true
        enableSafe()
    }

    private fun enableSafe() {
        val i = VpnService.prepare(this)
        if (i != null) {
            Session.keep = true
            try { startActivityForResult(i, REQ_VPN) }
            catch (e: Exception) { Session.keep = false; toast("VPN চালু করা যাচ্ছে না") }
        } else startSafe()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) startSafe()
            else { pendingLink = false; toast("VPN অনুমতি না দিলে এটা চালু হবে না") }
        }
    }

    private fun startSafe() {
        if (pendingLink) { pendingLink = false; Prefs.setLinkBlock(this, true) } else Prefs.setSafe(this, true)
        DnsVpnService.start(this)
        refreshLater()
    }

    private fun fmtDate(t: Long): String {
        val f = java.text.SimpleDateFormat("d MMM yyyy, hh:mm a", java.util.Locale.ENGLISH)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")
        return f.format(java.util.Date(t))
    }

    private fun startLockFlow() {
        val c: Context = this
        if (!a11yOn() || !dpm.isAdminActive(admin)) {
            toast("আগে গার্ড ও অ্যাডমিন চালু করুন"); return
        }
        val pmx = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pmx.isIgnoringBatteryOptimizations(packageName)) {
            toast("আগে ব্যাটারি সেভার থেকে NafsGuard বাদ দিন"); return
        }
        if (Prefs.expiryAt(c) <= Prefs.now(c)) {
            toast("অ্যাডমিন এখনো মেয়াদ নির্ধারণ করেননি বা মেয়াদ শেষ"); return
        }
        AlertDialog.Builder(c)
            .setTitle("লক নিশ্চিত করুন")
            .setMessage("${fmtDate(Prefs.expiryAt(c))} পর্যন্ত আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না। শুধু অ্যাডমিন খুলে দিলে বা মেয়াদ শেষ হলে খোলা যাবে। নিশ্চিত?")
            .setPositiveButton("লক করুন") { _, _ -> Prefs.startLock(c); Owner.applyLock(c); build() }
            .setNegativeButton("বাতিল", null).show()
    }

    private var taps = 0

    private fun build() {
        val c: Context = this
        val locked = Prefs.locked(c)
        val adminOn = dpm.isAdminActive(admin)
        val a11y = a11yOn()
        val battOk = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val safe = DnsVpnService.active
        val allOk = a11y && adminOn && battOk && safe

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 18); setPadding(p, Ui.dp(c, 24), p, p)
        }

        // উপরের ডানদিকে নোটিফিকেশন আইকন: চাপলে সব পুশ বার্তার পুরো লেখা দেখা যায়
        root.addView(LinearLayout(c).apply {
            gravity = android.view.Gravity.END
            addView(android.widget.ImageView(c).apply {
                setImageResource(R.drawable.ic_bell)
                setColorFilter(Ui.NEON)
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val ip = Ui.dp(c, 10); setPadding(ip, ip, ip, ip)
                contentDescription = "বার্তা ও নোটিশ"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Ui.CARD); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE)
                }
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44))
                setOnClickListener { showNoticesDialog() }
            })
        })
        root.addView(Ui.heroLogo(c, 96))
        root.addView(Ui.dtext(c, "NafsGuard", 24f, true, Ui.NEON).apply {
            gravity = android.view.Gravity.CENTER
            setOnClickListener { if (++taps >= 5) { taps = 0; Diag.show = !Diag.show; build() } }
        })
        root.addView(Ui.dtext(c, if (allOk) "🛡️ আপনি সুরক্ষিত আছেন" else "⚠️ সুরক্ষা সম্পূর্ণ হয়নি", 20f, true,
            if (allOk) Ui.TXT else Ui.AMBER).apply { gravity = android.view.Gravity.CENTER })
        root.addView(Ui.dtext(c,
            "স্বাগতম, ${Prefs.name(c)}\n" + if (allOk) "নিরাপদ ও পরিচ্ছন্ন অনলাইন অভিজ্ঞতা উপভোগ করুন。" else "নিচের সুইচগুলো চালু করুন।",
            14f, false, Ui.TXT2).apply { gravity = android.view.Gravity.CENTER })


        val setup = Ui.dcard(c)
        setup.addView(Ui.dtext(c, "সুরক্ষা সেটআপ", 17f, true))

        setup.addView(Ui.toggleRow(c, "🛡️", "গার্ড", "খারাপ শব্দ ও সাইট আটকায়", a11y) {
            if (!a11y) openExternal(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else toast("গার্ড চালু আছে")
        })
        if (!a11y) setup.addView(Ui.dtext(c,
            "সুইচে চাপ দিয়ে NafsGuard খুঁজে চালু করুন। Android 13+ এ অপশন ধূসর থাকলে: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings。",
            12.5f, false, Ui.TXT2))
        setup.addView(Ui.divider(c))

        setup.addView(Ui.toggleRow(c, "🔐", "অ্যাডমিন অ্যাপ", "সহজে আনইন্সটল ঠেকায়", adminOn) {
            if (!adminOn) openExternal(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "আনইন্সটল ঠেকাতে NafsGuard-কে অ্যাডমিন হতে হবে।")
            }) else toast("অ্যাডমিন চালু আছে")
        })
        setup.addView(Ui.divider(c))

        setup.addView(Ui.toggleRow(c, "🔋", "ব্যাটারি সেভার থেকে মুক্ত", "গার্ড যেন বন্ধ না হয়", battOk) {
            if (!battOk) openExternal(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))) else toast("ব্যাটারি সেভার থেকে মুক্ত আছে")
        })
        setup.addView(Ui.divider(c))

        setup.addView(Ui.toggleRow(c, "🔍", "সেইফ সার্চ", "অনুপযুক্ত ফলাফল ফিল্টার করে", safe) {
            when {
                !safe -> enableSafe()
                locked -> toast("লক চালু থাকায় সেইফ সার্চ বন্ধ করা যাবে না")
                else -> { Prefs.setSafe(c, false); if (!Prefs.linkBlock(c)) DnsVpnService.stop(c); refreshLater() }
            }
        })
        if (!safe) setup.addView(Ui.dtext(c,
            "সুইচে চাপলে Android একবার VPN অনুমতি চাইবে, OK দিন। এটা শুধু নিরাপদ DNS-এর জন্য, আপনার ইন্টারনেট স্পিড বা ডেটা বদলায় না।",
            12.5f, false, Ui.TXT2))
        setup.addView(Ui.divider(c))

        val vpnAlwaysOn = Prefs.vpnAlways(c)
        setup.addView(Ui.toggleRow(c, "🔒", "VPN সবসময় চালু", "সেইফ সার্চ কেউ বন্ধ করতে পারবে না", vpnAlwaysOn) {
            if (!vpnAlwaysOn) {
                if (!Prefs.safe(c)) {
                    toast("আগে সেইফ সার্চ চালু করুন")
                } else {
                    Prefs.setVpnAlways(c, true)
                    val ownerOk = Owner.setAlwaysOnVpn(c, true)
                    if (ownerOk) {
                        toast("VPN Always-on চালু হয়েছে")
                    } else {
                        try {
                            startActivity(android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                        } catch (e: Exception) {}
                        toast("NafsGuard-এর পাশে ⚙️ চেপে Always-on VPN চালু করুন")
                    }
                    refreshLater()
                }
            } else {
                if (Prefs.locked(c)) {
                    toast("লক চালু থাকায় বন্ধ করা যাবে না")
                } else {
                    Prefs.setVpnAlways(c, false)
                    Owner.setAlwaysOnVpn(c, false)
                    toast("VPN Always-on বন্ধ করা হয়েছে")
                    refreshLater()
                }
            }
        })
        if (!vpnAlwaysOn) setup.addView(Ui.dtext(c,
            "চালু করলে সেইফ সার্চ VPN বন্ধ হয়ে গেলে নিজে থেকে আবার চালু হবে। সাধারণ ফোনে একবার Settings → VPN → NafsGuard → Always-on VPN অন করতে হবে।",
            12.5f, false, Ui.TXT2))
        root.addView(setup)

        val verName = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        root.addView(Ui.dtext(c, "অ্যাপ সংস্করণ: $verName (কোড ${Update.currentCode(c)})", 12.5f, false, Ui.TXT2))

        val fbOn = Prefs.fbReel(c)
        val igOn = Prefs.igReel(c)
        val ytOn = Prefs.ytShorts(c)
        val rc = Ui.dcard(c)
        rc.addView(Ui.dtext(c, "Reels ব্লক", 17f, true))
        rc.addView(Ui.toggleRow(c, "📘", "Facebook Reels ব্লক", "Reels খুললেই নিজে থেকে পেছনে যাবে", fbOn) {
            toggleReel(true, fbOn)
        })
        rc.addView(Ui.divider(c))
        rc.addView(Ui.toggleRow(c, "📸", "Instagram Reels ব্লক", "Reels খুললেই নিজে থেকে পেছনে যাবে", igOn) {
            toggleReel(false, igOn)
        })
        rc.addView(Ui.divider(c))
        rc.addView(Ui.toggleRow(c, "▶️", "YouTube Shorts ব্লক", "Shorts খুললেই নিজে থেকে পেছনে যাবে", ytOn) {
            toggleYt(ytOn)
        })
        if ((fbOn || igOn || ytOn) && !a11y) rc.addView(Ui.dtext(c, "Reels ব্লক কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।", 12.5f, false, Ui.AMBER))
        rc.addView(Ui.dtext(c,
            if (fbOn || igOn || ytOn) "চালু করা সুইচ শুধু অ্যাডমিন বন্ধ করতে পারবেন।" else "সুইচ চালু করলে Facebook/Instagram-এ Reels আর YouTube-এ Shorts খুললেই NafsGuard পেছনে পাঠিয়ে দেবে।",
            12.5f, false, Ui.TXT2))
        root.addView(rc)

        val mOn = Prefs.media(c)
        val mc = Ui.dcard(c)
        mc.addView(Ui.dtext(c, "ছবি ও ভিডিও সার্চ ব্লক", 17f, true))
        mc.addView(Ui.toggleRow(c, "🖼️", "ছবি ও ভিডিও সার্চ বন্ধ", "ব্রাউজারে ছবি/ভিডিও সার্চ করলে পেছনে যাবে", mOn) {
            toggleMedia(mOn)
        })
        if (mOn && !a11y) mc.addView(Ui.dtext(c, "এটা কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।", 12.5f, false, Ui.AMBER))
        mc.addView(Ui.dtext(c,
            if (mOn) "চালু করা সুইচ শুধু অ্যাডমিন বন্ধ করতে পারবেন।" else "চালু করলে Chrome, Firefox ইত্যাদি ব্রাউজারে Google/Bing/DuckDuckGo এর Images ও Videos সার্চ খুলবে না।",
            12.5f, false, Ui.TXT2))
        root.addView(mc)

        val lOn = Prefs.linkBlock(c)
        val lc = Ui.dcard(c)
        lc.addView(Ui.dtext(c, "লিংক ব্লক", 17f, true))
        lc.addView(Ui.toggleRow(c, "🔗", "Terabox / Telegram / WhatsApp লিংক বন্ধ", "এই লিংকে ক্লিক, কপি বা খোলা যাবে না", lOn) {
            toggleLink(lOn)
        })
        if (lOn && !a11y) lc.addView(Ui.dtext(c, "ক্লিক ও কপি ব্লক কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।", 12.5f, false, Ui.AMBER))
        lc.addView(Ui.dtext(c,
            if (lOn) "চালু করা সুইচ শুধু অ্যাডমিন বন্ধ করতে পারবেন।" else "চালু করলে এই তিন সাইটের লিংক ব্রাউজারে খুলবে না, ক্লিক করলে ব্লক হবে, আর কপি বা পেস্ট করা যাবে না। এটা শুধু আপনার ফোনেই চালু হবে।",
            12.5f, false, Ui.TXT2))
        root.addView(lc)

        val kc = Ui.dcard(c)
        kc.addView(Ui.dtext(c, "সবসময় চালু রাখুন", 17f, true))
        kc.addView(Ui.dtext(c,
            "ফোন ব্যাকগ্রাউন্ডে অ্যাপ বন্ধ করে দিলে সুরক্ষা থেমে যায়। নিচের সেটিংগুলো একবার করে দিন।",
            12.5f, false, Ui.TXT2))
        kc.addView(Ui.dlink(c, "১. অটো-স্টার্ট / ব্যাকগ্রাউন্ড অনুমতি দিন") { openAutoStart() })
        kc.addView(Ui.dlink(c, "২. সবসময় চালু VPN (সেইফ সার্চ থামবে না)") {
            openExternal(Intent(Settings.ACTION_VPN_SETTINGS))
            toast("NafsGuard-এর পাশে ⚙️ চেপে \"Always-on VPN\" চালু করুন")
        })
        kc.addView(Ui.dtext(c,
            "৩. Recent apps এ NafsGuard এর কার্ড চেপে ধরে \"Lock\" বা পিন করুন, যাতে \"সব বন্ধ করুন\" চাপলে অ্যাপ বন্ধ না হয়।",
            12.5f, false, Ui.TXT2))
        root.addView(kc)

        val dc = Ui.dcard(c)
        dc.addView(Ui.dtext(c, "🔧 পাহারার স্ট্যাটাস (শুধু পরীক্ষার জন্য)", 16f, true))
        val nowT = System.currentTimeMillis()
        fun ago(t: Long) = if (t == 0L) "কখনো না" else "${(nowT - t) / 1000} সেকেন্ড আগে"
        val lines = listOf(
            "সার্ভিস সর্বশেষ চলেছে: ${ago(Diag.loopAt)}",
            "শেষ দেখা অ্যাপ: ${if (Diag.pkg.isEmpty()) "-" else Diag.pkg} (পাহারা চলছে: ${if (Diag.watched) "হ্যাঁ" else "না"})",
            "পেজের লেখা: ${Diag.textNodes} অংশ, ${Diag.textChars} অক্ষর, খারাপ শব্দ ${Diag.textBad} টি (${ago(Diag.textAt)})",
            "স্ক্রিন-তথ্য না পাওয়া: ${Diag.rootNull} বার",
            "Reels পাহারা: ${if (Diag.reelPkg.isEmpty()) "-" else Diag.reelPkg}, সংকেত: ${Diag.reelHit} (${ago(Diag.reelAt)})",
            "সর্বশেষ ব্লক: ${Diag.lastBlock} (${ago(Diag.lastBlockAt)})",
            "Lite ভিডিও: ${Diag.liteVideo} (${ago(Diag.liteVideoAt)})",
            "Lite লেখা: ${Diag.liteOcr} (${ago(Diag.liteOcrAt)}) ${Diag.liteErr}",
            "স্ক্রিনের ক্লাস: ${if (Diag.reelWin.isEmpty()) "-" else Diag.reelWin}",
            "স্ক্রিনের নমুনা: ${if (Diag.reelDump.isEmpty()) "-" else Diag.reelDump}"
        )
        for (l in lines) dc.addView(Ui.dtext(c, l, 12.5f, false, Ui.TXT2))
        dc.addView(Ui.dlink(c, "স্ট্যাটাস রিফ্রেশ") { build() })
        dc.addView(Ui.dlink(c, "এখনই ভার্সন চেক করুন") {
            Toast.makeText(c, "চেক হচ্ছে...", Toast.LENGTH_SHORT).show()
            UpdateCheck.forceNow(c) { msg ->
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    AlertDialog.Builder(c).setMessage(msg).setPositiveButton("ঠিক আছে", null).show()
                    Update.showIfNeeded(this)
                }
            }
        })
        if (Diag.show) root.addView(dc)

        val wc = Ui.dcard(c)
        wc.addView(Ui.dtext(c, "নিজের ব্লক শব্দ", 17f, true))
        val input = Ui.dedit(c, "শব্দ লিখুন")
        wc.addView(input)
        wc.addView(Ui.dbutton(c, "শব্দ যোগ করুন") {
            val w = input.text.toString().trim()
            if (w.length < 3) { toast("কমপক্ষে ৩ অক্ষর দিন (খুব ছোট শব্দ দিলে ভুলভাবে সাধারণ শব্দও ব্লক হয়ে যেতে পারে)"); return@dbutton }
            if (Guard.isCommonWord(w)) {
                toast("এটি খুবই সাধারণ একটি শব্দ, এটা যোগ করলে স্বাভাবিক লেখাও ব্লক হয়ে যাবে, তাই যোগ করা গেল না")
                return@dbutton
            }
            Prefs.addWord(c, w); build()
        })
        for (w in Prefs.words(c).sorted()) {
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4))
            }
            row.addView(Ui.dtext(c, "•  $w", 15f).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (!locked) row.addView(Ui.dtext(c, "মুছুন", 14f, true, Ui.RED).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6))
                setOnClickListener { Prefs.removeWord(c, w); build() }
            })
            wc.addView(row)
        }
        if (locked) wc.addView(Ui.dtext(c, "লক চালু থাকায় শব্দ মুছা যাবে না (যোগ করা যাবে)।", 12.5f, false, Ui.TXT2))
        root.addView(wc)

        root.addView(Blacklist.card(this, locked) { build() })

        root.addView(Pin.card(this) { build() })

        // নাম পরিবর্তন করে 'lc' এর পরিবর্তে 'mainLockCard' করা হয়েছে যাতে কনফ্লিক্ট না হয়
        val mainLockCard = Ui.dcard(c)
        mainLockCard.addView(Ui.dtext(c, "মেইন লক", 17f, true))
        val isOwner = Owner.isOwner(c)
        mainLockCard.addView(Ui.toggleRow(c, "🔒", "অ্যাপ ও আনইন্সটল লক", "মেয়াদ থাকা পর্যন্ত আনইন্সটল ও গার্ড বন্ধ করা যাবে না", locked) {
            if (locked) toast("লক শুধু অ্যাডমিন খুলে দিতে পারবেন বা মেয়াদ শেষে খুলবে") else startLockFlow()
        })
        mainLockCard.addView(Ui.dtext(c,
            if (isOwner) "🛡️ Device Owner সক্রিয় (সবচেয়ে শক্ত লক)" else "Device Owner বন্ধ (সাধারণ লক)", 13f, false, Ui.TXT2))
        if (locked) {
            mainLockCard.addView(Ui.dtext(c, "🔒 লক চালু, মেয়াদ শেষ: ${fmtDate(Prefs.lockUntil(c))}", 15f, true, Ui.NEON))
            mainLockCard.addView(Ui.dtext(c, "লক শুধু অ্যাডমিন খুলে দিতে পারবেন বা মেয়াদ শেষে নিজে খুলবে।", 12.5f, false, Ui.TXT2))
        } else {
            if (isOwner) mainLockCard.addView(Ui.dbutton(c, "Device Owner বন্ধ করুন", true) { releaseOwner() })
            if (adminOn && !isOwner) mainLockCard.addView(Ui.dbutton(c, "অ্যাডমিন বন্ধ করুন (আনইন্সটলের জন্য)", true) {
                dpm.removeActiveAdmin(admin); Prefs.clearLock(c); build()
            })
        }
        root.addView(mainLockCard)

        setContentView(ScrollView(c).apply { background = Ui.bg(); addView(root) })
    }

    // সেভ করা পুশ বার্তাগুলো (৭ দিনের মধ্যে) পুরো লেখাসহ দেখানো
    private fun showNoticesDialog() {
        if (isFinishing) return
        val c: Context = this
        val notices = Prefs.getNotices(c)
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val pd = Ui.dp(c, 18); setPadding(pd, pd, pd, pd)
            background = GradientDrawable().apply {
                setColor(Ui.CARD); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE)
                cornerRadius = Ui.dp(c, 20).toFloat()
            }
        }
        box.addView(Ui.dtext(c, "বার্তা ও নোটিশ", 19f, true, Ui.NEON))
        box.addView(Ui.dtext(c, "বার্তা ৭ দিন পর নিজে থেকে মুছে যায়।", 12f, false, Ui.TXT2))

        val list = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        if (notices.isEmpty()) {
            list.addView(Ui.dtext(c, "এই মুহূর্তে কোনো বার্তা নেই।", 14f, false, Ui.TXT2))
        } else {
            for (n in notices) {
                val item = Ui.dcard(c)
                item.addView(Ui.dtext(c, n.title, 15f, true, Ui.NEON))
                item.addView(Ui.dtext(c, n.body, 14f).apply { setTextIsSelectable(true) })
                item.addView(Ui.dtext(c, "🕒 ${fmtDate(n.timestamp)}", 11.5f, false, Ui.TXT2))
                list.addView(item)
            }
        }
        // তালিকা লম্বা হলে স্ক্রল করবে; বাটন সবসময় স্ক্রিনে থাকবে (উচ্চতা স্ক্রিনের ৬০%-এর বেশি হবে না)
        val maxH = (c.resources.displayMetrics.heightPixels * 0.6).toInt()
        val sv = object : ScrollView(c) {
            override fun onMeasure(w: Int, h: Int) =
                super.onMeasure(w, android.view.View.MeasureSpec.makeMeasureSpec(maxH, android.view.View.MeasureSpec.AT_MOST))
        }
        sv.addView(list)
        box.addView(sv)

        val dlg = Dialog(c)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((c.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        box.addView(Ui.dbutton(c, "বন্ধ করুন", true) { dlg.dismiss() })
        dlg.show()
    }

    private fun toggleReel(fb: Boolean, currentlyOn: Boolean) {
        val c = this
        val name = if (fb) "Facebook Reels" else "Instagram Reels"
        if (!currentlyOn) {
            if (fb) Prefs.setFbReel(c, true) else Prefs.setIgReel(c, true)
            toast("$name ব্লক চালু হয়েছে" + if (!a11yOn()) "। গার্ড চালু না থাকলে কাজ করবে না" else "")
            build(); return
        }
        toast("$name ব্লক শুধু অ্যাডমিন বন্ধ করতে পারবেন")
    }

    private fun toggleYt(currentlyOn: Boolean) {
        val c = this
        if (!currentlyOn) {
            Prefs.setYtShorts(c, true)
            toast("YouTube Shorts ব্লক চালু হয়েছে" + if (!a11yOn()) "। গার্ড চালু না থাকলে কাজ করবে না" else "")
            build(); return
        }
        toast("YouTube Shorts ব্লক শুধু অ্যাডমিন বন্ধ করতে পারবেন")
    }

    private fun toggleLink(currentlyOn: Boolean) {
        if (!currentlyOn) { enableLinkBlock(); return }
        toast("লিংক ব্লক শুধু অ্যাডমিন বন্ধ করতে পারবেন")
    }

    private fun toggleMedia(currentlyOn: Boolean) {
        val c = this
        if (!currentlyOn) {
            Prefs.setMedia(c, true)
            toast("ছবি ও ভিডিও সার্চ ব্লক চালু হয়েছে" + if (!a11yOn()) "। গার্ড চালু না থাকলে কাজ করবে না" else "")
            build(); return
        }
        toast("ছবি ও ভিডিও সার্চ ব্লক শুধু অ্যাডমিন বন্ধ করতে পারবেন")
    }

    private fun releaseOwner() {
        if (Owner.releaseAll(this)) toast("Device Owner বন্ধ হয়েছে") else toast("Device Owner সরানো যায়নি")
        build()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
