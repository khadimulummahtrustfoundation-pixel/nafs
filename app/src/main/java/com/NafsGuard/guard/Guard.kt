package com.NafsGuard.guard

import android.content.Context

object Guard {
    // ডিফল্ট ব্লক তালিকা (ইউজার নিজের শব্দ অ্যাপ থেকে যোগ করতে পারবে)
    val defaults = listOf(
        "porn", "xxx", "nude", "naked", "nsfw", "hentai", "erotic", "onlyfans",
        "xvideos", "xnxx", "pornhub", "xhamster", "redtube", "brazzers", "youporn",
        "sexvideo", "sex video", "bluefilm", "chudai", "camgirl", "boobs",
        "পর্ন", "পর্ণ", "সেক্স", "নগ্ন", "ন্যুড", "চুদাচুদি", "ব্লুফিল্ম"
    )

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    fun isBad(c: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val l = text.lowercase()
        val n = norm(text)
        return (defaults + Prefs.words(c)).any { w ->
            val lw = w.lowercase()
            val nw = norm(lw)
            lw.isNotBlank() && (l.contains(lw) || (nw.isNotEmpty() && n.contains(nw)))
        }
    }
}
