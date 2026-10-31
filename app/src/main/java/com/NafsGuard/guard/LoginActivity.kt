package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.net.Uri
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

class LoginActivity : Activity() {

    private fun last10(s: String) = s.filter { it.isDigit() }.takeLast(10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
        val c: Context = this
        val returning = Prefs.loggedIn(c)

        // এই খোলায় আগেই লগইন করা থাকলে সরাসরি ভেতরে
        if (returning && Session.unlocked) {
            startActivity(Intent(c, MainActivity::class.java)); finish(); return
        }

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.bg()
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
        }
        root.addView(Ui.heroLogo(c, 110))
        root.addView(Ui.dtext(c, "NafsGuard", 30f, true, Ui.NEON).apply { gravity = Gravity.CENTER })
        root.addView(Ui.dtext(c, if (returning) "অ্যাপে ঢুকতে আবার লগইন করুন" else "লগইন করুন")
            .apply { gravity = Gravity.CENTER })

        val phone = Ui.dedit(c, "ফোন নম্বর").apply { inputType = InputType.TYPE_CLASS_PHONE }
        if (returning) phone.setText(Prefs.phone(c))
        val pass = Ui.dedit(c, "পাসওয়ার্ড").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.dtext(c, "", 14f, false, Ui.RED)
        root.addView(phone); root.addView(pass); root.addView(status)

        lateinit var btn: Button
        btn = Ui.dbutton(c, "লগইন") {
            val p = phone.text.toString().trim()
            val w = pass.text.toString()
            if (p.isEmpty() || w.isEmpty()) { status.text = "ফোন ও পাসওয়ার্ড দিন"; return@dbutton }
            btn.isEnabled = false
            status.setTextColor(Ui.NEON); status.text = "যাচাই হচ্ছে..."
            Thread {
                // শীটের ভার্সনের সাথে অ্যাপের ভার্সন না মিললে লগইন নয়, আপডেট পপ-আপ
                val sv = Api.version()
                if (sv != null && sv != Update.current(c)) {
                    runOnUiThread {
                        status.text = ""; btn.isEnabled = true
                        Update.show(this@LoginActivity)
                    }
                    return@Thread
                }
                val r = Api.login(p, w)
                // ইন্টারনেট না থাকলে আগের সেভ করা তথ্যের সাথে মিলিয়ে ঢুকতে দেওয়া
                val offlineOk = r.net && returning &&
                    last10(p) == last10(Prefs.phone(c)) && w == Prefs.pass(c)
                runOnUiThread {
                    if (r.ok || offlineOk) {
                        if (r.ok) {
                            Prefs.setLogin(c, p, w, r.name.ifEmpty { Prefs.name(c) })
                                                        Prefs.applyServer(c, r)
                        }
                        Session.unlocked = true
                        startActivity(Intent(c, MainActivity::class.java)); finish()
                    } else {
                        status.setTextColor(Ui.RED)
                        status.text = r.msg; btn.isEnabled = true
                    }
                }
            }.start()
        }
        root.addView(btn)
        if (!returning) {
            root.addView(Ui.dlink(c, "নতুন? অ্যাকাউন্ট খুলুন (Sign up)") {
                startActivity(Intent(c, RegisterActivity::class.java))
            })
        }
        // অ্যাডমিনের সাথে যোগাযোগ (টেলিগ্রাম)
        val tg = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(c, 22), Ui.dp(c, 11), Ui.dp(c, 22), Ui.dp(c, 11))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(Ui.dp(c, 1.5f), Ui.NEON)
                cornerRadius = Ui.dp(c, 50).toFloat()
            }
            isClickable = true
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/rishadhossainmahib")))
                } catch (e: Exception) {}
            }
        }
        tg.addView(ImageView(c).apply { setImageResource(R.drawable.ic_telegram) },
            LinearLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20)))
        tg.addView(Ui.dtext(c, "অ্যাডমিনের সাথে যোগাযোগ", 15f, true, Ui.NEON).apply {
            setPadding(Ui.dp(c, 10), 0, 0, 0)
        })
        root.addView(tg, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(c, 18) })

        setContentView(ScrollView(c).apply {
            background = Ui.bg(); isFillViewport = true; addView(root)
        })
    }
}
