# AnimeComic Komiic API

入口：`https://43.131.255.74:8443/api/komiic`。独立进程监听 `127.0.0.1:4181`，计划任务 `AnimeComic-KomiicApi` 使用 LOCAL SERVICE 账号。BGM 书籍 API 的进程、缓存和限流独立。

## 契约

`POST /query` 只接受 JSON `{operation, variables}`。操作名与变量如下：

| operation | variables | 响应 data 字段 |
| --- | --- | --- |
| Search | `{keyword: string}` | `searchComicsAndAuthors.comics`，含 id、title、imageUrl |
| Chapters | `{comicId: string}` | `chaptersByComicId`，含 id、serial、type、size |
| Images | `{comicId: string, chapterId: string}` | `imagesByChapterId`，含 kid、width、height、url |

关键词去空白后 1–100 字符；ID 为 1–10 位正整数字符串。拒绝其他字段、任意 GraphQL、mutation、URL 参数及客户端凭据。
服务器生成固定的三种 GraphQL 查询并直连 `https://komiic.com/api/query`，匿名请求，不带客户端 Cookie / Authorization，不跟随重定向。

`GET /images/{payload}/{signature}` 使用搜索封面或图片列表返回的完整签名 URL。只访问 `komiic.com/api/image/{kid}` 或 `public.komiic.com/comics/{hash}/{file}`，页图 Referer 固定为对应章节阅读页。`GET /icon` 返回固定站点图标。`GET /health` 为本地健康检查。

## 限制与错误

JSON 请求体上限 4 KiB，上游 JSON 2 MiB，图片 16 MiB，每次上游请求 15 秒。缓存总上限 1000 项 / 64 MiB；搜索 60 秒、目录与图片列表 5 分钟、图片一小时；相同并发请求合并。

JSON 每 IP 突发 30、每分钟 60 次，全局突发 60、每分钟 300 次；每 IP 4 并发、全局 6。
图片每 IP 突发 90、每分钟 240 次，全局突发 180、每分钟 900 次；每 IP 6 并发、全局 8。
只信任回环代理覆盖的 X-Real-IP。签名保护图片请求范围，不是 App 身份认证。

错误为 `{error: string}`。400 参数/凭据错误；404 路径或签名错误；405 方法错误；413 请求过大；415 非 JSON；429 限流，带 Retry-After；502 上游异常；504 超时。
上游 401/403 与 GraphQL 访问限制正常报错；上游 402 阅读额度限制映射为 429 / `komiic_reading_limit`，使 App 显示额度提示并停止传输错误重试。上游 402/429 触发 30 秒冷却，冷却期间仍可读取已有缓存。不自动重试写操作、切换出口或绕过登录/验证码/阅读额度。服务仅提供读取操作。

## 部署与验证

```powershell
node --test services/komiic-api/server.test.mjs
node services/komiic-api/smoke.mjs
powershell -NoProfile -ExecutionPolicy Bypass -File .\install.ps1 -ConfigureHttps
```

版本目录位于 `C:\ProgramData\AnimeComicKomiicApi\releases`，签名密钥位于 `config/image-signing.key`，只允许管理员、SYSTEM 和服务账号读取。密钥跨部署保留。日志只记录生命周期，不保存请求正文或账号数据。
部署前运行测试，备份计划任务与 Caddy 配置，服务健康后启用 HTTPS；失败时恢复已有任务与本次修改的 Caddy 配置。首次部署失败留下停用任务，便于排查。Caddy 配置变更需要短暂重启现有 HTTPS 任务。

App 的 `MangaSourceRepository` 调用固定操作并直接使用签名图片地址；Komiic 原站 Cookie 不发送给转发服务，其他漫画来源沿用原来的会话与网络路径。现有 Komiic 书籍/章节 ID 和阅读记录兼容，离线图片保留。
