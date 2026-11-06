package com.NafsGuard.guard

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout

/** সার্ভারের latest_version_code অ্যাপের versionCode-এর চেয়ে বড় হলে আপডেট পপ-আপ দেখায়। */
object Update {
    const val SITE = "https://nafsguard.netlify.app/"

    /** ব্যাকগ্রাউন্ড চেকে নতুন ভার্সন পাওয়া গেলে খোলা স্ক্রিনকে জানায় */
    @Volatile var onFound: Runnable? = null
    private var cur: Dialog? = null

    /** সার্ভারের latest_version_code অ্যাপের versionCode-এর চেয়ে বড় হলে true */
    fun needed(c: Context): Boolean {
        val v = Prefs.latestCode(c)
        return v > 0 && v > currentCode(c)
    }

    /** নতুন ভার্সন থাকলে (এবং লক চালু না থাকলে) আপডেট পপ-আপ দেখায় */
    fun showIfNeeded(act: Activity) {
        if (act.isFinishing || Prefs.locked(act) || !needed(act)) return
        try { cur?.dismiss() } catch (e: Exception) {}
        show(act)
    }

    @Suppress("DEPRECATION")
    fun currentCode(c: Context): Int = try {
        val p = c.packageManager.getPackageInfo(c.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode.toInt() else p.versionCode
    } catch (e: Exception) { Int.MAX_VALUE }   // পড়া না গেলে ভুল করে পপ-আপ দেখাবে না

    fun show(act: Activity) {
        val c: Context = act
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pd = Ui.dp(c, 22); setPadding(pd, pd, pd, pd)
            background = GradientDrawable().apply {
                setColor(Ui.CARD); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE)
                cornerRadius = Ui.dp(c, 20).toFloat()
            }
        }
        box.addView(Ui.dtext(c, "🔄 নতুন আপডেট এসেছে", 20f, true, Ui.NEON).apply { gravity = Gravity.CENTER })
        box.addView(Ui.dtext(c, "আপনার অ্যাপের ভার্সন পুরোনো। চালিয়ে যেতে অ্যাপটি আপডেট করুন।", 14f, false, Ui.TXT2)
            .apply { gravity = Gravity.CENTER })
        box.addView(Ui.dtext(c, "১. নিচের বাটনে ক্লিক করে নতুন ভার্সন ডাউনলোড করুন।", 15f))
        box.addView(Ui.dtext(c, "২. নতুন ভার্সন ইনস্টল করার আগে পুরোনো ভার্সনটি আনইনস্টল করে নিন।", 15f))

        val dlg = Dialog(c)
        cur = dlg
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setCancelable(false)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((c.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        box.addView(Ui.dbutton(c, "নতুন ভার্সন ডাউনলোড করুন") {
            try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Prefs.updUrl(c).ifEmpty { SITE }))) } catch (e: Exception) {}
        })
        dlg.show()
    }
}
