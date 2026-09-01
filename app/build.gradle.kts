plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.idepython"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.idepython"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // Just arm64-v8a: virtually every Android tablet from the last
            // ~8 years, and keeps the APK under chat-delivery size limits.
            // Add "armeabi-v7a" back in if you need to support very old
            // 32-bit-only devices.
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

// การตั้งค่า Chaquopy (ตัวรัน Python บน Android)
chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            // เพิ่มไลบรารี Python ที่อยากให้ติดตั้งมากับแอปตรงนี้ เช่น:
            // install("numpy")
            // install("requests")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
}
