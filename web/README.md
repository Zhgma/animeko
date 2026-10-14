# AnimeComic Web

网页以静态文件部署，API 通过 `https://43.131.255.74:8443/api/web` 访问 当前远程服务器上的远程服务。`serve.mjs` 仅用于本地静态预览；用户电脑无需 Node 转发进程。漫画继续使用 App 的原有 API。路由、部署与边界见 [Web API](../services/web-api/README.md) 和 [静态部署验收](STATIC_DEPLOYMENT.md)。


Kotlin/Wasm + Compose Multiplatform 静态网页预览。已接入追漫、追番、Animeko 热榜和搜索、站内详情、评论、收藏编辑、云端播放历史、账号资料和部分设置。已接入 WebTorrent、RSS 订阅和浏览器媒体播放；漫画正文及完整原生播放流程尚未接入。完整分类与差异见 [功能清单](PARITY.md)。

## 构建与预览

需要 JDK 21 和 Node.js。工作区已有的 JBR 可由脚本自动找到；其他环境使用 `JAVA_HOME`。

```powershell
cd web
./build.ps1 -Task wasmJsBrowserDevelopmentExecutableDistribution
node serve.mjs --development
```

当前源码及功能对齐状态见 [对齐清单](PARITY.md)。邮箱登录、验证码、账号菜单、头像、主题选择和收藏加载控制层与 App 共用源码。

Windows 需要持续运行时执行 `./start.ps1`，它启动独立隐藏后台进程；重复执行会检查服务并复用现有进程。日志位于 `build/server/`。使用环境代理时执行 `./start.ps1 -UseEnvironmentProxy`（Node.js 24），代理值由当前环境提供。此方式不依赖当前终端持续打开，但未配置开机自启。

打开 `http://127.0.0.1:4173`。开发预览产物在 `build/dist/wasmJs/developmentExecutable`。

发布构建执行 `./build.ps1`，随后使用 `node serve.mjs` 预览；Wasm 多轮优化可能需要较长时间。也可在此目录执行 `../gradlew.bat wasmJsBrowserDistribution`。完整静态产物位于 `build/dist/wasmJs/productionExecutable`，部署时服务器应为 `.wasm` 返回 `application/wasm`。

## 共享范围审查

完整逐页审查见 [功能与源码审查清单](PARITY.md)。每次 `build.ps1` 成功后生成 `build/shared-source-audit.json`；也可运行 `node audit-shared-sources.mjs --list` 查看实际参与编译的 App Kotlin 文件及尚未纳入 Web 的 App 改动。

书籍路由 / 令牌规则使用 App 的 `MangaRequestRouting.kt`，搜索 JSON 和每页 20 条使用 `BookSearchRequest.kt`。共享文件修改后需重新构建并刷新网页；原生 `commonMain` 中其他文件不会自动进入 Web。账号登录已经使用 Animeko 的邮箱 / OAuth 接口，账号资料管理尚未完整移植。

## 与 App 共用同一份页面源码

`web/` 是独立 Gradle 工程，复用根目录版本目录和 Wrapper，不修改主工程的 settings、构建约定或模块依赖。以文件白名单直接编译原目录中的源码，不维护另一套页面副本：

- `ui-subject/.../ui/manga/MangaCollectionContent.kt`：完整追漫页面，包含登录提示、分类、数量、分页、下拉刷新和收藏网格。原生路由与 Web 入口都调用 `MangaCollectionPageContent`。
- `ui-subject/.../ui/manga/TrackingCollectionLayout.kt`：追番/追漫切换及漫画卡片。
- `ui-subject/.../ui/subject/collection/CollectionPageLayout.kt`：原有收藏页面框架、分类栏与分页器。
- `ui-adaptive/.../ui/adaptive/AniTopAppBar.kt` 及 `navigation/`：App 顶栏和自适应导航组件。
- `ui-foundation`：窗口尺寸、间距、输入类型检测与刷新组件。
- `app-platform/.../ui/theme/DefaultSeedColor.kt`：原有主题种子色；使用同版本 MaterialKolor。
- `app-data/.../manga/BangumiMangaCollectionLoader.kt`：原有收藏数据模型、分页、漫画分类过滤和进度映射。
- `web/app-language`：直接编译 App 的 Lang 及原有字符串资源。

共享展示代码仍放在原有功能目录、沿用原包名。原生 ViewModel、导航、账号服务留在原模块；浏览器入口注入状态及事件回调。浏览器图片、窗口、账号及网络适配集中在 `web/`。这让后续合并上游时主要检查少量展示层提取改动，但仍需要处理上游对共享页面的变更。

## Bangumi OAuth

授权协议与 App 的 `BangumiOAuthClient` 相同：

1. 生成随机 UUID，调用 `GET /v2/users/bangumi/oauth` 获取授权链接。
2. 在 Bangumi 网站登录并授权。OAuth 回调和换取 token 由 Animeko 服务端处理。
3. 每秒调用 `GET /v2/users/bangumi/result`；HTTP 425 表示尚未授权。
4. 保存返回的 Animeko / Bangumi token、刷新令牌与有效期。会话过期时调用 `POST /v2/users/auth/refresh`。

