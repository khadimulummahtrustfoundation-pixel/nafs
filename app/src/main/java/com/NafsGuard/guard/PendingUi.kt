package com.NafsGuard.guard

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "অনুমোদনের অপেক্ষায়" / "মেয়াদ শেষ" পেজের নতুন প্রফেশনাল ডিজাইন + অ্যানিমেশন।
 * সব কিছু কোডে আঁকা (আলাদা ছবি লাগে না)।
 */
class PendingView(
    val frame: FrameLayout,
    val root: LinearLayout,
    private val sections: List<View>,
    private val loops: List<Animator>
) {
    /** ধাপে ধাপে ভেসে ওঠা + চলতে থাকা অ্যানিমেশন চালু */
    fun start() {
        sections.forEachIndexed { i, v ->
            v.animate().alpha(1f).translationY(0f).setDuration(460L)
                .setStartDelay(i * 80L).setInterpolator(DecelerateInterpolator()).start()
        }
        loops.forEach { try { it.start() } catch (e: Exception) {} }
    }

    /** সব অ্যানিমেশন বন্ধ (পেজ ছাড়ার সময় ডাকতে হয়) */
    fun stop() {
        sections.forEach { v -> v.animate().cancel(); v.alpha = 1f; v.translationY = 0f }
        loops.forEach { try { it.cancel() } catch (e: Exception) {} }
    }
}

// ---------------------------------------------------------------- কাস্টম ভিউ/ড্রয়েবল

