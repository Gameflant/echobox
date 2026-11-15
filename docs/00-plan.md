# EchoBox 技术方案 v1（待确认）

## 0. 已实测通过的前提

在 `/workspace/EchoBox/probe` 里做了最小工程验证，结论（避免方案建立在假设上）：

1. **AGP 9.5.1 内置 Kotlin 2.2.10 读不了 Miuix 0.9.4**（metadata 2.4.0 > 可读上限 2.3.0）。
   必须关闭内置 Kotlin，显式用 KGP **2.4.21**：
   ```properties
   android.builtInKotlin=false
   android.newDsl=false
   ```
   `android.newDsl=false` 是官方要求的配套项（只关一个会报 “Cannot add extension with name 'kotlin'”）。
2. **KSP 2.3.14 的 plugin marker 在镜像上是 502**。用 `pluginManagement.resolutionStrategy.eachPlugin` 把它重定向到 `symbol-processing-gradle-plugin`，可正常解析；KSP + Room 2.8.5 生成 `*_Impl.kt` 通过。
3. **Miuix 0.9.4 的 `miuix-glass` / `miuix-material` 没有发布到 Maven**（POM 404）。`miuix-blur-android:0.9.4` 已发布且可用（`layerBackdrop` + `textureBlur`）。
   → 玻璃面板自建，不依赖 `miuix-glass`。
4. compileSdk 37 需要 `compileSdk = 37` + `compileSdkMinor = 0`；buildTools 用 36.0.0；Kotlin jvmTarget 必须显式设 17，否则和 Java 17 冲突报 “Inconsistent JVM-target”。
5. `assembleDebug` 出包成功；debug APK 34.9MB（未 R8，含全量依赖），与“release < 25MB”是两回事，见 §9。
6. ffmpeg-kit 原版已从 Maven 下架，社区维护分支可用：
   `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7`（LGPL-3.0，AAR 21.5MB，含 arm64-v8a + x86_64）。

## 1. 技术栈与版本（Version Catalog 锁定）

| 组件 | 版本 | 说明 |
|---|---|---|
| AGP / Gradle / JDK | 9.5.1 / 9.7.1 / 21 | 环境既定 |
| Kotlin（KGP + compose + serialization 插件） | 2.4.21 | 必须与 Miuix 对齐 |
| Compose BOM | 2026.09.00 | material3 随 BOM |
| Miuix | 0.9.4（ui / blur / icons / preference） | 视觉层 |
| Media3 | 1.11.1（exoplayer / session / datasource / database-ui） | 播放 |
| Room | 2.8.5（KSP） | 索引与本地状态 |
| WorkManager | 2.12.0 | 下载 / 转码 / 同步 |
| Navigation Compose | 2.10.2 | 单 Activity |
| Lifecycle | 2.11.0 | ViewModel |
| DataStore | 1.3.0 | 偏好 |
| Coil | 3.6.3 + coil-network-ktor3 | 复用 Ktor，省一个 OkHttp |
| Ktor | 3.6.1（core / okhttp / content-negotiation / logging-no-op） | 网络 |
| kotlinx-serialization | 1.13.0 | 流式解析 |
| KSP | 2.3.14 | Room |
| ffmpeg-kit | 8.1.7（-audio，LGPL-3.0） | 转码 |
| security-crypto | 1.1.0 | EncryptedSharedPreferences |

许可证：全部 Apache-2.0 / LGPL-3.0，无 GPL 传染项；项目自身 GPL-3.0。
（Miuix = Apache-2.0，Media3/Ktor/Coil/Room/WorkManager = Apache-2.0，ffmpeg-kit-audio = LGPL-3.0，只动态链接、不修改。）

## 2. 分层与模块（7 个 Gradle 模块）

```
:app                  Application、Activity、导航图、DI 装配、启动优化
:core                 领域模型 / 网络 / Room / Repository / 同步 / 下载 / 上传 / 转码 / DataStore
:core:media           Media3 播放服务、队列、播放缓存、MediaSession
:core:designsystem    液态玻璃组件、Miuix 主题封装、图标、动效 token
:feature:main         主页 / 音乐 / 发现 / 设置 + 底部导航宿主 + 曲目详情 + 搜索
:feature:player       播放页（手势、跟手模糊、shared element 落点）
:feature:upload       上传表单 + 我的投稿
```

