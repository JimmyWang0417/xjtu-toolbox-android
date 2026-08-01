# XJTU Toolbox - HarmonyOS 宿主工程

## 构建步骤

### 1. 编译 shared 模块生成 .so 产物

在项目根目录执行：

```bash
./gradlew -c settings.ohos.gradle.kts :shared:linkOhosArm64
```

产物路径：`shared/build/bin/ohosArm64/releaseShared/libshared.so`
头文件路径：`shared/build/bin/ohosArm64/releaseShared/libshared_api.h`

### 2. 拷贝产物到 ohosApp

```bash
mkdir -p ohosApp/entry/libs/arm64-v8a/
cp shared/build/bin/ohosArm64/releaseShared/libshared.so ohosApp/entry/libs/arm64-v8a/
cp shared/build/bin/ohosArm64/releaseShared/libshared_api.h ohosApp/entry/src/main/cpp/
```

### 3. 安装鸿蒙依赖

```bash
cd ohosApp/entry
ohpm install
```

### 4. 使用 DevEco Studio 构建

用 DevEco Studio 打开 `ohosApp` 目录，同步工程后编译运行。

## 项目结构

```
ohosApp/
├── entry/
│   ├── oh-package.json5          # 鸿蒙依赖配置
│   ├── src/main/
│   │   ├── cpp/                  # C++ NAPI 桥接
│   │   │   ├── napi_init.cpp
│   │   │   ├── CMakeLists.txt
│   │   │   └── types/libentry/index.d.ts
│   │   └── ets/
│   │       ├── entryability/
│   │       │   └── EntryAbility.ets
│   │       ├── kuikly/
│   │       │   ├── MyNativeManager.ets
│   │       │   ├── KuiklyViewDelegate.ets
│   │       │   └── adapters/
│   │       │       ├── AppKRLogAdapter.ets
│   │       │       └── AppKRRouterAdapter.ets
│   │       └── pages/
│   │           ├── Index.ets       # 主页面
│   │           └── KuiklyPage.ets  # 路由页面
│   └── libs/                      # .so 产物存放目录
└── README.md
```
