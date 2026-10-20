package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

class LoginActivity : Activity() {

    private fun last10(s: String) = s.filter { it.isDigit() }.takeLast(10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.GREEN
        val c: Context = this
        val returning = Prefs.loggedIn(c)

        // এই খোলায় আগেই লগইন করা থাকলে সরাসরি ভেতরে
        if (returning && Session.unlocked) {
            startActivity(Intent(c, MainActivity::class.java)); finish(); return
        }

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.logo(c, 110))
        root.addView(Ui.text(c, "NafsGuard", 30f, true, Ui.GREEN).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(c, if (returning) "অ্যাপে ঢুকতে আবার লগইন করুন" else "লগইন করুন")
            .apply { gravity = Gravity.CENTER })

        val phone = Ui.edit(c, "ফোন নম্বর").apply { inputType = InputType.TYPE_CLASS_PHONE }
        if (returning) phone.setText(Prefs.phone(c))
        val pass = Ui.edit(c, "পাসওয়ার্ড").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.text(c, "", 14f, false, Color.parseColor("#C62828"))
        root.addView(phone); root.addView(pass); root.addView(status)

        lateinit var btn: Button
        btn = Ui.button(c, "লগইন") {
            val p = phone.text.toString().trim()
            val w = pass.text.toString()
            if (p.isEmpty() || w.isEmpty()) { status.text = "ফোন ও পাসওয়ার্ড দিন"; return@button }
            btn.isEnabled = false
            status.setTextColor(Ui.GREEN); status.text = "যাচাই হচ্ছে..."
            Thread {
                val r = Api.login(p, w)
                // ইন্টারনেট না থাকলে আগের সেভ করা তথ্যের সাথে মিলিয়ে ঢুকতে দেওয়া
                val offlineOk = r.net && returning &&
                    last10(p) == last10(Prefs.phone(c)) && w == Prefs.pass(c)
                runOnUiThread {
                    if (r.ok || offlineOk) {
                        if (r.ok) {
                            Prefs.setLogin(c, p, w, r.name.ifEmpty { Prefs.name(c) })
                            Prefs.setUnlock(c, r.unlock)
                        }
                        Session.unlocked = true
                        startActivity(Intent(c, MainActivity::class.java)); finish()
                    } else {
                        status.setTextColor(Color.parseColor("#C62828"))
                        status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        if (!returning) {
            root.addView(Ui.link(c, "নতুন? অ্যাকাউন্ট খুলুন (Sign up)") {
                startActivity(Intent(c, RegisterActivity::class.java))
            })
        }
        setContentView(ScrollView(c).apply {
            setBackgroundColor(Color.WHITE); isFillViewport = true; addView(root)
        })
    }
}
