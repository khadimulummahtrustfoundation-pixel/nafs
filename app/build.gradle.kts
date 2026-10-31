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
        versionCode = 2
        versionName = "1.2"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
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
