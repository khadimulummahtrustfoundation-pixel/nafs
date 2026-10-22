package com.NafsGuard.guard

/** পরীক্ষার জন্য: পেজের লেখা পাহারা কেন কাজ করছে বা করছে না, সেটা হোমে দেখানোর তথ্য */
object Diag {
    @Volatile var show = false         // স্ট্যাটাস কার্ড দেখানো হবে কিনা (লোগোর নামে ৫ বার চাপলে)
    @Volatile var loopAt = 0L          // স্ক্যান লুপ সর্বশেষ কখন চলেছে
    @Volatile var pkg = ""             // সর্বশেষ দেখা (NafsGuard ছাড়া) অ্যাপ
    @Volatile var watched = false      // সেই অ্যাপে পাহারা চলছে কিনা
    @Volatile var rootNull = 0         // স্ক্রিনের তথ্য পাওয়া যায়নি এমন বার
    @Volatile var textNodes = 0        // সর্বশেষ পেজে কতগুলো লেখার অংশ পাওয়া গেছে
    @Volatile var textChars = 0
    @Volatile var textBad = -1         // সর্বশেষ পেজে খারাপ শব্দের সংখ্যা
    @Volatile var textAt = 0L
    @Volatile var reelPkg = ""         // Reels পাহারায় সর্বশেষ দেখা Facebook/Instagram অ্যাপ
    @Volatile var reelHit = "-"        // কোন সংকেত মিলে Reels ধরা পড়েছে
    @Volatile var reelAt = 0L
    @Volatile var reelWin = ""         // Facebook/Instagram-এর সর্বশেষ স্ক্রিনের ক্লাস নাম
    @Volatile var reelDump = ""        // স্ক্রিনে দেখা id/লেখার নমুনা (সংকেত ঠিক করার জন্য)
}
