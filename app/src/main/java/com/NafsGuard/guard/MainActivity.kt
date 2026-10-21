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

    // সার্ভারে এক্সেস বন্ধ/মেয়াদ শেষ হলে লগআউট (লক থাকলে হবে না)
    private fun recheck() {
        val c = this
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            if (r.ok) {
                val old = Prefs.unlockOk(c)
                Prefs.setUnlock(c, r.unlock)
                if (old != r.unlock) runOnUiThread { if (!isFinishing && Session.unlocked) build() }
            }
            if (!r.ok && !r.net && !Prefs.locked(c)) {
                runOnUiThread {
                    stopEverything()
                    Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); goLogin()
                }
            }
        }.start()
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
        val imgSupported = Build.VERSION.SDK_INT >= 30 && NudeDetector.modelBundled(c)
        val imgOn = imgSupported && Prefs.imageGuard(c)
        setup.addView(Ui.toggleRow(c, "🖼️", "ছবি পাহারা",
            when {
                Build.VERSION.SDK_INT < 30 -> "Android 11 বা তার উপরে লাগবে"
                !imgSupported -> "মডেল পাওয়া যায়নি (অ্যাপ নতুন করে বিল্ড করুন)"
                NudeDetector.hasFailed() -> "ছবি চেনার মডেল চালু হয়নি, অ্যাপ আপডেট করুন"
                else -> "ব্রাউজার, টেলিগ্রাম, টেরাবক্স ও হোয়াটসঅ্যাপে নগ্ন ছবি আটকায়"
            }, imgOn) {
            when {
                !imgSupported -> toast("এই ফোনে বা এই বিল্ডে ছবি পাহারা চালু করা যাচ্ছে না")
                imgOn && locked -> toast("লক চালু থাকায় ছবি পাহারা বন্ধ করা যাবে না")
                else -> { Prefs.setImageGuard(c, !imgOn); build() }
            }
        })
        root.addView(setup)

        // ---- পরীক্ষার স্ট্যাটাস (ছবি/লেখা পাহারা কাজ না করলে কারণ বোঝার জন্য) ----
        val dc = Ui.dcard(c)
        dc.addView(Ui.dtext(c, "🔧 পাহারার স্ট্যাটাস (শুধু পরীক্ষার জন্য)", 16f, true))
        val nowT = System.currentTimeMillis()
        fun ago(t: Long) = if (t == 0L) "কখনো না" else "${(nowT - t) / 1000} সেকেন্ড আগে"
        val lines = listOf(
            "Android: ${Build.VERSION.SDK_INT} (ছবি পাহারার জন্য ৩০+ লাগে)",
            "সার্ভিস সর্বশেষ চলেছে: ${ago(Diag.loopAt)}",
            "শেষ দেখা অ্যাপ: ${if (Diag.pkg.isEmpty()) "-" else Diag.pkg} (পাহারার তালিকায়: ${if (Diag.watched) "হ্যাঁ" else "না"})",
            "পেজের লেখা: ${Diag.textNodes} অংশ, ${Diag.textChars} অক্ষর, খারাপ শব্দ ${Diag.textBad} টি (${ago(Diag.textAt)})",
            "মডেল ফাইল: ${if (NudeDetector.modelBundled(c)) "আছে" else "নেই"}, অবস্থা: ${Diag.model}",
            "স্ক্রিনশট: ${Diag.shot} (${ago(Diag.shotAt)})",
            "শেষ ছবির স্কোর: ${if (Diag.score < 0) "-" else String.format("%.2f", Diag.score)}",
            "স্ক্রিন-তথ্য না পাওয়া: ${Diag.rootNull} বার"
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
