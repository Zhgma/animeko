# AnimeComic 书籍 API

公网入口：`https://119.28.149.19:8443/api/books`。TLS 使用服务器现有的 IP 证书。
独立 Node.js 24 服务只监听 `127.0.0.1:4180`，Caddy 转发 `/api/books/*`。

## 接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/v0/search/subjects?limit=20&offset=0` | 搜索 BGM 书籍 |
| GET | `/v0/subjects/{id}` | 书籍详情；非书籍返回 404 |
| GET | `/v0/me` | 当前 BGM 用户的 id 和 username |
| GET | `/v0/users/{username}/collections?subject_type=1&type=3&limit=30&offset=0` | 本人书籍收藏列表 |
| GET | `/v0/users/{username}/collections/{id}` | 本人书籍收藏状态、话数/卷数等 |
| POST | `/v0/users/-/collections/{id}` | 新建或修改书籍收藏状态 |
| PATCH | `/v0/users/-/collections/{id}` | 局部更新书籍收藏状态、话数/卷数 |
| GET | `/health` | 进程健康检查，不请求 BGM |

机器可读契约：[openapi.yaml](openapi.yaml)。

搜索 JSON：

```json
{"keyword":"碧蓝之海","sort":"match","filter":{"type":[1]}}
```

`keyword` 必填，去除首尾空格后 1–100 字符。`sort` 可选 `match`（默认）、`rank`、`heat`、`score`。
`filter` 只接受 `type:[1]` 和可选布尔值 `nsfw`；省略 `type` 时由服务器补为 `[1]`。
包含漫画、小说、单行本等全部 BGM `type=1` 书籍，不按 `platform` 限定为漫画。
`limit` 为 1–20，`offset` 为 0–1000。拒绝未知字段、未知或重复查询参数。

成功响应保留 BGM v0 的 JSON 结构：搜索含 `total`、`limit`、`offset`、`data`；
详情含 `id`、`type`、`name`、`name_cn`、`images`、`summary`、`rating`、`infobox`、`tags` 等原始字段。
错误响应为 `{"error":"错误码"}`。常见状态为 400 参数错误、404 书籍不存在或类型不符、
405 方法不允许、413 请求过大、415 内容类型错误、429 限流、502/503/504 上游故障。
429/503 提供 `Retry-After`；调用方应退避，不立即循环重试。

### 收藏状态和阅读进度

账号/收藏接口要求 `Authorization: Bearer <当前用户的 Bangumi access token>`，不是 Animeko 的 JWT。
应用复用现有 BGM 登录结果。服务器用 `/v0/me` 核验当前用户，`username` 必须为本人 username 或 `-`。
列表强制 `subject_type=1`，最多每页 30 条，offset 上限 10000，type 可选 1–5；无 type 时读取全部分类。
单条读取和写入先验证目标 BGM 条目为书籍。Token 只在该请求内传给固定 BGM HTTPS 上游，不持久保存；
个人响应不进入公共缓存、不合并跨用户请求，均返回 `Cache-Control: no-store`。

状态对应 `1=想读、3=在读、2=读过、4=搁置、5=抛弃`。

```json
{"type":3}
```

POST 只接受上面的 type 字段。PATCH 接受非空的 `type`、`ep_status`、`vol_status` 子集，例如：

```json
{"ep_status":12,"vol_status":2}
```

话数/卷数为 0–1000000 的整数，允许手动修正为较小的进度。省略字段不提交，保留评分、评价、标签、
隐私和其他进度。保留 BGM 写入响应的 202 Accepted 或 204 No Content；202 表示已接受，调用方可回读确认最终状态。
失败不会伪装成功或自动重放写操作。BGM 返回 401/403 时保留其授权含义；
不存在的收藏或非书籍返回 404。PATCH 不创建未收藏条目，先用 POST 标记收藏状态。
服务不接受代替他人写入、评分/评论/隐私修改、删除收藏或动画剧集进度请求。

## 范围和资源限制

