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
        // 版本号用日期（yyyymmdd），保证每次发版一定大于历史任何构建，避免"降级安装"被拒
        versionCode = 20261124
        versionName = "1.4"
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
