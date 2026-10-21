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
        versionCode = 1
        versionName = "1.0"
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

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.1")
}

// NudeNet 320n মডেল (MIT লাইসেন্স) বিল্ডের সময় ডাউনলোড হয়ে APK এর ভেতরে ঢোকে
val modelDir = File(projectDir, "src/main/assets/models")
tasks.register("downloadModel") {
    doLast {
        modelDir.mkdirs()
        val f = File(modelDir, "320n.onnx")
        if (!f.exists() || f.length() < 1_000_000) {
            try {
                URL("https://github.com/notAI-tech/NudeNet/releases/download/v3.4-weights/320n.onnx")
                    .openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                println("Downloaded model: 320n.onnx (" + f.length() + " bytes)")
            } catch (e: Exception) {
                println("Model download failed: " + e.message)
                f.delete()
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("downloadModel") }
