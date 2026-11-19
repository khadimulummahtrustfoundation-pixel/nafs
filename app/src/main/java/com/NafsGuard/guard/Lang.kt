package com.NafsGuard.guard

import android.content.Context

object Lang {
    private val en = mapOf(
        "রেজিস্ট্রেশন করুন" to "Register", "ইংরেজিতে আপনার নাম" to "Your name (English)",
        "ইংরেজিতে ফোন নাম্বার" to "Phone number (English)", "শুধু ইংরেজিতে লিখুন" to "Please use English only",
        "নাম লিখুন" to "Enter your name", "সঠিক ফোন নম্বর দিন" to "Enter a valid phone number",
        "অ্যাকাউন্ট তৈরি হচ্ছে..." to "Creating account...", "প্রবেশ করুন" to "Continue",
        "পাসওয়ার্ড দিন" to "Enter password", "অফলাইন পাসওয়ার্ড" to "Offline password",
        "পাসওয়ার্ড ভুল" to "Incorrect password", "ভুলে গেলে অ্যাডমিনের সাথে যোগাযোগ করুন।" to "Contact the administrator if you forget it.",
        "অ্যাডমিনের সাথে যোগাযোগ →" to "Contact administrator →", "শুধু ইংরেজিতে লিখুন" to "Please use English only",
        "🛡️ সুরক্ষা" to "🛡️ Protection", "🚫 ব্লক" to "🚫 Block", "📝 শব্দ" to "📝 Words", "🔒 লক" to "🔒 Lock",
        "🛡️ আপনি সুরক্ষিত আছেন" to "🛡️ You are protected", "⚠️ সুরক্ষা সম্পূর্ণ হয়নি" to "⚠️ Protection incomplete",
        "সুরক্ষা সেটআপ" to "Protection Setup", "গার্ড" to "Guard", "খারাপ শব্দ ও সাইট আটকায়" to "Blocks unwanted words and websites",
        "অ্যাডমিন অ্যাপ" to "Device Admin", "সহজে আনইন্সটল ঠেকায়" to "Helps prevent easy uninstallation",
        "ব্যাটারি সেভার থেকে মুক্ত" to "Ignore Battery Optimization", "গার্ড যেন বন্ধ না হয়" to "Keep Guard running",
        "সেইফ সার্চ" to "Safe Search", "অনুপযুক্ত ফলাফল ফিল্টার করে" to "Filters inappropriate results",
        "VPN সবসময় চালু" to "Always-on VPN", "সুইচে চাপ দিয়ে NafsGuard খুঁজে চালু করুন। Android 13+ এ অপশন ধূসর থাকলে: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings。" to "Find NafsGuard in Accessibility settings and turn it on. On Android 13+, if disabled: Settings → Apps → NafsGuard → ⋮ → Allow restricted settings.",
        "আনইন্সটল ঠেকাতে NafsGuard-কে অ্যাডমিন হতে হবে।" to "NafsGuard needs device-admin permission to help prevent uninstallation.",
        "অ্যাডমিন চালু আছে" to "Device admin is enabled", "ব্যাটারি সেভার থেকে মুক্ত আছে" to "Battery optimization is disabled",
        "সেইফ সার্চ বন্ধ করা যাবে না" to "Safe Search cannot be disabled while the lock is active",
        "Reels ব্লক" to "Reels Block", "Facebook Reels" to "Facebook Reels", "Instagram Reels" to "Instagram Reels", "YouTube Shorts" to "YouTube Shorts",
        "Reels ব্লক কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।" to "Turn on Guard above for Reels blocking to work.",
        "চালু করা সুইচ শুধু অ্যাডমিন বন্ধ করতে পারবেন।" to "Only the administrator can turn off an enabled switch.",
        "ছবি ও ভিডিও সার্চ ব্লক" to "Image & Video Search Block", "ছবি ও ভিডিও সার্চ বন্ধ" to "Block image and video search",
        "ব্রাউজারে ছবি/ভিডিও সার্চ করলে পেছনে যাবে" to "Returns back when image/video search is opened in a browser",
        "এটা কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।" to "Turn on Guard above for this feature to work.",
        "লিংক ব্লক" to "Link Block", "Terabox / Telegram / WhatsApp লিংক বন্ধ" to "Block Terabox / Telegram / WhatsApp links",
        "এই লিংকে ক্লিক, কপি বা খোলা যাবে না" to "These links cannot be opened, clicked or copied",
        "ক্লিক ও কপি ব্লক কাজ করতে ওপরের \"গার্ড\" চালু থাকতে হবে।" to "Turn on Guard above for click and copy blocking to work.",
        "সবসময় চালু রাখুন" to "Keep Protection Active", "ফোন ব্যাকগ্রাউন্ডে অ্যাপ বন্ধ করে দিলে সুরক্ষা থেমে যায়। নিচের সেটিংগুলো একবার করে দিন।" to "Protection may stop if the phone closes the app in the background. Configure these settings once.",
        "১. অটো-স্টার্ট / ব্যাকগ্রাউন্ড অনুমতি দিন" to "1. Allow Auto-start / Background activity", "২. সবসময় চালু VPN (সেইফ সার্চ থামবে না)" to "2. Always-on VPN (keeps Safe Search active)",
        "৩. Recent apps এ NafsGuard এর কার্ড চেপে ধরে \"Lock\" বা পিন করুন, যাতে \"সব বন্ধ করুন\" চাপলে অ্যাপ বন্ধ না হয়।" to "3. In Recent apps, press and hold NafsGuard and choose Lock or Pin so it is not closed by Clear all.",
        "নিজের ব্লক শব্দ" to "Custom Blocked Words", "শব্দ লিখুন" to "Enter a word", "শব্দ যোগ করুন" to "Add Word", "মুছুন" to "Remove",
        "কমপক্ষে ৩ অক্ষর দিন (খুব ছোট শব্দ দিলে ভুলভাবে সাধারণ শব্দও ব্লক হয়ে যেতে পারে)" to "Enter at least 3 characters. Very short words may block normal text by mistake.",
        "এটি খুবই সাধারণ একটি শব্দ, এটা যোগ করলে স্বাভাবিক লেখাও ব্লক হয়ে যাবে, তাই যোগ করা গেল না" to "This is a common word and may block normal text, so it cannot be added.",
        "লক চালু থাকায় শব্দ মুছা যাবে না (যোগ করা যাবে)।" to "Words cannot be removed while the lock is active (you can still add words).",
        "মেইন লক" to "Main Lock", "অ্যাপ ও আনইন্সটল লক" to "App & Uninstall Lock", "মেয়াদ থাকা পর্যন্ত আনইন্সটল, গার্ড বন্ধ ও ফ্যাক্টরি রিসেট করা যাবে না" to "Prevents uninstalling, disabling Guard and factory reset during the active period",
        "লক শুধু অ্যাডমিন খুলে দিতে পারবেন বা মেয়াদ শেষে খুলবে" to "Only the administrator can unlock it, or it unlocks when the period ends",
        "অ্যাডমিন বন্ধ করুন (আনইন্সটলের জন্য)" to "Disable Device Admin (to uninstall)", "বার্তা ও নোটিশ" to "Messages & Notices",
        "বার্তা ৭ দিন পর নিজে থেকে মুছে যায়।" to "Messages are automatically deleted after 7 days.", "এই মুহূর্তে কোনো বার্তা নেই।" to "There are no messages right now.", "বন্ধ করুন" to "Close",
        "অ্যাপ সংস্করণ:" to "App version:", "সুরক্ষা সম্পূর্ণ হয়নি" to "Protection is incomplete", "নিচের সুইচগুলো চালু করুন।" to "Turn on the switches below.",
        "নিরাপদ ও পরিচ্ছন্ন অনলাইন অভিজ্ঞতা উপভোগ করুন。" to "Enjoy a safer and cleaner online experience.",
        "১. অটো-স্টার্ট / ব্যাকগ্রাউন্ড অনুমতি দিন" to "1. Allow Auto-start / Background activity", "স্ট্যাটাস রিফ্রেশ" to "Refresh status",
        "এখনই ভার্সন চেক করুন" to "Check for updates now", "চেক হচ্ছে..." to "Checking...", "ঠিক আছে" to "OK",
        "কখনো না" to "Never", "হ্যাঁ" to "Yes", "না" to "No",
        "🛡️ সুরক্ষা" to "🛡️ Protection", "🚫 ব্লক" to "🚫 Block", "📝 শব্দ" to "📝 Words", "🔒 লক" to "🔒 Lock",
        "VPN সবসময় চালু" to "Always-on VPN", "সেইফ সার্চ কেউ বন্ধ করতে পারবে না" to "Prevents Safe Search from being turned off",
        "Reels খুললেই নিজে থেকে পেছনে যাবে" to "Automatically returns when Reels opens", "Shorts খুললেই নিজে থেকে পেছনে যাবে" to "Automatically returns when Shorts opens",
        "Facebook Reels ব্লক" to "Block Facebook Reels", "Instagram Reels ব্লক" to "Block Instagram Reels", "YouTube Shorts ব্লক" to "Block YouTube Shorts",
        "সুইচ চালু করলে Facebook/Instagram-এ Reels আর YouTube-এ Shorts খুললেই NafsGuard পেছনে পাঠিয়ে দেবে।" to "When enabled, NafsGuard returns to the previous screen when Reels on Facebook/Instagram or Shorts on YouTube are opened.",
        "ব্রাউজারে ছবি/ভিডিও সার্চ করলে পেছনে যাবে" to "Returns back when image/video search opens in a browser",
        "এই লিংকে ক্লিক, কপি বা খোলা যাবে না" to "These links cannot be clicked, copied or opened",
        "চালু করলে Chrome, Firefox ইত্যাদি ব্রাউজারে Google/Bing/DuckDuckGo এর Images ও Videos সার্চ খুলবে না।" to "Blocks Images and Videos search on Google, Bing and DuckDuckGo in browsers such as Chrome and Firefox.",
        "চালু করলে এই তিন সাইটের লিংক ব্রাউজারে খুলবে না, ক্লিক করলে ব্লক হবে, আর কপি বা পেস্ট করা যাবে না। এটা শুধু আপনার ফোনেই চালু হবে।" to "Blocks links from these three services in the browser. They cannot be opened, clicked, copied or pasted. This applies only to your phone.",
        "সুইচে চাপলে Android একবার VPN অনুমতি চাইবে, OK দিন। এটা শুধু নিরাপদ DNS-এর জন্য, আপনার ইন্টারনেট স্পিড বা ডেটা বদলায় না।" to "When you switch this on, Android will ask for VPN permission. Tap OK. It is only used for secure DNS and does not change your internet speed or data.",
        "লক চালু থাকায় সেইফ সার্চ বন্ধ করা যাবে না" to "Safe Search cannot be turned off while the lock is active",
        "আগে গার্ড ও অ্যাডমিন চালু করুন" to "Enable Guard and Device Admin first", "আগে ব্যাটারি সেভার থেকে NafsGuard বাদ দিন" to "Exclude NafsGuard from battery optimization first",
        "লক নিশ্চিত করুন" to "Confirm Lock", "লক করুন" to "Lock", "বাতিল" to "Cancel", "চালু করুন" to "Enable", "পরে" to "Later",
        "সবসময় চালু রাখার অনুমতি" to "Allow background operation", "ফোন ব্যাকগ্রাউন্ডে NafsGuard বন্ধ করে দিলে সুরক্ষা থেমে যায়। পরের পর্দায় \"Allow\" চাপলে ব্যাটারি সেভার আর NafsGuard-কে বন্ধ করবে না।" to "Protection stops if your phone closes NafsGuard in the background. Tap Allow on the next screen to prevent battery optimization from stopping it.",
        "এখানে \"Battery\" বা \"Autostart\" খুঁজে সবসময় চালু / Unrestricted করুন" to "Find Battery or Autostart here and set it to Always on / Unrestricted",
        "VPN চালু করা যাচ্ছে না" to "Unable to start VPN", "VPN অনুমতি না দিলে এটা চালু হবে না" to "This feature will not work without VPN permission",
        "Facebook Reels ব্লক" to "Block Facebook Reels", "Instagram Reels ব্লক" to "Block Instagram Reels", "YouTube Shorts ব্লক" to "Block YouTube Shorts",
        "Reels খুললেই নিজে থেকে পেছনে যাবে" to "Automatically return when Reels opens", "Shorts খুললেই নিজে থেকে পেছনে যাবে" to "Automatically return when Shorts opens",
        "অ্যাপ সংস্করণ:" to "App version:", "নিরাপদ ও পরিচ্ছন্ন অনলাইন অভিজ্ঞতা উপভোগ করুন。" to "Enjoy a safer and cleaner online experience.",
        "লক চালু আছে" to "Lock is active", "লক চালু হয়েছে" to "Lock activated", "দিন" to "Days", "ঘণ্টা" to "Hours", "মিনিট" to "Minutes", "সেকেন্ড" to "Seconds",
        "ব্ল্যাকলিস্ট" to "App Blacklist", "+ চেপে ফোনের অ্যাপ বেছে নিন। যেগুলো যোগ করবেন সেগুলোতে আর কোনোভাবেই ঢোকা যাবে না।" to "Tap + to choose apps on your phone. Apps you add here will be blocked.",
        "এখনো কোনো অ্যাপ যোগ করা হয়নি।" to "No apps have been added yet.", "লক চালু থাকায় অ্যাপ মুছা যাবে না (যোগ করা যাবে)।" to "Apps cannot be removed while the lock is active (you can still add apps).",
        "ব্ল্যাকলিস্টে যোগ করুন" to "Add to App Blacklist", "অ্যাপ খুঁজুন" to "Search apps", "যোগ করুন" to "Add", "কমপক্ষে একটি অ্যাপ বেছে নিন" to "Select at least one app",
        "নিশ্চিত করুন" to "Confirm", "অফলাইন পাসওয়ার্ড (ঐচ্ছিক)" to "Offline Password (Optional)",
        "সেট করলে অ্যাপে ঢুকতে প্রতিবার এই পাসওয়ার্ড লাগবে। ইন্টারনেট ছাড়াই কাজ করে।" to "Once set, this password will be required each time you open the app. Works offline.",
        "পাসওয়ার্ড সেট করুন" to "Set Password", "নতুন পাসওয়ার্ড" to "New Password", "কমপক্ষে ৪ অক্ষর" to "At least 4 characters",
        "কমপক্ষে ৪ অক্ষর দিন" to "Enter at least 4 characters", "আবার লিখুন" to "Enter again", "পাসওয়ার্ড নিশ্চিত করুন" to "Confirm password",
        "দুটি মিলছে না" to "The passwords do not match", "পাসওয়ার্ড সেট হয়েছে" to "Password set", "✅ চালু আছে। ভুলে গেলে অ্যাডমিনকে জানান।" to "✅ Enabled. Contact the administrator if you forget it.",
        "পাসওয়ার্ড বদলান" to "Change Password", "বর্তমান পাসওয়ার্ড" to "Current Password", "বর্তমান পাসওয়ার্ড ভুল" to "Current password is incorrect",
        "পাসওয়ার্ড বদলেছে" to "Password changed", "পাসওয়ার্ড বন্ধ করুন" to "Disable Password", "পাসওয়ার্ড ভুল" to "Incorrect password", "পাসওয়ার্ড বন্ধ হয়েছে" to "Password disabled",
        "খোলার সময়:" to "Unlock time:", "লক শুধু অ্যাডমিন খুলে দিতে পারবেন বা মেয়াদ শেষে নিজে খুলবে।" to "Only the administrator can unlock it, or it will unlock when the period ends.",
        "খোলার সময়:" to "Unlock time:", "অ্যাডমিনের সাথে যোগাযোগ →" to "Contact administrator →",
        "লক চালু থাকায় বন্ধ করা যাবে না" to "Cannot turn this off while the lock is active", "VPN Always-on চালু হয়েছে" to "Always-on VPN enabled",
        "VPN Always-on বন্ধ করা হয়েছে" to "Always-on VPN disabled", "আগে সেইফ সার্চ চালু করুন" to "Enable Safe Search first",
        "NafsGuard-এর পাশে ⚙️ চেপে Always-on VPN চালু করুন" to "Tap ⚙️ beside NafsGuard and enable Always-on VPN",
        "স্ট্যাটাস রিফ্রেশ" to "Refresh status", "এখনই ভার্সন চেক করুন" to "Check for updates now", "চেক হচ্ছে..." to "Checking...",
        "অ্যাডমিন এখনো মেয়াদ নির্ধারণ করেননি বা মেয়াদ শেষ" to "The administrator has not set a validity period, or it has expired",
        "আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না। শুধু অ্যাডমিন খুলে দিলে বা মেয়াদ শেষ হলে খোলা যাবে। নিশ্চিত?" to "You cannot uninstall or disable the app until the expiry date. Only the administrator can unlock it early. Continue?",
        "লক শুধু অ্যাডমিন বন্ধ করতে পারবেন" to "Only the administrator can turn off this lock",
        "Facebook Reels ব্লক চালু হয়েছে" to "Facebook Reels blocking enabled", "Instagram Reels ব্লক চালু হয়েছে" to "Instagram Reels blocking enabled",
        "YouTube Shorts ব্লক চালু হয়েছে" to "YouTube Shorts blocking enabled", "লিংক ব্লক শুধু অ্যাডমিন বন্ধ করতে পারবেন" to "Only the administrator can disable Link Block",
        "ছবি ও ভিডিও সার্চ ব্লক চালু হয়েছে" to "Image and Video Search Block enabled", "শব্দ যোগ করুন" to "Add Word"
        "সুরক্ষা" to "Protection", "ব্লক" to "Block", "শব্দ" to "Words", "লক" to "Lock",
        "আপনি সুরক্ষিত আছেন" to "You are protected", "সুরক্ষা সম্পূর্ণ হয়নি" to "Protection incomplete",
        "ছবি ও ভিডিও সার্চ" to "Image & video search", "ব্ল্যাকলিস্ট অ্যাপ" to "Blacklisted apps",
        "অ্যাপ যোগ করুন" to "Add app",
        "অ্যাপ যোগ করুন চেপে ফোনের অ্যাপ বেছে নিন। যেগুলো যোগ করবেন সেগুলোতে আর কোনোভাবেই ঢোকা যাবে না।" to "Tap Add app to pick apps from your phone. Apps you add can no longer be opened in any way.",
        "পাহারার স্ট্যাটাস (শুধু পরীক্ষার জন্য)" to "Guard status (for testing only)",
        "চালু আছে। ভুলে গেলে অ্যাডমিনকে জানান।" to "Enabled. Contact the admin if you forget it.",
    )
    private fun enDigits(s: String) = s.map { if (it in '০'..'৯') '0' + (it - '০') else it }.joinToString("")

