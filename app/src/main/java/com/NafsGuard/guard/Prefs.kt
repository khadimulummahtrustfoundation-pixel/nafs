package com.NafsGuard.guard

import android.content.Context

object Prefs {
    const val LOCK_MS = 30L * 24 * 60 * 60 * 1000

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("nafsguard", Context.MODE_PRIVATE)

    fun loggedIn(c: Context) = sp(c).getBoolean("in", false)
    fun phone(c: Context) = sp(c).getString("phone", "") ?: ""
    fun pass(c: Context) = sp(c).getString("pass", "") ?: ""
    fun name(c: Context) = sp(c).getString("name", "") ?: ""

    fun setLogin(c: Context, phone: String, pass: String, name: String) =
        sp(c).edit().putBoolean("in", true).putString("phone", phone)
            .putString("pass", pass).putString("name", name).apply()

    fun logout(c: Context) =
        sp(c).edit().putBoolean("in", false).remove("pass").apply()

    fun words(c: Context): Set<String> =
        sp(c).getStringSet("words", emptySet())!!.toSet()

    fun addWord(c: Context, w: String) {
        val s = words(c).toMutableSet(); s.add(w.trim().lowercase())
        sp(c).edit().putStringSet("words", s).apply()
    }

    fun removeWord(c: Context, w: String) {
        val s = words(c).toMutableSet(); s.remove(w)
        sp(c).edit().putStringSet("words", s).apply()
    }

    fun lockUntil(c: Context) = sp(c).getLong("lock", 0L)
    fun locked(c: Context) = System.currentTimeMillis() < lockUntil(c)
    fun startLock(c: Context) =
        sp(c).edit().putLong("lock", System.currentTimeMillis() + LOCK_MS).apply()
    fun clearLock(c: Context) = sp(c).edit().putLong("lock", 0L).apply()

    fun safe(c: Context) = sp(c).getBoolean("safe", false)
    fun setSafe(c: Context, v: Boolean) = sp(c).edit().putBoolean("safe", v).apply()

    // অ্যাডমিন Google Sheet-এ UnlockAccess ON করেছে কিনা (সার্ভার থেকে সর্বশেষ জানা মান)
    fun unlockOk(c: Context) = sp(c).getBoolean("unlock", false)
    fun setUnlock(c: Context, v: Boolean) = sp(c).edit().putBoolean("unlock", v).apply()

    // ---- Reels ব্লক: ইউজারের নিজের সুইচ ----
    fun fbReel(c: Context) = sp(c).getBoolean("fbReel", false)
    fun setFbReel(c: Context, v: Boolean) = sp(c).edit().putBoolean("fbReel", v).apply()
    fun igReel(c: Context) = sp(c).getBoolean("igReel", false)
    fun setIgReel(c: Context, v: Boolean) = sp(c).edit().putBoolean("igReel", v).apply()

    // ---- Reels অনুমতি: অ্যাডমিন Google Sheet-এ ON করলে ইউজার ব্লক বন্ধ করতে পারবে ----
    fun fbAccess(c: Context) = sp(c).getBoolean("fbAccess", false)
    fun igAccess(c: Context) = sp(c).getBoolean("igAccess", false)
    fun setReelAccess(c: Context, fb: Boolean, ig: Boolean) =
        sp(c).edit().putBoolean("fbAccess", fb).putBoolean("igAccess", ig).apply()

    // ---- ছবি ও ভিডিও সার্চ ব্লক (ব্রাউজারে) ----
    fun media(c: Context) = sp(c).getBoolean("media", false)
    fun setMedia(c: Context, v: Boolean) = sp(c).edit().putBoolean("media", v).apply()
    // অ্যাডমিন Google Sheet-এ "Image and Video Access" ON করলে ইউজার এই ব্লক বন্ধ করতে পারবে
    fun mediaAccess(c: Context) = sp(c).getBoolean("mediaAccess", false)
    fun setMediaAccess(c: Context, v: Boolean) = sp(c).edit().putBoolean("mediaAccess", v).apply()

    // সার্ভার থেকে পাওয়া সর্বশেষ অনুমতিগুলো একসাথে সেভ
    fun applyServer(c: Context, r: Api.Res) {
        setUnlock(c, r.unlock)
        setReelAccess(c, r.fbAccess, r.igAccess)
        setMediaAccess(c, r.mediaAccess)
    }

    // ব্যাটারি অনুমতি চাওয়ার সর্বশেষ সময় (বারবার বিরক্ত না করতে)
    fun battAskedAt(c: Context) = sp(c).getLong("battAsk", 0L)
    fun setBattAskedAt(c: Context, t: Long) = sp(c).edit().putLong("battAsk", t).apply()

    // ---- সার্ভার থেকে নামানো স্ক্রিন-চেনার নিয়ম ----
    fun rulesJson(c: Context) = sp(c).getString("rules", "") ?: ""
    fun setRulesJson(c: Context, v: String) = sp(c).edit().putString("rules", v).apply()
    fun rulesAt(c: Context) = sp(c).getLong("rulesAt", 0L)
    fun setRulesAt(c: Context, t: Long) = sp(c).edit().putLong("rulesAt", t).apply()
}
