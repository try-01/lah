plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.screenm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.screenm"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi")
    }
}

dependencies {
    // Compose UI
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // WebRTC - getstream fork (Google prebuilt is dead since 2018)
    implementation("io.getstream:stream-webrtc-android:1.2.2")

    // WebSocket server (local signaling)
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    // JSON
    implementation("org.json:json:20240303")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
}

ktlint {
    android.set(true) // Mengaktifkan aturan penataan/format khusus Android
    verbose.set(true) // Menampilkan informasi lebih detail saat proses linter berjalan
    outputToConsole.set(true)

    // Opsional: Jika ingin mengabaikan aturan tertentu secara global
    // disabledRules.set(setOf("no-wildcard-imports"))
}

detekt {
    toolVersion = "1.23.8"
    buildUponDefaultConfig = true // Menggunakan aturan bawaan Detekt sebagai basis
    allRules = false // Ubah ke 'true' jika ingin mengaktifkan semua aturan termasuk yang eksperimental

    // Jika Anda belum memiliki file konfigurasi detekt.yml, Anda dapat mengomentari baris di bawah ini terlebih dahulu
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true) // Menghasilkan laporan berformat HTML agar mudah dibaca di browser
        xml.required.set(true) // Menghasilkan laporan berformat XML (berguna untuk integrasi CI/CD)
        txt.required.set(false)
    }
}