    fun t(c: Context, value: String): String {
        if (Prefs.language(c) != "en" || value.isEmpty()) return value
        en[value]?.let { return it }
        if (Regex("^[০-৯]+ / [০-৯]+ চালু$").matches(value)) return enDigits(value.replace(" চালু", " on"))
        if (Regex("^[০-৯]+টি$").matches(value)) return enDigits(value.removeSuffix("টি"))
        if (value.startsWith("অ্যাপ সংস্করণ:")) return value.replace("অ্যাপ সংস্করণ:", "App version:").replace("(কোড ", "(Code ")
        if (value.startsWith("চালু করলে Chrome, Firefox")) return "When enabled, Images and Videos search on Google, Bing and DuckDuckGo will be blocked in browsers such as Chrome and Firefox."
        if (value.startsWith("চালু করলে এই তিন সাইটের লিংক")) return "When enabled, links from these three services will be blocked in the browser and cannot be copied or pasted. This applies only to this phone."
        if (value.startsWith("চালু করলে Facebook/Instagram")) return "When enabled, NafsGuard will return to the previous screen when Reels on Facebook/Instagram or Shorts on YouTube are opened."
        if (value.startsWith("চালু করলে সেইফ সার্চ VPN")) return "When enabled, Safe Search VPN will restart automatically if it stops. On most phones, enable Settings → VPN → NafsGuard → Always-on VPN once."
        if (value.startsWith("খোলার সময়: ")) return value.replace("খোলার সময়:", "Unlock time:")
        if (value.contains(" টি অ্যাপ ব্ল্যাকলিস্টে যাবে")) return value.replace(" টি অ্যাপ ব্ল্যাকলিস্টে যাবে, এগুলোতে আর ঢোকা যাবে না.", " apps will be added to the blacklist and cannot be opened.").replace("\n\nলক চালু থাকায় লক শেষ না হওয়া পর্যন্ত এগুলো তালিকা থেকে সরানো যাবে না।", "\n\nThese apps cannot be removed until the lock expires.")
        if (value.contains("পর্যন্ত আপনি অ্যাপ আনইন্সটল বা বন্ধ করতে পারবেন না")) return value.substringBefore(" পর্যন্ত") + " You cannot uninstall or disable the app until the expiry date. Only the administrator can unlock it early. Continue?"
        if (value.startsWith("স্বাগতম,")) return value.replace("স্বাগতম,", "Welcome,").replace("নিরাপদ ও পরিচ্ছন্ন অনলাইন অভিজ্ঞতা উপভোগ করুন。", "Enjoy a safer and cleaner online experience.").replace("নিচের সুইচগুলো চালু করুন।", "Turn on the switches below.")
        return value
    }
}
