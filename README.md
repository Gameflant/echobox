# EchoBox

Undertale 同人音乐播放器（Android 14+）。曲库以 GitHub 仓库为数据源：音频放 Releases，索引与封面放仓库正文。

不含任何社交功能：没有点赞、评论、关注、歌单分享。

## 功能

- 在线流式播放；已下载的曲目自动走本地文件
- 音乐 / 发现 / 设置 / 播放器，Miuix + 液态玻璃 UI，白底 + 悬浮玻璃底栏
- 播放页：歌词、上一首 / 下一首、循环、收藏
- 下载：暂停 / 继续 / 断点续传（`Range`），暂停后在设置里换源，继续时自动用新源
- 网络源单选：直连 / GhProxy / GhProxy.org / DDLC / jsDelivr，真实下载测速，失败不自动切换
- 封面与网络源解耦，固定走 jsDelivr，换源不影响封面
- 本地音乐 / 我的收藏 / 最近播放 / 下载列表 四个快捷入口
- 转码（ffmpeg-kit）

## 技术栈

Kotlin 2.4.21 · AGP 9.5.1 · Jetpack Compose (BOM 2026.09.00) · Miuix 0.9.4 · Media3 1.11.1 · Room 2.8.5 · Ktor 3.6.1 · Coil 3.6.3 · WorkManager 2.12.0 · ffmpeg-kit 8.1.7 (LGPL)

## 构建

前置：JDK 21、Android SDK Platform 37.0、build-tools 36.0.0。

```bash
gradle :app:assembleRelease -Prelease --no-daemon --max-workers=2
```

Miuix 的玻璃模块没有发布到 Maven，工程用 composite build 直接编译本地 `miuix` 源码，
改动与升级步骤见 [docs/miuix-local.md](docs/miuix-local.md)。

`local.properties`（不入库）：

```properties
sdk.dir=/path/to/android-sdk
prod.owner=<GitHub 用户名>
prod.repo=<音乐源仓库名>
prod.branch=main
```

签名配置在 `keystore.properties`（同样不入库），release 构建会读取。

产物：`app/build/outputs/apk/release/app-release.apk`，约 17MB（R8 + 资源收缩 + 仅 arm64-v8a）。

## 架构

- 7 个模块：`:app`、`:core`、`:core:media`、`:core:designsystem`、`:feature:main`、`:feature:player`、`:feature:upload`
- MVVM + Repository，单 Activity + Navigation Compose
- 手写依赖容器 `CoreGraph`，不引 Hilt
- 索引用 `Json.decodeToSequence` 流式解析，不把整份 JSON 读进内存

详见 [docs/architecture.md](docs/architecture.md)。

## 网络源

内置直连、GhProxy、GhProxy.org、DDLC、jsDelivr 五个音频源，用户单选，不做自动 fallback。
测速是真实下载 512KB 计时，结果直接显示在设置页。

- 索引同步与在线播放都走当前选中的源
- 封面固定在 jsDelivr
- 下载走 OkHttp 原生 `byteStream()`；Ktor 的 channel→InputStream 桥吞吐只有几十 KB/s
- URL 只在 `MirrorResolver` 拼接：https + 443 + host 白名单 + 重定向逐跳校验

详见 [docs/mirror.md](docs/mirror.md)。

## 数据源

- 仓库结构与索引 schema：见 [docs/admin-contract.md](docs/admin-contract.md)
- App 与审核软件的对接契约（staging、meta.json、status.json）：同文件

## 贡献

1. 一个 PR 只做一件事，附上改动理由
2. 不要提交 `local.properties`、`keystore.properties`、任何 token
3. 新增依赖前先确认许可证（禁止 GPL 传染性依赖）
4. UI 改动附截图；涉及镜像是 URL 拼接的改动必须说明安全边界

## 开源协议

Apache License 2.0。完整文本见 [LICENSE](LICENSE)。

依赖许可证：Kotlin/Compose/AndroidX/Media3/Ktor/Coil/Room/WorkManager/Miuix 为 Apache-2.0；ffmpeg-kit 为 LGPL-3.0（动态链接，未修改）。

## 免责声明

本项目仅提供存储与索引服务，不对内容版权承担审核义务。上传者须确认已获得原作者授权或作品为本人原创；因作品引发的任何版权纠纷、侵权责任与法律后果，均由上传者本人承担，与本项目及其维护者无关。

本项目在收到权利人投诉时，会直接下架相关作品并保留追责记录。

曲库中的音频、封面版权归各自作者所有，本项目不主张任何权利。
