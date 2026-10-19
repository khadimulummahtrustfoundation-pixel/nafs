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
        window.statusBarColor = Ui.GREEN
        val c: Context = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.text(c, "🛡️ NafsGuard", 30f, true, Ui.GREEN).apply { gravity = Gravity.CENTER })
        root.addView(Ui.text(c, "নতুন অ্যাকাউন্ট খুলুন").apply { gravity = Gravity.CENTER })
        val name = Ui.edit(c, "আপনার নাম")
        val phone = Ui.edit(c, "ফোন নম্বর").apply { inputType = InputType.TYPE_CLASS_PHONE }
        val pass = Ui.edit(c, "পাসওয়ার্ড (কমপক্ষে ৪ অক্ষর)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val pass2 = Ui.edit(c, "পাসওয়ার্ড আবার লিখুন").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.text(c, "", 14f, false, Color.parseColor("#C62828"))
        root.addView(name); root.addView(phone); root.addView(pass); root.addView(pass2); root.addView(status)

        lateinit var btn: Button
        btn = Ui.button(c, "অ্যাকাউন্ট খুলুন") {
            val n = name.text.toString().trim()
            val p = phone.text.toString().trim()
            val w = pass.text.toString()
            status.setTextColor(Color.parseColor("#C62828"))
            when {
                n.isEmpty() -> { status.text = "নাম লিখুন"; return@button }
                p.filter { it.isDigit() }.length < 10 -> { status.text = "সঠিক ফোন নম্বর দিন"; return@button }
                w.length < 4 -> { status.text = "পাসওয়ার্ড কমপক্ষে ৪ অক্ষর হতে হবে"; return@button }
                w != pass2.text.toString() -> { status.text = "পাসওয়ার্ড দুটি মিলছে না"; return@button }
            }
            btn.isEnabled = false
            status.setTextColor(Ui.GREEN); status.text = "অ্যাকাউন্ট তৈরি হচ্ছে..."
            Thread {
                val r = Api.register(n, p, w)
                runOnUiThread {
                    if (r.ok && r.approved) {
                        Prefs.setLogin(c, p, w, r.name.ifEmpty { n })
                        startActivity(Intent(c, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    } else if (r.ok) {
                        Toast.makeText(c, r.msg, Toast.LENGTH_LONG).show(); finish()
                    } else {
                        status.setTextColor(Color.parseColor("#C62828"))
                        status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        root.addView(Ui.link(c, "আগে থেকেই অ্যাকাউন্ট আছে? লগইন করুন") { finish() })
        setContentView(ScrollView(c).apply {
            setBackgroundColor(Color.WHITE); isFillViewport = true; addView(root)
        })
    }
}
