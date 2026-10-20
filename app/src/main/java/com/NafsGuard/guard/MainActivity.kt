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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
        val battOk = (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        setup.addView(Ui.text(c, if (battOk) "✅ ব্যাটারি সেভার থেকে মুক্ত" else "⚠️ ব্যাটারি সেভার গার্ড বন্ধ করে দিতে পারে"))
        if (!battOk) {
            setup.addView(Ui.button(c, "ব্যাটারি সেভার থেকে বাদ দিন") {
                openExternal(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")))
            })
        }
        val safe = DnsVpnService.active
        setup.addView(Ui.text(c, if (safe) "✅ সেইফ সার্চ চালু" else "❌ সেইফ সার্চ বন্ধ"))
        if (!safe) {
            setup.addView(Ui.text(c, "বাটন চাপলে Android একবার VPN অনুমতি চাইবে, OK দিন। এটা শুধু নিরাপদ DNS-এর জন্য, আপনার ইন্টারনেট স্পিড বা ডেটা বদলায় না।", 13f))
            setup.addView(Ui.button(c, "সেইফ সার্চ অন করুন") { enableSafe() })
        } else if (!locked) {
            setup.addView(Ui.button(c, "সেইফ সার্চ বন্ধ করুন") {
                Prefs.setSafe(c, false)
                DnsVpnService.stop(c)
                refreshLater()
            })
        }

        root.addView(setup)

        // ---- শব্দ তালিকা ----
        val wc = Ui.card(c)
        wc.addView(Ui.text(c, "নিজের ব্লক শব্দ", 17f, true))
        val input = Ui.edit(c, "শব্দ লিখুন")
        wc.addView(input)
        wc.addView(Ui.button(c, "শব্দ যোগ করুন") {
            val w = input.text.toString().trim()
            if (w.length < 3) { toast("কমপক্ষে ৩ অক্ষর দিন (খুব ছোট শব্দ দিলে ভুলভাবে সাধারণ শব্দও ব্লক হয়ে যেতে পারে)"); return@button }
            if (Guard.isCommonWord(w)) {
                toast("এটি খুবই সাধারণ একটি শব্দ, এটা যোগ করলে স্বাভাবিক লেখাও ব্লক হয়ে যাবে, তাই যোগ করা গেল না")
                return@button
            }
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
        val isOwner = Owner.isOwner(c)
        lc.addView(Ui.text(c, if (isOwner) "🛡️ Device Owner সক্রিয় (সবচেয়ে শক্ত লক)" else "Device Owner বন্ধ (সাধারণ লক)", 14f))
        if (locked) {
            val days = ((Prefs.lockUntil(c) - System.currentTimeMillis()) / 86_400_000L) + 1
            lc.addView(Ui.text(c, "🔒 লক চালু, বাকি প্রায় $days দিন। এই সময় আনইন্সটল, লগআউট ও গার্ড বন্ধ করা যাবে না।"))
            val ok = Prefs.unlockOk(c)
            lc.addView(Ui.button(c, if (isOwner) "Device Owner বন্ধ করুন" else "লক খুলুন") { requestUnlock() }.apply {
                isEnabled = ok
                if (!ok) background = GradientDrawable().apply {
                    setColor(Color.parseColor("#B0BEC5")); cornerRadius = Ui.dp(c, 12).toFloat()
                }
            })
        } else {
            lc.addView(Ui.button(c, "🔒 ১ মাসের জন্য লক করুন") {
                if (!a11yOn() || !dpm.isAdminActive(admin)) {
                    toast("আগে গার্ড ও অ্যাডমিন চালু করুন"); return@button
                }
                val pmx = getSystemService(Context.POWER_SERVICE) as PowerManager
                if (!pmx.isIgnoringBatteryOptimizations(packageName)) {
                    toast("আগে ব্যাটারি সেভার থেকে NafsGuard বাদ দিন"); return@button
                }
                AlertDialog.Builder(c)
                    .setTitle("লক নিশ্চিত করুন")
                    .setMessage("৩০ দিন আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না। শুধু অ্যাডমিন শিটে অনুমতি দিলে খোলা যাবে। নিশ্চিত?")
                    .setPositiveButton("লক করুন") { _, _ -> Prefs.startLock(c); Owner.applyLock(c); build() }
                    .setNegativeButton("বাতিল", null).show()
            })
            if (isOwner) lc.addView(Ui.button(c, "Device Owner বন্ধ করুন") { releaseOwner() })
            if (adminOn && !isOwner) lc.addView(Ui.button(c, "অ্যাডমিন বন্ধ করুন (আনইন্সটলের জন্য)") {
                dpm.removeActiveAdmin(admin); Prefs.clearLock(c); build()
            })
            lc.addView(Ui.button(c, "লগআউট") { stopEverything(); goLogin() })
        }
        root.addView(lc)

        setContentView(ScrollView(c).apply { setBackgroundColor(Color.WHITE); addView(root) })
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
