package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.view.Window

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
        root.addView(Ui.dtext(c, "রেজিস্ট্রেশন করুন").apply { gravity = Gravity.CENTER })
        val name = Ui.dedit(c, "ইংরেজিতে আপনার নাম")
        val phone = Ui.dedit(c, "ইংরেজিতে ফোন নাম্বার").apply { inputType = InputType.TYPE_CLASS_PHONE }
        val status = Ui.dtext(c, "", 14f, false, Ui.RED)
        root.addView(name); root.addView(phone); root.addView(status)
        // বাংলা বা অন্য ভাষার অক্ষর লেখা (বা পেস্ট করা) আটকে দেয়, শুধু ইংরেজি অক্ষর/সংখ্যা/চিহ্ন চলবে
        val en = englishOnly(status)
        name.filters = arrayOf(en); phone.filters = arrayOf(en)

        lateinit var btn: Button
        btn = Ui.dbutton(c, "রেজিস্ট্রেশন করুন") {
            val n = name.text.toString().trim()
            val p = phone.text.toString().trim()
            status.setTextColor(Ui.RED)
            when {
                listOf(n, p).any { t -> t.any { ch -> ch.code > 126 } } ->
                    { status.text = "শুধু ইংরেজিতে লিখুন"; return@dbutton }
                n.isEmpty() -> { status.text = "নাম লিখুন"; return@dbutton }
                p.filter { it.isDigit() }.length < 10 -> { status.text = "সঠিক ফোন নম্বর দিন"; return@dbutton }
            }
            btn.isEnabled = false
            status.setTextColor(Ui.NEON); status.text = "অ্যাকাউন্ট তৈরি হচ্ছে..."
            // নোটিফিকেশন (FCM) token নিয়ে শীটে নাম, ফোন, token ও তারিখ-সময় পাঠানো
            Push.fetchToken { token ->
                if (token.isEmpty()) {
                    runOnUiThread {
                        status.setTextColor(Ui.RED)
                        status.text = "নোটিফিকেশন সার্ভিস (Google Play Services) পাওয়া যায়নি। ইন্টারনেট দেখে আবার চেষ্টা করুন"
                        btn.isEnabled = true
                    }
                    return@fetchToken
                }
                Thread {
                    val r = Api.register(n, p, token)
                    runOnUiThread {
                        if (r.ok) {
                            Prefs.setRegistered(c, p, n)
                            Push.subscribeAll()
                            UpdateCheck.ensure(c)   // রেজিস্ট্রেশন সফল: প্রথম ভার্সন চেক (Remote Config)
                            // সফল হলে Pending পেজ
                            startActivity(Intent(c, LoginActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        } else {
                            status.setTextColor(Ui.RED)
                            status.text = r.msg; btn.isEnabled = true
                        }
                    }
                }.start()
            }
        }
        root.addView(btn)
        setContentView(ScrollView(c).apply {
            background = Ui.bg(); isFillViewport = true; addView(root)
        })
        Push.askPermission(this)   // প্রথম পেইজেই নোটিফিকেশনের অনুমতি, যাতে অনুমোদনের খবর পায়
    }

    private fun englishOnly(status: TextView) = InputFilter { src, start, end, _, _, _ ->
        var bad = false
        val sb = StringBuilder()
        for (i in start until end) {
            val ch = src[i]
            if (ch.code in 32..126) sb.append(ch) else bad = true
        }
        if (bad) {
            status.setTextColor(Ui.RED); status.text = "শুধু ইংরেজিতে লিখুন"
            sb
        } else null
    }
}
