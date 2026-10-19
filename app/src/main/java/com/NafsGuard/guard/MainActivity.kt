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
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private val DNS_HOST = "family-filter-dns.cleanbrowsing.org"

    private val admin get() = ComponentName(this, AdminReceiver::class.java)
    private val dpm get() = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.GREEN
        if (!Prefs.loggedIn(this)) goLogin()
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.loggedIn(this)) return
        build()
        recheck()
    }

    private fun goLogin() {
        startActivity(Intent(this, LoginActivity::class.java)); finish()
    }

    // সার্ভারে এক্সেস বন্ধ/মেয়াদ শেষ হলে লগআউট (লক থাকলে হবে না)
    private fun recheck() {
        val c = this
        Thread {
            val r = Api.login(Prefs.phone(c), Prefs.pass(c))
            if (!r.ok && !r.net && !Prefs.locked(c)) {
                Prefs.logout(c)
                runOnUiThread { Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); goLogin() }
            }
        }.start()
    }

    private fun a11yOn(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("$packageName/${GuardService::class.java.name}", true) ||
            s.contains("$packageName/.GuardService", true)
    }

    private fun safeOn(): Boolean = try {
        Settings.Global.getString(contentResolver, "private_dns_mode") == "hostname" &&
            Settings.Global.getString(contentResolver, "private_dns_specifier") == DNS_HOST
    } catch (e: Exception) { false }

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
        root.addView(Ui.text(c, "স্বাগতম, ${Prefs.name(c)}"))

        // ---- সেটআপ ----
        val setup = Ui.card(c)
        setup.addView(Ui.text(c, "সুরক্ষা সেটআপ", 17f, true))
        setup.addView(Ui.text(c, if (a11y) "✅ গার্ড (অ্যাক্সেসিবিলিটি) চালু" else "❌ গার্ড বন্ধ"))
        if (!a11y) {
            setup.addView(Ui.text(c, "NafsGuard খুঁজে চালু করুন। Android 13+ এ সাইডলোড অ্যাপে অপশন ধূসর থাকলে: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings।", 13f))
            setup.addView(Ui.button(c, "গার্ড চালু করুন") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
        }
        setup.addView(Ui.text(c, if (adminOn) "✅ অ্যাডমিন অ্যাপ চালু" else "❌ অ্যাডমিন অ্যাপ বন্ধ"))
        if (!adminOn) {
            setup.addView(Ui.button(c, "অ্যাডমিন অনুমতি দিন") {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "আনইন্সটল ঠেকাতে NafsGuard-কে অ্যাডমিন হতে হবে।")
                })
            })
        }
        val safe = safeOn()
        setup.addView(Ui.text(c, if (safe) "✅ সেইফ সার্চ চালু" else "❌ সেইফ সার্চ বন্ধ"))
        if (!safe) {
            setup.addView(Ui.button(c, "সেইফ সার্চ অন করুন") { enableSafeSearch() })
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
            val row = Ui.text(c, "• $w")
            wc.addView(row)
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
            lc.addView(Ui.text(c, "🔒 লক চালু — বাকি প্রায় $days দিন। এই সময় আনইন্সটল, লগআউট ও গার্ড বন্ধ করা যাবে না।"))
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
            lc.addView(Ui.button(c, "লগআউট") { Prefs.logout(c); goLogin() })
        }
        root.addView(lc)

        setContentView(ScrollView(c).apply { setBackgroundColor(Color.WHITE); addView(root) })
    }

    private fun enableSafeSearch() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("dns", DNS_HOST))
        try {
            Settings.Global.putString(contentResolver, "private_dns_mode", "hostname")
            Settings.Global.putString(contentResolver, "private_dns_specifier", DNS_HOST)
            toast("সেইফ সার্চ চালু হয়েছে")
            build()
        } catch (e: Exception) {
            toast("হোস্ট কপি হয়েছে। Private DNS → Provider hostname এ পেস্ট করে Save দিন")
            try { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
