# AnimeComic API 部署

生产入口为 `https://43.131.255.74:8443`，SSH 为 `ubuntu@43.131.255.74`。
本机 SSH 别名为 `ubuntuserver`。三个 Node.js 服务由 systemd 开机启动并在失败时重启，
Caddy 提供 HTTPS、证书续期和真实客户端 IP 转发。

| 路径 | systemd 服务 | 回环端口 | 客户端用途 |
| --- | --- | --- | --- |
| `/api/books/*` | `pocket-books` | 4180 | Web / App 书籍搜索、详情、关联、评论、收藏与阅读进度、签名封面及头像 |
| `/api/komiic/*` | `pocket-komiic` | 4181 | Web / App Komiic 搜索、目录、图片列表、签名正文图片 |
| `/api/web/*` | `pocket-web` | 4182 | Web Animeko / Bangumi API、账号授权、订阅及种子元数据、在线来源解析和签名媒体转发 |

App 番剧保持原 Animeko 接口；其他漫画源使用各自既有网络路径。
网页使用 `web/src/wasmJsMain/resources/api-endpoints.js` 和共享漫画路由。
签名媒体端点支持 Range 和 HLS 子清单、分片、密钥；仅允许目录来源提取的媒体。
源站登录、验证码、阅读额度及失效媒体仍按源站错误返回。

## Ubuntu 更新

版本目录为 `/srv/pocket/stage/extras/AnimeComicApi/releases/<version>`，保留仓库相对布局：

- `services/{books,komiic,web}-api/*.mjs`，包含测试文件。
- `services/deploy-ubuntu.sh`。
- `web/{animeko-relay,bangumi-relay,oauth-relay,online-relay,online-video}.mjs`。
- `web/src/wasmJsMain/resources/*.mjs` 和 `api-endpoints.js`。
- `web/tests/{animeko-relay,bangumi-relay,oauth-relay,online-relay,video-extraction}.test.mjs`。

目录须允许 `pocket` 服务账号读取。以 sudo 执行：

```bash
sudo bash <release>/services/deploy-ubuntu.sh <release>
```

脚本验证 Caddy 和后端测试，备份既有 systemd 配置，再通过独立 drop-in 切换三个服务。
它检查回环和公网 HTTPS 健康，激活失败时恢复原服务定义。共享 Caddy 路由不被覆盖。
备份位于 `/srv/pocket/stage/extras/AnimeComicApi/backups/<timestamp>`。

`ProgramData=/srv/pocket/stage/extras` 指向持久配置和日志。
书籍及 Komiic 图片密钥分别位于 `AnimeComicBooksApi/config/image-signing.key` 和
`AnimeComicKomiicApi/config/image-signing.key`；更新保留这些文件，避免缓存链接失效。
服务日志位于对应 `AnimeComic*Api/logs/service.log`，仅用于进程状态排查。

```bash
systemctl status pocket-books pocket-komiic pocket-web
systemctl is-enabled pocket-books pocket-komiic pocket-web caddy
curl --fail https://43.131.255.74:8443/api/books/health
curl --fail https://43.131.255.74:8443/api/komiic/health
curl --fail https://43.131.255.74:8443/api/web/health
```

Windows 部署使用各服务目录中的 `install.ps1`，其计划任务说明仅适用于 Windows 主机。
