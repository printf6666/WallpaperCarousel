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
        val appMinor = 8
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

    /**
     * 固定签名。
     *
     * 以前两个 buildType 都吃默认的 debug 签名（~/.android/debug.keystore），
     * 那把钥匙是「构建环境首次构建时随机生成」的：换个机器、换个用户目录、
     * 甚至重装构建环境，钥匙就换了一把，手机上的旧版本就会被判签名冲突、无法覆盖安装。
     *
     * 现在签名文件随工程走：debug / release 都用 keystore/wallpaper.jks，
     * 任何机器、任何会话打出来的包签名都一致，可长期覆盖升级。
     * 口令是个人项目用途，直接写在这里方便换机重建；请勿把本工程当作公开仓库发布。
     */
    signingConfigs {
        create("app") {
            storeFile = rootProject.file("keystore/wallpaper.jks")
            storePassword = "wallpaper123"
            keyAlias = "wallpaper"
            keyPassword = "wallpaper123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("app")
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
