package com.NafsGuard.guard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Terabox / Telegram / WhatsApp / t.co / Messenger-গ্রুপ লিংক ব্লক।
 * - hasBlockedLink : লেখার ভেতরে এই ধরনের লিংক আছে কি না
 * - stripLinks     : লেখা থেকে শুধু ওই লিংকগুলো বাদ দেয়
 * - isBlockedHost  : DNS-এ ডোমেইন ব্লক করার জন্য (DnsVpnService ব্যবহার করে)
 * - guardClipboard : কপি করা লিংক ক্লিপবোর্ড থেকে মুছে ফেলে
 *
 * নতুন ডোমেইন ব্লক করতে শুধু নিচের domains তালিকায় যোগ করুন।
 * দ্রষ্টব্য: bare "whatsapp.com" ইচ্ছা করে নেই, যাতে WhatsApp অ্যাপের নিজের কাজ না ভাঙে।
 */
object LinkBlock {
    private val domains = listOf(
        // Telegram
        "t.me", "telegram.me", "telegram.dog", "telegram.org", "telesco.pe", "telegra.ph",
        // WhatsApp
        "wa.me", "wa.link", "chat.whatsapp.com", "api.whatsapp.com", "web.whatsapp.com",
        // Terabox (আরও অনেক মিরর আছে, নিচে "terabox" শব্দ ধরেও ব্লক হয়)
        "terabox.com", "terabox.app", "teraboxapp.com", "1024terabox.com", "1024tera.com",
        "terafileshare.com", "teraboxlink.com", "4funbox.com", "4funbox.co", "mirrobox.com",
        "nephobox.com", "momerybox.com", "tibibox.com",
        // নির্দিষ্ট ব্লগ ও t.co শর্টলিংক (X/Twitter রিডাইরেক্ট)
        "aappllee0.blogspot.com", "t.co",
        // Messenger গ্রুপ ইনভাইট লিংক (m.me/j/..., m.me/join/...)
        "m.me",
        // লিংক-শর্টনার ও বিজ্ঞাপন-রিডাইরেক্ট (ক্লিক করলে একাধিক অ্যাড পেজ ঘুরে আসল গন্তব্যে নেয়)
        "bit.ly", "tinyurl.com", "cutt.ly", "is.gd", "v.gd", "rb.gy", "shorturl.at", "shorturl.asia",
        "t.ly", "ow.ly", "buff.ly", "rebrand.ly", "bl.ink", "s.id", "tiny.cc", "lnkd.in", "soo.gd", "qr.ae",
        "adf.ly", "ouo.io", "ouo.press", "linkvertise.com", "link-to.net", "shrinkme.io", "shrinkearn.com",
        "exe.io", "exe.app", "fc.lc", "bc.vc", "shorte.st", "sh.st", "adfoc.us", "za.gl", "cutwin.com",
        "clk.sh", "droplink.co", "gplinks.co", "gplinks.in", "tii.la", "tnshort.net", "urlshortx.com",
        "shareus.io", "ez4short.com", "linkshortify.com", "oke.io", "tpi.li", "mboost.me", "dz4link.com"
    )

    private val re = Regex(
        "(?<![a-z0-9-])(?:[a-z0-9-]+\\.)*(?:" + domains.joinToString("|") { Regex.escape(it) } + ")(?![a-z0-9-])" +
            "|(?<![a-z0-9-])[a-z0-9-]*terabox[a-z0-9-]*\\.[a-z]{2,}" +
            "|(?:tg|whatsapp)://" +
            // facebook.com/messages/join/..., messenger.com/join/... ইত্যাদি গ্রুপ-জয়েন লিংক
            "|(?<![a-z0-9-])(?:[a-z0-9-]+\\.)*(?:facebook|messenger|fb)\\.(?:com|me)/(?:messages/)?(?:join|j)/",
        RegexOption.IGNORE_CASE
    )


    // ---------- Facebook/Instagram/Messenger থেকে বাইরের লিংক: শুধু অনুমোদিতগুলো খুলবে ----------
    /** এই হোস্ট (ও তার সাবডোমেইন) সবসময় চলবে। নতুন সাইট চালু রাখতে এখানে যোগ করুন */
    private val allowHosts = listOf(
        "forms.gle", "forms.office.com",                       // ফর্ম
        "bdjobs.com", "linkedin.com", "indeed.com", "glassdoor.com", "chakri.com", // চাকরির সাইট
        "gov.bd", "gov", "mil.bd", "police.gov.bd"              // সরকারি
    )
    /** docs.google.com / google.com-এর শুধু /forms পাথ চলবে */
    private val googleHosts = setOf("docs.google.com", "google.com", "www.google.com")
    /** Meta-র নিজের ডোমেইন; এগুলো "বাইরের লিংক" নয় */
    private val metaHosts = listOf(
        "facebook.com", "fb.com", "fb.me", "instagram.com", "messenger.com", "meta.com",
        "fbcdn.net", "cdninstagram.com", "whatsapp.net", "facebook.net"
    )
    private fun hostIn(h: String, list: List<String>) = list.any { h == it || h.endsWith(".$it") }