/** কাচের মতো কার্ড: গ্র্যাডিয়েন্ট ভরাট + সবুজ থেকে সোনালি বর্ডার (gold=true হলে নিচের ডানে সোনালি বাঁক) */
class GlassDrawable(private val r: Float, private val stroke: Float, private val gold: Boolean) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val swoosh = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val sw = Path()

    override fun onBoundsChange(b: Rect) {
        val hs = stroke / 2f
        rect.set(b.left + hs, b.top + hs, b.right - hs, b.bottom - hs)
        fill.shader = LinearGradient(0f, b.top.toFloat(), 0f, b.bottom.toFloat(),
            Color.parseColor("#F0114D36"), Color.parseColor("#F00A2F22"), Shader.TileMode.CLAMP)
        line.strokeWidth = stroke
        val end = if (gold) Color.parseColor("#E6F2B01E") else Color.parseColor("#AA2FBF70")
        line.shader = LinearGradient(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            intArrayOf(Color.parseColor("#CC4DF09A"), Color.parseColor("#882FBF70"), end),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        clip.reset()
        clip.addRoundRect(rect, r, r, Path.Direction.CW)
        if (gold) {
            val w = rect.width(); val h = rect.height()
            sw.reset()
            sw.moveTo(rect.left + w * .68f, rect.bottom)
            sw.quadTo(rect.left + w * .92f, rect.bottom - h * .01f, rect.right, rect.bottom - h * .30f)
            sw.lineTo(rect.right, rect.bottom)
            sw.close()
            swoosh.shader = LinearGradient(rect.left + w * .68f, rect.bottom, rect.right, rect.bottom - h * .30f,
                Color.parseColor("#33B8860B"), Color.parseColor("#F2F2B01E"), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(cv: Canvas) {
        cv.drawRoundRect(rect, r, r, fill)
        if (gold) {
            cv.save(); cv.clipPath(clip); cv.drawPath(sw, swoosh); cv.restore()
        }
        cv.drawRoundRect(rect, r, r, line)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/** পেছনের নকশা: মসজিদের খিলান (arch), হালকা আভা ও মসজিদ-মিনারের ছায়া */
class ArtView(c: Context, private val scale: Float) : View(c) {
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arch = Path()
    private val sky = Path()
    private var lastU = -1f

    private fun build(u: Float) {
        lastU = u
        arch.reset()
        arch.moveTo(.07f * u, .56f * u)
        arch.lineTo(.07f * u, .34f * u)
        arch.cubicTo(.07f * u, .20f * u, .40f * u, .14f * u, .5f * u, .02f * u)
        arch.cubicTo(.60f * u, .14f * u, .93f * u, .20f * u, .93f * u, .34f * u)
        arch.lineTo(.93f * u, .56f * u)

        sky.reset()
        val base = .50f * u
        fun dome(cx: Float, r: Float) {
            sky.addArc(RectF(cx - r, base - r, cx + r, base + r), 180f, 180f)
            sky.addRect(cx - .004f * u, base - r - .03f * u, cx + .004f * u, base - r + .002f * u, Path.Direction.CW)
        }
        fun minaret(x: Float, wd: Float, top: Float) {
            sky.addRect(x, top, x + wd, base, Path.Direction.CW)
            sky.addRect(x - .006f * u, top + .035f * u, x + wd + .006f * u, top + .046f * u, Path.Direction.CW)
            sky.moveTo(x - .002f * u, top); sky.lineTo(x + wd / 2f, top - .055f * u); sky.lineTo(x + wd + .002f * u, top); sky.close()
        }
        sky.addRect(0f, base - .05f * u, .40f * u, base, Path.Direction.CW)
        dome(.27f * u, .065f * u)
        dome(.13f * u, .035f * u)
        minaret(.04f * u, .022f * u, .30f * u)
        minaret(.19f * u, .02f * u, .33f * u)
        minaret(.34f * u, .02f * u, .28f * u)

        fillP.shader = LinearGradient(0f, .24f * u, 0f, .52f * u,
            Color.parseColor("#00000000"), Color.parseColor("#A0020F0A"), Shader.TileMode.CLAMP)
        lineP.strokeWidth = resources.displayMetrics.density * 1.4f
        lineP.shader = LinearGradient(0f, 0f, 0f, .56f * u,
            Color.parseColor("#CC2FE077"), Color.parseColor("#0022E566"), Shader.TileMode.CLAMP)
        glowP.shader = RadialGradient(u / 2f, .20f * u, .62f * u,
            Color.parseColor("#4422E566"), Color.parseColor("#0022E566"), Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val u = w * scale
        if (u != lastU) build(u)
        cv.save()
        cv.translate((w - u) / 2f, 0f)
        cv.drawCircle(u / 2f, .20f * u, .62f * u, glowP)
        cv.drawPath(sky, fillP)
        cv.save(); cv.scale(-1f, 1f, u / 2f, 0f); cv.drawPath(sky, fillP); cv.restore()
        lineP.alpha = 255
        cv.drawPath(arch, lineP)
        cv.save(); cv.scale(.93f, .96f, u / 2f, .56f * u)
        lineP.alpha = 90
        cv.drawPath(arch, lineP)
        cv.restore()
        cv.restore()
    }
}

/** লোগোর পেছনে নরম সবুজ আভা (শ্বাসের মতো ওঠানামা করে) */
class GlowView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = minOf(w, h) / 2f
        p.shader = RadialGradient(w / 2f, h / 2f, r,
            intArrayOf(Color.parseColor("#7722E566"), Color.parseColor("#2222E566"), Color.parseColor("#0022E566")),
            floatArrayOf(.45f, .75f, 1f), Shader.TileMode.CLAMP)
    }
    override fun onDraw(cv: Canvas) {
        cv.drawCircle(width / 2f, height / 2f, minOf(width, height) / 2f, p)
    }
}

/** অ্যাভাটারের চারপাশে ঘুরতে থাকা উজ্জ্বল চাপ */
class ArcRing(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rect = RectF()
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val sw = resources.displayMetrics.density * 2.6f
        p.strokeWidth = sw
        rect.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f)
        p.shader = SweepGradient(w / 2f, h / 2f,
            intArrayOf(Color.parseColor("#0022E566"), Color.parseColor("#0022E566"), Color.parseColor("#FF6BFFA4"), Color.parseColor("#0022E566")),
            floatArrayOf(0f, .6f, .96f, 1f))
    }
    override fun onDraw(cv: Canvas) { cv.drawArc(rect, 0f, 360f, false, p) }
}

/** ঘড়ি: মিনিটের কাঁটা ঘুরতে থাকে */
class ClockView(c: Context, tint: Int) : View(c) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = tint }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = tint; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = tint }
    var angle = 0f
        set(v) { field = v; invalidate() }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f; val cy = height / 2f
        ring.strokeWidth = d * 2.4f; hand.strokeWidth = d * 2.2f
        val r = minOf(width, height) / 2f - ring.strokeWidth / 2f
        cv.drawCircle(cx, cy, r, ring)
        cv.save(); cv.rotate(angle / 12f + 40f, cx, cy)
        cv.drawLine(cx, cy, cx, cy - r * .45f, hand); cv.restore()
        cv.save(); cv.rotate(angle, cx, cy)
        cv.drawLine(cx, cy, cx, cy - r * .70f, hand); cv.restore()
        cv.drawCircle(cx, cy, d * 2f, dot)
    }
}

