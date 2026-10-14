# AnimeComic Web API

入口：`https://43.131.255.74:8443/api/web`。静态前端托管在文件服务器或 GitHub Pages，API 在 迁移后的服务器独立运行；用户电脑只需要浏览器。

## 路由契约

| 路径（相对于入口） | 方法 | 上游和用途 |
| --- | --- | --- |
| `/health` | GET | 服务健康检查 |
| `/auth/animeko/providers` | GET | Animeko 登录方式 |
| `/auth/animeko/email-otp` | POST | 原邮箱 OTP API；JSON `{email}` |
| `/auth/animeko/email-login` | POST | 原登录 API；JSON `{otpId,otpValue}` |
| `/auth/animeko/refresh` | POST | 原刷新 API；JSON `{refreshToken}` |
| `/auth/animeko/start` | GET | 原 Bangumi/GitHub OAuth；查询 `requestId,provider,mode,os,arch`；绑定需要 Animeko Bearer |
| `/auth/animeko/result` | GET | 按 `requestId` 轮询；425 等待，410 过期 |
| `/animeko/v2/subjects/{id}` | GET/PATCH/DELETE | 原详情、收藏和评分请求；写操作需要 Animeko Bearer |
| `/animeko/v2/subjects/{id}/episodes/batchUpdate` | PATCH | 原剧集状态批量更新 |
| `/animeko/v2/episodes/{id}/comments` | GET | 原剧集评论；`offset,limit,includeBangumi,after`；支持匿名读取及可选 Animeko Bearer，合并 Bangumi 评论 |
| `/animeko/v2/subjects/list`、`/count` | GET | 账号收藏列表、计数 |
| `/animeko/v2/subjects/search`、`/v2/trends/subjects` | GET | 原搜索、热榜 |
| `/animeko/v1/schedule/airing` | GET | 原新番时间表 |
| `/animeko/v2/home/recommendations` | GET | 原账号推荐 |
| `/animeko/v1/me`、`/v2/users/me` | GET、PATCH | 原个人资料读取、编辑 |
| `/animeko/v2/playback-histories/sync` | POST | 原云端历史读取；只接受空 `ops` |
| `/bangumi/v0/subjects/{id}`、`/subjects` | GET | 原作品详情和关联作品 |
| `/bangumi/v0/subjects`、`/episodes` | GET | 原浏览列表、剧集 |
| `/bangumi/v0/search/subjects` | POST | 原搜索 JSON |
| `/bangumi/p1/subjects/{id}/comments` | GET | `next.bgm.tv` 公开评论，无用户凭据 |
| `/bangumi/v0/me`、`/users/{name}/collections`、`/users/{name}/collections/{id}` | GET | Bangumi 本人资料与收藏，使用 Bangumi Bearer |
| `/bangumi/v0/users/-/collections/{id}` | POST/PATCH | 原收藏请求；POST 仅 `type`，PATCH 仅指定的进度、评分、短评、隐私字段 |
| `/bangumi/image?url=…` | GET | 固定 Bangumi / Animeko CDN 的封面和头像，无凭据 |
| `/online/subscription` | GET | App 默认网站订阅，服务端缓存一小时 |
| `/online/page?url=…` | GET | 订阅中网站的 HTML/JSON；返回 `{url,text}`，供共享 Kotlin 解析器使用 |
| `/feeds?url=…` | GET | HTTPS JSON/RSS/Atom 订阅、RSS 查询，原文返回 |
| `/torrent?url=…` | GET | HTTPS `.torrent` 元数据读取；不运行普通 BT 下载器 |

`/animeko/*` 保持 App 生成客户端的路径、参数和请求模型。Animeko 和 Bangumi 的 access token 分开使用；授权回调与 token 签发仍由 Animeko 处理。登录响应含 `tokens` 与 `user`，前端继续使用原会话适配。

漫画使用 App 的 `MangaRequestRouting`，对应现有 `/api/books/*`；Komiic 保持 `/api/komiic/*`。两者有独立服务、契约和签名图片地址，不重复实现。

## 运行和边界

- Node.js 24，`127.0.0.1:4182`；Ubuntu 的 `pocket-web` systemd 服务以 `pocket` 账号开机启动、失败重启。Caddy 的 IP 证书负责 HTTPS。部署和持久配置见 [API 部署](../README.md)。
- CORS 精确允许 `https://zhgma.github.io`、API 自身 origin，以及本地 `localhost/127.0.0.1:4173/4180` 预览。自定义域名必须在 `server.mjs` 的允许列表中配置并重新部署。
- 浏览器使用 `credentials: omit` 与 Bearer，不使用跨站 Cookie。转发只给指定上游必要头部，日志仅记录进程生命周期；不记录请求 URL、令牌、邮箱、验证码、正文或原始错误。
- 每 IP 每分钟 300 个请求、邮箱 OTP 3 次；并发每 IP 16、全局 48。错误带 CORS；限流带 `Retry-After`。
- 写操作仅发送一次，不因网络失败自动重放。读操作可在 App 的固定 Animeko 区域节点间切换。HTTP 401/403/400 保留其失败含义。
- 动态来源仅 HTTPS 443、公网 IPv4；每次重定向重新解析并固定 DNS，禁止内网、回环、云元数据地址及 URL 用户名密码。来源响应至多 2 MiB，跳转最多四次，总读取有超时；只返回规定类型的内容。
- 网站 HTML 读取限 App 默认网站订阅及已验证跳转来源。自定义订阅可读取/导入，但默认目录之外的网站执行还需扩展服务器来源策略。
- 在线视频、HLS 清单/分片/密钥由签名媒体接口转发，WebTorrent 节点仍由浏览器访问。动态 JS 嗅探、站点验证码、DRM、原生 TCP/UDP BT、完整漫画阅读能力不属于此 API 网关。

## 部署

发布目录必须保留以下相对布局，复用四个已测试的 Web 传输适配器：

```text
<release>/services/web-api/{server.mjs,server.test.mjs,run.mjs,install.ps1}
<release>/web/{animeko-relay.mjs,bangumi-relay.mjs,oauth-relay.mjs,online-relay.mjs,online-video.mjs}
<release>/web/src/wasmJsMain/resources/video-extraction.mjs
```

Ubuntu 使用 [统一部署脚本](../deploy-ubuntu.sh)。Windows 主机通过 SSH 上传到 `C:\ProgramData\AnimeComicWebApi\releases\<version>`，远端执行 `services\web-api\install.ps1 -ConfigureHttps`。Windows 脚本先跑测试，再切计划任务，检查 loopback 健康；首次挂接 HTTPS 前备份并验证 Caddy，短暂停止/启动 HTTPS 任务。激活失败会恢复已有任务/配置。

本地回归：`node --test services/web-api/server.test.mjs web/tests/*.test.mjs`。测试仅使用模拟账号，不发真实验证码、不修改收藏。

## 在线视频接口

- `GET /api/web/online/resolve?source=<目录中 searchUrl>&url=<剧集页>`：校验订阅目录来源，按 App 配置附加媒体请求头，在有界静态页面/配置中提取媒体；返回签名播放地址。
- `GET /api/web/online/media?ticket=...`：校验 6 小时 HMAC 票据，校验每次重定向的公网 IPv4 地址，流式传送媒体，支持 Range；HLS 子清单、分片、密钥和初始化文件继承原票据期限。不提供任意地址的裸代理。
- 不执行来源脚本，不处理验证码或 DRM。动态脚本播放器可能无法解析。签名密钥随进程生成，服务重启后需重新选源获取票据。