App 与 Web 共用 `app-data/.../domain/session/auth/OAuthPolling.kt` 的轮询逻辑。网页用 `crypto.randomUUID()` 创建请求标识，弹窗、取消与会话存储由 `oauth.js` 适配。退出会清除会话；取消后到达的结果不会重新登录。

网页账号适配请求 winserver 的账号 API，由服务端调用 Animeko 原邮箱、OAuth 和刷新接口，不包含 OAuth client secret。番剧生成客户端使用远程 `/api/web/animeko`；Bangumi 公共信息、评论、图片、订阅和网站读取使用对应远程路由。漫画直接复用 `MangaRequestRouting` 中已有的 HTTPS 书籍 API 和凭据规则。`api-endpoints.js` 集中声明浏览器入口地址。

账号入口复用 App 邮箱和验证码页面，通过 Animeko 原接口登录；支持服务端发现的 Bangumi / GitHub OAuth、登录后关联与会话刷新。昵称编辑接入生成客户端和共享校验；头像、解绑、合并冲突等剩余差异见 [网页与 App 复用及功能对齐清单](PARITY.md)。

## 当前边界

- 手机使用底部导航，电脑使用侧边导航；追漫页内部布局直接由 App 代码决定。
- 首次进入显示 App 未登录提示，头像打开原账号菜单，登录进入原邮箱页面。示例收藏仅保留为测试资源，不在用户设置中显示。
- 通过 Animeko 的 Bangumi OAuth 授权登录，授权结果自动保存到标签页会话。可在站内详情编辑收藏、进度与评价；仅点击保存后提交变更字段。番剧收藏状态已用真实接口验证写入与刷新读回；评分、评论及剧集进度使用模拟传输测试，未对真实账号执行。
- 点击漫画/动画进入本站 `#subject/编号`；Bangumi 外链仅由显式按钮打开。详情使用 App 的分栏、手机页头、简介、信息表、统计及评分组件；详情可手动搜索订阅种子并加入 WebTorrent 下载；漫画正文阅读与自动验证可读性未实现；下载页支持浏览器媒体播放。
- 封面经远程图片 API 读取，在 Compose 内绘制，遵循原页面的圆角、裁剪和层级；上游网络故障时显示占位图。
- 浏览器使用普通不透明页面背景；原生窗口拖动与模糊背景效果由浏览器适配层处理。

搜索复用原输入框、番剧/漫画菜单、结果排序与布局工具栏、封面网格及列表卡片；设置复用原账号横幅、四组分类与完整主题分组。搜索历史与路由由浏览器适配。探索使用原热度轮播、继续观看卡片、推荐封面卡片与分区标题，追番使用原收藏布局与卡片容器。页面状态和浏览器请求由 Web 适配，不能等同完整 App 页面全部复用。未实现的设置分类只有说明，不计作功能完成。

## 验证

下述历史真实账号和在线播放结果来自启用本机转发的环境，不是纯静态部署证据。当前纯静态验收以 [STATIC_DEPLOYMENT.md](STATIC_DEPLOYMENT.md) 为准。

当前验证以 [PARITY.md](PARITY.md) 为准：Node 80 项、Kotlin/Wasm 浏览器 16 项测试通过，原生三个相关模块编译通过；真实收藏、搜索、热榜、账号资料和云端历史已在浏览器检查。下文为此前验证记录，未在本轮全部重跑。


2026-10-11：开发版 Wasm 静态构建通过；浏览器检查覆盖 390×844 / 1440×900、分类切换、浅深色、滚动及图片与顶栏的裁剪层级。当前完整发布优化未完成，交付包为开发预览。

原生 `MangaCollectionPageTest` 的 3 项 UI 测试通过，覆盖分类顺序、数量徽标、卡片点击及分页交互；漫画数据仓库 4 项测试通过。浏览器适配测试：

```powershell
node --test tests/*.test.mjs
```

浏览器适配及转发层 38 项测试、共享页面及收藏/详情逻辑 16 项原生测试通过；此前共享轮询 2 项 Kotlin 测试通过。真实授权链接与等待状态接口、公开条目和封面转发已验证；未代用户确认 Bangumi 授权，真实账号会话与收藏读取尚未完成端到端验证。浏览器实际检查了手机探索、中文搜索、站内详情与评论，以及桌面详情/设置，尚未完成全 App 像素对比与手机真机验证。

## 资源

需要支持 Wasm GC 的现代浏览器。首次打开会下载 Compose、Skia 与中文字体。Noto Sans SC 字体使用 SIL Open Font License，许可证随资源提供。App 图标从原桌面资源在构建时复制，不单独维护网页副本。

## 浏览器下载和订阅

