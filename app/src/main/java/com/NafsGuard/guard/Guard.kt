package com.NafsGuard.guard

import android.content.Context

/**
 * খারাপ শব্দ চেনা: এখন পুরো শব্দ ধরে মেলানো হয় (আগে শব্দের ভেতরের অংশ ধরত, তাই
 * "Essex" বা সাধারণ বাংলা শব্দেও ভুল করে আটকে যেত)।
 */
object Guard {
    private val tokenRe = Regex("[\\p{L}\\p{M}\\p{N}]+")

    // পুরো শব্দ হিসেবে মিললে ব্লক
    private val exact = setOf(
        "sex", "sexy", "xxx", "nsfw", "nude", "nudes", "nudity", "erotic", "erotica",
        "onlyfans", "camgirl", "camgirls", "chudai", "boobs", "blowjob", "handjob",
        "milf", "fetish", "bdsm", "orgy", "bluefilm", "sexvideo", "sexvideos", "sexchat",
        "সেক্স", "সেক্সি", "নগ্ন", "ন্যুড", "চুদাচুদি", "ব্লুফিল্ম"
    )

    // শব্দের শুরু মিললে ব্লক (সাইটের নাম ও রূপভেদ ধরার জন্য, যেমন pornhub, pornography)
    private val prefixes = listOf(
        "porn", "xvideo", "xnxx", "xhamster", "redtube", "brazzers", "youporn",
        "hentai", "onlyfans", "chudai", "camgirl", "পর্ন", "পর্ণ"
    )

    // অতি-সাধারণ বাংলা/ইংরেজি শব্দ যা ভুলবশত ব্লক-তালিকায় যোগ হলে স্বাভাবিক লেখাও
    // ব্লক হয়ে যায় (যেমন "পারে", "না", "আছে")। এই শব্দগুলো নিজের ব্লক-তালিকায়
    // যোগ করতে দেওয়া হয় না।
    private val commonWords = setOf(
        "না", "হয়", "হবে", "হবো", "করে", "করি", "করব", "করবে", "পারে", "পারি",
        "পারব", "পারবে", "আছে", "নেই", "থেকে", "সাথে", "দিয়ে", "নিয়ে", "কিন্তু",
        "এবং", "অথবা", "তাই", "যদি", "তবে", "এখন", "তখন", "আমি", "তুমি", "সে",
        "আমরা", "তোমরা", "তারা", "এই", "ওই", "সেই", "যে", "কি", "কেন", "কীভাবে",
        "the", "and", "for", "are", "but", "not", "you", "all", "can", "has",
        "was", "were", "with", "that", "this", "from", "have", "will"
    )

    fun isCommonWord(w: String): Boolean = plain(w).let { it.size == 1 && it[0] in commonWords }

    private fun plain(text: String): List<String> =
        tokenRe.findAll(text.lowercase()).map { it.value }.toList()

    // "p o r n" ধরনের ফাঁক দিয়ে লেখা ধরতে একক অক্ষরের সারি জোড়া লাগানো
    private fun tokens(text: String): List<String> {
        val base = plain(text)
        val extra = ArrayList<String>()
        val run = StringBuilder()
        var cnt = 0
        for (t in base + "") {
            if (t.length == 1) {
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

    private fun tokenBad(c: Context, t: String, custom: Set<String>): Boolean {
        if (t in exact) return true
        if (prefixes.any { t.startsWith(it) }) return true
        for (w in custom) {
            val wt = plain(w)
            if (wt.size == 1) {
                val x = wt[0]
                if (t == x || (x.length >= 5 && t.startsWith(x))) return true
            }
        }
        return false
    }

    /** পেজের লেখায় কতবার খারাপ শব্দ আছে (পেজ স্ক্যানের জন্য) */
    fun badCount(c: Context, text: String): Int {
        if (text.isBlank()) return 0
        val tk = tokens(text)
        if (tk.isEmpty()) return 0
        val custom = Prefs.words(c)
        var n = tk.count { tokenBad(c, it, custom) }
        for (w in custom) {
            val wt = plain(w)
            if (wt.size > 1 && containsSeq(tk, wt)) n++
        }
        return n
    }

    /**
     * টেক্সট বক্সের জন্য: পুরো লেখা না মুছে শুধু খারাপ শব্দ(গুলো) বাদ দিয়ে বাকি লেখা ফেরত দেয়।
     * "p o r n" ধরনের ফাঁকা-অক্ষর এবং একাধিক শব্দের ব্লক-বাক্যও সরানো হয়।
     */
    fun removeBad(c: Context, text: String): String {
        val ms = tokenRe.findAll(text).toList()
        if (ms.isEmpty()) return text
        val custom = Prefs.words(c)
        val del = BooleanArray(ms.size)

        for (i in ms.indices) if (tokenBad(c, ms[i].value.lowercase(), custom)) del[i] = true

        // একাধিক শব্দের ব্লক-বাক্য
        for (w in custom) {
            val wt = plain(w)
            if (wt.size < 2) continue
            for (i in 0..(ms.size - wt.size)) {
                var ok = true
                for (j in wt.indices) if (ms[i + j].value.lowercase() != wt[j]) { ok = false; break }
                if (ok) for (j in wt.indices) del[i + j] = true
            }
        }

        // ফাঁক দিয়ে লেখা একক অক্ষরের সারি (যেমন "p o r n")
        var i = 0
        while (i < ms.size) {
            if (ms[i].value.length == 1) {
                var j = i
                while (j + 1 < ms.size && ms[j + 1].value.length == 1 &&
                    text.substring(ms[j].range.last + 1, ms[j + 1].range.first).isBlank()) j++
                if (j - i + 1 >= 3) {
                    val joined = (i..j).joinToString("") { ms[it].value.lowercase() }
                    if (tokenBad(c, joined, custom)) for (k in i..j) del[k] = true
                }
                i = j + 1
            } else i++
        }

        if (del.none { it }) return text
        val sb = StringBuilder()
        var pos = 0
        for (k in ms.indices) {
            if (!del[k]) continue
            sb.append(text, pos, ms[k].range.first)
            pos = ms[k].range.last + 1
            // শব্দ সরানোর পর দুই ফাঁক পাশাপাশি হলে একটা বাদ
            if (sb.isNotEmpty() && sb.last().isWhitespace() && pos < text.length && text[pos].isWhitespace()) pos++
        }
        sb.append(text, pos, text.length)
        return sb.toString()
    }

    fun isBad(c: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val tk = tokens(text)
        if (tk.isEmpty()) return false
        if (tk.any { it in exact }) return true
        if (tk.any { t -> prefixes.any { t.startsWith(it) } }) return true
        // ইউজারের নিজের যোগ করা শব্দ: পুরো শব্দ মিললে (৫+ অক্ষরের হলে শুরু মিললেও)
        for (w in Prefs.words(c)) {
            val wt = plain(w)
            if (wt.isEmpty()) continue
            if (wt.size == 1) {
                val x = wt[0]
                if (tk.any { it == x || (x.length >= 5 && it.startsWith(x)) }) return true
            } else if (containsSeq(tk, wt)) return true
        }
        return false
    }
}
