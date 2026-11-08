import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.NafsGuard.guard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.NafsGuard.guard"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "2.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    // সব বিল্ড একই চাবি (nafsguard.jks) দিয়ে সই হয়, তাই নতুন APK পুরোনোটার ওপর সরাসরি আপডেট হয় (আনইনস্টল লাগে না, ডাটা ও লক থাকে)
    // GitHub Actions প্রতিবার নতুন debug চাবি বানায়, সেই কারণেই আগে আনইনস্টল করতে হতো
    signingConfigs {
        create("fixed") {
            storeFile = file("nafsguard.jks")
            storePassword = System.getenv("NAFS_KS_PASS") ?: "nafsguard-fixed"
            keyAlias = "nafsguard"
            keyPassword = System.getenv("NAFS_KS_PASS") ?: "nafsguard-fixed"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("fixed") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Facebook Lite-এর স্ক্রিনে লেখা পড়ার জন্য (ফোনেই চলে, ইন্টারনেট লাগে না)
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // অ্যাডমিনের Push Command (FCM)
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging")
    // ভার্সন চেক: Firebase Remote Config + দিনে একবার WorkManager ব্যাকগ্রাউন্ড কাজ
    implementation("com.google.firebase:firebase-config")
    implementation("androidx.work:work-runtime:2.9.1")
}

// google-services.json (Firebase Console থেকে নামানো) app/ ফোল্ডারে থাকলে তবেই plugin চালু হয়;
// না থাকলে অ্যাপ বিল্ড হয় কিন্তু Push কাজ করে না।
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Hind Siliguri ফন্ট বিল্ডের সময় ডাউনলোড হয়ে APK এর ভেতরে ঢুকে যায়
val fontDir = File(projectDir, "src/main/assets/fonts")
tasks.register("downloadFonts") {
    doLast {
        fontDir.mkdirs()
        listOf("Regular", "Bold").forEach { w ->
            val f = File(fontDir, "HindSiliguri-$w.ttf")
            if (!f.exists() || f.length() < 10000) {
                try {
                    URL("https://raw.githubusercontent.com/google/fonts/main/ofl/hindsiliguri/HindSiliguri-$w.ttf")
                        .openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    println("Downloaded font: $w")
                } catch (e: Exception) {
                    println("Font download failed ($w): ${e.message}")
                    f.delete()
                }
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("downloadFonts") }
