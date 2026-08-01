plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
                }
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    // ohosArm64 target requires KuiklyUI ohos Gradle plugin.
    // Build with: ./gradlew -c settings.ohos.gradle.kts :shared:linkOhosArm64
    // See ohosApp/README.md for details.

    sourceSets {
        all {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("com.tencent.kuikly.compose.material3.ExperimentalMaterial3Api")
        }

        commonMain.dependencies {
            // KuiklyUI (includes compose runtime 1.7.3 transitively)
            implementation(libs.kuikly.core)
            implementation(libs.kuikly.compose)
            implementation(libs.kuikly.core.annotations)

            // Ktor (HTTP client)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)

            // Kotlinx
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)

            // Ksoup (HTML parsing, Jsoup KMP port)
            implementation(libs.ksoup)

            // Multiplatform Settings (SharedPreferences replacement)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)

            // Room
            implementation(libs.room.runtime)
        }

        androidMain.dependencies {
            // Ktor engine
            implementation(libs.ktor.client.okhttp)
            implementation(libs.kotlinx.coroutines.android)

            // KuiklyUI Android render
            implementation(libs.kuikly.core.render.android)

            // Android-specific
            implementation(libs.androidx.core.ktx)
            implementation(libs.security.crypto)
            implementation(libs.zxing.core)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
        }

        iosMain.dependencies {
            // Ktor engine
            implementation(libs.ktor.client.darwin)
        }

        // ohosMain dependencies are configured when building with ohos Gradle plugin:
        // implementation(libs.ktor.client.cio)
    }
}

android {
    namespace = "com.xjtu.toolbox.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 23
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspAndroid", libs.kuikly.core.ksp)
    add("kspIosX64", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
