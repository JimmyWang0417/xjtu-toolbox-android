# XJTUToolBox CMPS

Compose Multiplatform rewrite of 岱宗盒子 for Android and iOS.

## Current Shape

- `shared`: common Compose UI, MIUIX components, navigation, campus data models, repository boundary, auth/network bridge.
- `androidApp`: Android host app that renders the shared Compose tree.
- `iosApp`: SwiftUI host app that embeds `MainViewController()` from the shared framework.
- `../miuix-ref`: included build for local MIUIX mainline reuse.

## Verified Locally

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME='D:\AndroidSDK'
$env:ANDROID_SDK_ROOT='D:\AndroidSDK'
.\gradlew.bat :shared:compileKotlinMetadata
.\gradlew.bat :androidApp:assembleDebug
```

The Android debug APK is generated at:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

## Migration Principle

The project keeps UI and campus-service logic separated:

- UI screens call `CampusRepository`.
- Real Android/iOS implementations can replace `DemoCampusRepository` without rewriting screens.
- CAS/MFA/WebVPN/session refresh is represented by `AuthBridge`.
- Network clients are platform-specific through `platformHttpClient()` using OkHttp on Android and Darwin on iOS.

## Known Build Notes

- MIUIX mainline currently requires Android compile SDK 37.
- AGP 9.2 with Kotlin Multiplatform still needs compatibility properties until the project moves to `com.android.kotlin.multiplatform.library`.
- iOS targets are `iosArm64` and `iosSimulatorArm64`; `iosX64` is intentionally omitted because current Compose/MIUIX artifacts do not resolve for it.
