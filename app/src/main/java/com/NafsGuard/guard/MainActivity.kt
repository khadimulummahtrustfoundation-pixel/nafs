package com.NafsGuard.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_VPN = 77

    private val admin get() = ComponentName(this, AdminReceiver::class.java)
    private val dpm get() = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.GREEN
    }

    override fun onResume() {
        super.onResume()
        // সেটিংসে গিয়ে ৩ মিনিটের বেশি দেরি করলে সেশন শেষ
        if (Session.keepUntil != 0L && System.currentTimeMillis() > Session.keepUntil) {
            Session.unlocked = false
        }
        Session.keepUntil = 0L
        // প্রতিবার অ্যাপে ঢোকার সময় লগইন লাগবে
        if (!Prefs.loggedIn(this) || !Session.unlocked) { goLogin(); return }
        build()
        recheck()
        ensureSafe()
    }

    override fun onStop() {
        super.onStop()
        // অ্যাপ থেকে বের হলে সেশন শেষ (সেটিংস স্ক্রিনে যাওয়ার সময় বাদে)
        if (System.currentTimeMillis() > Session.keepUntil) Session.unlocked = false
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }

    private fun openExternal(i: Intent) {
        Session.keepFor()
        try { startActivity(i) } catch (e: Exception) { Session.keepUntil = 0L }
    }

    private fun stopEverything() {
        Prefs.logout(this)
        Prefs.setSafe(this, false)
        try { DnsVpnService.stop(this) } catch (e: Exception) {}
        Session.unlocked = false
        Session.keepUntil = 0L
    }

    // সার্ভারে এক্সেস বন্ধ/মেয়াদ শেষ হলে লগআউট (লক থাকলে হবে না)
    private fun recheck() {
        val c = this
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            if (!r.ok && !r.net && !Prefs.locked(c)) {
                runOnUiThread {
                    stopEverything()
                    Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); goLogin()
                }
            }
        }.start()
    }

    private fun ensureSafe() {
        if (Prefs.dnsAuto(this) && !PrivateDns.isOn(this)) PrivateDns.apply(this)
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

    // সেইফ সার্চ: অনুমতি থাকলে Private DNS অটো সেট; না থাকলে সহজ গাইড
    private fun enableSafe() {
        if (PrivateDns.apply(this)) {
            Prefs.setDnsAuto(this, true)
            toast("✅ সেইফ সার্চ অটো সেট হয়েছে")
            build()
            return
        }
        showDnsHelp()
    }

    private fun showDnsHelp() {
        val msg = "নিচের বাটন চাপলে নামটি কপি হবে ও সেটিংস খুলবে।\n\n" +
            "সেখানে Private DNS → 'Private DNS provider hostname' বেছে পেস্ট করে Save দিন।"
        AlertDialog.Builder(this)
            .setTitle("সেইফ সার্চ সেট করুন")
            .setMessage(msg)
            .setPositiveButton("কপি করে সেটিংস খুলুন") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("dns", PrivateDns.HOST))
                toast("কপি হয়েছে: ${PrivateDns.HOST}")
                Session.keepFor()
                PrivateDns.openSettings(this)
            }
            .setNeutralButton("VPN মোড") { _, _ -> enableVpnMode() }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    private fun disableSafe() {
        Prefs.setSafe(this, false)
        Prefs.setDnsAuto(this, false)
        try { DnsVpnService.stop(this) } catch (e: Exception) {}
        if (PrivateDns.isOn(this) && !PrivateDns.off(this)) {
            Session.keepFor()
            PrivateDns.openSettings(this)
        }
        build()
        refreshLater()
    }

    // বিকল্প: VPN দিয়ে DNS ফিল্টার
    private fun enableVpnMode() {
        val i = VpnService.prepare(this)
        if (i != null) {
            Session.keepFor()
            try { startActivityForResult(i, REQ_VPN) }
            catch (e: Exception) { Session.keepUntil = 0L; toast("VPN চালু করা যাচ্ছে না") }
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

    private fun build() {
        val c: Context = this
        val locked = Prefs.locked(c)
        val adminOn = dpm.isAdminActive(admin)
        val a11y = a11yOn()

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Ui.dp(c, 18); setPadding(p, p, p, p)
        }
        root.addView(Ui.logo(c, 72))
        root.addView(Ui.text(c, "NafsGuard", 26f, true, Ui.GREEN).apply { gravity = android.view.Gravity.CENTER })
        root.addView(Ui.text(c, "স্বাগতম, ${Prefs.name(c)}").apply { gravity = android.view.Gravity.CENTER })

        // ---- সেটআপ ----
        val setup = Ui.card(c)
        setup.addView(Ui.text(c, "সুরক্ষা সেটআপ", 17f, true))
        setup.addView(Ui.text(c, if (a11y) "✅ গার্ড (অ্যাক্সেসিবিলিটি) চালু" else "❌ গার্ড বন্ধ"))
        if (!a11y) {
            setup.addView(Ui.text(c, "NafsGuard খুঁজে চালু করুন। Android 13+ এ সাইডলোড অ্যাপে অপশন ধূসর থাকলে: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings।", 13f))
            setup.addView(Ui.button(c, "গার্ড চালু করুন") {
                openExternal(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        setup.addView(Ui.text(c, if (adminOn) "✅ অ্যাডমিন অ্যাপ চালু" else "❌ অ্যাডমিন অ্যাপ বন্ধ"))
        if (!adminOn) {
            setup.addView(Ui.button(c, "অ্যাডমিন অনুমতি দিন") {
                openExternal(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "আনইন্সটল ঠেকাতে NafsGuard-কে অ্যাডমিন হতে হবে।")
                })
            })
        }
        val dnsOn = PrivateDns.isOn(c)
        val vpnOn = DnsVpnService.active
        val safe = dnsOn || vpnOn
        setup.addView(Ui.text(c, when {
            dnsOn -> "✅ সেইফ সার্চ চালু (Private DNS)"
            vpnOn -> "✅ সেইফ সার্চ চালু (VPN)"
            else -> "❌ সেইফ সার্চ বন্ধ"
        }))
        if (!safe) {
            setup.addView(Ui.text(c, "বাটন চাপলে অ্যাডাল্ট সাইট ব্লক ও সেইফ সার্চ সেট হবে।", 13f))
            setup.addView(Ui.button(c, "সেইফ সার্চ অন করুন") { enableSafe() })
        } else if (!locked) {
            setup.addView(Ui.button(c, "সেইফ সার্চ বন্ধ করুন") { disableSafe() })
        }
        root.addView(setup)

        // ---- শব্দ তালিকা ----
        val wc = Ui.card(c)
        wc.addView(Ui.text(c, "নিজের ব্লক শব্দ", 17f, true))
        val input = Ui.edit(c, "শব্দ লিখুন")
        wc.addView(input)
        wc.addView(Ui.button(c, "শব্দ যোগ করুন") {
            val w = input.text.toString().trim()
            if (w.length < 2) { toast("কমপক্ষে ২ অক্ষর দিন"); return@button }
            Prefs.addWord(c, w); build()
        })
        for (w in Prefs.words(c).sorted()) {
            wc.addView(Ui.text(c, "• $w"))
            if (!locked) {
                wc.addView(Ui.button(c, "মুছুন: $w") { Prefs.removeWord(c, w); build() })
            }
        }
        if (locked) wc.addView(Ui.text(c, "লক চালু থাকায় শব্দ মুছা যাবে না (যোগ করা যাবে)।", 13f))
        root.addView(wc)

        // ---- লক ----
        val lc = Ui.card(c)
        lc.addView(Ui.text(c, "১ মাসের লক", 17f, true))
        if (locked) {
            val days = ((Prefs.lockUntil(c) - System.currentTimeMillis()) / 86_400_000L) + 1
            lc.addView(Ui.text(c, "🔒 লক চালু, বাকি প্রায় $days দিন। এই সময় আনইন্সটল, লগআউট ও গার্ড বন্ধ করা যাবে না।"))
        } else {
            lc.addView(Ui.button(c, "🔒 ১ মাসের জন্য লক করুন") {
                if (!a11yOn() || !dpm.isAdminActive(admin)) {
                    toast("আগে গার্ড ও অ্যাডমিন চালু করুন"); return@button
                }
                AlertDialog.Builder(c)
                    .setTitle("লক নিশ্চিত করুন")
                    .setMessage("৩০ দিন আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না। নিশ্চিত?")
                    .setPositiveButton("লক করুন") { _, _ -> Prefs.startLock(c); build() }
                    .setNegativeButton("বাতিল", null).show()
            })
            if (adminOn) lc.addView(Ui.button(c, "অ্যাডমিন বন্ধ করুন (আনইন্সটলের জন্য)") {
                dpm.removeActiveAdmin(admin); Prefs.clearLock(c); build()
            })
            lc.addView(Ui.button(c, "লগআউট") { stopEverything(); goLogin() })
        }
        root.addView(lc)

        setContentView(ScrollView(c).apply { setBackgroundColor(Color.WHITE); addView(root) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
