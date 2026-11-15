# 镜像源接入

## 1. 概念

镜像 = 一组 URL 模板。App 只通过 `MirrorResolver` 拼接，业务层不允许出现 `https://raw.githubusercontent.com` 之类的字符串。

模板必须以 `/` 结尾，App 直接拼相对路径。

| 字段 | 用途 |
|---|---|
| `rawTemplate` | 索引、封面、featured、stats 等仓库正文文件 |
| `assetTemplate` | Releases 里的音频资产；空串表示该镜像不支持音频 |

## 2. 内置镜像

以 `owner/repo` 为 `echobox/echobox-music`、branch `main` 为例：

```text
GitHub Raw
  raw   https://raw.githubusercontent.com/{owner}/{repo}/{branch}/
  asset https://github.com/{owner}/{repo}/releases/download/audio/

jsDelivr
  raw   https://fastly.jsdelivr.net/gh/{owner}/{repo}@{branch}/
  asset （空，jsDelivr 不代理 Releases）

ghproxy
  raw   https://ghproxy.net/https://raw.githubusercontent.com/{owner}/{repo}/{branch}/
  asset https://ghproxy.net/https://github.com/{owner}/{repo}/releases/download/audio/
```

## 3. 自定义镜像

允许用户添加，规则：

- 必须 `https`
- 不允许带 userinfo、不允许非 443 端口
- host 不能是 IP 字面量、`localhost`、`.local`、`.internal`
- 模板里的 host 会被加入运行时白名单（用户添加即视为确认）
- `id` 由 App 生成（`custom-<uuid>`），用户不可改
- 支持启用/禁用、拖动排序

## 4. 选择与 fallback

1. 启动时对每个启用镜像 `HEAD index/meta.json`，3 秒超时，并发执行
2. 有 RTT 的按 RTT 升序；没测过的按优先级排在后面
3. 请求失败按顺序换下一个镜像；连续失败 10 分钟后重新参与排序
4. 全部失败 → 读本地缓存，UI 标「离线模式」

## 5. URL 校验（防 SSRF）

每次拼接都执行：

```
scheme == https
port == -1 || port == 443
userInfo == null
host ∈ 内置集合 ∪ 用户自定义集合
path 匹配 ^(index|covers)/[A-Za-z0-9][A-Za-z0-9._/-]{0,255}$
     或 ^(featured|stats)\.json$
     或音频文件名 ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$
```

重定向：

- Ktor `followRedirects = false`
- 302/307 的 `Location` 解析后重新跑一遍上面的校验
- 最多 5 跳，超过直接失败

## 6. 重置

设置页提供「重置默认」：清空所有自定义镜像，重新写入 3 个内置镜像。
「仅用自定义」模式下，内置镜像全部禁用但保留记录，方便恢复。
