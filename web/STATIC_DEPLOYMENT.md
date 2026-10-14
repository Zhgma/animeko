# 静态网页与远程 API 部署验收

## 架构

浏览器加载静态 HTML、JavaScript、Wasm、字体和图片资源。业务 API 使用 `https://43.131.255.74:8443/api/web`，由远程网关访问 Animeko、Bangumi、订阅和网站。用户电脑无需 Node/API 进程。漫画继续编译 App 的 `MangaRequestRouting`，使用现有书籍 API；Komiic 原有服务保持独立。

`api-endpoints.js` 声明远程入口。App 生成的 Animeko 客户端使用 `/animeko` 前缀，保留请求模型；浏览器 OAuth/邮箱适配使用 `/auth/animeko`。全部路由、鉴权、来源策略和部署方法见 [Web API 契约](../services/web-api/README.md)。

## 静态构建

使用 JDK 21，在 `web/` 执行 `../gradlew.bat :wasmJsBrowserDevelopmentExecutableDistribution` 或优化发布任务 `:wasmJsBrowserDistribution`。上传对应 `build/dist/wasmJs/developmentExecutable` 或 `productionExecutable` 的内容。资源 URL 为相对路径，导航使用 hash，可在 GitHub Pages 仓库子路径运行。静态服务器需支持 Wasm MIME。

本地 `serve.mjs` 只提供静态文件和 `/api/health` 启动诊断。生产静态站无需实现任何 `/api/*`。API CORS 已精确允许 `https://zhgma.github.io`；自定义 Pages 域名需要添加到服务允许列表。

## 2026-10-12 本次部署证据

- 新服务部署在 `C:\ProgramData\AnimeComicWebApi\releases\20261012-web-api-v1`，计划任务 `AnimeComic-WebApi` 开机启动、失败重启，只监听远端 `127.0.0.1:4182`；现有 Caddy 和 IP TLS 证书提供公网入口。
- 公网正常验证证书，以 GitHub Pages Origin 请求：健康、登录方式、公开番剧详情、热榜、剧集、评论、默认网站订阅、AnimeGarden RSS 均 HTTP 200，允许 origin 精确匹配。PATCH 预检 204；无凭据写入 401；未许可 origin 403。
- Bangumi/GitHub OAuth 开始请求均 200，返回对应正确授权站点；随后轮询均 425 等待。未在第三方完成授权，未发送真实邮件、未上传用户令牌、未改收藏。
- 原有书籍详情和 Komiic 健康均 200。
- 92 项 Node 回归通过，包含网关 6 项；远端部署前重复通过网关测试。23 项 Kotlin/Wasm 浏览器测试通过，静态开发分发构建成功。
- 真实浏览器从只提供文件的 `http://127.0.0.1:4180/animeko/` 加载 Compose 网页。探索内容、详情封面和播放第 1 话入口正常；账号显示邮箱页面和 Bangumi/GitHub 登录按钮。
- 独立浏览器只读验收：登录方式 `bangumi, github`；番剧 622288 共 14 个剧集记录；默认订阅 18 个来源；RSS 成功；网站搜索返回 41679 字符。请求目标均为远程 API，纯文件服务器本身没有这些接口。
- 本机原 4173 进程保持运行。本次使用独立源码快照构建和 4180 文件服务验证，未覆盖主任务的构建产物。

证据保存在仓库外 `../artifacts/static-web-repair-20261012/`，包括 remote-node-tests.log、remote-build.log、remote-install.log、remote-browser-results.json、remote-oauth-probe.json 和截图。

## 明确未完成的范围

- 真实邮箱登录、OAuth 完整授权、令牌刷新、收藏写入：传输契约已回归测试，本轮未对真实账号做写操作。
- 视频 CDN/HLS 分片使用签名流式 API。动态 JS 嗅探、验证码、DRM 和浏览器不支持的编码仍需要单独适配。实际验证范围见下方播放页记录。
- 订阅可读取/导入；网站 HTML 执行限 App 默认网站订阅及其验证过的跳转来源。额外自定义网站需要扩展服务器来源策略。
- WebTorrent 仍是浏览器 WebRTC/Web Seed；网关可读取 .torrent 元数据，不运行普通 TCP/UDP BT 下载器。
- 完整原生 ViewModel、Room、播放和漫画阅读适配仍按 [功能差异清单](PARITY.md) 记录。API 部署不代表全部页面与 App 已完全对齐。

## 数据源管理与 Pages 发布包（2026-10-12）

