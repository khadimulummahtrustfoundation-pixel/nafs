package com.NafsGuard.guard

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
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

    // ================= ডার্ক গ্রিন থিম (অ্যাপের স্ক্রিনগুলোর জন্য) =================
    // নোট: ওপরের পুরোনো ফাংশনগুলো সতর্কবার্তা পপ-আপ (সাদা কার্ড) ব্যবহার করে, তাই ওগুলো অপরিবর্তিত।
    val BG_TOP = Color.parseColor("#03201A")
    val BG_BOTTOM = Color.parseColor("#0C4A33")
    val NEON = Color.parseColor("#22E566")
    val CARD = Color.parseColor("#0F3D2C")
    val CARD_STROKE = Color.parseColor("#1D6B4B")
    val TXT = Color.WHITE
    val TXT2 = Color.parseColor("#A6CDB6")
    val RED = Color.parseColor("#FF7A7A")
    val AMBER = Color.parseColor("#FFB74D")

    fun bg() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))

    fun bars(a: Activity) {
        a.window.statusBarColor = BG_TOP
        a.window.navigationBarColor = BG_BOTTOM
    }

    // লোগোর চারপাশে হালকা সবুজ আভা
    fun heroLogo(c: Context, size: Int): FrameLayout {
        val box = size + 44
        return FrameLayout(c).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(c, box / 2).toFloat()
                setColors(intArrayOf(0x5522E566, 0x00000000))
            }
            layoutParams = LinearLayout.LayoutParams(dp(c, box), dp(c, box)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(c, 4)
            }
            addView(ImageView(c).apply { setImageResource(R.drawable.logo) },
                FrameLayout.LayoutParams(dp(c, size), dp(c, size), Gravity.CENTER))
        }
    }

    fun dtext(c: Context, s: String, size: Float = 15f, bold: Boolean = false, color: Int = TXT) =
        TextView(c).apply {
            text = s; textSize = size; setTextColor(color)
            typeface = tf(c, bold)
            layoutParams = lp(c, 6)
        }

    fun dcard(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 16))
        background = GradientDrawable().apply {
            setColor(CARD); setStroke(dp(c, 1), CARD_STROKE); cornerRadius = dp(c, 20).toFloat()
        }
        layoutParams = lp(c, 14)
    }

    // outline=false: উজ্জ্বল সবুজ বাটন, outline=true: শুধু সবুজ বর্ডার
    fun dbutton(c: Context, label: String, outline: Boolean = false, onClick: () -> Unit) = Button(c).apply {
        text = label; isAllCaps = false; textSize = 16f; typeface = tf(c, true)
        setTextColor(if (outline) NEON else Color.parseColor("#04281C"))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 16).toFloat()
            if (outline) { setColor(Color.TRANSPARENT); setStroke(dp(c, 1.5f), NEON) } else setColor(NEON)
        }
        stateListAnimator = null
        minHeight = dp(c, 52)
        layoutParams = lp(c, 10)
        setOnClickListener { onClick() }
    }

    fun dedit(c: Context, hint: String) = EditText(c).apply {
        this.hint = hint; setHintTextColor(Color.parseColor("#7FA891"))
        setTextColor(TXT); typeface = tf(c, false)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#082B1F")); setStroke(dp(c, 1.5f), CARD_STROKE)
            cornerRadius = dp(c, 14).toFloat()
        }
        layoutParams = lp(c, 10)
    }

    fun dlink(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = label; textSize = 15f; setTextColor(NEON); typeface = tf(c, true)
        gravity = Gravity.CENTER
        setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 14))
        layoutParams = lp(c, 6)
        setOnClickListener { onClick() }
    }

    fun divider(c: Context) = android.view.View(c).apply {
        setBackgroundColor(Color.parseColor("#1D5A42"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1))
    }

    // আইকন + লেখা + ডানদিকে সুইচ (পুরো সারিতে চাপ দিলে কাজ করে)
    fun toggleRow(c: Context, icon: String, title: String, sub: String?, on: Boolean, onClick: () -> Unit) =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(c, 12), 0, dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            addView(TextView(c).apply {
                text = icon; textSize = 18f; gravity = Gravity.CENTER
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#1A5A41")) }
            }, LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)))

            val mid = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(TextView(c).apply {
                text = title; textSize = 15.5f; setTextColor(TXT); typeface = tf(c, true)
            })
            if (sub != null) mid.addView(TextView(c).apply {
                text = sub; textSize = 12.5f; setTextColor(TXT2); typeface = tf(c, false)
            })
            addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(c, 12); rightMargin = dp(c, 8)
            })

            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            addView(Switch(c).apply {
                isChecked = on; isClickable = false; isFocusable = false
                thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, Color.parseColor("#9DB3A8")))
                trackTintList = ColorStateList(st, intArrayOf(NEON, Color.parseColor("#2B4A3D")))
            })
            setOnClickListener { onClick() }
        }

    fun dp(c: Context, v: Float) = (v * c.resources.displayMetrics.density).toInt()
}
