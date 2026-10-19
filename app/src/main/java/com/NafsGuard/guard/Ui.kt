package com.NafsGuard.guard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.*

object Ui {
    val GREEN = Color.parseColor("#2E7D32")
    val DARK = Color.parseColor("#1B5E20")
    val LIGHT = Color.parseColor("#E8F5E9")

    private var reg: Typeface? = null
    private var bld: Typeface? = null
    private var loaded = false

    // Hind Siliguri (assets/fonts এ থাকলে), না থাকলে সিস্টেম ফন্ট
    fun tf(c: Context, bold: Boolean): Typeface {
        if (!loaded) {
            loaded = true
            try { reg = Typeface.createFromAsset(c.applicationContext.assets, "fonts/HindSiliguri-Regular.ttf") } catch (e: Exception) {}
            try { bld = Typeface.createFromAsset(c.applicationContext.assets, "fonts/HindSiliguri-Bold.ttf") } catch (e: Exception) {}
        }
        val f = if (bold) (bld ?: reg) else (reg ?: bld)
        return f ?: if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    fun logo(c: Context, size: Int) = ImageView(c).apply {
        setImageResource(R.drawable.logo)
        layoutParams = LinearLayout.LayoutParams(dp(c, size), dp(c, size)).apply {
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(c, 8)
        }
    }

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    private fun lp(c: Context, top: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(c, top) }

    fun text(c: Context, s: String, size: Float = 15f, bold: Boolean = false, color: Int = DARK) =
        TextView(c).apply {
            text = s; textSize = size; setTextColor(color)
            typeface = tf(c, bold)
            layoutParams = lp(c, 6)
        }

    fun button(c: Context, label: String, onClick: () -> Unit) = Button(c).apply {
        text = label; setTextColor(Color.WHITE); isAllCaps = false; textSize = 16f; typeface = tf(c, true)
        background = GradientDrawable().apply { setColor(GREEN); cornerRadius = dp(c, 12).toFloat() }
        layoutParams = lp(c, 8)
        setOnClickListener { onClick() }
    }

    fun edit(c: Context, hint: String) = EditText(c).apply {
        this.hint = hint; setTextColor(Color.BLACK); typeface = tf(c, false)
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE); setStroke(dp(c, 2), GREEN); cornerRadius = dp(c, 12).toFloat()
        }
        layoutParams = lp(c, 10)
    }

    fun card(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply { setColor(LIGHT); cornerRadius = dp(c, 16).toFloat() }
        layoutParams = lp(c, 14)
    }

    fun link(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 15f; setTextColor(GREEN); typeface = tf(c, true)
        gravity = android.view.Gravity.CENTER
        setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 14))
        layoutParams = lp(c, 6)
        setOnClickListener { onClick() }
    }
}