依赖方向严格单向：`feature:* → core:designsystem / core:media / core`，`:app` 只做装配，不含业务。
没有 Hilt：手写 `AppGraph`（构造注入 + `ViewModelProvider.Factory`）。理由：省 KSP 一轮、省 ~1.5MB、缩短构建；本项目依赖图只有十几项，手写完全可控。

### 数据流

```
Compose UI ──事件──▶ ViewModel(StateFlow<UiState>) ──▶ Repository
                                                     ├─ Room（本地真相，UI 首帧来源）
                                                     ├─ Ktor + MirrorResolver（远端）
                                                     └─ WorkManager（下载/转码/同步）
播放：PlayerController(:core:media) ──StateFlow──▶ 播放页 / 迷你播放条
下载：DownloadRepository ──入队──▶ DownloadWorker ──Range──▶ .part ──sha256 校验──▶ 本地文件 + Room
上传：UploadRepository ──校验──▶ GitHub Contents/Git Data/Releases API ──▶ staging ──▶ 本地 pending 记录
```

## 3. 数据模型

### 3.1 Room（version 1，导出 schema 到 `core/schemas`）

```sql
tracks(
  id TEXT PK, title, author, game,
  author_contacts TEXT,     -- JSON: {bilibili,netease,soundcloud,other}
  series_name, series_index INT, series_total INT,
  authorization,            -- self_made | authorized_repost
  resource_license,
  file_name, format, duration_sec INT, cover_name, sha256,
  uploaded_at_epoch INT, stats_downloads INT, stats_plays INT,
  search_blob TEXT,         -- 小写归一化：title+author+series+tags+game
  updated_at_epoch INT
)
-- 索引：uploaded_at_epoch、stats_downloads、game、series_name、search_blob

track_tags(track_id, tag, PRIMARY KEY(track_id, tag))      -- 分类筛选走这张表
series(name PK, declared_total INT, track_count INT)
http_cache(url PK, etag, last_modified, fetched_at_epoch, byte_size)
sync_state(key PK, value)                                  -- last_sync、index_schema
downloads(track_id PK, state, file_path, bytes_done, bytes_total, etag, error, queue_order, updated_at)
queue_items(id PK AUTOINCREMENT, track_id, position INT)
player_state(id=1 PK, current_track_id, position_ms, mode)  -- 顺序/单曲/随机
play_history(track_id, played_at_epoch, position_ms)        -- 最近播放
mirrors(id PK, name, kind, raw_template, asset_template, enabled, priority, last_rtt_ms, last_check_epoch)
submissions(record_id PK, state, reason, submitted_at, updated_at, payload_json, audio_name, cover_name)
```

搜索实现：`WHERE search_blob LIKE '%'||:q||'%'`（q 先做小写、去控制字符、长度≤64）。曲库量级按 5k 估，LIKE 全表扫描 <10ms；不用 FTS，避免中文分词问题。

### 3.2 远端 JSON（App 只读）

`index/tracks.json` —— **顶层 JSON 数组**（为流式 `Json.decodeToSequence` 服务，内存恒定）：

```json
[ { "id": "20261114-a3f9c1", "title": "...", ... }, ... ]
```

`index/meta.json`（新增，App 用来判断是否需要全量）：
```json
{ "schema_version": 1, "count": 4213, "generated_at": "2026-11-14T10:00:00Z" }
```

`index/incremental/manifest.json`（新增，避免逐日 404 探测）：
```json
{ "schema_version": 1, "files": [ { "name": "2026-11-14.json", "range": ["2026-11-14T00:00:00Z","2026-11-14T23:59:59Z"], "count": 12 } ] }
```

`index/incremental/2026-11-14.json`（数组，流式）：
```json
[ { "op": "upsert", "track": { ...4.2 单条... } },
  { "op": "delete", "id": "20261001-b7e2d0" } ]
```

