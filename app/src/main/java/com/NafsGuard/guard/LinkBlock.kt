package com.NafsGuard.guard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Terabox / Telegram / WhatsApp লিংক ব্লক।
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
        "nephobox.com", "momerybox.com", "tibibox.com"
    )

    private val re = Regex(
        "(?<![a-z0-9-])(?:[a-z0-9-]+\\.)*(?:" + domains.joinToString("|") { Regex.escape(it) } + ")(?![a-z0-9-])" +
            "|(?<![a-z0-9-])[a-z0-9-]*terabox[a-z0-9-]*\\.[a-z]{2,}" +
            "|(?:tg|whatsapp)://",
        RegexOption.IGNORE_CASE
    )

    fun hasBlockedLink(text: String): Boolean {
        if (text.isBlank()) return false
        return re.containsMatchIn(text.take(4000))
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
