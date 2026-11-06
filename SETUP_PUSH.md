# Push Command সেটআপ (একবারের কাজ)

## ১. Firebase
1. https://console.firebase.google.com → নতুন প্রজেক্ট।
2. Android অ্যাপ যোগ করুন, package নাম: `com.NafsGuard.guard`।
3. `google-services.json` নামিয়ে GitHub রিপোর `app/` ফোল্ডারে রাখুন (`app/google-services.json`)।
   এই ফাইল না থাকলে APK বিল্ড হবে কিন্তু Push কাজ করবে না।
4. Project settings → Service accounts → "Generate new private key" (JSON)। এটি **শুধু** Apps Script-এর Script Properties-এ যাবে, GitHub বা APK-তে কখনো নয়।

## ২. Apps Script
`server/PushAdmin.gs`-এর ওপরের মন্তব্যের ধাপ অনুযায়ী ফাইলটি আপনার প্রজেক্টে যোগ করুন, Script Properties দিন, `doGet`-এ দুই লাইন বসান, নতুন ভার্সনে Deploy করুন।

## ৩. ভার্সন চেক (Firebase Remote Config; শীটের Version ট্যাব আর লাগবে না)
1. Firebase Console → Run → Remote Config → "Create configuration"।
2. প্যারামিটার `latest_version` (String) = সর্বশেষ ভার্সন, যেমন `2.0`। এটি অ্যাপের `versionName`-এর সাথে হুবহু মিলতে হবে।
3. ঐচ্ছিক: `update_url` (String) = ডাউনলোড লিংক। না দিলে `https://nafsguard.netlify.app/` খুলবে।
4. **Publish changes** চাপুন।
- অ্যাপ প্রতিদিন একবার ব্যাকগ্রাউন্ডে এটি দেখে। ভার্সন না মিললে নোটিফিকেশন আসে এবং অ্যাপ খুলতেই (খোলা থাকলে সাথে সাথে) আপডেট পপ-আপ আসে।
- নতুন APK দিলে `app/build.gradle.kts`-এর `versionName` বাড়ান এবং Remote Config-এর `latest_version` সেই মানে বদলে Publish করুন।
- লক চালু থাকলে পপ-আপ দেখায় না (আনইন্সটল আটকানো থাকায় ইউজার আটকে যেত)।
- রিলিজের পর পুরোনো ইউজারকে আগের অ্যাপ আনইন্সটল করে নতুন APK ইনস্টল ও আবার রেজিস্ট্রেশন করতে হবে।

## ৪. ব্যবহার (শীটের "NafsGuard Push" মেনু)
Users ট্যাবে ইউজারের সারিতে ক্লিক করে মেনু থেকে কমান্ড দিন:
Approve (মেয়াদসহ), মেয়াদ বদলান, সুইচ OFF, Main Lock খুলুন, Revoke, পাসওয়ার্ড রিসেট, বার্তা (একজনকে / সবাইকে)।
প্রতিটি কমান্ড `PushLog` ট্যাবে লেখা থাকে।
