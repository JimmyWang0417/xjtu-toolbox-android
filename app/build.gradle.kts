android {
    namespace = "com.xjtu.toolbox"
    
    // ⚠️ 修改1：使用标准的纯数字语法，降级到最稳定的 34 (Android 14)
    compileSdk = 34 

    defaultConfig {
        applicationId = "com.xjtu.toolbox"
        
        // 修改2：minSdk 保持 32 没问题，如果你想更兼容，可以改成 28
        minSdk = 32 
        
        // ⚠️ 修改3：目标版本降级到 34，坚决不要写 36
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
                // 本地开发：从项目根目录读取 release.jks
                val localKeystore = rootProject.file("release.jks")
                if (localKeystore.exists()) {
                    storeFile = localKeystore
                    storePassword = "XjtuToolbox2026!"
                    keyAlias = "xjtu-toolbox"
                    keyPassword = "XjtuToolbox2026!"
                }
            }
            // ⚠️ 修改4：明确双重开启 V2 和 V3 签名，防止解析器找不到兼容签名
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
