package com.NafsGuard.guard

// অ্যাপ খোলার সময়কার সেশন (মেমরিতে থাকে; অ্যাপ থেকে বের হলে মুছে যায়)
object Session {
    @Volatile var unlocked = false   // এই খোলায় লগইন হয়েছে কিনা
    @Volatile var keep = false       // সেটিংস/অনুমতির স্ক্রিনে যাওয়ার সময় সেশন ধরে রাখা
}
