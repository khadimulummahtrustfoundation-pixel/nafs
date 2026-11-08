package com.NafsGuard.guard

import android.content.Context

/**
 * প্রতিটা অ্যাপের স্ক্রিন চেনার নিয়ম (id ও লেখা)।
 * নিয়মগুলো পুরোপুরি অ্যাপের ভেতরে (নিচের defaults)। সার্ভার বা ডাটাবেজ থেকে কিছু নামানো হয় না।
 * Facebook/Instagram আপডেট হয়ে নিয়ম বদলাতে হলে এই ফাইল বদলিয়ে নতুন APK দিতে হবে।
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

    fun forPkg(c: Context, pkg: String): R? = defaults[pkg]
}
