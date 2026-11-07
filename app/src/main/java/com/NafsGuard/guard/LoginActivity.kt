package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView

/**
 * এখন আর লগইন নেই। এটি অ্যাপের প্রবেশদ্বার (launcher):
 *  - রেজিস্ট্রেশন হয়নি  → রেজিস্ট্রেশন পেজ
 *  - রেজিস্টার্ড, অনুমোদন আসেনি → Pending পেজ (অনুমোদন না আসা পর্যন্ত প্রতিবার এটাই দেখায়)
 *  - অনুমোদিত → মূল ফিচার পেজ (ঐচ্ছিক অফলাইন পাসওয়ার্ড সেট থাকলে আগে সেটি চায়)
 */
class LoginActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var askedNotif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.bars(this)
    }

    override fun onResume() {
        super.onResume()
        UpdateCheck.ensure(this)                // রেজিস্টার্ড হলে ভার্সন চেক (ডুপ্লিকেট হয় না)
        Update.onFound = Runnable { runOnUiThread { Update.showIfNeeded(this) } }
        route()
        Update.showIfNeeded(this)
    }

    override fun onPause() {
        pulse?.cancel(); pulse = null
        h.removeCallbacksAndMessages(null)
        Update.onFound = null
        super.onPause()
    }

    private fun route() {
        val c: Context = this
        Access.enforceExpiry(c)          // মেয়াদ শেষ হলে এখানেই বন্ধ হয়ে Pending পেজে যাবে
        when {
            Prefs.loggedIn(c) -> approved()
            Prefs.registered(c) -> pending()
            else -> {
                startActivity(Intent(c, RegisterActivity::class.java)); finish()
            }
        }
    }

    private fun goMain() {
        Session.unlocked = true
        startActivity(Intent(this, MainActivity::class.java)); finish()
    }

    // ---------- অনুমোদিত ----------
    private fun approved() {
        val c: Context = this
        Push.subscribeAll()
        if (Session.unlocked || !Pin.has(c)) { goMain(); return }
        pinScreen()
    }

    private fun pinScreen() {
        val c: Context = this
        val root = baseRoot()
        root.addView(Ui.dtext(c, "পাসওয়ার্ড দিন", 18f, true).apply { gravity = Gravity.CENTER })
        val pass = Ui.dedit(c, "অফলাইন পাসওয়ার্ড").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val status = Ui.dtext(c, "", 14f, false, Ui.RED)
        root.addView(pass); root.addView(status)
        root.addView(Ui.dbutton(c, "প্রবেশ করুন") {
            if (Pin.check(c, pass.text.toString())) goMain()
            else { status.text = "পাসওয়ার্ড ভুল"; pass.setText("") }
        })
        root.addView(Ui.dtext(c, "ভুলে গেলে অ্যাডমিনের সাথে যোগাযোগ করুন।", 12.5f, false, Ui.TXT2).apply { gravity = Gravity.CENTER })
        root.addView(telegram(c))
        setContentView(ScrollView(c).apply { background = Ui.bg(); isFillViewport = true; addView(root) })
    }

    // ---------- Pending ----------
    private var pulse: android.animation.AnimatorSet? = null

    private fun ic(c: Context, res: Int, tint: Int, sizeDp: Int) = android.widget.ImageView(c).apply {
        setImageResource(res); setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp))
    }

    // নাম, ফোন, মেয়াদের মতো এক লাইনের তথ্য: বাঁয়ে আইকন, ডানে লেখা
    private fun infoRow(c: Context, res: Int, label: String, value: String, tint: Int = Ui.NEON) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
        addView(ic(c, res, tint, 22))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), 0, 0, 0)
            addView(Ui.dtext(c, label, 12f, false, Ui.TXT2).apply { setPadding(0, 0, 0, 0) })
            addView(Ui.dtext(c, value, 16f, true).apply { setPadding(0, 0, 0, 0) })
        })
    }

    // ধাপের সারি: state 0 = শেষ, 1 = এখন চলছে, 2 = বাকি
    private fun stepRow(c: Context, state: Int, title: String, sub: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
        val res = when (state) { 0 -> R.drawable.ic_check; 1 -> R.drawable.ic_dot; else -> R.drawable.ic_circle }
        val tint = when (state) { 0 -> Ui.NEON; 1 -> Ui.AMBER; else -> Ui.CARD_STROKE }
        addView(ic(c, res, tint, 24))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14), 0, 0, 0)
            addView(Ui.dtext(c, title, 15f, true, if (state == 2) Ui.TXT2 else Ui.TXT).apply { setPadding(0, 0, 0, 0) })
            addView(Ui.dtext(c, sub, 12.5f, false, Ui.TXT2).apply { setPadding(0, 0, 0, 0) })
        })
    }

    private fun pending() {
        val c: Context = this
        if (!askedNotif) { askedNotif = true; Push.askPermission(this) }   // অনুমোদনের খবর পেতে নোটিফিকেশনের অনুমতি
        val root = baseRoot()
        val expired = Prefs.expired(c)
        val accent = Ui.AMBER

        // স্ট্যাটাস আইকন: গোল বৃত্তের ভেতর (অপেক্ষায় থাকলে ধীরে স্পন্দিত হয়)
        val badge = android.widget.FrameLayout(c).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x22FFB74D); setStroke(Ui.dp(c, 2), accent)
            }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 88)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(c, 22); bottomMargin = Ui.dp(c, 8)
            }
            addView(ic(c, if (expired) R.drawable.ic_clock else R.drawable.ic_hourglass, accent, 42).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42), Gravity.CENTER)
            })
        }
        root.addView(badge)
        if (!expired) {
            pulse?.cancel()
            val sx = android.animation.ObjectAnimator.ofFloat(badge, "scaleX", 1f, 1.08f)
            val sy = android.animation.ObjectAnimator.ofFloat(badge, "scaleY", 1f, 1.08f)
            val al = android.animation.ObjectAnimator.ofFloat(badge, "alpha", 1f, 0.7f)
            for (a in listOf(sx, sy, al)) { a.repeatCount = android.animation.ValueAnimator.INFINITE; a.repeatMode = android.animation.ValueAnimator.REVERSE }
            pulse = android.animation.AnimatorSet().apply {
                playTogether(sx, sy, al); duration = 1100; start()
            }
        }

        root.addView(Ui.dtext(c, if (expired) "মেয়াদ শেষ হয়েছে" else "অনুমোদনের অপেক্ষায়", 22f, true, accent)
            .apply { gravity = Gravity.CENTER })
        root.addView(Ui.dtext(c,
            if (expired) "আপনার অ্যাকাউন্টের মেয়াদ শেষ, তাই সব সুরক্ষা বন্ধ আছে।"
            else "আপনার রেজিস্ট্রেশন সম্পন্ন হয়েছে। অ্যাডমিন অনুমোদন দিলে অ্যাপ চালু হবে।",
            14f, false, Ui.TXT2).apply { gravity = Gravity.CENTER })

        // আপনার তথ্য
        val info = Ui.dcard(c)
        info.addView(infoRow(c, R.drawable.ic_person, "নাম", Prefs.name(c)))
        info.addView(Ui.divider(c))
        info.addView(infoRow(c, R.drawable.ic_phone, "ফোন নম্বর", Prefs.phone(c)))
        if (expired) {
            info.addView(Ui.divider(c))
            val d = java.text.SimpleDateFormat("d MMMM yyyy, h:mm a", java.util.Locale("bn", "BD"))
                .format(java.util.Date(Prefs.expiryAt(c)))
            info.addView(infoRow(c, R.drawable.ic_clock, "মেয়াদ শেষ হয়েছে", d, Ui.AMBER))
        }
        root.addView(info)

        // ধাপসমূহ
        val steps = Ui.dcard(c)
        if (expired) {
            steps.addView(stepRow(c, 0, "মেয়াদ শেষ", "সুরক্ষা বন্ধ করা হয়েছে"))
            steps.addView(stepRow(c, 1, "অ্যাডমিনের কাছে নবায়ন", "অ্যাডমিনের সাথে যোগাযোগ করুন"))
            steps.addView(stepRow(c, 2, "আবার চালু", "অনুমোদন পেলেই অ্যাপ চালু হবে"))
        } else {
            steps.addView(stepRow(c, 0, "রেজিস্ট্রেশন", "সম্পন্ন হয়েছে"))
            steps.addView(stepRow(c, 1, "অ্যাডমিনের অনুমোদন", "অপেক্ষা করা হচ্ছে"))
            steps.addView(stepRow(c, 2, "অ্যাপ চালু", "অনুমোদন পেলেই শুরু হবে"))
        }
        root.addView(steps)

        root.addView(Ui.dbutton(c, "অ্যাডমিনের সাথে যোগাযোগ করুন") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/rishadhossainmahib"))) } catch (e: Exception) {}
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = Ui.dp(c, 18) })
        root.addView(Ui.dtext(c, "অনুমোদন এলে এই পেজ নিজে থেকেই মূল পেজে চলে যাবে।", 12.5f, false, Ui.TXT2)
            .apply { gravity = Gravity.CENTER })

        setContentView(ScrollView(c).apply { background = Ui.bg(); isFillViewport = true; addView(root) })
        // Push দিয়ে অনুমোদন এলে নিজে থেকেই এগিয়ে যাবে
        val tick = object : Runnable {
            override fun run() {
                if (isFinishing) return
                if (Prefs.loggedIn(c)) { route(); return }
                h.postDelayed(this, 1500)
            }
        }
        h.postDelayed(tick, 1500)
    }

    private fun baseRoot(): LinearLayout {
        val c: Context = this
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.bg()
            val p = Ui.dp(c, 28); setPadding(p, p, p, p)
            addView(Ui.heroLogo(c, 110))
            addView(Ui.dtext(c, "NafsGuard", 30f, true, Ui.NEON).apply { gravity = Gravity.CENTER })
        }
    }

    private fun telegram(c: Context) = Button(c).apply {
        text = "অ্যাডমিনের সাথে যোগাযোগ →"
        isAllCaps = false; textSize = 15f; typeface = Ui.tf(c, true)
        setTextColor(Ui.NEON)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(Ui.dp(c, 1.5f), Ui.NEON)
            cornerRadius = Ui.dp(c, 50).toFloat()
        }
        stateListAnimator = null
        minHeight = Ui.dp(c, 48)
        setPadding(Ui.dp(c, 24), 0, Ui.dp(c, 24), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(c, 18) }
        setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/rishadhossainmahib"))) } catch (e: Exception) {}
        }
    }
}
