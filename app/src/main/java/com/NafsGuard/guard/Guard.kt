package com.NafsGuard.guard

import android.content.Context

/**
 * খারাপ শব্দ চেনা। শুধু পুরো শব্দ (বা নির্দিষ্ট রূপভেদ) মিললে ব্লক হয়,
 * শব্দের ভেতরের অংশ মিললে আর হয় না।
 */
object Guard {
    private val tokenRe = Regex("[\\p{L}\\p{M}\\p{N}]+")

    // অদৃশ্য অক্ষর (ZWJ/ZWNJ ইত্যাদি) বাদ দেওয়া হয়, নইলে বাংলা শব্দ ভেঙে ছোট টুকরো হয়ে যেত
    private val invisibleRe = Regex("[\\u200B\\u200C\\u200D\\u2060\\uFEFF\\u00AD]")

    // পুরো শব্দ হিসেবে মিললে ব্লক
    private val exact = setOf(
        "sex", "sexy", "xxx", "nsfw", "nude", "nudes", "nudity", "erotic", "erotica",
        "onlyfans", "camgirl", "camgirls", "chudai", "boobs", "blowjob", "handjob",
        "milf", "fetish", "bdsm", "orgy", "bluefilm", "sexvideo", "sexvideos", "sexchat",
        "সেক্স", "সেক্সি", "নগ্ন", "ন্যুড", "চুদাচুদি", "ব্লুফিল্ম", "পর্ন", "পর্ণ"
    )

    // শব্দের শুরু মিললে ব্লক (শুধু সাইটের নাম ও স্পষ্ট রূপভেদ)
    private val prefixes = listOf(
        "porn", "xvideo", "xnxx", "xhamster", "redtube", "brazzers", "youporn",
        "hentai", "onlyfans", "chudai", "camgirl", "পর্নো", "পর্ণো", "পর্নগ্রাফি", "পর্ণগ্রাফি"
    )

    // ইউজারের যোগ করা শব্দের পরে বসতে পারে এমন সাধারণ প্রত্যয় (শুধু ৩+ অক্ষরের শব্দে)
    private val suffixes = setOf(
        "টা", "টি", "টার", "টির", "গুলো", "গুলা", "গুলি", "খানা", "কে", "রে", "র", "ের", "এর",
        "ে", "য়", "তে", "দের", "রা", "ও", "s", "es", "ed", "ing", "er", "ers"
    )

    private fun clean(text: String) = invisibleRe.replace(text.lowercase(), "")

    private fun plain(text: String): List<String> =
        tokenRe.findAll(clean(text)).map { it.value }.toList()

    // "p o r n" ধরনের ফাঁক দিয়ে লেখা ধরতে ইংরেজি একক অক্ষরের সারি জোড়া লাগানো
    // (বাংলা বাদ, কারণ ও/এ-র মতো এক অক্ষরের বাংলা শব্দ স্বাভাবিক)
    private fun tokens(text: String): List<String> {
        val base = plain(text)
        val extra = ArrayList<String>()
        val run = StringBuilder()
        var cnt = 0
        for (t in base + "") {
            if (t.length == 1 && t[0] in 'a'..'z') {
                run.append(t); cnt++
            } else {
                if (cnt >= 3) extra.add(run.toString())
                run.setLength(0); cnt = 0
            }
        }
        return base + extra
    }

    private fun containsSeq(tk: List<String>, seq: List<String>): Boolean {
        if (seq.isEmpty() || seq.size > tk.size) return false
        for (i in 0..(tk.size - seq.size)) {
            var ok = true
            for (j in seq.indices) {
                if (tk[i + j] != seq[j]) { ok = false; break }
            }
            if (ok) return true
        }
        return false
    }

    private fun userMatch(t: String, x: String): Boolean {
        if (t == x) return true
        if (x.length < 3 || !t.startsWith(x)) return false
        return t.substring(x.length) in suffixes
    }

    /** কোন শব্দ ধরা পড়ল সেটা ফেরত দেয়; খারাপ কিছু না পেলে null */
    fun findBad(c: Context, text: String): String? {
        if (text.isBlank()) return null
        val tk = tokens(text)
        if (tk.isEmpty()) return null
        tk.firstOrNull { it in exact }?.let { return it }
        tk.firstOrNull { t -> prefixes.any { t.startsWith(it) } }?.let { return it }
        for (w in Prefs.words(c)) {
            val wt = plain(w)
            if (wt.isEmpty()) continue
            if (wt.size == 1) {
                val x = wt[0]
                tk.firstOrNull { userMatch(it, x) }?.let { return it }
            } else if (containsSeq(tk, wt)) return w
        }
        return null
    }

    fun isBad(c: Context, text: String): Boolean = findBad(c, text) != null
}
