# XJTUToolBox CMPS — Compose Multiplatform 迁移尝试（历史存档）

> **这不是在用的代码。** 主 App 是仓库根目录 `app/` 下的原生 Android
> （Jetpack Compose + miuix）。本目录是一次跨平台迁移的探索，保留下来是为了留住
> 其中的协议移植成果与调研结论，**不参与主 App 构建**。
>
> 同一目标的另一条路见 `../XJTUTOOLBOX_CMP/`（腾讯 Kuikly，渲染到各端原生视图）。
> 本目录用 JetBrains Compose Multiplatform（自绘），已跑通 Android，iOS 仅到骨架。
>
> 结论摘要：**后端协议层移植成本低**（各站点 API 保留 70–95%，Ksoup 替 Jsoup、
> Ktor 替 OkHttp、仅十余处 `expect/actual`），**UI 层才是真正的代价**——
> miuix 是 Android 专属，跨端要么手工誊写、要么换 Material3 并放弃现有视觉。
> 详见 [MIGRATION_LEDGER.md](MIGRATION_LEDGER.md)。

Compose Multiplatform rewrite of 岱宗盒子 for Android and iOS.

## Current Shape

- `shared`: common Compose UI, MIUIX components, navigation, campus data models, repository boundary, auth/network bridge.
- `androidApp`: Android host app that renders the shared Compose tree.
- `iosApp`: SwiftUI host app that embeds `MainViewController()` from the shared framework.
- `../miuix-ref`: included build for local MIUIX mainline reuse.

## Verified Locally

```powershell
# 路径按本机实际情况替换；另需自建 local.properties 指定 sdk.dir
$env:JAVA_HOME='<Android Studio>\jbr'
$env:ANDROID_HOME='<Android SDK>'
$env:ANDROID_SDK_ROOT='<Android SDK>'
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
