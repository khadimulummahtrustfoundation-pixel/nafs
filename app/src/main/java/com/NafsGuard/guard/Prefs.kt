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

    fun dnsAuto(c: Context) = sp(c).getBoolean("dnsauto", false)
    fun setDnsAuto(c: Context, v: Boolean) = sp(c).edit().putBoolean("dnsauto", v).apply()

    fun safe(c: Context) = sp(c).getBoolean("safe", false)
    fun setSafe(c: Context, v: Boolean) = sp(c).edit().putBoolean("safe", v).apply()
}
