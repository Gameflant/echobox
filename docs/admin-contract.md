# EchoBox ↔ EchoBox Admin 对接契约 v1

面向审核软件（EchoBox Admin）开发者。App 端实现以本文件为准，字段改动必须同步升 `schema_version`。

## 1. 仓库拓扑

| 仓库 | 权限 | 说明 |
|---|---|---|
| prod | App 只读 | 正式曲库，仅审核软件写入 |
| staging | App 只写自己的投稿 | 投稿桶，审核软件读取/清理 |

两个仓库使用同一个 owner 下的两个仓库，默认名 `echobox-music`（prod）与 `echobox-music-staging`（staging），分支 `main`。

## 2. prod 结构

```
index/
  tracks.json                  # 顶层数组，全量索引
  meta.json                    # 全量索引版本信息
  incremental/
    manifest.json              # 增量文件清单
    2026-11-14.json            # 增量文件（数组）
covers/<track_id>.webp         # 800x800 webp，放仓库正文
featured.json                  # 推荐位
stats.json                     # 下载/播放统计，App 只读
Releases tag "audio"           # 音频资产：asset 名 <track_id>.<ext>
```

**音频必须在 Release asset 里**，仓库正文只放索引、封面、配置。逻辑路径 `audio/<file>` 即 `Releases/audio/<file>`。

### 2.1 `index/tracks.json`

顶层是 JSON 数组（App 用 `decodeToSequence` 流式读，内存恒定）。单条：

```json
{
  "id": "20261114-a3f9c1",
  "title": "曲名",
  "author": "作者名",
  "author_contacts": { "bilibili": "", "netease": "", "soundcloud": "", "other": "" },
  "game": "Undertale",
  "tags": ["boss", "piano"],
  "series": { "name": "OST 重编曲合集", "index": 3, "total": 12 },
  "authorization": "self_made",
  "resource_license": "CC BY-NC 4.0",
  "file": "20261114-a3f9c1.mp3",
  "format": "mp3",
  "duration": 183,
  "cover": "20261114-a3f9c1.webp",
  "hash": "sha256:3f9c1a...",
  "uploaded_at": "2026-11-14T10:00:00Z"
}
```

约束：

- `id`：`yyyyMMdd` + `-` + 6 位小写 hex（取音频 sha256 前 6 位，冲突时取 8、10 位）。禁止自增。
- `tags`：每个 1–32 字符，最多 12 个，去重。
- `series`：可选；`name` 为空时整个字段省略。
- `authorization`：只有 `self_made` / `authorized_repost`。
- `resource_license`：CC BY 4.0 / CC BY-NC 4.0 / CC BY-NC-SA 4.0 / CC0 / 自定义协议名。自定义时格式为 `名称 | 链接`。
- `hash`：`sha256:` + 64 位小写 hex。
- `uploaded_at`：ISO-8601 UTC，带 `Z`。

### 2.2 `index/meta.json`

```json
{ "schema_version": 1, "count": 4213, "generated_at": "2026-11-14T10:00:00Z" }
```

`count` 必须等于 `tracks.json` 的数组长度。App 用它判断是否需要重新拉全量。

### 2.3 `index/incremental/manifest.json`

```json
{
  "schema_version": 1,
  "files": [
    { "name": "2026-11-14.json", "from": "2026-11-14T00:00:00Z", "to": "2026-11-14T23:59:59Z", "count": 12 }
  ]
}
```

按 `to` 升序。App 取 `to > last_sync` 的文件依次应用；超过 31 天未同步则直接拉全量。

### 2.4 增量文件

```json
[
  { "op": "upsert", "track": { /* 与 2.1 单条完全一致 */ } },
  { "op": "delete", "id": "20261001-b7e2d0" }
]
```

- 同一个 `id` 一天内多次 upsert 以最后一次为准。
- `delete` 只带 `id`。
- 文件生成后不可修改（App 可能已缓存 ETag）。

### 2.5 `featured.json`

```json
{ "updated_at": "2026-11-14T10:00:00Z", "items": [ { "track_id": "20261114-a3f9c1", "badge": "编辑推荐", "blurb": "一句话介绍" } ] }
```

### 2.6 `stats.json`

```json
{ "updated_at": "2026-11-14T10:00:00Z", "tracks": { "20261114-a3f9c1": { "downloads": 12, "plays": 340 } } }
```

App 只读，不上报。

