import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.wallpapercarousel"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.wallpapercarousel"
        minSdk = 29          // Android 10+，覆盖 ColorOS 16
        targetSdk = 35

        // ── 真实语义化版本号（不再用日期）──
        // 发版时只改这三个数字：功能更新动 minor，修 bug 动 patch（一天发多次也能正确递增）
        val appMajor = 1
        val appMinor = 7
        val appPatch = 1
        versionName = "$appMajor.$appMinor.$appPatch"

        // versionCode 只用于系统判断"能否覆盖安装"，单调递增即可，用户看不到。
        // 基数 3000 万是为了高于历史上误用的日期版本号（20261126），避免被判成降级而拒装。
        versionCode = 30_000_000 + appMajor * 10_000 + appMinor * 100 + appPatch
    }

    // 产物命名：app-<buildType>-v<versionName>.apk，例如 app-debug-v1.4.apk
    applicationVariants.all {
        val vName = versionName
        val bt = buildType.name
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "app-$bt-v$vName.apk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
