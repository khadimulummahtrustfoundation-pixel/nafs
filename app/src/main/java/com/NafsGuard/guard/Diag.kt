package com.NafsGuard.guard

/** পরীক্ষার জন্য: ছবি/লেখা পাহারা কেন কাজ করছে বা করছে না, সেটা হোমে দেখানোর তথ্য */
object Diag {
    @Volatile var loopAt = 0L          // স্ক্যান লুপ সর্বশেষ কখন চলেছে
    @Volatile var pkg = ""             // সর্বশেষ দেখা (NafsGuard ছাড়া) অ্যাপ
    @Volatile var watched = false      // সেটা পাহারার তালিকায় আছে কিনা
    @Volatile var rootNull = 0         // স্ক্রিনের তথ্য পাওয়া যায়নি এমন বার
    @Volatile var textNodes = 0        // সর্বশেষ পেজে কতগুলো লেখার অংশ পাওয়া গেছে
    @Volatile var textChars = 0
    @Volatile var textBad = -1         // সর্বশেষ পেজে খারাপ শব্দের সংখ্যা
    @Volatile var textAt = 0L
    @Volatile var model = "এখনো চেষ্টা হয়নি"
    @Volatile var shot = "এখনো চেষ্টা হয়নি"
    @Volatile var score = -1f
    @Volatile var shotAt = 0L

    fun shotError(code: Int) = when (code) {
        1 -> "ব্যর্থ: ভেতরের ত্রুটি"
        2 -> "ব্যর্থ: স্ক্রিনশটের অনুমতি নেই (গার্ড বন্ধ করে আবার চালু করুন)"
        3 -> "ব্যর্থ: খুব তাড়াতাড়ি চেষ্টা"
        4 -> "ব্যর্থ: ডিসপ্লে ঠিক নেই"
        5 -> "ব্যর্থ: উইন্ডো ঠিক নেই"
        6 -> "ব্যর্থ: অ্যাপটি স্ক্রিনশট আটকাচ্ছে (secure)"
        else -> "ব্যর্থ: কোড $code"
    }
}
