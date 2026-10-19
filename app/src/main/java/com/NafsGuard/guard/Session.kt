package com.NafsGuard.guard

// অ্যাপ খোলার সময়কার সেশন (মেমরিতে থাকে; অ্যাপ থেকে বের হলে মুছে যায়)
object Session {
    @Volatile var unlocked = false   // এই খোলায় লগইন হয়েছে কিনা
    @Volatile var keepUntil = 0L     // সেটিংস/অনুমতির স্ক্রিনে যাওয়ার ছাড়, এই সময় পর্যন্ত

    // সেটিংসে গিয়ে ফিরে আসার জন্য সর্বোচ্চ ৩ মিনিট সেশন ধরে রাখা
    fun keepFor(ms: Long = 3 * 60 * 1000L) {
        keepUntil = System.currentTimeMillis() + ms
    }
}
