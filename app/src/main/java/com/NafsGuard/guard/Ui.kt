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
            text = Lang.t(c, s); textSize = size; setTextColor(color)
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
        text = Lang.t(c, label); textSize = 15f; setTextColor(GREEN); typeface = tf(c, true)
        gravity = android.view.Gravity.CENTER
        setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 14))
        layoutParams = lp(c, 6)
        setOnClickListener { onClick() }
    }

    // ================= ডার্ক গ্রিন থিম (অ্যাপের স্ক্রিনগুলোর জন্য) =================
    // নোট: ওপরের পুরোনো ফাংশনগুলো সতর্কবার্তা পপ-আপ (সাদা কার্ড) ব্যবহার করে, তাই ওগুলো অপরিবর্তিত।
    val BG_TOP = Color.parseColor("#04150F")
    val BG_BOTTOM = Color.parseColor("#0A2E22")
    val NEON = Color.parseColor("#2FE58A")
    val CARD = Color.parseColor("#0D2A20")
    val CARD_STROKE = Color.parseColor("#18463A")
    val TXT = Color.parseColor("#F2F8F4")
    val TXT2 = Color.parseColor("#9DBBAA")
    val RED = Color.parseColor("#FF6B6B")
    val AMBER = Color.parseColor("#FFC857")

    fun bg() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG_BOTTOM))

    fun bars(a: Activity) {
        a.window.statusBarColor = BG_TOP
        a.window.navigationBarColor = BG_BOTTOM
    }

    // লোগোর চারপাশে হালকা সবুজ আভা
    fun heroLogo(c: Context, size: Int, glow: Int = 44): FrameLayout {
        val box = size + glow
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
            text = Lang.t(c, s); textSize = size; setTextColor(color)
            typeface = tf(c, bold)
            layoutParams = lp(c, 6)
        }

    fun dcard(c: Context) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(c, 15), dp(c, 15), dp(c, 15), dp(c, 12))
        background = GradientDrawable().apply {
            setColor(CARD); setStroke(dp(c, 1), CARD_STROKE); cornerRadius = dp(c, 22).toFloat()
        }
        layoutParams = lp(c, 14)
    }

    // outline=false: উজ্জ্বল সবুজ বাটন, outline=true: শুধু সবুজ বর্ডার
    fun dbutton(c: Context, label: String, outline: Boolean = false, onClick: () -> Unit) = Button(c).apply {
        text = Lang.t(c, label); isAllCaps = false; textSize = 16f; typeface = tf(c, true)
        setTextColor(if (outline) NEON else Color.parseColor("#04281C"))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 15).toFloat()
            if (outline) { setColor(Color.TRANSPARENT); setStroke(dp(c, 1.5f), NEON) } else setColor(NEON)
        }
        stateListAnimator = null
        minHeight = dp(c, 50)
        layoutParams = lp(c, 10)
        setOnClickListener { onClick() }
    }

    fun dedit(c: Context, hint: String) = EditText(c).apply {
        this.hint = Lang.t(c, hint); setHintTextColor(Color.parseColor("#7FA891"))
        setTextColor(TXT); typeface = tf(c, false)
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply {
            setColor(INSET); setStroke(dp(c, 1.5f), CARD_STROKE)
            cornerRadius = dp(c, 14).toFloat()
        }
        layoutParams = lp(c, 10)
    }

    fun dlink(c: Context, label: String, onClick: () -> Unit) = TextView(c).apply {
        text = Lang.t(c, label); textSize = 15f; setTextColor(NEON); typeface = tf(c, true)
        gravity = Gravity.CENTER
        setPadding(dp(c, 8), dp(c, 14), dp(c, 8), dp(c, 14))
        layoutParams = lp(c, 6)
        setOnClickListener { onClick() }
    }

    fun divider(c: Context) = android.view.View(c).apply {
        setBackgroundColor(Color.parseColor("#153B2F"))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1))
    }

    // আইকন + লেখা + ডানদিকে সুইচ (পুরো সারিতে চাপ দিলে কাজ করে)
    fun toggleRow(c: Context, icon: String, title: String, sub: String?, on: Boolean, onClick: () -> Unit) =
        LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(c, 11), 0, dp(c, 11))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

            addView(
                if (ICONS.containsKey(icon)) tile(c, icon)
                else TextView(c).apply {
                    text = icon; textSize = 18f; gravity = Gravity.CENTER
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#174737")) }
                },
                LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)))

            val mid = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(TextView(c).apply {
                text = Lang.t(c, title); textSize = 15.5f; setTextColor(TXT); typeface = tf(c, true)
            })
            if (sub != null) mid.addView(TextView(c).apply {
                text = Lang.t(c, sub); textSize = 12.5f; setTextColor(TXT2); typeface = tf(c, false)
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


    // ================= নতুন থিম: ভেক্টর আইকন + রঙিন টাইল =================
    val CYAN = Color.parseColor("#5BD6E8")
    val SKY = Color.parseColor("#6AB7FF")
    val VIOLET = Color.parseColor("#A78BFA")
    val PINK = Color.parseColor("#FF7DB0")
    val ORANGE = Color.parseColor("#FFA45C")
    val INSET = Color.parseColor("#071D15")
    val TXT3 = Color.parseColor("#6F9382")

    // আইকনের নাম -> (ড্রয়েবল, ডিফল্ট রঙ)
    private val ICONS: Map<String, Pair<Int, Int>> by lazy {
        mapOf(
            "shield" to (R.drawable.ic_shield to NEON),
            "shield_check" to (R.drawable.ic_shield_check to NEON),
            "admin" to (R.drawable.ic_admin to CYAN),
            "battery" to (R.drawable.ic_battery to AMBER),
            "search" to (R.drawable.ic_search to SKY),
            "key" to (R.drawable.ic_key to VIOLET),
            "block" to (R.drawable.ic_block to RED),
            "edit" to (R.drawable.ic_edit to NEON),
            "movie" to (R.drawable.ic_movie to SKY),
            "camera" to (R.drawable.ic_camera to PINK),
            "play" to (R.drawable.ic_play to RED),
            "image" to (R.drawable.ic_image to ORANGE),
            "link" to (R.drawable.ic_link to CYAN),
            "warning" to (R.drawable.ic_warning to AMBER),
            "delete" to (R.drawable.ic_delete to RED),
            "add" to (R.drawable.ic_add to NEON),
            "apps" to (R.drawable.ic_apps to VIOLET),
            "refresh" to (R.drawable.ic_refresh to NEON),
            "arrow_forward" to (R.drawable.ic_arrow_forward to NEON),
            "lock" to (R.drawable.ic_lock to NEON),
            "bell" to (R.drawable.ic_bell to NEON),
            "clock" to (R.drawable.ic_clock to NEON),
            "info" to (R.drawable.ic_info to CYAN),
            "build" to (R.drawable.ic_build to AMBER),
            "check" to (R.drawable.ic_check to NEON)
        )
    }

    fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    fun iconView(c: Context, key: String, sizeDp: Int, tint: Int? = null) = ImageView(c).apply {
        val (res, col) = ICONS[key] ?: (R.drawable.ic_info to CYAN)
        setImageResource(res); setColorFilter(tint ?: col)
        layoutParams = LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp))
    }

    // রঙিন আইকন-টাইল: ১৫% রঙের ব্যাকগ্রাউন্ড + ২৮% রঙের বর্ডার
    fun tile(c: Context, key: String, size: Int = 42, color: Int? = null): FrameLayout {
        val (res, def) = ICONS[key] ?: (R.drawable.ic_info to CYAN)
        val col = color ?: def
        return FrameLayout(c).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(c, size * 13 / 42).toFloat()
                setColor(withAlpha(col, 38)); setStroke(dp(c, 1), withAlpha(col, 71))
            }
            addView(ImageView(c).apply { setImageResource(res); setColorFilter(col) },
                FrameLayout.LayoutParams(dp(c, size / 2), dp(c, size / 2), Gravity.CENTER))
        }
    }

    // সেকশন হেডার: আইকন + শিরোনাম + ডানে ছোট কাউন্ট-ট্যাগ (যেমন "৪ / ৫ চালু")
    fun sectionHeader(c: Context, key: String, title: String, count: String? = null) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(c, 4))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(iconView(c, key, 19, NEON))
        addView(TextView(c).apply {
            text = Lang.t(c, title); textSize = 16.5f; setTextColor(TXT); typeface = tf(c, true)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(c, 9) })
        if (count != null) addView(TextView(c).apply {
            text = Lang.t(c, count); textSize = 11.5f; setTextColor(NEON); typeface = tf(c, true)
            setPadding(dp(c, 9), dp(c, 3), dp(c, 9), dp(c, 3))
            background = GradientDrawable().apply { cornerRadius = dp(c, 99).toFloat(); setColor(withAlpha(NEON, 31)) }
        })
    }

    // তথ্য/সতর্কতা নোট বক্স
    fun note(c: Context, s: String, warn: Boolean = false) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(c, 11), dp(c, 10), dp(c, 11), dp(c, 10))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 13).toFloat()
            setColor(if (warn) withAlpha(AMBER, 20) else INSET)
        }
        layoutParams = lp(c, 6)
        addView(iconView(c, if (warn) "warning" else "info", 16, if (warn) AMBER else CYAN),
            LinearLayout.LayoutParams(dp(c, 16), dp(c, 16)).apply { topMargin = dp(c, 2) })
        addView(TextView(c).apply {
            text = Lang.t(c, s); textSize = 12.3f; typeface = tf(c, false)
            setTextColor(if (warn) Color.parseColor("#F1D9A0") else TXT2)
            setLineSpacing(0f, 1.15f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(c, 9) })
    }

    // হিরোর স্ট্যাটাস পিল: আইকন + লেখা (ok = সবুজ, নাহলে হলুদ)
    fun statusPill(c: Context, ok: Boolean, label: String) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val col = if (ok) NEON else AMBER
        setPadding(dp(c, 10), dp(c, 6), dp(c, 13), dp(c, 6))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 99).toFloat()
            setColor(withAlpha(col, 33)); setStroke(dp(c, 1), withAlpha(col, 89))
        }
        addView(iconView(c, if (ok) "shield_check" else "warning", 17, col))
        addView(TextView(c).apply {
            text = Lang.t(c, label); textSize = 14f; setTextColor(col); typeface = tf(c, true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(c, 7) })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(c, 8) }
    }

    // ৪ ভাগের প্রগ্রেস বার (চালু ধাপগুলো সবুজ)
    fun progressBar(c: Context, on: List<Boolean>) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(dp(c, 150), dp(c, 4))
            .apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(c, 10); bottomMargin = dp(c, 2) }
        on.forEachIndexed { i, b ->
            addView(android.view.View(c).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(c, 4).toFloat()
                    setColor(if (b) NEON else Color.parseColor("#1A4435"))
                }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) marginStart = dp(c, 5) })
        }
    }

    // ট্যাব বারের একটি আইটেম: ওপরে আইকন, নিচে লেখা
    fun tabItem(c: Context, key: String, label: String, sel: Boolean, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, dp(c, 8), 0, dp(c, 7))
        val col = if (sel) Color.parseColor("#03231A") else TXT2
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 14).toFloat()
            setColor(if (sel) NEON else Color.TRANSPARENT)
        }
        addView(iconView(c, key, 20, col), LinearLayout.LayoutParams(dp(c, 20), dp(c, 20)))
        addView(TextView(c).apply {
            text = Lang.t(c, label); textSize = 12f; setTextColor(col); typeface = tf(c, true)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(c, 3) })
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(c, 2); marginEnd = dp(c, 2) }
        setOnClickListener { onClick() }
    }

    // আইকনসহ বাটন (outline=true: শুধু সবুজ বর্ডার)
    fun iconButton(c: Context, key: String, label: String, outline: Boolean = false, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val col = if (outline) NEON else Color.parseColor("#04281C")
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 15).toFloat()
            if (outline) { setColor(Color.TRANSPARENT); setStroke(dp(c, 1.5f), withAlpha(NEON, 178)) } else setColor(NEON)
        }
        minimumHeight = dp(c, 50)
        setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12))
        layoutParams = lp(c, 10)
        addView(iconView(c, key, 19, col))
        addView(TextView(c).apply {
            text = Lang.t(c, label); textSize = 15.5f; setTextColor(col); typeface = tf(c, true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(c, 8) })
        setOnClickListener { onClick() }
    }

    fun bn(n: Int): String = n.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

    // "মুছুন" চিপ (আইকন + লেখা, হালকা লাল ব্যাকগ্রাউন্ড)
    fun deleteChip(c: Context, onClick: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6))
        background = GradientDrawable().apply { cornerRadius = dp(c, 10).toFloat(); setColor(withAlpha(RED, 26)) }
        addView(iconView(c, "delete", 16, RED))
        addView(TextView(c).apply {
            text = Lang.t(c, "মুছুন"); textSize = 13.5f; setTextColor(RED); typeface = tf(c, true)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(c, 5) })
        setOnClickListener { onClick() }
    }

    // তালিকার সারি: বাঁয়ে আইকন/ছবি, মাঝে নাম, ডানে মুছুন চিপ
    fun wordRow(c: Context, lead: android.view.View, name: String, canDelete: Boolean, onDelete: () -> Unit) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(c, 10), 0, dp(c, 10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(lead, LinearLayout.LayoutParams(dp(c, 34), dp(c, 34)))
        addView(TextView(c).apply { text = name; textSize = 15f; setTextColor(TXT); typeface = tf(c, false) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(c, 10); marginEnd = dp(c, 8) })
        if (canDelete) addView(deleteChip(c, onDelete))
    }

    fun languageSelector(c: Context, onChanged: () -> Unit): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        val current = Prefs.language(c)
        fun choice(label: String, code: String) = TextView(c).apply {
            text = label; textSize = 13f; typeface = tf(c, true)
            gravity = Gravity.CENTER
            setTextColor(if (current == code) Color.parseColor("#04281C") else NEON)
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 18).toFloat()
                if (current == code) setColor(NEON) else { setColor(Color.TRANSPARENT); setStroke(dp(c, 1), NEON) }
            }
            setPadding(dp(c, 18), dp(c, 8), dp(c, 18), dp(c, 8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(c, 4); marginEnd = dp(c, 4)
            }
            setOnClickListener { if (Prefs.language(c) != code) { Prefs.setLanguage(c, code); onChanged() } }
        }
        addView(choice("বাংলা", "bn")); addView(choice("English", "en"))
    }

    fun dp(c: Context, v: Float) = (v * c.resources.displayMetrics.density).toInt()
}
