# 用本地 Miuix 源码构建（composite build）

Miuix 0.9.4 在 Maven 上**没有发布** `miuix-glass` / `miuix-material`（POM 404，metadata 为空），
但 EchoBox 的玻璃表面需要它们。因此工程用 composite build 直接吃 `/workspace/miuix` 的源码：

```kotlin
// settings.gradle.kts
includeBuild("/workspace/miuix") {
    dependencySubstitution { /* top.yukonga.miuix.kmp:* → 对应子项目 */ }
}
```

依赖写成 KMP 模块名（`miuix-ui`、`miuix-glass`…），Gradle 会解析到源码的 Android 变体。

## 对 miuix 源码做的本地改动

全部是**构建配置**，没有改库代码。换 Miuix 版本时按下面几项重新对一遍。

| 文件 | 改动 | 原因 |
|---|---|---|
| `miuix/settings.gradle.kts` | `include` 只保留 core / ui / preference / material / blur / glass / glass-icons / squircle / icons | 去掉 example、docs、baselineprofile、nav、window，避免配置它们时解析 JS/Wasm 依赖 |
| `miuix/build.gradle.kts` | dokka 列表删掉 `miuix-nav` / `miuix-window` | 对应项目已不 include |
| `miuix/*/build.gradle.kts` | 删除 `jvm("desktop")`、`ios*`、`macos*`、`js {}`、`wasmJs {}`、`named("desktopTest")`、`baselineProfile {}` 块 | 只保留 Android target；否则 Gradle 会去解析 `animation-js:1.12.2` 等镜像上没有的构件（实测 502） |
| `miuix/build-logic/src/main/kotlin/BuildConfig.kt` | `BUILD_TOOLS_VERSION` 37.0.0 → 36.0.0 | 本机只有 build-tools 36.0.0 |
| `miuix/gradle.properties` | `-Xmx8g` → `-Xmx2g`、关 parallel、关 configuration-cache | 沙箱内存有限，8g 会被 OOM kill |
| `miuix/miuix-ui/build.gradle.kts` | 删 `baselineProfile(projects.baselineprofile)` | 该模块依赖 example:android |

另外 `/workspace/miuix` 和 `/workspace/EchoBox` 都 `git init` + 空 commit：
miuix 的 `module.publication` 插件会在配置阶段执行 `git rev-parse`，目录不是仓库时直接失败。

## 构建命令

必须带 `-Prelease`：跳过 publication 插件的 git hash 计算。

```bash
source /workspace/AndroidDevKit/scripts/envs.sh
gradle :app:assembleDebug  -Prelease --no-daemon --max-workers=2
gradle :app:assembleRelease -Prelease --no-daemon --max-workers=2
```

首次构建会编译 miuix 的 Android 变体（约 3–5 分钟），之后走增量。

## 升级 Miuix

1. `cd /workspace/miuix && git pull`
2. 按上表重新应用改动
3. `gradle :app:assembleDebug -Prelease` 验证
4. 若官方在此版本发布了 `miuix-glass` / `miuix-material` 的 AAR，可以删掉 `includeBuild`，改回 Maven 依赖
