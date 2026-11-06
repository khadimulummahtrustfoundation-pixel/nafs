package com.NafsGuard.guard

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

// নোটিশ ডেটা ক্লাস
data class NoticeItem(
    val title: String,
    val body: String,
    val timestamp: Long
)

object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("nafsguard", Context.MODE_PRIVATE)

    // ---- রেজিস্ট্রেশন ও অনুমোদন (লগইন নেই; অনুমোদন আসে অ্যাডমিনের Push Command থেকে) ----
    // loggedIn = অ্যাডমিন অনুমোদন দিয়েছেন (আগের নামই রাখা হয়েছে যাতে GuardService বদলাতে না হয়)
    fun loggedIn(c: Context) = sp(c).getBoolean("in", false)
    fun registered(c: Context) = sp(c).getBoolean("reg", false)
    fun phone(c: Context) = sp(c).getString("phone", "") ?: ""
    fun name(c: Context) = sp(c).getString("name", "") ?: ""

    fun setRegistered(c: Context, phone: String, name: String) =
        sp(c).edit().putBoolean("reg", true).putString("phone", phone).putString("name", name).apply()

    // অনুমোদন + মেয়াদ (মেয়াদ epoch millis; serverNow = Push পাঠানোর সময় সার্ভারের সময়)
    fun approve(c: Context, expiryAt: Long, serverNow: Long) {
        sp(c).edit().putBoolean("in", true).putLong("expiry", expiryAt).apply()
        anchor(c, serverNow)
    }

    // অনুমোদন প্রত্যাহার (Access.disableAll এখান দিয়ে যায়)
    fun logout(c: Context) = sp(c).edit().putBoolean("in", false).apply()

    // ---- মেয়াদ ও নির্ভরযোগ্য সময় ----
    // ফোনের ঘড়ি বদলালেও যেন মেয়াদ ফাঁকি দেওয়া না যায়: সার্ভারের সময়ের সাথে elapsedRealtime জুড়ে গণনা
    @Volatile private var bootCache = Int.MIN_VALUE
    private fun bootCount(c: Context): Int {
        if (bootCache != Int.MIN_VALUE) return bootCache
        bootCache = try { Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1) } catch (e: Exception) { -1 }
        return bootCache
    }

    private fun anchor(c: Context, serverNow: Long) {
        val t = if (serverNow > 0) serverNow else now(c)
        sp(c).edit().putLong("tAnchor", t).putLong("tElapsed", SystemClock.elapsedRealtime())
            .putInt("tBoot", bootCount(c)).putLong("tOffset", t - System.currentTimeMillis()).apply()
    }

    /** সার্ভারের সময়ের কাছাকাছি বর্তমান সময় (ফোনের ঘড়ি বদলালেও ঠিক থাকে) */
    fun now(c: Context): Long {
        val p = sp(c)
        val a = p.getLong("tAnchor", 0L)
        if (a == 0L) return System.currentTimeMillis()
        val el = SystemClock.elapsedRealtime()
        val e0 = p.getLong("tElapsed", 0L)
        if (p.getInt("tBoot", -1) == bootCount(c) && el >= e0) return a + (el - e0)
        // ফোন রিস্টার্ট হয়েছে: ঘড়ি + আগের পার্থক্য দিয়ে নতুন করে অ্যাঙ্কর
        val t = System.currentTimeMillis() + p.getLong("tOffset", 0L)
        sp(c).edit().putLong("tAnchor", t).putLong("tElapsed", el)
            .putInt("tBoot", bootCount(c)).apply()
        return t
    }

    fun expiryAt(c: Context) = sp(c).getLong("expiry", 0L)
    fun setExpiry(c: Context, expiryAt: Long, serverNow: Long) {
        sp(c).edit().putLong("expiry", expiryAt).apply()
        if (serverNow > 0) anchor(c, serverNow)
    }

    // ---- অফলাইন পাসওয়ার্ড (ঐচ্ছিক; শুধু ফোনে hash করে রাখা হয়) ----
    fun pinHash(c: Context) = sp(c).getString("pinHash", "") ?: ""
    fun pinSalt(c: Context) = sp(c).getString("pinSalt", "") ?: ""
    fun setPin(c: Context, hash: String, salt: String) =
        sp(c).edit().putString("pinHash", hash).putString("pinSalt", salt).apply()
    fun clearPin(c: Context) = sp(c).edit().remove("pinHash").remove("pinSalt").apply()

    // ---- অ্যাপ আপডেট (সার্ভারের D1 থেকে দিনে একবার জানা) ----
    fun latestCode(c: Context) = sp(c).getInt("latestCode", 0)
    fun latestName(c: Context) = sp(c).getString("latestName", "") ?: ""
    fun updUrl(c: Context) = sp(c).getString("updUrl", "") ?: ""
    fun setUpdate(c: Context, code: Int, name: String, url: String) =
        sp(c).edit().putInt("latestCode", code).putString("latestName", name).putString("updUrl", url).apply()
    fun updNotified(c: Context) = sp(c).getInt("updNotifiedCode", 0)
    fun setUpdNotified(c: Context, code: Int) = sp(c).edit().putInt("updNotifiedCode", code).apply()

    // ---- Push কমান্ডের পুনরাবৃত্তি ঠেকানো ----
    fun markSeen(c: Context, id: String): Boolean {
        val l = (sp(c).getString("seen", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (id in l) return false
        l.add(id)
        while (l.size > 40) l.removeAt(0)
        sp(c).edit().putString("seen", l.joinToString(",")).apply()
        return true
    }

    // ---- ইন-অ্যাপ নোটিশ ও বার্তা সেভ এবং রিড করার ফাংশন ----
    private const val KEY_NOTICES = "admin_notices"

    fun getNotices(context: Context): List<NoticeItem> {
        val jsonStr = sp(context).getString(KEY_NOTICES, "[]") ?: "[]"
        val list = mutableListOf<NoticeItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    NoticeItem(
                        title = obj.optString("title", "NafsGuard নোটিশ"),
                        body = obj.optString("body", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveNotice(context: Context, title: String, body: String, timestamp: Long) {
        val notices = getNotices(context).toMutableList()
        notices.add(0, NoticeItem(title, body, timestamp)) // নতুন নোটিশ উপরে যোগ হবে
        
        // সর্বোচ্চ ৫০টি নোটিশ সেভ রাখবে
        if (notices.size > 50) {
            notices.removeAt(notices.size - 1)
        }

        val arr = JSONArray()
        for (item in notices) {
            val obj = JSONObject().apply {
                put("title", item.title)
                put("body", item.body)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        
        sp(context).edit().putString(KEY_NOTICES, arr.toString()).apply()
    }

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

    // ---- মেইন লক: মেয়াদ শেষের সময় অ্যাডমিনের পাঠানো মেয়াদ (expiry) থেকে আসে ----
    fun lockOn(c: Context) = sp(c).getBoolean("lockOn", false)
    fun lockUntil(c: Context) = if (lockOn(c)) expiryAt(c) else 0L
    fun locked(c: Context) = lockOn(c) && expiryAt(c) > 0L && now(c) < expiryAt(c)
    fun startLock(c: Context) = sp(c).edit().putBoolean("lockOn", true).apply()
    fun clearLock(c: Context) = sp(c).edit().putBoolean("lockOn", false).apply()

    fun safe(c: Context) = sp(c).getBoolean("safe", false)
    fun setSafe(c: Context, v: Boolean) = sp(c).edit().putBoolean("safe", v).apply()

    // ---- VPN Always-on সেটিং ----
    fun vpnAlways(c: Context) = sp(c).getBoolean("vpnAlways", false)
    fun setVpnAlways(c: Context, v: Boolean) = sp(c).edit().putBoolean("vpnAlways", v).apply()

    // অ্যাডমিন সার্ভারে UnlockAccess ON করেছে কিনা (সার্ভার থেকে সর্বশেষ জানা মান)
    fun unlockOk(c: Context) = sp(c).getBoolean("unlock", false)
    fun setUnlock(c: Context, v: Boolean) = sp(c).edit().putBoolean("unlock", v).apply()

    // ---- Reels ব্লক: ইউজারের নিজের সুইচ ----
    fun fbReel(c: Context) = sp(c).getBoolean("fbReel", false)
    fun setFbReel(c: Context, v: Boolean) = sp(c).edit().putBoolean("fbReel", v).apply()
    fun igReel(c: Context) = sp(c).getBoolean("igReel", false)
    fun setIgReel(c: Context, v: Boolean) = sp(c).edit().putBoolean("igReel", v).apply()

    // ---- YouTube Shorts ব্লক: ইউজারের নিজের সুইচ (বন্ধ করার অনুমতি = শীটের YT Shorts Access) ----
    fun ytShorts(c: Context) = sp(c).getBoolean("ytShorts", false)
    fun setYtShorts(c: Context, v: Boolean) = sp(c).edit().putBoolean("ytShorts", v).apply()
    // অ্যাডমিন শীটে "YT Shorts Access" ON করলে ইউজার এই ব্লক বন্ধ করতে পারবে
    fun ytAccess(c: Context) = sp(c).getBoolean("ytAccess", false)
    fun setYtAccess(c: Context, v: Boolean) = sp(c).edit().putBoolean("ytAccess", v).apply()

    // ---- Reels অনুমতি: অ্যাডমিন সার্ভারে ON করলে ইউজার ব্লক বন্ধ করতে পারবে ----
    fun fbAccess(c: Context) = sp(c).getBoolean("fbAccess", false)
    fun igAccess(c: Context) = sp(c).getBoolean("igAccess", false)
    fun setReelAccess(c: Context, fb: Boolean, ig: Boolean) =
        sp(c).edit().putBoolean("fbAccess", fb).putBoolean("igAccess", ig).apply()

    // ---- ছবি ও ভিডিও সার্চ ব্লক (ব্রাউজারে) ----
    fun media(c: Context) = sp(c).getBoolean("media", false)
    fun setMedia(c: Context, v: Boolean) = sp(c).edit().putBoolean("media", v).apply()
    // অ্যাডমিন সার্ভারে "Image and Video Access" ON করলে ইউজার এই ব্লক বন্ধ করতে পারবে
    fun mediaAccess(c: Context) = sp(c).getBoolean("mediaAccess", false)
    fun setMediaAccess(c: Context, v: Boolean) = sp(c).edit().putBoolean("mediaAccess", v).apply()

    // সার্ভার থেকে পাওয়া সর্বশেষ অনুমতিগুলো একসাথে সেভ
    fun applyServer(c: Context, r: Api.Res) {
        setUnlock(c, r.unlock)
        setReelAccess(c, r.fbAccess, r.igAccess)
        setMediaAccess(c, r.mediaAccess)
        setYtAccess(c, r.ytAccess)
    }

    // ব্যাটারি অনুমতি চাওয়ার সর্বশেষ সময় (বারবার বিরক্ত না করতে)
    fun battAskedAt(c: Context) = sp(c).getLong("battAsk", 0L)
    fun setBattAskedAt(c: Context, t: Long) = sp(c).edit().putLong("battAsk", t).apply()

    // ---- সার্ভার থেকে নামানো স্ক্রিন-চেনার নিয়ম ----
    fun rulesJson(c: Context) = sp(c).getString("rules", "") ?: ""
    fun setRulesJson(c: Context, v: String) = sp(c).edit().putString("rules", v).apply()
    fun rulesAt(c: Context) = sp(c).getLong("rulesAt", 0L)
    fun setRulesAt(c: Context, t: Long) = sp(c).edit().putLong("rulesAt", t).apply()

    // ---- ব্ল্যাকলিস্ট: যে অ্যাপগুলোতে ঢোকা যাবে না (প্যাকেজ নামের তালিকা) ----
    fun blacklist(c: Context): Set<String> =
        sp(c).getStringSet("blacklist", emptySet())!!.toSet()

    fun addBlacklist(c: Context, pkgs: Collection<String>) {
        val s = blacklist(c).toMutableSet(); s.addAll(pkgs)
        sp(c).edit().putStringSet("blacklist", s).apply()
    }

    fun removeBlacklist(c: Context, pkg: String) {
        val s = blacklist(c).toMutableSet(); s.remove(pkg)
        sp(c).edit().putStringSet("blacklist", s).apply()
    }
}