- 默认链接由 `DefaultMediaSubscriptions` 提供，App 的原生默认配置和 Web 同时引用：`bt1.json`、`css1.json`、仓库 `subs/manga.json`。首次迁移只追加缺失订阅；保留已有配置，手动删除不会在刷新页面后自动恢复。
- 直接共享 `AddMediaSubscriptionDialog`、`MangaSubscriptionPresentation`、订阅组框架和订阅/来源行。漫画订阅的编辑、刷新、开关、复制与删除使用同一份 Compose 界面；浏览器存储与请求是 Web 适配。
- 已适配批量启用/禁用/删除、默认订阅恢复、更新失败保留旧来源、单源配置导出。漫画原生适配器和阅读器仍未接入；原生源编辑器、测速、来源排序、临时启用仍未迁移，参见 `PARITY.md`。
- 以 Pages Origin 请求三个默认目录均 HTTP 200，分别包含 2 / 18 / 7 个来源。17 个 HTTPS 网站规则可执行解析；这不代表每个站点的视频播放均已验证。
- 92 项 Node 测试、23 项 Kotlin/Wasm 浏览器测试、2 项 App 漫画订阅 Compose 交互测试通过。源码 zip 解压后独立编译通过。浏览器验证了桌面、手机、1060×450 iframe 横屏布局和单源复制结果（仅 1 项）；未操作平板。
- Windows 发布构建：`./web/scripts/build-pages.ps1`。它执行 Kotlin release 编译、Binaryen `-O1`，再生成正式静态分发；主 Wasm 约 15.9 MiB，Skiko 约 8.24 MiB。默认 Gradle 高强度优化耗时较长，发布脚本选择较短的确定性优化流程。
- 打包：`node web/scripts/prepare-pages.mjs <仓库外发布目录>`。包包含 `.nojekyll`、静态资源、版本清单以及可独立编译的 `source.zip`，关于页提供源码下载。共享 Kotlin 保持原项目路径；不需要提交主工作目录的其他 App 改动。
- 本机 4173 和 4180 服务继续运行；4180 的 `/animeko/` 用于检验 GitHub Pages 项目子路径。`gh-pages` 分支提交后由仓库现有 Pages 工作流自动部署，无需额外登录。


## 当前公网入口

- 网站：[AnimeComic Web](https://zhgma.github.io/animeko/#settings/sources)。GitHub Pages 正式构建部署已成功；分支 `gh-pages`，主工作目录与主分支没有打包提交。
- [对应源码](https://zhgma.github.io/animeko/source.zip)也可通过网页“设置 → 关于 → 下载源码”获取。
- 正式 JS 与主 Wasm 从公网下载后 SHA-256 与本地发布包一致。首页、版本清单、脚本和 Wasm 返回 HTTP 200。
- `prepare-pages.mjs` 给入口和独立浏览器模块加入同一版本参数，避免 Pages 缓存旧脚本。Webpack 主 bundle 原样保留，不能改写其内部 Wasm import table。

## 2026-10-13 播放页构建

- 原目录共享 App 播放页布局、标题与剧集网格；Web 平台用 HtmlElementView 连接 HTML video/HLS。对应源码包同时包含 `services/web-api`、视频提取器与签名媒体转发实现。
- Node 111 项回归、Kotlin/Wasm 23 项测试通过；App 选源相关 Compose 14 项测试通过。生产构建通过。
- 本地 4210 静态站配合 4212 API 验证稀饭备用线路 HLS 实播 1920×1080，倍速控制和第二集切换生效。原有 4173/4180 服务保持运行。
- API 已迁移到 `43.131.255.74:8443`，旧主机任务停用。新主机基础接口正常，但 `/api/web/online/resolve` 返回 404，尚未部署此播放页所需的视频提取后端；本地 API 实播验证不代表公网播放可用。
- 动态 JS、验证码、DRM、弹幕、外挂字幕和播放进度云端写入不在当前已验证功能中。手机布局验证使用浏览器尺寸模拟，不是真机。

## 2026-10-13 API 地址迁移验收

- 更新网页网关、App 共享 Books/Komiic 默认地址和部署健康检查。旧服务器保持停用。
- TLS 校验开启，Web、Books、Komiic 健康、番剧与漫画详情均 HTTP 200；Pages 和本地静态站 Origin 允许跨域。
- 4180 真实浏览器使用现有会话加载追番和追漫收藏成功。迁移后 Books API 仍返回旧主机封面地址，网页只迁移已知 API 的图片路径并保留签名；新主机图片请求 HTTP 200。
- 148 项 Node 回归及 Kotlin/Wasm 浏览器测试通过；图片迁移另有回归测试。已有平板安装包未修改，需要重新构建才能使用新默认地址。
- 新服务器视频提取接口仍需更新服务版本，基础接口连通不代表该接口已部署。
