package com.NafsGuard.guard

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * ব্ল্যাকলিস্ট সেকশন: + চাপলে ফোনের সব অ্যাপ দেখায়, যেগুলো বেছে যোগ করা হবে
 * সেগুলো GuardService আর খুলতে দেবে না। লক চালু থাকলে যোগ করা যায়, মুছা যায় না।
 */
object Blacklist {

    private class AppItem(val pkg: String, val label: String)

    // ফোনে ঢুকতে না পারলে বিপদ হয়, তাই এই অ্যাপগুলো তালিকায় দেখানো হয় না
    private fun protectedPkgs(c: Context): Set<String> {
        val s = mutableSetOf(c.packageName, "com.android.settings", "com.android.systemui", "android")
        val pm = c.packageManager
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            for (r in pm.queryIntentActivities(home, 0)) s.add(r.activityInfo.packageName)
        } catch (e: Exception) {}
        try {
            val tm = c.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage?.let { s.add(it) }
        } catch (e: Exception) {}
        return s
    }

    private fun labelOf(c: Context, pkg: String): String = try {
        val pm = c.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    fun card(act: Activity, locked: Boolean, refresh: () -> Unit): LinearLayout {
        val c: Context = act
        val card = Ui.dcard(c)

        val head = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(Ui.dtext(c, "ব্ল্যাকলিস্ট", 17f, true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        head.addView(TextView(c).apply {
            text = "+"; textSize = 26f; gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#04281C")); typeface = Ui.tf(c, true)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.NEON) }
            layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 42))
            setOnClickListener { pick(act, refresh) }
        })
        card.addView(head)

        card.addView(Ui.dtext(c,
            "+ চেপে ফোনের অ্যাপ বেছে নিন। যেগুলো যোগ করবেন সেগুলোতে আর কোনোভাবেই ঢোকা যাবে না।",
            12.5f, false, Ui.TXT2))

        val list = Prefs.blacklist(c).map { AppItem(it, labelOf(c, it)) }.sortedBy { it.label.lowercase() }
        for (a in list) {
            val row = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 4))
            }
            row.addView(ImageView(c).apply {
                try { setImageDrawable(c.packageManager.getApplicationIcon(a.pkg)) } catch (e: Exception) {}
            }, LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)))
            row.addView(Ui.dtext(c, a.label, 15f).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { leftMargin = Ui.dp(c, 12) }
            })
            if (!locked) row.addView(Ui.dtext(c, "মুছুন", 14f, true, Ui.RED).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(Ui.dp(c, 12), Ui.dp(c, 6), Ui.dp(c, 4), Ui.dp(c, 6))
                setOnClickListener { Prefs.removeBlacklist(c, a.pkg); refresh() }
            })
            card.addView(row)
        }
        if (list.isEmpty()) card.addView(Ui.dtext(c, "এখনো কোনো অ্যাপ যোগ করা হয়নি।", 13f, false, Ui.TXT2))
        if (locked && list.isNotEmpty())
            card.addView(Ui.dtext(c, "লক চালু থাকায় অ্যাপ মুছা যাবে না (যোগ করা যাবে)।", 12.5f, false, Ui.TXT2))
        return card
    }

    private fun pick(act: Activity, refresh: () -> Unit) {
        val c: Context = act
        val pm = c.packageManager
        val skip = protectedPkgs(c)
        val already = Prefs.blacklist(c)

        val all: List<AppItem> = try {
            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(q, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it !in skip }
                .map { AppItem(it, labelOf(c, it)) }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) { emptyList() }

        if (all.isEmpty()) {
            Toast.makeText(c, "ফোনের অ্যাপ তালিকা পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            return
        }

        val chosen = mutableSetOf<String>()
        var shown: List<AppItem> = all

        val ad = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(i: Int) = shown[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View {
                val a = shown[i]
                val row = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8))
                }
                row.addView(ImageView(c).apply {
                    try { setImageDrawable(pm.getApplicationIcon(a.pkg)) } catch (e: Exception) {}
                }, LinearLayout.LayoutParams(Ui.dp(c, 38), Ui.dp(c, 38)))
                row.addView(Ui.dtext(c, a.label, 15f).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        .apply { leftMargin = Ui.dp(c, 12) }
                })
                val isOld = a.pkg in already
                row.addView(CheckBox(c).apply {
                    isChecked = isOld || a.pkg in chosen
                    isClickable = false; isFocusable = false
                    isEnabled = !isOld
                    buttonTintList = ColorStateList.valueOf(Ui.NEON)
                })
                row.alpha = if (isOld) 0.5f else 1f
                return row
            }
        }

        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val pd = Ui.dp(c, 16); setPadding(pd, pd, pd, pd)
            background = GradientDrawable().apply {
                setColor(Ui.CARD); setStroke(Ui.dp(c, 1), Ui.CARD_STROKE); cornerRadius = Ui.dp(c, 20).toFloat()
            }
        }
        box.addView(Ui.dtext(c, "ব্ল্যাকলিস্টে যোগ করুন", 17f, true))
        val search = Ui.dedit(c, "অ্যাপ খুঁজুন")
        box.addView(search)

        val lv = ListView(c).apply {
            divider = null
            this.adapter = ad
            setOnItemClickListener { _, _, i, _ ->
                val a = shown[i]
                if (a.pkg in already) return@setOnItemClickListener
                if (!chosen.add(a.pkg)) chosen.remove(a.pkg)
                ad.notifyDataSetChanged()
            }
        }
        val h = (c.resources.displayMetrics.heightPixels * 0.5).toInt()
        box.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h).apply {
            topMargin = Ui.dp(c, 8)
        })

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val t = s?.toString()?.trim()?.lowercase() ?: ""
                shown = if (t.isEmpty()) all else all.filter { it.label.lowercase().contains(t) }
                ad.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, cnt: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, cnt: Int) {}
        })

        val dlg = Dialog(c)
        dlg.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dlg.setContentView(box)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dlg.window?.setLayout(
            (c.resources.displayMetrics.widthPixels * 0.92).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

        val btns = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        btns.addView(Ui.dbutton(c, "বাতিল", true) { dlg.dismiss() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { topMargin = Ui.dp(c, 10); rightMargin = Ui.dp(c, 6) }
        })
        btns.addView(Ui.dbutton(c, "যোগ করুন") {
            if (chosen.isEmpty()) {
                Toast.makeText(c, "কমপক্ষে একটি অ্যাপ বেছে নিন", Toast.LENGTH_SHORT).show()
                return@dbutton
            }
            val n = chosen.size
            val lockedNow = Prefs.locked(c)
            AlertDialog.Builder(c)
                .setTitle("নিশ্চিত করুন")
                .setMessage("$n টি অ্যাপ ব্ল্যাকলিস্টে যাবে, এগুলোতে আর ঢোকা যাবে না।" +
                    if (lockedNow) "\n\nলক চালু থাকায় লক শেষ না হওয়া পর্যন্ত এগুলো তালিকা থেকে সরানো যাবে না।" else "")
                .setPositiveButton("যোগ করুন") { _, _ ->
                    Prefs.addBlacklist(c, chosen)
                    dlg.dismiss()
                    refresh()
                }
                .setNegativeButton("বাতিল", null)
                .show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { topMargin = Ui.dp(c, 10); leftMargin = Ui.dp(c, 6) }
        })
        box.addView(btns)

        dlg.show()
    }
}
