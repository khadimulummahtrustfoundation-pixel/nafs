package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout

class LoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.GREEN
        if (Prefs.loggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java)); finish(); return
        }
        val c: Context = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.text(c, "🛡️ NafsGuard", 30f, true, Ui.GREEN).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(c, "লগইন করুন").apply { gravity = Gravity.CENTER })
        val phone = Ui.edit(c, "ফোন নম্বর").apply { inputType = InputType.TYPE_CLASS_PHONE }
        val pass = Ui.edit(c, "পাসওয়ার্ড").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.text(c, "", 14f, false, Color.parseColor("#C62828"))
        root.addView(phone); root.addView(pass); root.addView(status)
        lateinit var btn: android.widget.Button
        btn = Ui.button(c, "লগইন") {
            val p = phone.text.toString().trim()
            val w = pass.text.toString()
            if (p.isEmpty() || w.isEmpty()) { status.text = "ফোন ও পাসওয়ার্ড দিন"; return@button }
            btn.isEnabled = false
            status.setTextColor(Ui.GREEN); status.text = "যাচাই হচ্ছে..."
            Thread {
                val r = Api.login(p, w)
                runOnUiThread {
                    if (r.ok) {
                        Prefs.setLogin(c, p, w, r.name)
                        startActivity(Intent(c, MainActivity::class.java)); finish()
                    } else {
                        status.setTextColor(Color.parseColor("#C62828"))
                        status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        root.addView(Ui.link(c, "নতুন? অ্যাকাউন্ট খুলুন (Sign up)") {
            startActivity(Intent(c, RegisterActivity::class.java))
        })
        setContentView(android.widget.ScrollView(c).apply {
            setBackgroundColor(Color.WHITE); isFillViewport = true; addView(root)
        })
    }
}
