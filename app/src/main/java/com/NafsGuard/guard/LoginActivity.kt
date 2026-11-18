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
        pend?.stop(); pend = null
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
    private var pend: PendingView? = null

    // স্ক্রিনে আঁটানোর জন্য মাপের ধাপ (১ = পুরো মাপ; ছোট ফোনে ধাপে ধাপে ছোট হয়)
    private val PK = floatArrayOf(1f, .92f, .84f, .76f, .68f, .6f)

    private fun sysBar(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun newPending(k: Float): PendingView {
        val c: Context = this
        val expired = Prefs.expired(c)
        val exp = if (expired) java.text.SimpleDateFormat("d MMMM yyyy, h:mm a", java.util.Locale("bn", "BD"))
            .format(java.util.Date(Prefs.expiryAt(c))) else ""
        return PendingUi.build(this, k, expired, Prefs.name(c), Prefs.phone(c), exp) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/rishadhossainmahib"))) } catch (e: Exception) {}
        }
    }

    /** স্ক্রিনের উচ্চতায় আঁটে এমন সবচেয়ে বড় মাপের ধাপ বেছে নেয় (আনুমানিক; পরে আসল লেআউটে আবার যাচাই হয়) */
    private fun pickIdx(): Int {
        val dm = resources.displayMetrics
        val avail = dm.heightPixels - sysBar("status_bar_height")
        for (i in PK.indices) {
            val r = newPending(PK[i]).root
            r.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(dm.widthPixels, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED))
            if (r.measuredHeight <= avail) return i
        }
        return PK.size - 1
    }

    private fun renderPending(idx: Int) {
        val c: Context = this
        pend?.stop()
        val pv = newPending(PK[idx])
        pend = pv
        val sv = ScrollView(c).apply { background = Ui.bg(); isFillViewport = true; clipChildren = false; addView(pv.frame) }
        setContentView(sv)
        pv.start()

        // আসল স্ক্রিনে কনটেন্ট এখনো বেশি লম্বা হলে এক ধাপ ছোট করে আবার সাজায় (সবচেয়ে ছোট ধাপে স্ক্রল থেকে যায়)
        if (idx < PK.size - 1) {
            sv.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (sv.height == 0) return
                    sv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    if (!isFinishing && pv.root.height > sv.height) renderPending(idx + 1)
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
