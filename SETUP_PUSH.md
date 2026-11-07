# NafsGuard সার্ভার সেটআপ (Cloudflare Worker + D1)

সার্ভার এখন Google Apps Script/Sheet নয়; Cloudflare Worker আর D1 ডাটাবেজ (`nafsguard`)।
অ্যাপ যে লিংকে কথা বলে: `https://nafsguard-api.khadimulummahtrustfoundation.workers.dev`
(লিংক `app/src/main/java/com/NafsGuard/guard/Api.kt`-এ; Worker-এর নাম অবশ্যই `nafsguard-api` হতে হবে)

## ১. Firebase (শুধু Push-এর জন্য)
1. Firebase প্রজেক্টে Android অ্যাপ (package `com.NafsGuard.guard`) যোগ করা থাকতে হবে।
2. `google-services.json` রিপোর `app/` ফোল্ডারে থাকবে। না থাকলে Push কাজ করবে না।
3. Service account-এর private key JSON শুধু Cloudflare Worker-এর Secret (`FCM_SA_JSON`)-এ যাবে, GitHub বা APK-তে কখনো নয়।

## ২. Worker
Worker-এর কোড ও বসানোর ধাপ আলাদা দেওয়া হবে (`worker.js`)। Worker-এ লাগবে:
- D1 binding: নাম `DB`, ডাটাবেজ `nafsguard`
- Secrets: `FCM_PROJECT_ID`, `FCM_SA_JSON`, `ADMIN_KEY`

## ৩. ভার্সন চেক (Firebase Remote Config)
ভার্সন চেকে Cloudflare Worker/D1 লাগে না। Firebase Console → Engage → **Remote Config**-এ তিনটি প্যারামিটার বানান (Publish করতে ভুলবেন না):

| Parameter | উদাহরণ |
|---|---|
| `latest_version_code` | `5` |
| `latest_version_name` | `2.2` |
| `update_url` | `https://nafsguard.netlify.app/` |

- রেজিস্ট্রেশন সফল হলে অ্যাপ সাথে সাথে প্রথম Fetch করে; সফল হলে WorkManager-এ প্রতি ২৪ ঘণ্টার কাজ বসে (ডুপ্লিকেট হয় না)।
- Minimum Fetch Interval ২৪ ঘণ্টা। ইন্টারনেট না থাকলে ক্যাশ করা মান ব্যবহার হয়, নেটওয়ার্ক এলে আবার Fetch হয়।
- অ্যাপ বন্ধ থাকলেও চেক চালানোর চেষ্টা হয়, তবে Battery Optimization/Doze/Background Restriction-এ কিছুটা দেরি হতে পারে।
- `latest_version_code` অ্যাপের `versionCode`-এর চেয়ে বড় হলে নোটিফিকেশন আসে এবং অ্যাপ খুলতেই আপডেট পপ-আপ আসে।
- লক চালু থাকলে পপ-আপ দেখায় না (আনইন্সটল আটকানো থাকায় ইউজার আটকে যেত)।

নতুন APK দিলে: `app/build.gradle.kts`-এ `versionCode` ১ বাড়ান → APK আপলোড হলে Remote Config-এ `latest_version_code` (ও ইচ্ছামতো name/url) বদলে **Publish** করুন।
- বর্তমান বিল্ড: `versionCode = 4`, `versionName = "2.1"`।
- পুরোনো ইউজারকে আগের অ্যাপ আনইন্সটল করে নতুন APK ইনস্টল ও আবার রেজিস্ট্রেশন করতে হবে।

## ৪. Rules (Facebook/Instagram স্ক্রিন চেনার নিয়ম)
ডিফল্ট নিয়ম কোডে আছে। বদলাতে চাইলে D1-এর `rules` টেবিলে প্যাকেজ ধরে সারি বসান (কলাম `reel_ids`, `skip_ids`, `reel_desc` — JSON অ্যারে লেখা)। অ্যাপ প্রতি ৬ ঘণ্টায় নতুন নিয়ম নামায়।

## ৫. কমান্ড
অ্যাডমিন অ্যাপ থেকে: অনুমোদন (মেয়াদসহ), মেয়াদ বদল, সুইচ বন্ধ, লক খোলা, অ্যাক্সেস বন্ধ, পাসওয়ার্ড রিসেট, বার্তা। প্রতিটি কমান্ড D1-এর `push_log` টেবিলে লেখা থাকে।