- 固定访问 `https://api.bgm.tv`，使用 Node HTTPS 直连；不使用环境代理、不接受上游 URL、不跟随重定向。
- 公开搜索/详情拒绝 `Authorization`；本人书籍收藏接口要求 Bearer 凭据。所有接口拒绝 Cookie，不代理评分写入、评论、关联、剧集、封面图片或阅读资源。
- 受 BGM 登录权限限制的数据不保证可访问，NSFW 参数不绕过 BGM 的授权规则。
- 每 IP 平均 60 次/分钟，突发 30 次；全局平均 300 次/分钟，突发 60 次；限流包含缓存命中。
- 每 IP 最多 6 个并发数据请求，适配应用最多 4 条目并行读取；全局最多 8 个，最多 128 个 HTTP 连接。
- 收藏写入另有每 IP 平均 30 次/分钟、突发 10 次的限制。
- 请求体最多 4 KiB，上游响应最多 2 MiB，单个上游请求超时 7 秒；账号流程的核验和读写共享 15 秒总时限。
- 搜索缓存 60 秒，书籍详情缓存 1 小时；404 缓存 1 分钟、非书籍拒绝缓存 5 分钟。
- 相同请求合并；缓存最多 1000 项/16 MiB。上游 429 触发全局 15 秒退避。
- API 支持跨域。账号接口需显式提供 Bearer，不接受浏览器 Cookie 凭据；公共接口的范围和限流不构成只有某个 APK 能调用的身份认证。
- Caddy 覆盖 `X-Real-IP`，后端仅信任回环代理；外部不能通过自报转发头绕过每 IP 限流。

## 应用接入

复用 `MangaBangumiHttp` 和现有 JSON 解析，不修改 Animeko 的自动生成客户端。
`MangaBangumiNetwork` 提供 `BOOKS_API_BASE_URL`，将书籍搜索、基础详情、本人身份/收藏查询及收藏状态/阅读进度写入路由到本服务。
公开请求不发送用户 BGM token，账号请求复用当前用户的 token。评分写入、评论和关联使用原地址；番剧继续使用原有 Animeko/BGM 接口。
手动进度与阅读器自动完成一话调用同一个 `MangaLibraryRepository.setProgress`，真实同步成功才刷新应用状态。
客户端账号请求总超时为 20 秒，公开请求为 8 秒。
本机缓存仍使用已有 `MangaBangumiCache`。已安装的旧应用需要更新构建后才会使用新入口。

## 验证与部署

```powershell
node --test services/books-api/server.test.mjs
node services/books-api/smoke.mjs
```

上传该目录到服务器独立版本目录，然后在管理员 PowerShell 执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -ConfigureHttps
```

目录：`C:\ProgramData\AnimeComicBooksApi`。
计划任务：`AnimeComic-BooksApi`，以 `LOCAL SERVICE` 身份开机启动，失败后每分钟重启，最多 999 次。
运行时独立复制，Node 堆上限 128 MiB；日志位于 `logs\service.log`，不记录搜索词、账号凭据或响应内容。
`install.ps1` 检查服务和 Caddy 配置，备份原配置，只有服务健康后才加入 HTTPS 路由。
服务器 Caddy 管理 API 关闭，应用配置时会短暂重启 `WatchTogether-HTTPS` 任务。
服务健康检查失败恢复已有计划任务；HTTPS 健康检查失败自动恢复原 Caddy 配置。现有站点路径和 HTTPS 证书管理沿用原配置。

检查与重启：

```powershell
Get-ScheduledTask -TaskName AnimeComic-BooksApi
Get-Content C:\ProgramData\AnimeComicBooksApi\logs\service.log -Tail 20
Stop-ScheduledTask -TaskName AnimeComic-BooksApi
Start-ScheduledTask -TaskName AnimeComic-BooksApi
```

回滚服务可从 `backups\时间戳\task.xml` 恢复原任务；首次部署回滚可停用 `AnimeComic-BooksApi`，
移除 Caddy 中 `BEGIN ANIMECOMIC BOOKS` 至 `END ANIMECOMIC BOOKS` 的块并恢复原来的 401 响应，
校验配置后重启 HTTPS 任务。恢复整份 Caddy 备份前应核对期间其他服务对配置的修改。
