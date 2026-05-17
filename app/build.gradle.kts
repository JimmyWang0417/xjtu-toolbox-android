plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xjtu.toolbox"
    
    // ⚠️ 保持 37 编译，用于解决 Miuix 和 新版 AndroidX 库的编译硬性要求
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xjtu.toolbox"
        minSdk = 32
        
        // ⚠️ 将运行时倾向降到最稳妥的 34（Android 14）
        // 这样既能兼容高版本的库，又能保证生成的 APK 资源和结构能被低版本环境/卓易通正确解析
        targetSdk = 34
        
        versionCode = 23
        versionName = "3.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_PATH")
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                val localKeystore = rootProject.file("release.jks")
                if (localKeystore.exists()) {
                    storeFile = localKeystore
                    storePassword = "XjtuToolbox2026!"
                    keyAlias = "xjtu-toolbox"
                    keyPassword = "XjtuToolbox2026!"
                }
            }
            // 确保双重开启 V2 和 V3 签名，提高容器解析通过率
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material.icons)
    implementation(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.coroutines.android)
    implementation(libs.navigation.compose)
    implementation(libs.security.crypto)
    implementation(libs.zxing.core)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.1")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
}
