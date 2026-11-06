package com.NafsGuard.guard

import android.content.Context
import org.json.JSONObject

/**
 * প্রতিটা অ্যাপের স্ক্রিন চেনার নিয়ম (id ও লেখা)।
 * ডিফল্ট নিয়ম কোডে আছে। Facebook/Instagram আপডেট হলে অ্যাপ না বদলিয়ে Google Sheet-এর "Rules" ট্যাব থেকে নিয়ম বদলানো যায়
 * (অ্যাপ প্রতি ৬ ঘণ্টায় নতুন নিয়ম নামিয়ে নেয়)।
 *
 * reelIds  : এই id-র ভিউ স্ক্রিনে দেখা গেলে Reels প্লেয়ার (ব্লক)
 * skipIds  : এই id দেখা গেলে এটা স্টোরি/ডিএম-এর রিল, কখনো ব্লক হবে না (reelIds-এর চেয়ে আগে দেখা হয়)
 * reelDesc : এই লেখা দিয়ে শুরু হওয়া content-description/লেখা দেখা গেলে Reels প্লেয়ার (ব্লক)
 */
object Rules {
    class R(val reelIds: List<String>, val skipIds: List<String>, val reelDesc: List<String>)

    private val defaults: Map<String, R> = mapOf(
        "com.instagram.android" to R(
            reelIds = listOf("like_button"),
            skipIds = listOf("reel_viewer_title", "reply_bar_edittext"),
            reelDesc = emptyList()
        ),
        "com.facebook.katana" to R(
            reelIds = emptyList(),
            skipIds = emptyList(),
            // Reels প্লেয়ারের নিচের কমেন্ট বারের লেখা (বিভিন্ন ভাষায়)
            reelDesc = listOf("add a comment", "adiciona um comentário", "añade un comentario", "agrega un comentario",
                "ajoutez un commentaire", "kommentieren", "добавьте комментарий", "yorum ekle", "dodaj komentarz",
                "tilføj en kommentar", "hozzászólás írása", "aggiungi un commento", "コメントを追加",
                "lägg till en kommentar", "মন্তব্য যোগ করুন", "একটি মন্তব্য যোগ করুন")
        )
    )

    @Volatile private var cache: Map<String, R>? = null

    private fun list(a: org.json.JSONArray?): List<String> {
        val out = ArrayList<String>()
        if (a != null) for (i in 0 until a.length()) out.add(a.optString(i).lowercase())
        return out
    }

    private fun load(c: Context): Map<String, R> {
        val m = HashMap<String, R>(defaults)
        try {
            val s = Prefs.rulesJson(c)
            if (s.isNotEmpty()) {
                val j = JSONObject(s)
                val it = j.keys()
                while (it.hasNext()) {
                    val pk = it.next()
                    val o = j.optJSONObject(pk) ?: continue
                    m[pk] = R(list(o.optJSONArray("reel_ids")), list(o.optJSONArray("skip_ids")), list(o.optJSONArray("reel_desc")))
                }
            }
        } catch (e: Exception) {}
        cache = m
        return m
    }

    fun forPkg(c: Context, pkg: String): R? = (cache ?: load(c))[pkg]

    // সার্ভার থেকে নতুন নিয়ম নামানো (ব্যাকগ্রাউন্ড থ্রেড থেকে ডাকতে হবে)
    fun refresh(c: Context) {
        val s = Api.rules() ?: return
        Prefs.setRulesJson(c, s)
        Prefs.setRulesAt(c, System.currentTimeMillis())
        cache = null
    }

    fun due(c: Context) = System.currentTimeMillis() - Prefs.rulesAt(c) > 6L * 3600 * 1000
}
