# 架构说明

## 1. 模块

```
┌───────────────────────────── :app ─────────────────────────────┐
│ Application / MainActivity / NavHost / AppGraph（装配）          │
└───────┬─────────────────┬──────────────────┬───────────────────┘
        │                 │                  │
  :feature:main     :feature:player    :feature:upload
        │                 │                  │
        └────────┬────────┴──────────────────┘
                 │
        ┌────────▼─────────┐   ┌──────────────────┐
        │ :core:designsystem│  │   :core:media     │
        │ 玻璃 / 主题 / 图标 │  │ Media3 服务 / 队列 │
        └──────────────────┘   └────────┬─────────┘
                                        │
                              ┌─────────▼─────────┐
                              │      :core        │
                              │ model / network   │
                              │ db / sync / repo  │
                              │ download / upload │
                              │ transcode         │
                              └───────────────────┘
```

依赖方向严格单向，没有反向引用，也没有循环：

- `:app` 只做装配与导航，不含业务逻辑
- `:feature:*` 只依赖 `:core`、`:core:media`、`:core:designsystem`
- `:core:media` 依赖 `:core`
- `:core:designsystem` 不依赖任何业务模块

## 2. 数据流

### 启动

```
Application.onCreate
  └─ CoreGraph.warmUp()（Default 调度器，不阻塞首帧）
       ├─ MirrorResolver.ensureDefaults()   默认镜像入库
       ├─ MirrorProbe.probeAll()            并发 HEAD 测速，写 RTT
       └─ IndexSyncer.sync()
            ├─ index/meta.json（ETag）
            ├─ 全量：tracks.json 流式 upsert（每 200 条一事务）
            ├─ 增量：manifest → 逐文件 upsert/delete
            ├─ stats.json 合并下载/播放量
            └─ featured.json 存 sync_state
```

首帧只读 Room 里已有的数据；没有数据时显示骨架，同步完成由 Flow 自动刷新。

### 搜索

```
输入 → normalizeQuery（去控制字符/小写/限 64 字符）
     → LIKE '%q%' on tracks.searchBlob（title+author+game+series+tags 归一化）
     → Flow<List<Track>>
```

不引 FTS：中文分词不可靠，5k 量级 LIKE 全表扫描 <10ms。

### 播放（P3）

```
UI → PlayerController → ExoPlayer
数据源优先级：本地下载文件 → SimpleCache → 网络（MirrorResolver）
播放状态通过 StateFlow 回传 UI；MediaSessionService 负责后台与锁屏控制
```

### 下载（P4）

```
DownloadRepository.enqueue(track)
  → WorkManager（单 Worker 管队列，内部 3 并发）
  → Ktor Range 请求写 .part
  → sha256 校验 → rename → Room.downloads 状态更新
```

### 上传（P5）

```
表单 → UploadFormValidator（长度/字符集/控制字符/必填）
     → MediaExtractor 探测有效音轨
     → 封面压 800×800 webp
     → sha256 查重（prod 索引 + staging pending）
     → Releases Uploads API 传音频与封面（二进制流式）
     → Contents API 写 pending/<recordId>.json
     → Room.submissions 记录 pending
```

## 3. 关键决策与理由

| 决策 | 理由 |
|---|---|
| AGP 9.5.1 + KGP 2.4.21，关闭 `builtInKotlin` | AGP 内置 Kotlin 2.2.10 读不了 Miuix 0.9.4 的 metadata 2.4.0（实测报错）。`newDsl=false` 是官方要求的配套开关 |
| 不用 Hilt | 依赖图只有十几项，手写 `CoreGraph` 省一轮 KSP、省约 1.5MB、缩短构建。`ViewModel` 用自定义 Factory |
| 玻璃面板用 `miuix-blur` 自建 | `miuix-glass` / `miuix-material` 未发布到 Maven（POM 404），只有 `miuix-blur` 可用 |
| 索引流式解析 | `Json.decodeToSequence` + `InputStream`，单测 20 万条内存增长 <96MB |
| 音频走 Releases | 50MB 文件走 Git 正文会让仓库爆炸，且 `Contents API` 需要 base64（67MB 内存峰值）。Releases Uploads 支持二进制流式 |
| 手写重定向校验 | Ktor 的自动重定向会绕过 host 白名单，必须 `followRedirects=false` 并逐跳校验 |
| Coil 复用 Ktor | `coil-network-ktor3` 少一份 OkHttp/引擎依赖 |

## 4. 线程与并发

- 所有 IO 在 `Dispatchers.IO`，解析在 `Dispatchers.Default`
- 同一资源的并发请求由 `SingleFlight` 合并
- 索引写库用 `withTransaction`，每 200 条一批，避免长事务和内存堆积
- 下载并发上限 3（可配置），转码与上传各自独占 Worker

## 5. 缓存层级

| 层 | 位置 | 上限 |
|---|---|---|
| 图片内存 | Coil `MemoryCache` | 25% maxMemory（可配 15–35%） |
| 图片磁盘 | Coil `DiskCache` | 默认 256MB（可配） |
| 音频 | Media3 `SimpleCache` | 默认 512MB（可配） |
| 索引 | Room | 全量 |
| HTTP 条件请求 | `http_cache` 表 | 按 URL |

## 6. 安全边界

1. **URL**：只有 `MirrorResolver` 能拼 URL；https + 443 + host 白名单 + 相对路径正则；重定向逐跳校验（最多 5 跳）
2. **Token**：GitHub OAuth Device Flow，存 `EncryptedSharedPreferences`（`TokenStore` 抽象），卸载即清；不上传到任何日志/崩溃上报
3. **上传**：扩展名白名单 + MediaExtractor 有效音轨 + 大小/时长限制 + 字段长度/字符集校验，通过后才读字节
4. **明文流量**：`usesCleartextTraffic=false`，无任何 trust-all 配置
5. **崩溃上报**：默认关闭，开关需明示同意

## 7. 性能手段

- Application 零 IO，Room 懒打开，首帧用缓存
- 系统 SplashScreen + 主题背景同色，无白屏
- `LazyColumn` + `key` + `contentType`，封面请求指定尺寸
- R8 + 资源收缩 + 只打 `arm64-v8a`
- 低端机 / 关闭模糊 / 无 `RuntimeShader` 时玻璃退化为半透明纯色
