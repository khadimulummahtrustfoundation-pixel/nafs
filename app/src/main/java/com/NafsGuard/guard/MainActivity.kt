package com.NafsGuard.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
    }

    override fun onResume() {
        super.onResume()
        Session.keep = false
        // প্রতিবার অ্যাপে ঢোকার সময় লগইন লাগবে
        if (!Prefs.loggedIn(this) || !Session.unlocked) { goLogin(); return }
        build()
        recheck()
        ensureSafe()
        if (Prefs.locked(this)) Owner.applyLock(this)
        askBattery()
        askNotif()
    }

    override fun onStop() {
        super.onStop()
        // অ্যাপ থেকে বের হলে সেশন শেষ (সেটিংস স্ক্রিনে যাওয়ার সময় বাদে)
        if (!Session.keep) Session.unlocked = false
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

    // সার্ভারে এক্সেস বন্ধ করলে সব সুইচ বন্ধ ও লগআউট (লক থাকলেও)
    private fun recheck() {
        val c = this
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            if (r.off) {
                runOnUiThread {
                    Access.disableAll(c)
                    Toast.makeText(c, "অ্যাডমিন অ্যাক্সেস বন্ধ করেছেন, NafsGuard এখন নিষ্ক্রিয়", Toast.LENGTH_LONG).show()
                    goLogin()
                }
                return@Thread
            }
            if (r.ok) {
                val old = Prefs.unlockOk(c)
                val oldFb = Prefs.fbAccess(c); val oldIg = Prefs.igAccess(c); val oldMd = Prefs.mediaAccess(c)
                Prefs.applyServer(c, r)
                if (old != r.unlock || oldFb != r.fbAccess || oldIg != r.igAccess || oldMd != r.mediaAccess)
                    runOnUiThread { if (!isFinishing && Session.unlocked) build() }
            }
            if (!r.ok && !r.net && !Prefs.locked(c)) {
                runOnUiThread {
                    stopEverything()
                    Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); goLogin()
                }
            }
        }.start()
    }

    // ব্যাটারি অপটিমাইজেশন থেকে বাদ দেওয়ার অনুমতি অ্যাপের ভেতরেই চাওয়া (ইনস্টলের পর প্রথমবার, তারপর ৬ ঘণ্টা পর পর)
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

    // Android 13+: নোটিফিকেশনের অনুমতি (সবসময় চালু থাকার নোটিফিকেশন দেখাতে)
    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 91) } catch (e: Exception) {}
        }
    }

    // ফোনের কোম্পানি অনুযায়ী অটো-স্টার্ট সেটিং খোলা (না পেলে অ্যাপের তথ্য পেজ)
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
        if (Prefs.safe(this) && !DnsVpnService.active && VpnService.prepare(this) == null) {
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

    // সেইফ সার্চ: VPN অনুমতি নিয়ে এক ক্লিকে চালু
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
            else toast("VPN অনুমতি না দিলে সেইফ সার্চ চালু হবে না")
        }
    }

    private fun startSafe() {
        Prefs.setSafe(this, true)
        DnsVpnService.start(this)
        refreshLater()
    }

    // ১ মাসের লক চালুর আগের যাচাই ও নিশ্চিতকরণ
    private fun startLockFlow() {
        val c: Context = this
        if (!a11yOn() || !dpm.isAdminActive(admin)) {
            toast("আগে গার্ড ও অ্যাডমিন চালু করুন"); return
        }
        val pmx = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pmx.isIgnoringBatteryOptimizations(packageName)) {
            toast("আগে ব্যাটারি সেভার থেকে NafsGuard বাদ দিন"); return
        }
        AlertDialog.Builder(c)
            .setTitle("লক নিশ্চিত করুন")
            .setMessage("৩০ দিন আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না। শুধু অ্যাডমিন শিটে অনুমতি দিলে খোলা যাবে। নিশ্চিত?")
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

        // ---- উপরের অংশ: লোগো + সুরক্ষার অবস্থা ----
        root.addView(Ui.heroLogo(c, 96))
        root.addView(Ui.dtext(c, "NafsGuard", 24f, true, Ui.NEON).apply {
            gravity = android.view.Gravity.CENTER
            setOnClickListener { if (++taps >= 5) { taps = 0; Diag.show = !Diag.show; build() } }   // লুকানো স্ট্যাটাস: ৫ বার চাপুন
        })
        root.addView(Ui.dtext(c, if (allOk) "🛡️ আপনি সুরক্ষিত আছেন" else "⚠️ সুরক্ষা সম্পূর্ণ হয়নি", 20f, true,
            if (allOk) Ui.TXT else Ui.AMBER).apply { gravity = android.view.Gravity.CENTER })
        root.addView(Ui.dtext(c,
            "স্বাগতম, ${Prefs.name(c)}\n" + if (allOk) "নিরাপদ ও পরিচ্ছন্ন অনলাইন অভিজ্ঞতা উপভোগ করুন।" else "নিচের সুইচগুলো চালু করুন।",
            14f, false, Ui.TXT2).apply { gravity = android.view.Gravity.CENTER })

        // ---- সুরক্ষা সেটআপ (সুইচে চাপ দিলেই ওই সেটিং খুলবে) ----
        val setup = Ui.dcard(c)
        setup.addView(Ui.dtext(c, "সুরক্ষা সেটআপ", 17f, true))

        setup.addView(Ui.toggleRow(c, "🛡️", "গার্ড", "খারাপ শব্দ ও সাইট আটকায়", a11y) {
            if (!a11y) openExternal(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else toast("গার্ড চালু আছে")
        })
        if (!a11y) setup.addView(Ui.dtext(c,
            "সুইচে চাপ দিয়ে NafsGuard খুঁজে চালু করুন। Android 13+ এ অপশন ধূসর থাকলে: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings।",
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
                else -> { Prefs.setSafe(c, false); DnsVpnService.stop(c); refreshLater() }
            }
        })
        if (!safe) setup.addView(Ui.dtext(c,
            "সুইচে চাপলে Android একবার VPN অনুমতি চাইবে, OK দিন। এটা শুধু নিরাপদ DNS-এর জন্য, আপনার ইন্টারনেট স্পিড বা ডেটা বদলায় না।",
            12.5f, false, Ui.TXT2))
        setup.addView(Ui.divider(c))

        // ৫ম সুইচ: VPN Always-on
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

        // ---- Reels ব্লক ----
        val fbOn = Prefs.fbReel(c)
        val igOn = Prefs.igReel(c)
        val rc = Ui.dcard(c)
        rc.addView(Ui.dtext(c, "Reels ব্লক", 17f, true))
        rc.addView(Ui.toggleRow(c, "📘", "Facebook Reels ব্লক", "Reels খুললেই নিজে থেকে পেছনে যাবে", fbOn) {
            toggleReel(true, fbOn)
        })
        rc.addView(Ui.divider(c))
        rc.addView(Ui.toggleRow(c, "📸", "Instagram Reels ব্লক", "Reels খুললেই নিজে থেকে পেছনে যাবে", igOn) {
            toggleReel(false, igOn)
        })
        if ((fbOn || igOn) && !a11y) rc.addView(Ui.dtext(c, "Reels ব্লক কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।", 12.5f, false, Ui.AMBER))
        rc.addView(Ui.dtext(c,
            if (fbOn || igOn) "চালু করা সুইচ বন্ধ করতে অ্যাডমিনের অনুমতি লাগবে।" else "সুইচ চালু করলে Facebook/Instagram-এ Reels খুললেই NafsGuard পেছনে পাঠিয়ে দেবে।",
            12.5f, false, Ui.TXT2))
        root.addView(rc)

        // ---- ছবি ও ভিডিও সার্চ ব্লক ----
        val mOn = Prefs.media(c)
        val mc = Ui.dcard(c)
        mc.addView(Ui.dtext(c, "ছবি ও ভিডিও সার্চ ব্লক", 17f, true))
        mc.addView(Ui.toggleRow(c, "🖼️", "ছবি ও ভিডিও সার্চ বন্ধ", "ব্রাউজারে ছবি/ভিডিও সার্চ করলে পেছনে যাবে", mOn) {
            toggleMedia(mOn)
        })
        if (mOn && !a11y) mc.addView(Ui.dtext(c, "এটা কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।", 12.5f, false, Ui.AMBER))
        mc.addView(Ui.dtext(c,
            if (mOn) "চালু করা সুইচ বন্ধ করতে অ্যাডমিনের অনুমতি লাগবে." else "চালু করলে Chrome, Firefox ইত্যাদি ব্রাউজারে Google/Bing/DuckDuckGo এর Images ও Videos সার্চ খুলবে না।",
            12.5f, false, Ui.TXT2))
        root.addView(mc)

        // ---- সবসময় চালু রাখার সেটিং ----
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

        // ---- পরীক্ষার স্ট্যাটাস (লেখা পাহারা কাজ না করলে কারণ বোঝার জন্য) ----
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
        if (Diag.show) root.addView(dc)

        // ---- শব্দ তালিকা ----
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

        // ---- লক ----
        val lc = Ui.dcard(c)
        lc.addView(Ui.dtext(c, "১ মাসের লক", 17f, true))
        val isOwner = Owner.isOwner(c)
        lc.addView(Ui.toggleRow(c, "🔒", "অ্যাপ ও আনইন্সটল লক", "৩০ দিন আনইন্সটল, লগআউট ও গার্ড বন্ধ করা যাবে না", locked) {
            if (locked) toast("লক খুলতে নিচের বাটন ব্যবহার করুন") else startLockFlow()
        })
        lc.addView(Ui.dtext(c,
            if (isOwner) "🛡️ Device Owner সক্রিয় (সবচেয়ে শক্ত লক)" else "Device Owner বন্ধ (সাধারণ লক)", 13f, false, Ui.TXT2))
        if (locked) {
            val days = ((Prefs.lockUntil(c) - System.currentTimeMillis()) / 86_400_000L) + 1
            lc.addView(Ui.dtext(c, "🔒 লক চালু, বাকি প্রায় $days দিন।", 15f, true, Ui.NEON))
            val ok = Prefs.unlockOk(c)
            lc.addView(Ui.dbutton(c, if (isOwner) "Device Owner বন্ধ করুন" else "লক খুলুন") { requestUnlock() }.apply {
                isEnabled = ok
                if (!ok) {
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#3A5247")); cornerRadius = Ui.dp(c, 16).toFloat()
                    }
                    setTextColor(Color.parseColor("#8FA89B"))
                }
            })
            if (!ok) lc.addView(Ui.dtext(c, "লক খুলতে অ্যাডমিনের অনুমতি লাগবে।", 12.5f, false, Ui.TXT2))
        } else {
            if (isOwner) lc.addView(Ui.dbutton(c, "Device Owner বন্ধ করুন", true) { releaseOwner() })
            if (adminOn && !isOwner) lc.addView(Ui.dbutton(c, "অ্যাডমিন বন্ধ করুন (আনইন্সটলের জন্য)", true) {
                dpm.removeActiveAdmin(admin); Prefs.clearLock(c); build()
            })
            lc.addView(Ui.dbutton(c, "লগআউট", true) { stopEverything(); goLogin() })
        }
        root.addView(lc)

        setContentView(ScrollView(c).apply { background = Ui.bg(); addView(root) })
    }

    // Reels সুইচ: চালু করা সবসময় যাবে; বন্ধ করতে অ্যাডমিন শিটে অনুমতি (ON) লাগবে
    private fun toggleReel(fb: Boolean, currentlyOn: Boolean) {
        val c = this
        val name = if (fb) "Facebook Reels" else "Instagram Reels"
        if (!currentlyOn) {
            if (fb) Prefs.setFbReel(c, true) else Prefs.setIgReel(c, true)
            toast("$name ব্লক চালু হয়েছে" + if (!a11yOn()) "। গার্ড চালু না থাকলে কাজ করবে না" else "")
            build(); return
        }
        toast("যাচাই হচ্ছে...")
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            runOnUiThread {
                if (r.off) { Access.disableAll(c); toast("অ্যাডমিন অ্যাক্সেস বন্ধ করেছেন"); goLogin(); return@runOnUiThread }
                if (r.ok) Prefs.applyServer(c, r)
                val allowed = r.ok && (if (fb) r.fbAccess else r.igAccess)
                if (allowed) {
                    if (fb) Prefs.setFbReel(c, false) else Prefs.setIgReel(c, false)
                    toast("$name ব্লক বন্ধ হয়েছে")
                } else {
                    toast(if (r.net) r.msg else "অ্যাডমিন এখনো অনুমতি দেননি")
                }
                if (!isFinishing && Session.unlocked) build()
            }
        }.start()
    }

    // ছবি-ভিডিও সার্চ সুইচ: চালু করা সবসময় যাবে; বন্ধ করতে শিটে "Image and Video Access" ON লাগবে
    private fun toggleMedia(currentlyOn: Boolean) {
        val c = this
        if (!currentlyOn) {
            Prefs.setMedia(c, true)
            toast("ছবি ও ভিডিও সার্চ ব্লক চালু হয়েছে" + if (!a11yOn()) "। গার্ড চালু না থাকলে কাজ করবে না" else "")
            build(); return
        }
        toast("যাচাই হচ্ছে...")
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            runOnUiThread {
                if (r.off) { Access.disableAll(c); toast("অ্যাডমিন অ্যাক্সেস বন্ধ করেছেন"); goLogin(); return@runOnUiThread }
                if (r.ok) Prefs.applyServer(c, r)
                if (r.ok && r.mediaAccess) {
                    Prefs.setMedia(c, false)
                    toast("ছবি ও ভিডিও সার্চ ব্লক বন্ধ হয়েছে")
                } else {
                    toast(if (r.net) r.msg else "অ্যাডমিন এখনো অনুমতি দেননি")
                }
                if (!isFinishing && Session.unlocked) build()
            }
        }.start()
    }

    // বাটন চাপলে সার্ভার থেকে আবার যাচাই করে (পুরোনো সেভ করা মানের ওপর ভরসা করে না)
    private fun requestUnlock() {
        toast("যাচাই হচ্ছে...")
        val c = this
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            runOnUiThread {
                if (r.ok && r.unlock) doUnlock()
                else {
                    Prefs.setUnlock(c, false)
                    toast(if (r.net) r.msg else "অ্যাডমিন এখনো অনুমতি দেননি")
                    build()
                }
            }
        }.start()
    }

    private fun doUnlock() {
        if (!Owner.releaseAll(this)) { toast("Device Owner সরানো যায়নি"); return }
        Prefs.clearLock(this)
        Prefs.setUnlock(this, false)
        toast("লক খোলা হয়েছে")
        build()
    }

    private fun releaseOwner() {
        if (Owner.releaseAll(this)) toast("Device Owner বন্ধ হয়েছে") else toast("Device Owner সরানো যায়নি")
        build()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