`featured.json`：`{ "updated_at": "...", "items": [{ "track_id": "...", "badge": "编辑推荐", "blurb": "..." }] }`
`stats.json`：`{ "updated_at": "...", "tracks": { "<id>": { "downloads": 12, "plays": 340 } } }`

同步策略：本地先出数据 → 启动后台协程 `IndexSyncer`：
`meta.json`（ETag）→ 若 `last_sync` 超过 31 天或 `schema_version` 变化则全量，否则 `manifest.json` → 拉差异文件逐条 upsert/delete，事务每 200 条提交。304 直接跳过解析。

## 4. 仓库契约（prod / staging）

**prod（App 只读）**
```
index/tracks.json, index/meta.json, index/incremental/*.json, index/incremental/manifest.json
covers/<id>.webp
featured.json, stats.json
Releases tag "audio"：asset 名 <id>.<ext>     ← 音频物理承载
```
「逻辑路径 `audio/<file>`」= Releases `audio` tag 下的同名 asset。仓库正文不存音频（满足 4.1 的要求）。

**staging（App 只写自己的 pending）**
```
pending/<recordId>.json          # meta.json（含审计勾选时间戳）
status/index.json                # 审核软件写，一次 ETag 请求拿到所有状态
Releases tag "staging-inbox"：asset 名 <recordId>.<ext> / <recordId>.webp
```
- 上传音频/封面走 Releases Uploads API（二进制流式，无 50MB base64 内存问题，可断点重试）。
- 审核通过后由 Admin：asset 改名搬 `audio` tag → 生成 id/SHA-256/索引 → 追加增量 → 初始化 stats → 删 pending 与 asset。
- `recordId = rec_<yyyyMMddHHmmss>_<6位短哈希>`，与 prod 的 `20261114-a3f9c1` 不冲突。
- 契约细节（payload schema、字段长度、状态机）放 `docs/admin-contract.md`，交付给 Admin 的 Agent。

## 5. 镜像与 URL 安全

`MirrorResolver` 是唯一出 URL 的地方：
```
raw:   {base}index/tracks.json        （内置 3 个模板，jsDelivr/ghproxy 可代理）
asset: {base}releases/download/audio/<file>   （仅 github 直连 + ghproxy 前缀镜像）
```
校验规则（每次拼接都走一遍）：
- 只允许 `https`、443 端口、无 userinfo；
- host 必须在内置集合或用户**显式添加并确认过**的自定义 host；
- 相对路径白名单正则 `^(index|covers|featured|stats)/[A-Za-z0-9._/-]+$`，拒绝 `..`、`//`、`%2e`、控制字符；
- Ktor 关闭自动重定向，手动最多 5 跳，**每一跳重新校验 host**（防 302 到内网）；
- 拒绝 IP 字面量与 `localhost/.local/.internal`。

测速：启动并发 HEAD `index/meta.json`（2s 超时，最多 3 并发），按 RTT 排序；失败进 10 分钟冷却；全失败则用本地缓存并在 UI 标「离线模式」。

## 6. 播放 / 下载 / 转码 / 上传

- 播放：`MediaSessionService` + ExoPlayer；数据源顺序 = 本地已下载文件 → `SimpleCache`（上限可配）→ 网络。前台服务只在真正播放/传输时启动。
- 下载：单 `DownloadWorker` 管理队列，内部 3 并发协程（上限可配）；HTTP Range + `.part` + etag 校验，完成后 SHA-256 校验再 rename；`NetworkType.UNMETERED` 对应「仅 Wi-Fi」。
- 转码：`ffmpeg-kit-audio`，ffprobe 先探测，非音频拒绝；预设 高质量(mp3 320k / m4a 256k)、体积优先(opus 96k)、兼容(mp3 192k/44.1k/stereo)；先写 cache 再 `DocumentFile` 落到 SAF 目录。
- 上传：Device Flow 取 token（`public_repo`）→ EncryptedSharedPreferences；表单校验（长度、字符集、控制字符、协议必填）→ MediaExtractor 探测 → 封面压 800×800 webp → SHA-256 查重（prod 索引 + staging pending）→ Releases 上传 → 写 `pending/<recordId>.json` → 本地 `submissions`。
- 我的投稿：启动 + 进页面各拉一次 `status/index.json`（ETag）。

