import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.scanaggregator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.scanaggregator"
        minSdk = 24 // ZXingLite 要求 23+，本项目从 24 起兼容更多旧设备
        targetSdk = 34
        versionCode = 13
        versionName = "1.1.0"

        // 构建时间写入 BuildConfig，关于页展示
        val buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 演示项目：Release 使用 debug 签名保证 APK 可直接安装；正式对外发布请替换为专属 keystore
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true // 关于页需要读取 BUILD_TIME
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2") // 历史记录列表
    implementation("androidx.activity:activity-ktx:1.9.3")     // Photo Picker 相册选图

    // 扫码：ZXingLite（CameraX + ZXing 本地识别，不使用 ML Kit）；zxing core 需单独引入
    implementation("com.github.jenly1314:zxing-lite:3.5.0")
    implementation("com.google.zxing:core:3.5.3")
}
