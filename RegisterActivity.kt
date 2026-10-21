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
import android.widget.Toast

class RegisterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
        val c: Context = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.bg()
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.heroLogo(c, 96))
        root.addView(Ui.dtext(c, "NafsGuard", 30f, true, Ui.NEON).apply { gravity = Gravity.CENTER })
        root.addView(Ui.dtext(c, "নতুন অ্যাকাউন্ট খুলুন").apply { gravity = Gravity.CENTER })
        val name = Ui.dedit(c, "আপনার নাম")
        val phone = Ui.dedit(c, "ফোন নম্বর").apply { inputType = InputType.TYPE_CLASS_PHONE }
        val pass = Ui.dedit(c, "পাসওয়ার্ড (কমপক্ষে ৪ অক্ষর)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pass2 = Ui.dedit(c, "পাসওয়ার্ড আবার লিখুন").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.dtext(c, "", 14f, false, Ui.RED)
        root.addView(name); root.addView(phone); root.addView(pass); root.addView(pass2); root.addView(status)

        lateinit var btn: Button
        btn = Ui.dbutton(c, "অ্যাকাউন্ট খুলুন") {
            val n = name.text.toString().trim()
            val p = phone.text.toString().trim()
            val w = pass.text.toString()
            status.setTextColor(Ui.RED)
            when {
                n.isEmpty() -> { status.text = "নাম লিখুন"; return@dbutton }
                p.filter { it.isDigit() }.length < 10 -> { status.text = "সঠিক ফোন নম্বর দিন"; return@dbutton }
                w.length < 4 -> { status.text = "পাসওয়ার্ড কমপক্ষে ৪ অক্ষর হতে হবে"; return@dbutton }
                w != pass2.text.toString() -> { status.text = "পাসওয়ার্ড দুটি মিলছে না"; return@dbutton }
            }
            btn.isEnabled = false
            status.setTextColor(Ui.NEON); status.text = "অ্যাকাউন্ট তৈরি হচ্ছে..."
            Thread {
                val r = Api.register(n, p, w)
                runOnUiThread {
                    if (r.ok && r.approved) {
                        Prefs.setLogin(c, p, w, r.name.ifEmpty { n })
                        Session.unlocked = true
                        startActivity(Intent(c, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    } else if (r.ok) {
                        Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); finish()
                    } else {
                        status.setTextColor(Ui.RED)
                        status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        root.addView(Ui.dlink(c, "আগে থেকেই অ্যাকাউন্ট আছে? লগইন করুন") { finish() })
        setContentView(ScrollView(c).apply {
            background = Ui.bg(); isFillViewport = true; addView(root)
        })
    }
}
