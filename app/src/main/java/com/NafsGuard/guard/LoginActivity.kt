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
        UpdateCheck.checkOnOpen(this)           // অ্যাপ খুললেও চেক (৬ ঘণ্টার মধ্যে আবার নেটওয়ার্কে যায় না)
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

    // স্ক্রিনে আঁটানোর জন্য মাপের ধাপ (১ = আগের মতো পুরো মাপ; ছোট ফোনে ধাপে ধাপে ছোট হয়)
    private val PK = floatArrayOf(1f, .9f, .8f, .72f, .64f, .56f)

    private fun sysBar(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun ic(c: Context, res: Int, tint: Int, sizeDp: Float) = android.widget.ImageView(c).apply {
        setImageResource(res); setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(Ui.dp(c, sizeDp), Ui.dp(c, sizeDp))
    }

    // লেখা: উপরের ফাঁক (top) k দিয়ে ছোট হয়, ফন্ট kt দিয়ে সামান্য ছোট হয়
    private fun tx(c: Context, s: String, size: Float, kt: Float, k: Float, bold: Boolean = false,
                   color: Int = Ui.TXT, top: Float = 6f) =
        Ui.dtext(c, s, size * kt, bold, color).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(c, top * k)
        }

    private fun pcard(c: Context, k: Float) = Ui.dcard(c).apply {
        setPadding(Ui.dp(c, 16f * k), Ui.dp(c, 14f * k), Ui.dp(c, 16f * k), Ui.dp(c, 16f * k))
        (layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(c, 14f * k)
    }

    // নাম, ফোন, মেয়াদের মতো এক লাইনের তথ্য: বাঁয়ে আইকন, ডানে লেখা
    private fun infoRow(c: Context, res: Int, label: String, value: String, tint: Int, k: Float, kt: Float) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(c, 8f * k), 0, Ui.dp(c, 8f * k))
        addView(ic(c, res, tint, 22f * k))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14f * k), 0, 0, 0)
            addView(tx(c, label, 12f, kt, k, color = Ui.TXT2))
            addView(tx(c, value, 16f, kt, k, bold = true))
        })
    }

    // ধাপের সারি: state 0 = শেষ, 1 = এখন চলছে, 2 = বাকি
    private fun stepRow(c: Context, state: Int, title: String, sub: String, k: Float, kt: Float) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, Ui.dp(c, 8f * k), 0, Ui.dp(c, 8f * k))
        val res = when (state) { 0 -> R.drawable.ic_check; 1 -> R.drawable.ic_dot; else -> R.drawable.ic_circle }
        val tint = when (state) { 0 -> Ui.NEON; 1 -> Ui.AMBER; else -> Ui.CARD_STROKE }
        addView(ic(c, res, tint, 24f * k))
        addView(LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 14f * k), 0, 0, 0)
            addView(tx(c, title, 15f, kt, k, bold = true, color = if (state == 2) Ui.TXT2 else Ui.TXT))
            addView(tx(c, sub, 12.5f, kt, k, color = Ui.TXT2))
        })
    }

    /** পেজের পুরো কনটেন্ট k মাপে বানায়। ফেরত: (মূল লেআউট, স্পন্দিত আইকনের বৃত্ত) */
    private fun pendingContent(k: Float): Pair<LinearLayout, android.view.View> {
        val c: Context = this
        val kt = 0.85f + 0.15f * k          // ফন্ট কম ছোট হয়, বেশি ছোট হয় ফাঁকা জায়গা
        val expired = Prefs.expired(c)
        val accent = Ui.AMBER

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = Ui.bg()
            val ph = Ui.dp(c, 28f * maxOf(k, .75f)); val pv = Ui.dp(c, 28f * k)
            setPadding(ph, pv, ph, pv)
            addView(Ui.heroLogo(c, (110f * k).toInt(), (44f * k).toInt()))
            addView(tx(c, "NafsGuard", 30f, kt, k, bold = true, color = Ui.NEON).apply { gravity = Gravity.CENTER })
        }

        // স্ট্যাটাস আইকন: গোল বৃত্তের ভেতর (অপেক্ষায় থাকলে ধীরে স্পন্দিত হয়)
        val badge = android.widget.FrameLayout(c).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x22FFB74D); setStroke(Ui.dp(c, 2), accent)
            }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 88f * k), Ui.dp(c, 88f * k)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(c, 22f * k); bottomMargin = Ui.dp(c, 8f * k)
            }
            addView(ic(c, if (expired) R.drawable.ic_clock else R.drawable.ic_hourglass, accent, 42f * k).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(Ui.dp(c, 42f * k), Ui.dp(c, 42f * k), Gravity.CENTER)
            })
        }
        root.addView(badge)

        root.addView(tx(c, if (expired) "মেয়াদ শেষ হয়েছে" else "অনুমোদনের অপেক্ষায়", 22f, kt, k, bold = true, color = accent)
            .apply { gravity = Gravity.CENTER })
        root.addView(tx(c,
            if (expired) "আপনার অ্যাকাউন্টের মেয়াদ শেষ, তাই সব সুরক্ষা বন্ধ আছে।"
            else "আপনার রেজিস্ট্রেশন সম্পন্ন হয়েছে। অ্যাডমিন অনুমোদন দিলে অ্যাপ চালু হবে।",
            14f, kt, k, color = Ui.TXT2).apply { gravity = Gravity.CENTER })

        // আপনার তথ্য
        val info = pcard(c, k)
        info.addView(infoRow(c, R.drawable.ic_person, "নাম", Prefs.name(c), Ui.NEON, k, kt))
        info.addView(Ui.divider(c))
        info.addView(infoRow(c, R.drawable.ic_phone, "ফোন নম্বর", Prefs.phone(c), Ui.NEON, k, kt))
        if (expired) {
            info.addView(Ui.divider(c))
            val d = java.text.SimpleDateFormat("d MMMM yyyy, h:mm a", java.util.Locale("bn", "BD"))
                .format(java.util.Date(Prefs.expiryAt(c)))
            info.addView(infoRow(c, R.drawable.ic_clock, "মেয়াদ শেষ হয়েছে", d, Ui.AMBER, k, kt))
        }
        root.addView(info)

        // ধাপসমূহ
        val steps = pcard(c, k)
        if (expired) {
            steps.addView(stepRow(c, 0, "মেয়াদ শেষ", "সুরক্ষা বন্ধ করা হয়েছে", k, kt))
            steps.addView(stepRow(c, 1, "অ্যাডমিনের কাছে নবায়ন", "অ্যাডমিনের সাথে যোগাযোগ করুন", k, kt))
            steps.addView(stepRow(c, 2, "আবার চালু", "অনুমোদন পেলেই অ্যাপ চালু হবে", k, kt))
        } else {
            steps.addView(stepRow(c, 0, "রেজিস্ট্রেশন", "সম্পন্ন হয়েছে", k, kt))
            steps.addView(stepRow(c, 1, "অ্যাডমিনের অনুমোদন", "অপেক্ষা করা হচ্ছে", k, kt))
            steps.addView(stepRow(c, 2, "অ্যাপ চালু", "অনুমোদন পেলেই শুরু হবে", k, kt))
        }
        root.addView(steps)

        root.addView(Ui.dbutton(c, "অ্যাডমিনের সাথে যোগাযোগ করুন") {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/rishadhossainmahib"))) } catch (e: Exception) {}
        }.apply {
            minHeight = Ui.dp(c, 52f * maxOf(k, .8f))
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = Ui.dp(c, 18f * k)
        })
        root.addView(tx(c, "অনুমোদন এলে এই পেজ নিজে থেকেই মূল পেজে চলে যাবে।", 12.5f, kt, k, color = Ui.TXT2)
            .apply { gravity = Gravity.CENTER })
        return Pair(root, badge)
    }

    /** স্ক্রিনের উচ্চতায় আঁটে এমন সবচেয়ে বড় মাপের ধাপ বেছে নেয় (আনুমানিক; পরে আসল লেআউটে আবার যাচাই হয়) */
    private fun pickIdx(): Int {
        val dm = resources.displayMetrics
        val avail = dm.heightPixels - sysBar("status_bar_height")
        for (i in PK.indices) {
            val r = pendingContent(PK[i]).first
            r.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            if (r.measuredHeight <= avail) return i
        }
        return PK.size - 1
    }

    private fun renderPending(idx: Int) {
        val c: Context = this
        pulse?.cancel(); pulse = null
        val (root, badge) = pendingContent(PK[idx])
        val sv = ScrollView(c).apply { background = Ui.bg(); isFillViewport = true; addView(root) }
        setContentView(sv)

        if (!Prefs.expired(c)) {
            val sx = android.animation.ObjectAnimator.ofFloat(badge, "scaleX", 1f, 1.08f)
            val sy = android.animation.ObjectAnimator.ofFloat(badge, "scaleY", 1f, 1.08f)
            val al = android.animation.ObjectAnimator.ofFloat(badge, "alpha", 1f, 0.7f)
            for (a in listOf(sx, sy, al)) { a.repeatCount = android.animation.ValueAnimator.INFINITE; a.repeatMode = android.animation.ValueAnimator.REVERSE }
            pulse = android.animation.AnimatorSet().apply {
                playTogether(sx, sy, al); duration = 1100; start()
            }
        }

        // আসল স্ক্রিনে কনটেন্ট এখনো বেশি লম্বা হলে এক ধাপ ছোট করে আবার সাজায় (সবচেয়ে ছোট ধাপে স্ক্রল থেকে যায়)
        if (idx < PK.size - 1) {
            sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (sv.height == 0) return
                    sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (!isFinishing && root.height > sv.height) renderPending(idx + 1)
                }
            })
        }
    }

    private fun pending() {
        val c: Context = this
        if (!askedNotif) { askedNotif = true; Push.askPermission(this) }   // অনুমোদনের খবর পেতে নোটিফিকেশনের অনুমতি
        renderPending(pickIdx())
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
