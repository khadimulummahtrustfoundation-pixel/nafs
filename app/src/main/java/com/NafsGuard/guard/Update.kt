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

/** অ্যাপের ভার্সন Google Sheet-এর সাথে না মিললে আপডেট পপ-আপ দেখায়। */
object Update {
    const val SITE = "https://nafsguard.netlify.app/"

    fun current(c: Context): String = try {
        (c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "").trim()
    } catch (e: Exception) { "" }

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
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setCancelable(false)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout((c.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        box.addView(Ui.dbutton(c, "নতুন ভার্সন ডাউনলোড করুন") {
            try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE))) } catch (e: Exception) {}
        })
        dlg.show()
    }
}