## 7. 液态玻璃设计系统

`GlassSurface`（自建，底层 `miuix-blur`）：
```
Box(Modifier.layerBackdrop(backdrop)) { 页面内容 }
玻璃层 = Modifier.textureBlur(backdrop, shape, blurRadius=…, colors=BlurColors(tint, …))
       + 1px 渐变描边（linearGradient top-start 亮 / bottom-end 暗）
       + 圆角 20–28dp（统一 token）
```
- 顶栏：`GlassTopBar` 滚动时 `blurRadius 0→24dp`、`alpha 1→0.72` 用 `animateFloatAsState(spring)` 渐变，禁止硬切。
- 底部导航：玻璃胶囊 + 4 项；选中指示器 spring。
- 播放页：封面模糊作背景 + `glassPanel` 信息卡；下滑跟手（`draggable` → offset + 背景 blur 随进度变化）。
- 转场：`SharedTransitionLayout`，列表封面 → 详情封面 / 迷你条 → 播放页。
- 降级：`isRuntimeShaderSupported()` 为 false、或设置里关模糊、或 `isLowRamDevice` → 半透明纯色 + 描边，动效改 `snap`。
- 可访问性：玻璃上的文字用 `onBackground`/`onSurface` 高对比色，实测对比度 ≥4.5:1；`contentDescription` 全量补齐。

## 8. 性能与包体积

- 启动：Application 零 IO；Room 懒打开；首帧用 Room 缓存；系统 SplashScreen（minSdk 34 原生）+ 主题背景同色，避免白屏；`:app` 不加载业务模块初始化。
- 列表：`LazyColumn` + `key = { it.id }` + `contentType` + 固定 item 高度；封面请求带 `size()`。
- 图片：Coil 内存上限 25% maxMemory（可配 15–35%），磁盘上限默认 256MB（可配），webp。
- 网络去重：`SingleFlight`（`ConcurrentHashMap<String, Deferred<T>>`）+ 同 key 合并。
- 禁止主线程 IO：debug 开 StrictMode；所有 IO 显式 `Dispatchers.IO`。
- R8 + `shrinkResources` + `abiFilters = arm64-v8a`（x86_64 仅 debug 构建）。
- 包体积预算：R8 后基础 ~8MB + ffmpeg arm64 ~9MB ≈ **17–19MB**，留 6MB 余量；超 25MB 的第一刀是移除 x86_64 / 换 `ffmpeg-kit-min`（但 min 无 mp3 编码器，只在必要时）。

## 9. 安全清单 → 实现落点

| 要求 | 落点 |
|---|---|
| 密钥不硬编码 | `local.properties`（gitignore）→ `BuildConfig.GITHUB_CLIENT_ID`；密钥用 `keystore.properties` |
| token 存储 | `TokenStore` 抽象 + EncryptedSharedPreferences（卸载即清） |
| 强制 HTTPS / 证书校验 | Ktor `HttpsURLConnection`/OkHttp 默认校验，无任何 trust-all；`network_security_config` 禁明文 |
| 上传前校验 | 扩展名白名单 + MediaExtractor 有效音轨 + MIME + 大小/时长 → 才允许读字节 |
| 防 SSRF | MirrorResolver 白名单 + 手动重定向逐跳校验 + 路径正则 |
| payload schema | `UploadFormValidator`：字段长度、字符集、控制字符、URL 字段只允许 https |
| 崩溃上报默认关 | 默认不初始化；开关明示 + 同意后才启用，metadata 只含版本/机型/堆栈 |
| 依赖许可证 | 见 §1，README 列清单，CI 里不做 |

## 10. 目录结构

