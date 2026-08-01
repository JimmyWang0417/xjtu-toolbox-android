import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.xjtu.toolbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xjtu.toolbox"
        minSdk = 23
        targetSdk = 36
        versionCode = 14
        versionName = "3.0.0-cmp"
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
                // 本地签名走 keystore.properties（已 gitignore），不在源码里硬编码口令。
                // 缺文件时不配置签名，release 构建会退回未签名产物。
                val localKeystore = rootProject.file("release.jks")
                val props = rootProject.file("keystore.properties")
                if (localKeystore.exists() && props.exists()) {
                    val p = Properties()
                    props.inputStream().use { p.load(it) }
                    storeFile = localKeystore
                    storePassword = p.getProperty("storePassword")
                    keyAlias = p.getProperty("keyAlias")
                    keyPassword = p.getProperty("keyPassword")
                }
            }
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.kuikly.core.render.android)
    implementation(libs.kuikly.core)
}