进入“设置 → 数据源管理”，添加 RSS/Atom 或 Animeko `mediaSources` JSON 链接；也可导入文件。展开 RSS 条目可添加下载，JSON 中的 RSS v1 来源支持关键词搜索。支持启停、更新、删除、导出链接，自动更新默认关闭，只有网页打开时运行。

配置跨端迁移：在“设置 → 设置备份”复制配置，粘贴到 App 同名页面恢复；App 复制的设置也可在网页粘贴恢复，备份页直接复用 App 的两个操作及确认弹窗，浏览器剪贴板不可用时才显示手动复制或粘贴框。共享主题、界面设置即时生效，网页未实现的 App 配置保留供再次导出；不迁移账号令牌和 PikPak 登录信息。Kotlin JSON 保留 64 位颜色整数和未知字段。网页 BT 偏好使用 `animecomicWebSettings` 扩展，App 忽略此字段，不能经 App 再导出恢复网页专用参数。

数据源配置在“设置 → 数据源管理”单独使用“粘贴配置”“复制配置”“复制全部配置”。支持 App 的 `mediaSources` 剪贴板格式与 `exportedMediaSourceDataList` 订阅包装；输出为原生剪贴板格式。未知 factory/version 保留原始配置但不执行。复制的是配置快照，不包括启停状态；远程订阅自动更新关系通过“复制链接”迁移。本地 RSS 文件条目没有对应的 App 来源配置，不包含在配置导出中。剪贴板权限不可用时可手动粘贴、选择复制或使用文件。

进入“下载”，可添加磁力链接、Info Hash、种子 URL 或导入 `.torrent`。支持真实进度、文件选择、停止/继续、保存到本机；可解码的音视频文件有“播放”按钮。设置中的 WebTorrent 限速、连接数、WSS Tracker、Web Seed 和播放倍速实际生效。HTTP Web Seed 需正确响应 Range，远程订阅和 Web Seed 需允许 CORS。普通 TCP/UDP 节点不兼容浏览器 WebTorrent，需额外下载服务。

下载、RSS、web-selector 和账号适配均为静态前端代码。远程 API、订阅和网站需允许浏览器跨域请求；上游拒绝时显示错误，不会启用本机或公共转发服务。关闭网页停止任务，再打开不会自动恢复传输，点击“继续”重新校验。缓存可能被系统回收，保存到本机的文件独立保留。

自有验证资源位于 `src/wasmJsMain/resources/samples/`，仅用于本地 `127.0.0.1:4173` 验证；不会自动添加到用户账号。完整功能差异及本轮证据见 [PARITY.md](PARITY.md)。

已实测可直接读取的远程 RSS 示例：`https://api.animes.garden/feed.xml?page=1&pageSize=3`。App 自带 `sub.creamycake.org/v1/bt1.json` 格式可导入，但目前该服务没有开放 CORS，需先下载文件再导入；其中源地址是否可请求须分别验证。来源可读与种子存在可连接的 WebRTC 节点是两个独立条件。

番剧详情通过与 App 共用的选集/播放控件进入统一数据源面板：自动匹配、手动查找和 BT 资源。初始模式读取 App 备份中的 `mediaSelectorSettings.preferKind`；模式切换不修改此设置。剧集来自 App 生成的 Animeko 客户端。漫画种子仍进入 BT 列表。网页查询会话和 HTML5/HLS 播放属于平台适配，完整自动择源和原生播放器尚未复用，见 PARITY.md。


## 非 BT 来源与探索

设置 → 数据源管理 → 数据源操作 → 添加 App 在线订阅，可接入 App 默认 `css1.json`。在线来源复用原 Kotlin CSS/JSONPath 搜索和线路解析器、手动选集组件；原生 WebView 动态嗅探仍需适配。静态视频使用 HTML5/HLS；站点动态脚本、验证、CORS 或禁止嵌入可能阻止播放。漫画来源搜索共用 App 的 MangaSourceSearch，正文阅读器尚未接入。

探索首页按 App 分为最高热度、继续观看、推荐，使用原轮播/卡片/尺寸参数，以及生成的 Animeko 热榜、推荐、收藏和时间表客户端。继续观看的原生进度策略、完整排行和时间表导航仍有差异，详见 PARITY.md。

## 漫画数据源选择

漫画详情的“数据源”使用 App 的 `MediaSelectorTopBar`、模式菜单、来源下拉框、搜索框、来源卡片和 `ManualResultsList`。电脑使用侧栏、手机使用底部面板、低高度横屏使用全屏容器。启用的 `manga-native` v1 订阅进入共享 `MangaSourceSearch` 解析器；自动匹配查询作品名并列出候选，用户明确选择后按 BGM 条目在浏览器保存来源。手动查询按来源缓存，输入时不请求，提交或重试才刷新；移除来源会取消查询并清除其结果。网页暂不探测整章可读性，完整章节及正文阅读器尚未接入，选择后提供明确的原站链接。“查找漫画种子”独立保留 BT 下载入口。