```
EchoBox/
├─ app/                      Application、MainActivity、AppGraph、NavHost、启动主题
├─ core/
│  ├─ model/                 Track、Series、Mirror、License、Submission…
│  ├─ network/               HttpClientFactory、MirrorResolver、SingleFlight、ConditionalGet
│  ├─ db/                    Entities、DAO、Converters、Migration
│  ├─ repo/                  TrackRepo、IndexRepo、DownloadRepo、UploadRepo、SettingsRepo
│  ├─ sync/                  IndexSyncer、StreamingIndexParser
│  ├─ download/              DownloadWorker、RangeDownloader
│  ├─ upload/                DeviceFlowAuth、GitHubUploader、UploadFormValidator、ImageCompressor
│  ├─ transcode/             FfmpegTranscoder、TranscodePreset、TranscodeWorker
│  └─ datastore/             SettingsDataStore、TokenStore、MirrorStore
├─ core/media/               PlaybackService、PlayerController、QueueManager、MediaCache
├─ core/designsystem/        GlassSurface、GlassTopBar、GlassNavBar、MiuixTheme、Tokens、Icons
├─ feature/main/             home/ library/ discover/ settings/ detail/ search/ shell/
├─ feature/player/           PlayerScreen、gesture、shared element
├─ feature/upload/           UploadForm、SubmissionList
├─ docs/                     architecture、admin-contract、repo-format、mirror
├─ gradle/libs.versions.toml
└─ README.md / LICENSE
```

## 11. 交付批次与验收

| 批次 | 内容 | 验收 |
|---|---|---|
| P0 骨架 | 工程、7 模块、主题、玻璃组件、4 tab 空壳、导航、CI 构建 | `assembleDebug` 通过；4 tab 可切换；玻璃降级开关生效；冷启动 <800ms（模拟器实测） |
| P1 数据 | Room、模型、MirrorResolver、流式解析、同步、ETag、去重 | 单测：解析 5 万条不 OOM（`-Xmx512m` 跑）；镜像失败 fallback；304 不解析；离线读缓存 |
| P2 发现 | 最新/最热/分类/详情/搜索 | 列表 60fps（Macrobenchmark 或帧统计）；搜索输入 200ms 内出结果；详情显示授权与协议 |
| P3 播放 | 服务、队列、模式、播放页手势、缓存 | 后台播放、锁屏控制、切歌不断音；下滑关闭跟手；缓存命中不联网 |
| P4 下载转码 | 断点续传、并发/仅 Wi-Fi、通知、转码预设 | 断网恢复续传；SHA-256 不符丢弃；5 种格式互转成功；非音频被拒 |
| P5 上传 | Device Flow、表单校验、查重、Releases 上传、我的投稿 | 缺项禁用提交；工程文件被拒；50MB 上传不 OOM；状态轮询正确 |
| P6 收尾 | R8、包体积、启动优化、文档、README | release APK <25MB；README/架构/契约文档齐全；lint 无 error |

每批交付后我会给出：改动清单 + 构建产物 + 自测记录 + 下批前置。

## 12. 需要你确认的 6 个决策

1. **音频承载**：prod 音频放 Releases `audio` tag（asset 名 `<id>.<ext>`），仓库正文只放索引/封面；staging 上传同样走 Releases。是否接受？（否则只能把 50MB 音频塞进 Git 正文，仓库会膨胀且要 base64 上传）
2. **索引契约微调**：`tracks.json` 顶层改成数组、新增 `index/meta.json` 与 `incremental/manifest.json`、增量支持 `op: delete`。没有 manifest 的话 App 只能逐日 404 探测。
3. **搜索**：用 `LIKE` + 索引列，不引 FTS（中文分词不可靠）。可接受？
4. **DI**：手写 `AppGraph`，不用 Hilt。可接受？
5. **Miuix 玻璃**：`miuix-glass` 未发布，玻璃面板自己用 `miuix-blur` 拼。可接受？
6. **GitHub OAuth**：Device Flow 只能申请 `public_repo`（fine-grained token 不支持 Device Flow），「仅写 staging」只能在 App 端约束目标仓库，token 层面做不到更细。是否接受，还是改成「用户自建 fine-grained PAT 手动粘贴」？
```
