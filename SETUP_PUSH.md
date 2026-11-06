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

## ৩. ভার্সন চেক (Cloudflare D1)
অ্যাপ দিনে একবার ব্যাকগ্রাউন্ডে `?action=version` ডাকে। সার্ভারের `latest_version_code` অ্যাপের `versionCode`-এর চেয়ে বড় হলে নোটিফিকেশন আসে এবং অ্যাপ খুলতেই (খোলা থাকলে সাথে সাথে) আপডেট পপ-আপ আসে।

নতুন APK দিলে:
1. `app/build.gradle.kts`-এ `versionCode` ১ বাড়ান (`versionName` ইচ্ছামতো)।
2. APK তৈরি ও আপলোড হলে D1 Console-এ চালান (৫ নম্বর উদাহরণ ধরে):
```sql
UPDATE app_config SET value='5' WHERE key='latest_version_code';
UPDATE app_config SET value='2.2' WHERE key='latest_version_name';
```
3. ডাউনলোড লিংক বদলাতে হলে:
```sql
UPDATE app_config SET value='https://nafsguard.netlify.app/' WHERE key='update_url';
```
- লক চালু থাকলে পপ-আপ দেখায় না (আনইন্সটল আটকানো থাকায় ইউজার আটকে যেত)।
- বর্তমান বিল্ড: `versionCode = 4`, `versionName = "2.1"`। রিলিজের পর D1-এ `latest_version_code` ৪ করে দিন।
- পুরোনো ইউজারকে আগের অ্যাপ আনইন্সটল করে নতুন APK ইনস্টল ও আবার রেজিস্ট্রেশন করতে হবে।

## ৪. Rules (Facebook/Instagram স্ক্রিন চেনার নিয়ম)
ডিফল্ট নিয়ম কোডে আছে। বদলাতে চাইলে D1-এর `rules` টেবিলে প্যাকেজ ধরে সারি বসান (কলাম `reel_ids`, `skip_ids`, `reel_desc` — JSON অ্যারে লেখা)। অ্যাপ প্রতি ৬ ঘণ্টায় নতুন নিয়ম নামায়।

## ৫. কমান্ড
অ্যাডমিন অ্যাপ থেকে: অনুমোদন (মেয়াদসহ), মেয়াদ বদল, সুইচ বন্ধ, লক খোলা, অ্যাক্সেস বন্ধ, পাসওয়ার্ড রিসেট, বার্তা। প্রতিটি কমান্ড D1-এর `push_log` টেবিলে লেখা থাকে।