    private val urlToken = Regex(
        "(?<![a-z0-9@._-])(?:https?://)?((?:[a-z0-9-]+\\.)+(?:com|net|org|info|xyz|top|site|online|live|club|link|click|me|co|io|bd|gov|edu|app|cc|tv|ly|to|in|us|uk|pk|page|shop|store|world|win|vip|gle|ink|lol|fun|buzz|cyou|icu|today|work|rest|cam|sex|xxx|porn|adult|tube|bid|men|date|loan|stream|gq|tk|ml|ga|cf))(/[^\\s]*)?",
        RegexOption.IGNORE_CASE
    )
    private val wrapU = Regex("[?&]u=([^&\\s]+)", RegexOption.IGNORE_CASE)

    private fun hostPathAllowed(host0: String, path: String): Boolean {
        val h = host0.lowercase().trimEnd('.')
        if (hostIn(h, allowHosts)) return true
        if (h in googleHosts && path.lowercase().startsWith("/forms")) return true
        return false
    }

    /** লেখায় এমন বাইরের লিংক আছে কি না যা অনুমোদিত নয় (Meta-র নিজের লিংক ধরা হয় না) */
    fun externalNotAllowed(text: String): Boolean {
        if (text.isBlank()) return false
        val t = text.take(20000)
        for (m in urlToken.findAll(t)) {
            val host = m.groupValues[1].lowercase().trimEnd('.')
            val path = m.groupValues.getOrElse(2) { "" }
            if (hostIn(host, metaHosts)) {
                // l.facebook.com/l.php?u=<গন্তব্য> হলে গন্তব্য দেখে সিদ্ধান্ত
                val u = wrapU.find(path)?.groupValues?.get(1)
                if (u != null) {
                    try {
                        val dest = java.net.URLDecoder.decode(u, "UTF-8")
                        val dm = urlToken.find(dest)
                        if (dm != null) {
                            val dh = dm.groupValues[1].lowercase().trimEnd('.')
                            if (!hostIn(dh, metaHosts) && !hostPathAllowed(dh, dm.groupValues.getOrElse(2) { "" })) return true
                        } else return true
                    } catch (e: Throwable) { return true }
                }
                continue
            }
            if (!hostPathAllowed(host, path)) return true
        }
        return false
    }

    private val pctEsc = Regex("%[0-9a-fA-F]{2}")

    fun hasBlockedLink(text: String): Boolean {
        if (text.isBlank()) return false
        // l.facebook.com/l.php?u=https%3A%2F%2Ft.co%2F... ধরনের এনকোড করা লিংকেও ধরতে %XX কে ফাঁকা করে নিই
        return re.containsMatchIn(text.take(40000).replace(pctEsc, " "))
    }

    /** space দিয়ে আলাদা প্রতিটা অংশ দেখে, যেটায় ব্লক-লিংক আছে সেটা মুছে দেয় */
    fun stripLinks(text: String): String =
        Regex("\\S+").replace(text) { if (hasBlockedLink(it.value)) "" else it.value }

    fun isBlockedHost(host0: String): Boolean {
        val h = host0.lowercase().trimEnd('.')
        if (h.isEmpty()) return false
        if (h.contains("terabox")) return true
        return domains.any { h == it || h.endsWith(".$it") }
    }

    private fun cm(c: Context) = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun clipText(c: Context): String {
        return try {
            val clip = cm(c).primaryClip ?: return ""
            val sb = StringBuilder()
            for (i in 0 until clip.itemCount) sb.append(clip.getItemAt(i).coerceToText(c)).append('\n')
            sb.toString()
        } catch (e: Throwable) { "" }
    }

    fun wipeClipboard(c: Context) {
        try {
            if (Build.VERSION.SDK_INT >= 28) cm(c).clearPrimaryClip()
            else cm(c).setPrimaryClip(ClipData.newPlainText("", ""))
        } catch (e: Throwable) {}
    }

    /**
     * ক্লিপবোর্ডে ব্লক-লিংক থাকলে মুছে দেয়।
     * Android 10+ এ ব্যাকগ্রাউন্ড থেকে ক্লিপবোর্ড পড়া নাও যেতে পারে (তখন লেখা খালি আসে)।
     * unsure=true হলে (ইউজার "Copy" চাপল আর স্ক্রিনে ব্লক-লিংক ছিল) পড়তে না পারলেও মুছে দেয়।
     */
    fun guardClipboard(c: Context, unsure: Boolean): Boolean {
        val t = clipText(c)
        val bad = if (t.isNotBlank()) hasBlockedLink(t) else unsure
        if (bad) wipeClipboard(c)
        return bad
    }
}
