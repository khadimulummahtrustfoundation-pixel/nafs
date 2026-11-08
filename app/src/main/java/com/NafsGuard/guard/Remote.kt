package com.NafsGuard.guard

import android.content.Context

/**
 * অ্যাডমিনের Push Command প্রয়োগ। সব নিয়ন্ত্রণ এখান দিয়ে; সার্ভারের D1 ডাটাবেজ শুধু ডাটা রাখে।
 *
 * কমান্ড (data["cmd"]):
 *   APPROVE     অনুমোদন + মেয়াদ (expiry = epoch ms, serverNow = পাঠানোর সময় সার্ভারের সময়)
 *   REVOKE      অ্যাক্সেস বন্ধ (সব সুরক্ষা বন্ধ, Pending পেজে ফেরা)
 *   SWITCH_OFF  target = fb | ig | yt | media  — নির্দিষ্ট সুইচ OFF
 *   LOCK_OFF    Main Lock খুলে দেওয়া
 *   SET_EXPIRY  মেয়াদ বদলানো
 *   RESET_PIN   অফলাইন পাসওয়ার্ড মুছে দেওয়া
 *   NOTICE      শুধু বার্তা (title, body)
 * সবার জন্য (topic) পাঠালে APPROVE ও RESET_PIN গ্রহণ করা হয় না।
 */
object Remote {
    /** MainActivity খোলা থাকলে স্ক্রিন নতুন করে আঁকার জন্য */
    @Volatile var onChange: Runnable? = null

    private fun last10(s: String) = s.filter { it.isDigit() }.takeLast(10)

    fun handle(c: Context, d: Map<String, String>, fromTopic: Boolean) {
        if (!Prefs.registered(c)) return
        val cmd = (d["cmd"] ?: "").trim().uppercase()
        if (cmd.isEmpty()) return

        // সরাসরি পাঠানো কমান্ডে ফোন নম্বর থাকলে নিজের নম্বরের সাথে মিলতে হবে
        val ph = d["phone"] ?: ""
        if (!fromTopic && ph.isNotEmpty() && last10(ph) != last10(Prefs.phone(c))) return
        if (fromTopic && (cmd == "APPROVE" || cmd == "RESET_PIN")) return

        val id = d["id"] ?: ""
        if (id.isNotEmpty() && !Prefs.markSeen(c, id)) return   // একই কমান্ড দুবার নয়

        val approved = Prefs.loggedIn(c)
        val serverNow = d["serverNow"]?.toLongOrNull() ?: 0L
        val expiry = d["expiry"]?.toLongOrNull() ?: 0L

        when (cmd) {
            "APPROVE" -> {
                Prefs.approve(c, expiry, serverNow)
                Push.subscribeAll()
                Push.notify(c, "NafsGuard", "অ্যাডমিন আপনাকে অনুমোদন দিয়েছেন। অ্যাপ খুলুন।")
            }
            "REVOKE" -> if (approved) {
                Access.disableAll(c)
                Push.notify(c, "NafsGuard", "অ্যাডমিন আপনার অ্যাক্সেস বন্ধ করেছেন।")
            }
            "SWITCH_OFF" -> if (approved) {
                val t = (d["target"] ?: "").lowercase()
                when (t) {
                    "fb" -> Prefs.setFbReel(c, false)
                    "ig" -> Prefs.setIgReel(c, false)
                    "yt" -> Prefs.setYtShorts(c, false)
                    "media" -> Prefs.setMedia(c, false)
                    "link" -> Prefs.setLinkBlock(c, false)
                    else -> return
                }
                val nm = mapOf("fb" to "Facebook Reels", "ig" to "Instagram Reels",
                    "yt" to "YouTube Shorts", "media" to "ছবি ও ভিডিও সার্চ", "link" to "লিংক")[t] ?: t
                Push.notify(c, "NafsGuard", "অ্যাডমিন $nm ব্লক বন্ধ করেছেন। চাইলে আপনি আবার চালু করতে পারবেন।")
            }
            "LOCK_OFF" -> if (approved) {
                Prefs.clearLock(c)
                Push.notify(c, "NafsGuard", "অ্যাডমিন লক খুলে দিয়েছেন।")
            }
            "SET_EXPIRY" -> if (approved) {
                Prefs.setExpiry(c, expiry, serverNow)
            }
            "RESET_PIN" -> {
                Pin.clear(c)
                Push.notify(c, "NafsGuard", "আপনার অফলাইন পাসওয়ার্ড মুছে দেওয়া হয়েছে।")
            }
            "NOTICE" -> {
                Push.notify(c, d["title"] ?: "NafsGuard", d["body"] ?: "")
            }
            else -> return
        }
        onChange?.run()
    }
}