/** বাটন: মাঝে মাঝে ওপর দিয়ে আলোর ঝিলিক বয়ে যায় */
class ShimmerButton(c: Context) : LinearLayout(c) {
    var phase = 2f           // 0..1 = ঝিলিক চলছে, 1..2 = বিরতি
    var radius = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val band = Path()

    override fun dispatchDraw(cv: Canvas) {
        super.dispatchDraw(cv)
        if (phase > 1f || width == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        val bw = w * .26f
        val skew = h * .45f
        val x = -bw - skew + (w + bw * 2f + skew) * phase
        clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), radius, radius, Path.Direction.CW)
        band.reset()
        band.moveTo(x + skew, 0f); band.lineTo(x + bw + skew, 0f); band.lineTo(x + bw, h); band.lineTo(x, h); band.close()
        p.shader = LinearGradient(x, 0f, x + bw + skew, 0f,
            intArrayOf(Color.parseColor("#00FFFFFF"), Color.parseColor("#66FFFFFF"), Color.parseColor("#00FFFFFF")),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        cv.save(); cv.clipPath(clip); cv.drawPath(band, p); cv.restore()
    }
}

// ---------------------------------------------------------------- পেজ বানানো

object PendingUi {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * k = মাপের ধাপ (১ = পুরো মাপ, ছোট ফোনে কমে), যাতে সবকিছু এক স্ক্রিনে আঁটে।
     * expiryText: মেয়াদ শেষ হলে তারিখের লেখা (না হলে খালি)।
     */
    fun build(a: Activity, k: Float, expired: Boolean, name: String, phone: String,
              expiryText: String, onContact: () -> Unit): PendingView {
        val c: Context = a
        val kt = 0.85f + 0.15f * k                 // ফন্ট কম ছোট হয়, ফাঁকা জায়গা বেশি ছোট হয়
        fun px(v: Float) = Ui.dp(c, v * k)
        fun col(s: String) = Color.parseColor(s)

        fun tv(s: CharSequence, sp: Float, bold: Boolean = false, color: Int = Ui.TXT): TextView = TextView(c).apply {
            text = s; textSize = sp * kt; setTextColor(color); typeface = Ui.tf(c, bold)
        }
        fun lp(w: Int, h: Int, top: Float = 0f, side: Float = 0f) = LinearLayout.LayoutParams(w, h).apply {
            topMargin = px(top); leftMargin = px(side); rightMargin = px(side)
        }

        val sections = ArrayList<View>()
        val loops = ArrayList<Animator>()
        var clock: ClockView? = null

        // ---- মূল কাঠামো
        val frame = FrameLayout(c).apply { background = Ui.bg(); clipChildren = false }
        frame.addView(ArtView(c, 0.7f + 0.3f * k), FrameLayout.LayoutParams(MATCH, MATCH))
        val ph = Ui.dp(c, 16f * maxOf(k, .8f))
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            setPadding(ph, px(22f), ph, px(14f))
        }
        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))

        fun add(v: View, p: LinearLayout.LayoutParams) {
            v.alpha = 0f; v.translationY = px(16f).toFloat()
            root.addView(v, p); sections.add(v)
        }
        fun spacer(minDp: Float) {
            root.addView(View(c), LinearLayout.LayoutParams(1, px(minDp), 1f))
        }

        // ---- লোগো + আভা
        val L = px(92f)
        val logoWrap = FrameLayout(c).apply { clipChildren = false }
        val glow = GlowView(c)
        val gs = L + px(90f)
        logoWrap.addView(glow, FrameLayout.LayoutParams(gs, gs, Gravity.CENTER))
        logoWrap.addView(ImageView(c).apply {
            setImageResource(R.drawable.logo)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT); setStroke(Ui.dp(c, 2), col("#6622E566")); cornerRadius = L * .22f
            }
        }, FrameLayout.LayoutParams(L, L, Gravity.CENTER))
        add(logoWrap, lp(L + px(10f), L + px(10f)))

        // ---- NafsGuard (দুই রঙ)
        val span = SpannableString("NafsGuard").apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(col("#3DFF86")), 4, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val title = tv(span, 34f, true).apply {
            gravity = Gravity.CENTER; letterSpacing = 0.02f
            setShadowLayer(px(10f).toFloat(), 0f, 0f, col("#9922E566"))
        }
        add(title, lp(WRAP, WRAP, 2f))

        // ---- সোনালি রেখা + তালা
        val badge = FrameLayout(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(col("#FFE08A"), col("#E0A100"))).apply { cornerRadius = px(8f).toFloat() }
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_lock); setColorFilter(col("#3D2A00")) },
                FrameLayout.LayoutParams(px(15f), px(15f), Gravity.CENTER))
        }
        fun goldLine(left: Boolean) = View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (left) intArrayOf(col("#00F2B01E"), col("#CCF2B01E")) else intArrayOf(col("#CCF2B01E"), col("#00F2B01E")))
        }
        val divRow = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(goldLine(true), LinearLayout.LayoutParams(0, Ui.dp(c, 1.2f), 1f))
            addView(badge, LinearLayout.LayoutParams(px(28f), px(28f)).apply { leftMargin = px(10f); rightMargin = px(10f) })
            addView(goldLine(false), LinearLayout.LayoutParams(0, Ui.dp(c, 1.2f), 1f))
        }
        add(divRow, lp(MATCH, WRAP, 4f, 18f))

        // ---- শিরোনাম (সোনালি) ও বর্ণনা
        val heading = tv(if (expired) "মেয়াদ শেষ হয়েছে" else "অনুমোদনের অপেক্ষায়", 27f, true, col("#FFD54F")).apply {
            gravity = Gravity.CENTER
            setShadowLayer(px(8f).toFloat(), 0f, 0f, col("#66F2B01E"))
            paint.shader = LinearGradient(0f, 0f, 0f, textSize * 1.5f,
                col("#FFE9A0"), col("#F2A900"), Shader.TileMode.CLAMP)
        }
        add(heading, lp(WRAP, WRAP, 8f))
        val desc = tv(
            if (expired) "আপনার অ্যাকাউন্টের মেয়াদ শেষ, তাই সব সুরক্ষা বন্ধ আছে।"
            else "আপনার রেজিস্ট্রেশন সম্পন্ন হয়েছে।\nঅ্যাডমিন অনুমোদন দিলে অ্যাপ চালু হবে।",
            14.5f, false, col("#CFE8DA")).apply {
            gravity = Gravity.CENTER; setLineSpacing(px(2f).toFloat(), 1f)
        }
        add(desc, lp(WRAP, WRAP, 4f, 10f))
        spacer(10f)

        // ---- তথ্য কার্ড (অ্যাভাটার + নাম + ফোন)
        val card = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GlassDrawable(px(22f).toFloat(), Ui.dp(c, 1.5f).toFloat(), true)
            setPadding(px(16f), px(14f), px(16f), px(14f))
        }
        val av = px(74f)
        val ring = ArcRing(c)
        val avatar = FrameLayout(c).apply {
            addView(View(c).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(col("#0A3324")); setStroke(Ui.dp(c, 2), col("#2A8F5B"))
                }
            }, FrameLayout.LayoutParams(av, av))
            addView(ring, FrameLayout.LayoutParams(av, av))
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_person); setColorFilter(col("#6FF0A0")) },
                FrameLayout.LayoutParams(px(42f), px(42f), Gravity.CENTER))
        }
        card.addView(avatar, LinearLayout.LayoutParams(av, av))
        card.addView(View(c).apply { setBackgroundColor(col("#2A7A58")) },
            LinearLayout.LayoutParams(Ui.dp(c, 1), MATCH).apply {
                leftMargin = px(14f); rightMargin = px(14f); topMargin = px(2f); bottomMargin = px(2f)
            })

        fun infoLine(res: Int, label: String, value: String, tint: Int, vsp: Float) = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(ImageView(c).apply { setImageResource(res); setColorFilter(tint) }, LinearLayout.LayoutParams(px(16f), px(16f)))
                addView(tv(label, 12.5f, false, Ui.TXT2), LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = px(8f) })
            })
            addView(tv(value, vsp, true), LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = px(24f) })
        }
        val rows = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
        rows.addView(infoLine(R.drawable.ic_person, "নাম", name, Ui.NEON, 20f))
        rows.addView(View(c), LinearLayout.LayoutParams(1, px(8f)))
        rows.addView(infoLine(R.drawable.ic_phone, "ফোন নম্বর", phone, Ui.NEON, 20f))
        if (expired) {
            rows.addView(View(c), LinearLayout.LayoutParams(1, px(8f)))
            rows.addView(infoLine(R.drawable.ic_clock, "মেয়াদ শেষ হয়েছে", expiryText, Ui.AMBER, 15f))
        }
        card.addView(rows, LinearLayout.LayoutParams(0, WRAP, 1f))
        add(card, lp(MATCH, WRAP))
        spacer(14f)

        // ---- অ্যাকাউন্টের অবস্থা (৩ ধাপ)
        fun stepCol(state: Int, label: String, sub: String): LinearLayout = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            val box = px(40f)
            val iconView: View = when (state) {
                0 -> FrameLayout(c).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col("#22DB68")) }
                    addView(ImageView(c).apply { setImageResource(R.drawable.ic_tick); setColorFilter(col("#04281C")) },
                        FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
                }
                1 -> ClockView(c, Ui.AMBER).also { clock = it }
                else -> View(c).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(Ui.dp(c, 2), col("#5A8F78"))
                    }
                }
            }
            addView(iconView, LinearLayout.LayoutParams(box, box))
            addView(tv(label, 14f, true, if (state == 2) col("#BFD8CA") else Ui.TXT).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(8f) })
            val subColor = when (state) { 0 -> col("#35E07D"); 1 -> col("#FFC53D"); else -> Ui.TXT2 }
            addView(tv(sub, 12f, false, subColor).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = px(2f) })
        }
        fun vDivider() = View(c).apply { setBackgroundColor(col("#2A7A58")) }

        val statusCard = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GlassDrawable(px(22f).toFloat(), Ui.dp(c, 1.5f).toFloat(), false)
            setPadding(px(6f), px(27f), px(6f), px(14f))
        }
        val s = if (expired) listOf(
            Triple(0, "মেয়াদ শেষ", "সুরক্ষা বন্ধ"),
            Triple(1, "নবায়ন", "অ্যাডমিনের সাথে যোগাযোগ"),
            Triple(2, "আবার চালু", "অনুমোদন পেলে চালু হবে")
        ) else listOf(
            Triple(0, "রেজিস্ট্রেশন", "সম্পন্ন হয়েছে"),
            Triple(1, "অ্যাডমিন অনুমোদন", "অপেক্ষা করা হচ্ছে"),
            Triple(2, "অ্যাপ চালু", "অনুমোদন পেলে শুরু হবে")
        )
        s.forEachIndexed { i, t ->
            if (i > 0) statusCard.addView(vDivider(), LinearLayout.LayoutParams(Ui.dp(c, 1), px(64f)).apply { topMargin = px(4f) })
            statusCard.addView(stepCol(t.first, t.second, t.third), LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        val pill = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(col("#0F5A3D"), col("#0A3A28"))).apply {
                cornerRadius = px(16f).toFloat(); setStroke(Ui.dp(c, 1.5f), col("#2FBF70"))
            }
            setPadding(px(18f), px(7f), px(20f), px(7f))
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_shield_check); setColorFilter(Ui.NEON) },
                LinearLayout.LayoutParams(px(20f), px(20f)))
            addView(tv("অ্যাকাউন্টের অবস্থা", 15f, true), LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = px(8f) })
        }
        val statusWrap = FrameLayout(c).apply { clipChildren = false; clipToPadding = false }
        statusWrap.addView(statusCard, FrameLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(17f) })
        statusWrap.addView(pill, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        add(statusWrap, lp(MATCH, WRAP))
        spacer(16f)

        // ---- যোগাযোগ বাটন (ঝিলিকসহ)
        val btnR = px(32f).toFloat()
        val btn = ShimmerButton(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            radius = btnR
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(col("#2BE673"), col("#15A650"))).apply {
                cornerRadius = btnR; setStroke(Ui.dp(c, 1.5f), col("#8CFFB8"))
            }
            setPadding(px(10f), 0, px(12f), 0)
            isClickable = true; isFocusable = true
            setOnClickListener { onContact() }
            addView(FrameLayout(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col("#0B3A29")) }
                addView(ImageView(c).apply { setImageResource(R.drawable.ic_shield_check); setColorFilter(col("#D6FFE6")) },
                    FrameLayout.LayoutParams(px(24f), px(24f), Gravity.CENTER))
            }, LinearLayout.LayoutParams(px(42f), px(42f)))
            addView(View(c).apply { setBackgroundColor(col("#55FFFFFF")) },
                LinearLayout.LayoutParams(Ui.dp(c, 1), px(30f)).apply { leftMargin = px(12f); rightMargin = px(12f) })
            addView(tv("অ্যাডমিনের সাথে যোগাযোগ করুন", 16f, true).apply {
                gravity = Gravity.CENTER; setShadowLayer(px(3f).toFloat(), 0f, px(1f).toFloat(), col("#66000000"))
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_chevron); setColorFilter(Color.WHITE) },
                LinearLayout.LayoutParams(px(28f), px(28f)))
        }
        add(btn, lp(MATCH, px(60f), 0f, 2f))
        spacer(12f)

        // ---- নিচের নোট
        fun fadeLine(left: Boolean) = View(c).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                if (left) intArrayOf(col("#0022E566"), col("#8822E566")) else intArrayOf(col("#8822E566"), col("#0022E566")))
        }
        val foot = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(fadeLine(true), LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
            addView(ImageView(c).apply { setImageResource(R.drawable.ic_info); setColorFilter(Ui.NEON) },
                LinearLayout.LayoutParams(px(16f), px(16f)).apply { leftMargin = px(8f) })
            addView(tv("অনুমোদন এলে এই পেজ নিজে থেকেই মূল পেজে চলে যাবে।", 12f, false, col("#CFE8DA")).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = px(6f); rightMargin = px(6f) })
            addView(fadeLine(false), LinearLayout.LayoutParams(0, Ui.dp(c, 1), 1f))
        }
        add(foot, lp(MATCH, WRAP))

        // ---- চলতে থাকা অ্যানিমেশন (start() ডাকলে চালু হয়)
        fun loop(an: ValueAnimator, rev: Boolean, ms: Long) {
            an.repeatCount = ValueAnimator.INFINITE
            an.repeatMode = if (rev) ValueAnimator.REVERSE else ValueAnimator.RESTART
            an.duration = ms
            loops.add(an)
        }
        // লোগোর আভা শ্বাস নেয়
        val breathe = ObjectAnimator.ofPropertyValuesHolder(glow,
            PropertyValuesHolder.ofFloat(View.ALPHA, .45f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, .9f, 1.08f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, .9f, 1.08f))
        breathe.interpolator = AccelerateDecelerateInterpolator()
        loop(breathe, true, 2000L)
        // তালা হালকা স্পন্দন
        val lockPulse = ObjectAnimator.ofPropertyValuesHolder(badge,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f))
        lockPulse.interpolator = AccelerateDecelerateInterpolator()
        loop(lockPulse, true, 1100L)
        // অ্যাভাটারের চারপাশে ঘূর্ণি
        val spin = ObjectAnimator.ofFloat(ring, View.ROTATION, 0f, 360f)
        spin.interpolator = LinearInterpolator()
        loop(spin, false, 3000L)
        // ঘড়ির কাঁটা
        val ck = clock
        if (ck != null) {
            val tick = ValueAnimator.ofFloat(0f, 360f)
            tick.interpolator = LinearInterpolator()
            tick.addUpdateListener { ck.angle = it.animatedValue as Float }
            loop(tick, false, 6000L)
        }
        // বাটনের ঝিলিক
        val shine = ValueAnimator.ofFloat(0f, 2f)
        shine.interpolator = LinearInterpolator()
        shine.addUpdateListener { btn.phase = it.animatedValue as Float; btn.invalidate() }
        loop(shine, false, 3400L)

        return PendingView(frame, root, sections, loops)
    }
}
