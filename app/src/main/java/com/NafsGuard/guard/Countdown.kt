package com.NafsGuard.guard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

// লক চালুর পর অ্যানিমেশনসহ কাউন্টডাউন (দিন : ঘণ্টা : মিনিট : সেকেন্ড)
object Countdown {

    private fun bn(n: Long, pad: Int = 2): String {
        val d = "০১২৩৪৫৬৭৮৯"
        return n.toString().padStart(pad, '0').map { if (it in '0'..'9') d[it - '0'] else it }.joinToString("")
    }

    private fun ring(c: Context, size: Int, width: Float = 2.5f) = View(c).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT); setStroke(Ui.dp(c, width), Ui.NEON)
        }
        layoutParams = FrameLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size), Gravity.CENTER)
    }

    private fun pulse(v: View, delay: Long, to: Float) {
        v.alpha = 0f
        ObjectAnimator.ofPropertyValuesHolder(v,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, to),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, to),
            android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.75f, 0f)
        ).apply {
            duration = 2200; startDelay = delay
            repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun lockCore(c: Context, size: Int, textSp: Float) = TextView(c).apply {
        text = "🔒"; textSize = textSp; gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1A5A41")); setStroke(Ui.dp(c, 2f), Ui.NEON)
        }
        layoutParams = FrameLayout.LayoutParams(Ui.dp(c, size), Ui.dp(c, size), Gravity.CENTER)
    }

    /** লক কার্ডের ভেতরে বসানোর কাউন্টডাউন। start = লক শুরুর সময় (জানা না থাকলে 0) */
    fun view(c: Context, until: Long, start: Long): LinearLayout {
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // ঢেউ ছড়ানো লক আইকন
        val hero = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 150), Ui.dp(c, 150)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = Ui.dp(c, 8)
            }
        }
        val r1 = ring(c, 92); val r2 = ring(c, 92)
        hero.addView(r1); hero.addView(r2)
        val core = lockCore(c, 84, 36f)
        hero.addView(core)
        root.addView(hero)
        pulse(r1, 0, 1.6f); pulse(r2, 1100, 1.6f)
        ObjectAnimator.ofPropertyValuesHolder(core,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f)
        ).apply {
            duration = 1100; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator(); start()
        }

        val title = Ui.dtext(c, "লক চালু আছে", 16f, true, Ui.NEON).apply { gravity = Gravity.CENTER }
        root.addView(title)

        // চার ঘরের কাউন্টডাউন
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = Ui.dp(c, 12) }
        }
        val nums = ArrayList<TextView>()
        listOf("দিন", "ঘণ্টা", "মিনিট", "সেকেন্ড").map { Lang.t(c, it) }.forEach { label ->
            val cell = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#082B1F")); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE)
                    cornerRadius = Ui.dp(c, 14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = Ui.dp(c, 4); rightMargin = Ui.dp(c, 4) }
            }
            val n = TextView(c).apply {
                text = bn(0); textSize = 24f; setTextColor(Ui.NEON); typeface = Ui.tf(c, true); gravity = Gravity.CENTER
            }
            nums.add(n)
            cell.addView(n)
            cell.addView(TextView(c).apply {
                text = label; textSize = 11.5f; setTextColor(Ui.TXT2); typeface = Ui.tf(c, false); gravity = Gravity.CENTER
            })
            row.addView(cell)
        }
        root.addView(row)

        // অগ্রগতির বার (শুরুর সময় জানা থাকলে)
        val hasBar = start in 1 until until
        val bar = ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(Ui.NEON)
            progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#1D5A42"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 8))
                .apply { topMargin = Ui.dp(c, 14) }
        }
        val pct = Ui.dtext(c, "", 12.5f, false, Ui.TXT2).apply { gravity = Gravity.CENTER }
        if (hasBar) { root.addView(bar); root.addView(pct) }
        root.addView(Ui.dtext(c, "খোলার সময়: ${fmt(until)}", 12.5f, false, Ui.TXT2).apply { gravity = Gravity.CENTER })
        root.addView(Ui.dtext(c, "লক শুধু অ্যাডমিন খুলে দিতে পারবেন বা মেয়াদ শেষে নিজে খুলবে।", 12.5f, false, Ui.TXT2)
            .apply { gravity = Gravity.CENTER })

        // প্রতি সেকেন্ডে আপডেট; স্ক্রিন থেকে সরলে থামবে
        val h = Handler(Looper.getMainLooper())
        var lastSec = -1L
        val tick = object : Runnable {
            override fun run() {
                val left = (until - Prefs.now(c)).coerceAtLeast(0)
                val s = left / 1000
                val d = s / 86400; val hr = (s % 86400) / 3600; val m = (s % 3600) / 60; val sc = s % 60
                nums[0].text = bn(d); nums[1].text = bn(hr); nums[2].text = bn(m); nums[3].text = bn(sc)
                if (lastSec != sc) {
                    lastSec = sc
                    nums[3].animate().cancel()
                    nums[3].animate().scaleX(1.18f).scaleY(1.18f).setDuration(120).withEndAction {
                        nums[3].animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                    }.start()
                }
                if (hasBar) {
                    val p = (((until - left) - start).toDouble() / (until - start)).coerceIn(0.0, 1.0)
                    bar.progress = (p * 1000).toInt()
                    pct.text = "${bn((p * 100).toLong(), 1)}% সম্পন্ন"
                }
                if (left <= 0) { title.text = "মেয়াদ শেষ, লক খুলছে…"; return }
                h.postDelayed(this, 1000)
            }
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { h.removeCallbacks(tick); h.post(tick) }
            override fun onViewDetachedFromWindow(v: View) { h.removeCallbacksAndMessages(null) }
        })
        return root
    }

    private fun fmt(t: Long): String {
        val f = java.text.SimpleDateFormat("d MMM yyyy, hh:mm a", java.util.Locale.ENGLISH)
        f.timeZone = java.util.TimeZone.getTimeZone("Asia/Dhaka")
        return f.format(java.util.Date(t))
    }

    /** লক চালুর মুহূর্তে একটি ছোট উদযাপন অ্যানিমেশন (প্রায় ২.৪ সেকেন্ড) */
    fun showStarted(act: Activity, until: Long) {
        if (act.isFinishing) return
        val c: Context = act
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val p = Ui.dp(c, 24); setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(Ui.CARD); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE); cornerRadius = Ui.dp(c, 24).toFloat()
            }
        }
        val hero = FrameLayout(c).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 170), Ui.dp(c, 170))
        }
        val rings = listOf(ring(c, 96, 3f), ring(c, 96, 3f), ring(c, 96, 3f))
        rings.forEach { hero.addView(it) }
        val core = lockCore(c, 96, 42f).apply { scaleX = 0f; scaleY = 0f }
        hero.addView(core)
        box.addView(hero)
        val t1 = Ui.dtext(c, "লক চালু হয়েছে", 20f, true, Ui.NEON).apply { gravity = Gravity.CENTER; alpha = 0f }
        val t2 = Ui.dtext(c, "খোলার সময়: ${fmt(until)}", 13f, false, Ui.TXT2).apply { gravity = Gravity.CENTER; alpha = 0f }
        box.addView(t1); box.addView(t2)

        val dlg = Dialog(c)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((c.resources.displayMetrics.widthPixels * 0.8).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dlg.show()

        // লক আইকন ঝাঁকিয়ে বসে যাওয়া
        core.animate().scaleX(1f).scaleY(1f).setDuration(520).setInterpolator(OvershootInterpolator(2.4f)).start()
        rings.forEachIndexed { i, r -> pulse(r, 350L + i * 500L, 1.9f) }
        t1.animate().alpha(1f).translationYBy(-Ui.dp(c, 6).toFloat()).setStartDelay(450).setDuration(450).start()
        t2.animate().alpha(1f).setStartDelay(700).setDuration(450).start()

        Handler(Looper.getMainLooper()).postDelayed({
            try { if (!act.isFinishing && dlg.isShowing) dlg.dismiss() } catch (e: Exception) {}
        }, 2400)
    }
}