## 3. staging 结构

```
pending/<record_id>.json      # 投稿元数据（Git Contents API 写入）
status/index.json             # 审核状态（Admin 写）
Releases tag "staging-inbox"  # 音频与封面 asset：<record_id>.<ext> / <record_id>.webp
```

Admin 初始化 staging 仓库时必须预建 `staging-inbox` release（tag 已存在），否则 App 无法上传 asset。

### 3.1 `pending/<record_id>.json`

```json
{
  "schema_version": 1,
  "record_id": "rec_20261114102012_a3f9c1",
  "submitted_at": "2026-11-14T10:20:12Z",
  "app": { "name": "EchoBox", "version": "0.1.0", "min_sdk": 34 },
  "form": {
    "title": "曲名",
    "author": "作者",
    "author_contacts": { "bilibili": "", "netease": "", "soundcloud": "", "other": "" },
    "game": "Undertale",
    "tags": ["boss"],
    "series": { "name": "合集", "index": 3, "total": 12 },
    "authorization": "self_made",
    "resource_license": { "name": "CC BY-NC 4.0", "url": "" }
  },
  "audio": {
    "asset_name": "rec_20261114102012_a3f9c1.mp3",
    "sha256": "…64 hex…",
    "size": 5242880,
    "duration": 183,
    "format": "mp3"
  },
  "cover": {
    "asset_name": "rec_20261114102012_a3f9c1.webp",
    "sha256": "…64 hex…",
    "size": 88123,
    "width": 800,
    "height": 800
  },
  "consents": [
    { "key": "original_or_authorized", "accepted": true, "accepted_at": "2026-11-14T10:20:10Z" },
    { "key": "playable_audio", "accepted": true, "accepted_at": "…" },
    { "key": "liability", "accepted": true, "accepted_at": "…" },
    { "key": "takedown", "accepted": true, "accepted_at": "…" }
  ]
}
```

`consents` 四个 key 固定，缺任何一个或 `accepted != true` 的投稿一律驳回。`accepted_at` 是客户端时间戳，作为审计凭证。

### 3.2 `status/index.json`

```json
{
  "schema_version": 1,
  "updated_at": "2026-11-14T12:00:00Z",
  "records": {
    "rec_20261114102012_a3f9c1": {
      "state": "approved",
      "reason": null,
      "track_id": "20261114-a3f9c1",
      "updated_at": "2026-11-14T12:00:00Z"
    }
  }
}
```

- `state`：`pending` | `approved` | `rejected`
- `rejected` 时 `reason` 必填，≤200 字，纯文本（App 直接展示）
- `approved` 时 `track_id` 必填
- App 启动和进入「我的投稿」时各拉一次，带 `If-None-Match`

## 4. 审核动作

### approve

1. 从 `staging-inbox` 下载两个 asset，校验 sha256 与 size 与 meta 一致；不一致直接 reject（理由 `校验失败`）。
2. 用 `ffprobe`/MediaExtractor 再确认是可直接播放的音频。
3. 生成 `track_id`（规则见 2.1），把音频上传到 prod 的 `audio` release，改名 `<track_id>.<ext>`。
4. 封面转存为 `covers/<track_id>.webp`。
5. 追加 `tracks.json`（整份重写要保证原子性：写入临时文件 → 校验 count → 覆盖），同时更新 `meta.json`。
6. 追加当天的增量文件，并更新 `manifest.json`（先写增量文件，最后写 manifest，避免 App 拉到不存在的文件）。
7. 在 `stats.json` 里初始化该 id 的 `downloads/plays = 0`。
8. 更新 `status/index.json` 为 approved。
9. 删除 staging 的 pending 文件与两个 asset（asset 删除不可恢复，先确认 status 已写入）。

### reject

1. 写 status（state=rejected + reason）。
2. 删除 staging 的音频 asset、封面 asset、pending 文件，不保留副本。

## 5. 硬性要求

- 所有请求走 `https://api.github.com`，**不要**跟随不可信域名重定向。
- 写文件前校验目标路径只匹配 `^(pending|status|covers|index)/[A-Za-z0-9._/-]+$`。
- 任何来自 pending 的字符串都要做长度与控制字符校验后再落库/落文件。
- prod 的 `tracks.json` 用「写临时文件 → 校验 → 覆盖」的方式更新，避免 App 拉到半份 JSON。
- 不要修改已发布的增量文件。
