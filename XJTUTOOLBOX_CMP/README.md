# XJTUTOOLBOX_CMP — Kuikly 跨平台迁移尝试（历史存档）

> **这不是在用的代码。** 主 App 是 `app/` 下的原生 Android（Jetpack Compose + miuix）。
> 本目录是一次跨平台迁移的探索，保留下来是为了留住其中的调研结论，不参与主 App 构建。

## 这是什么

用腾讯 [Kuikly](https://github.com/Tencent-TDS/KuiklyUI)（`com.tencent.kuikly-open`，
基于 Kotlin Multiplatform，复用 Compose runtime 但自带 `kuikly.compose.*` UI API、
渲染到各端原生视图）把 App 迁到多端的尝试。共 4 次提交，止步于 Android 端能跑通的程度。

## 为什么停下

结论不是"技术上不行"，而是**代价集中在 UI 层，且需要放弃现有视觉**：

| 层 | 迁移后代码保留比例 |
|---|---|
| 业务后端（各站点 API） | **70–95%**（LibraryApi 702/997、LmsApi 688/731、NotificationApi 563/641、CampusCardApi 435/462） |
| UI 组件 | **27–82%**（TopAppBar 27%、TextField 35%、NavigationBar 62%、Card 67%、Checkbox 82%） |

后端几乎是纯 Kotlin，Regex / 字符串处理 / JSON / 数据类 / 状态机在 KMP 下一行不用改；
三个原本担心的点都有成熟解法：Jsoup → [Ksoup](https://github.com/fleeksoft/ksoup)（API 近乎一致）、
OkHttp → Ktor（机械替换）、RSA/Base64/URL 编码 → `expect/actual`。
**整个项目只有 13 处 `expect`** 就框住了全部平台差异：

```
getPlatform / CustomCourseStore / CredentialStore / CryptoUtils / Base64Utils
urlEncode / FileSystem / currentTimeMillis / createPlatformHttpClient
Logger / PlatformContext / cacheDir / filesDir
```

难的是 UI：miuix 是 Android/Compose 专属，Kuikly 上没有对应实现，只能手工誊写
（本目录 `shared/src/commonMain/.../ui/miuix/` 下那批文件，约 3000 行）。
Kuikly 自带 Material3（`kuikly.compose.material3.*`，见 `shared/build.gradle.kts` 的
`optIn` 声明），换 MD3 可以删掉这批手誊代码、白嫖官方维护——**但产品就长得不像现在这个 App 了**。
这是审美与品牌决策，不是工程问题，所以搁置。

另有一处未验证：WebView。主 App 的移动交大、交晓智、内置浏览器三处重度依赖它，
Kuikly 侧如何承载没有结论。

## 与 XJTUTOOLBOX_CMPS 的区别

同一个目标的两条路：本目录用 **Kuikly**（渲染到原生视图），
`XJTUTOOLBOX_CMPS/` 用 **JetBrains Compose Multiplatform**（自绘）。

## 构建说明

不接入主仓库的 Gradle 构建。若要单独编译，需自行创建 `local.properties` 指定 `sdk.dir`。
签名走 `keystore.properties`（不在版本控制中）或 `KEYSTORE_PATH` 等环境变量。
