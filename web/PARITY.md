# 网页与 App 功能、源码复用清单

> 部署边界：前端是静态文件；Animeko、Bangumi、图片、订阅和网站读取通过 winserver HTTPS API，漫画继续使用 App 的原有服务。API 契约见 [Web API](../services/web-api/README.md)，本次部署验证范围见 [STATIC_DEPLOYMENT.md](STATIC_DEPLOYMENT.md)。表中原有账号写入与播放记录属于各自历史验证轮次，不代表本次已经重做。


核对日期：2026-10-14。基准为当前工作区 AnimeComic / Animeko App（含漫画扩展）。

网页是独立 Kotlin/Wasm + Compose 工程，**部分组件和业务共用 App 源码，尚非完整 App Web target**。`web/build.gradle.kts` 按白名单编译原目录文件；App 改动只有被纳入白名单且重新构建后才在网页生效。当前共享 Kotlin 文件数由构建审计生成，含生成的 API 模型，不能解释为共享了同等数量的页面。

## 平板实机与网页对照（2026-10-14，本地版本）

使用 ADB 对照平板当前安装的 Release App：追番列表、《青之箱 第二季》（548156）详情和播放页。网页使用同一账号、同一作品，检查桌面 1205×753、手机 470×892 和低高度横屏 1060×450。网页尺寸为浏览器模拟；没有安装新的 APK，也没有手动修改收藏、订阅或设置。

| 检查项 | 当前实现 | 验证与剩余差异 |
| --- | --- | --- |
| 周更新与继续观看 | 直接编译原目录 `SubjectProgressInfo`、`SubjectProgressState`、`WeekFormatter` 和 `SubjectStatusStrings`；生成 API 数据转换为共享模型 | 平板和 Web 都显示“周日更新”“周六更新”“后天更新”。看完最新一集时，按钮目标为已看剧集，不将尚未播出的下一集作为继续观看 |
| 详情和播放页状态 | 直接使用 `AiringLabel`、季度日期格式和原生总话数文案 | 两端显示“2026 年 10 月 · 看过 02 (27) · 预定全 12 话”；播放页同样显示进度和总话数。连载类别的 API 装配仍在 Web 适配器中 |
| 详情选集 | 原生 `EpisodeListItem`、`PagedEpisodesGrid`、`EpisodesRow`、已看角标与播出配色 | 宽屏两行分页、窄屏横向列表；日期缺失不虚构更新时间，未播集保持原生未播配色；点击进入对应剧集。原生本机播放历史的高亮位置未同步到 Web |
| 手机收藏统计 | 原生紧凑页头、统计、收藏按钮；Web 约束操作区宽度 | 收藏/在看/想看三个数字不被全宽按钮挤压。进度与评价表单仍为 Web 装配 |
| 剧集剧照 | 共享图卡，浏览器图片解码；网关源码包含 `/episodes/{id}/still/{thumb,medium,large}` 匿名白名单 | 真实图片上游返回 200；远程网关仍拒绝该路径，网页显示占位。源码与 11 项转发测试通过，服务器尚未部署此修复，不能宣称图片已恢复 |

共享模型保留原模块、原包名。依赖原生仓库模型的转换函数放在相邻 `SubjectProgressModelMapping.kt`、`SubjectAiringModelMapping.kt`、`EpisodeListItemMapping.kt`；原生调用点使用这些扩展，不引入 Web 依赖。

验证：43 项 Wasm 浏览器测试、27 项 App 进度/连载状态测试、11 项图片/API 转发测试通过；App `ui-subject`、`shared` desktop 编译与 Web 开发分发构建通过。没有用编译结果代替 UI 检查，也未验证真实视频首帧或进度写入。证据位于仓库外 `../artifacts/ani-details-live.png`、`ani-anime-before.png`、`ani-player.png`、`web-details-aligned-desktop.png`、`web-details-aligned-mobile.png`、`web-collection-updates-mobile.png`、`web-player-status-aligned.png`。

这条流程仍有差异：角色/制作人员/讨论页、播放页收藏快捷按钮与相关推荐、完整播放历史、弹幕引擎尚未补齐；远程评论和视频解析转发的部署问题见文末播放页清单。本轮只更新本地预览，4173/4180 服务保持运行；没有发布 GitHub Pages。

## 剧集与统一选源（2026-10-12）

- App 与 Web 同时调用原目录的 `SourceKindPreferenceItem`、`initialMediaSelectorMode`、`SelectEpisodeButtonsContent`、`SubjectProgressButtonContent`、`EpisodeListDialogContent`、`EpisodeSortSquareButtonContent`、`MediaSelectorModeChip` 和 `WebSourceCardContent`。这些文件是共享实现，原生页面也使用它们。
- 番剧详情由选集/播放按钮进入同一个选源容器。默认读取 App 的 `mediaSelectorSettings.preferKind`；`WEB`/无偏好进入自动匹配，有 BT 来源且偏好 BT 时进入 BT。菜单切换只改变当前会话，不改备份设置。手动查找打开或切换来源后自动搜索，按共享 `standardBrowseEpisodeIndex` 预选当前集。
- 剧集列表使用 App 生成的 `SubjectsAniApi.getSubject`、`AniEpisodeCollection` 和 `EpisodeTypeMapping`。主线、SP、OP/ED 的身份不混用，保留已看状态。Web 的详情状态、继续观看策略、查询会话仍是平台装配；没有移植完整 `EpisodeViewModel`、Room、原生播放器或长按标记。
- 网页自动匹配目前只接受名称明确且唯一的条目和同号剧集。复杂别名回退、拆分季图谱、自动挑选最佳来源、线路记忆、解析失败自动换源仍缺；可继续共享相应业务层，需要为仓库/执行器提供 Web 适配。当前不会用模糊名称自动播放其他季度。
- 网络层通过远程 HTTPS 网关适配浏览器 CORS；Kotlin 生成客户端和网站解析器继续使用原路径及模型。公开番剧详情允许匿名读取。网站内容、视频 CDN 和原生播放能力仍分别受来源策略及平台限制。
- 验证：23 项 Wasm 浏览器测试、7 项共享组件交互测试通过，原生 App 编译通过。设置下拉、BT 初始模式与菜单切换、当前剧集传递已实测；在线优先保存后在手机布局读回。当前直连版本的真实取集、连续播放和手机/横屏播放面板未通过实测，不列为完成。

## 当前接入与验证

| 功能 | 同一份 App 源码 | Web 适配 / 剩余差异 | 验证 |
| --- | --- | --- | --- |
| 番剧收藏与追番列表 | `SubjectsAniApi`、请求模型、鉴权和序列化；收藏分类、布局与卡片组件 | Ktor JS 引擎、同源转发、分页和状态装配；无 Room 离线同步队列 | 真实账号将 430699 设为想看、刷新读回，再设为在看。收尾再次读取时云端为已看过、13/13；保持云端最新值，本轮未写入 13 话进度。列表显示 Animeko 账号收藏与进度 |
| 收藏状态菜单 | `SubjectCollectionTypeButton`、`EditCollectionTypeMenu`、删除确认与状态图标 | Web 注入状态及保存回调；不是原生详情 ViewModel | 浏览器菜单与真实写入通过；5 类状态的生成客户端请求均有测试 |
| 番剧进度与评价 | 生成的收藏 / 评价 / 剧集批量更新 API 模型 | 编辑表单仍是 Web 实现；数字进度只处理 MAIN 集，保留 SP；更新评分保留标签及未编辑字段 | Ktor MockEngine 在真实 Wasm 浏览器中验证字段与路径；真实评分、评论和进度写入未执行 |
| 番剧搜索 | `SubjectsAniApi.searchSubjects`、输入框、类型菜单、工具栏、结果卡片 | 查询和历史状态由 Web 管理；支持排序、NSFW、排除看过/抛弃；完整筛选、远程建议、宽屏预览仍缺 | 浏览器“金牌得主”搜索和封面通过；生成客户端查询字段及分页参数测试通过 |
| 漫画搜索 / 代理 API | `BookSearchRequest`、`MangaRequestRouting` | 浏览器 fetch；每页 20 条、收藏排序 heat、身份请求与写入均遵循共享路由 | Node 路由回归通过；没有另行维护漫画代理基址 |
| 追漫 | `MangaCollectionPageContent`、分类/Pager/网格、`BangumiMangaCollectionLoader`、收藏 Controller | HTTP、会话与持久化由 Web 实现；完整原生缓存未接入 | 原有原生组件测试保留；本轮浏览器核对真实收藏与长标题省略 |
| 探索 | 原生轮播、继续观看卡片、`SubjectProgressState`、封面网格、标题、水平滚动辅助；排行角标和热度 | 在看列表最多 64 项，有可看新集的优先；播放按钮进入网页选源和播放器；推荐/排行连续加载。尚未复用 Room 离线缓存及原生仓库全套排序 | 已对照 ADB 平板截图，并以浏览器桌面/手机视口核查；无账号写入 |
| 新番时间表 | `ScheduleLayout`、`ScheduleDayColumn`、`ScheduleDay`、`SchedulePageDataHelper`、`ScheduleItem` 与共享日期范围 | 浏览器仅转换生成 API 记录；独立路由、前后 7 日、桌面多列、手机页签/滑动、当前时间线、未知时间排序、同刻合并、每日重新加载 | 原生时间表 27 项测试；Wasm 时区/日期/时间线测试；平板与网页同日真实 API 内容对照 |
| 播放历史 | `PlaybackHistoryAniApi`、`PlaybackHistoryListItem`、显示模型与时间格式 | 读取云端历史（同步请求 ops 为空）；点击进入详情；不上传网页播放进度，不支持删除或续播 | 浏览器显示云端记录、进度和封面，手机宽度通过；无真实历史修改 |
| 账号登录 | 邮箱开始/验证码页面、第三方按钮、`OAuthPolling` | 弹窗、会话存储、Origin 适配由 Web 提供；调用 Animeko 邮箱/OAuth API | 既有会话读取通过；未为测试发送新验证码或重新授权 |
| 账号资料与昵称 | `UserAniApi`、`UserProfileAniApi`、用户 ID/关联账号行、`TextFieldItem`、`isValidProfileNickname` | 实际读取昵称/邮箱/关联状态；昵称保存接生成客户端；头像上传及完整账号管理尚缺 | 真实资料读取通过；无效昵称禁用确认通过；未改用户真实昵称 |
| 作品详情 | 分栏/手机页头、简介、信息表、评分和收藏统计组件 | Web 组装；公共详情/关系仍为独立资料请求；剧集使用同一 `SubjectsAniApi.getSubject` 与 `AniEpisodeCollection`；番剧账号数据走 Animeko；漫画走共享书籍路由 | 430699 站内详情与收藏实测；不再把详情卡片点击当外链 |
| 评论 | `MangaCommentsParser`、原 `CommentItemLayout`、`SubjectCommentsHeader`、五颗星、头像和时间格式 | 评论内容/分页由 Web 提供；未接原生富文本、回复、举报 | 355555 真实评论读取、App 评论行与半屏容器验证；未发送评论 |
| BT 选源 | 搜索框、当前集、列表/表格、来源列、属性、筛选、排除项及响应式容器 | RSS 执行与元数据解析独立；季度/OVA 完整匹配与自动选源未接入 | 既有手机/桌面/低高度选源验证保留，本轮未重跑真实下载 |
| 非 BT 在线来源 | App `SelectorSubjectFormatA/Indexed/JsonPathIndexed`、`SelectorChannelFormatIndexGrouped/NoChannel`、Ksoup/JSONPath、`ManualChannelChips` / `ManualEpisodeGrid` | 搜索状态与 HTML5/HLS 引擎为 Web 适配；提取及签名媒体流经 API 网关。动态 JS、验证码、DRM 和不支持的编码仍可能阻止播放 | 本地生产包及 API 验证稀饭备用线路 HLS 1920×1080 实播，28 集列表可选；公网 API 当前被外部操作停用，尚未完成本次公网发布验证 |
| 下载 | `DownloadOverallStats`、`DownloadFilterAndSortBar`、`SubjectDownloadGroupCard`、`DownloadRowContent`、选择状态/批量工具栏、条目汇总 | 浏览器 WebTorrent 记录映射至原生展示模型；桌面双栏、手机列表/详情、筛选排序与批量操作。记录仍按种子任务而非原生逐集缓存，文件导出另走浏览器 | 原生下载行测试、Wasm 分组/状态/批量异常测试、隔离样例筛选/多选和响应式核查；本轮未重新验证实际 P2P 传输 |
| 图片 | App 的图片调用入口和各组件裁剪布局 | 浏览器解码；固定 Bangumi / Animeko CDN 匿名同源转发，不携带 token | 列表、搜索、历史图片显示通过；上游缺失或网络故障显示占位 |

## 设置逐项状态

设置的分类顺序、图标、导航项、账号横幅、主题分组与大部分基础行直接编译 App 原目录中的源码。整个 SettingsScreen / ViewModel 没有移植，能打开分类页不等于功能完成。

| 分类 | 已可用 / 复用内容 | 未完成与原因 / 能否解决 |
| --- | --- | --- |
| 账号 | 登录、关联入口、真实资料与关联状态、昵称编辑接口、退出；共享显示组件和昵称规则 | 头像裁剪上传、邮箱更换/解绑、第三方解绑、扫码、合并冲突、注销：可继续移植 API 与交互流程 |
| 界面 | 共享 `AppearanceGroupContent`、`UISettings`；初始页、NSFW、优先原名、番剧搜索排除看过/抛弃 | 多语言、收藏动画、剧集外观未接；可移植，未实现项禁用 |
| 主题与色彩 | 共享 `ThemeGroupContent`、模型、色板、种子色、跟随系统/浅/深、纯黑 | 背景特效与视频取色未接，可适配；操作系统动态取色与窗口毛玻璃有平台差异 |
| 播放器和弹幕 | HTML 媒体播放、默认倍速 | 字幕、弹幕、自动跳过 OP/ED、一起看、网页进度上传未接；依赖浏览器播放链路，不能只照搬开关 |
| 数据源管理 | App 默认订阅常量、添加订阅弹窗、订阅/来源行、漫画订阅完整展示组件直接编译；浏览器持久化、默认迁移、批量启停/删除和配置传输单独适配 | 默认 BT 2 源、网站 18 源、漫画 7 源已导入。17 个 HTTPS 网站规则可解析，不等于均能播放。manga-native 保存/管理/导出配置及共享解析器搜索已接入，阅读器未接；来源排序、临时启用、原生编辑器、测速未迁移 |
| 观看偏好 | 原 `MediaPreference` / `MediaSelectorSettings`、字幕和分辨率拖动排序、字幕组规则、BT 偏好行及本地备份字段 | 偏好已作用于手动 RSS 筛选/排序；365 天完结判定共用 App 函数。不支持项禁用；来源类型下拉与初始模式共用 App 实现；完整自动择源、来源分级等待/解析超时和记忆来源仍待迁移 |
| 服务器区域 | 当前固定的服务地址，登录适配有故障转移 | 区域选择、测速、统一服务配置未接；可共享服务选择逻辑 |
| 代理 | 检查本站 API 连接 | 网页不能修改系统 HTTP/SOCKS 代理；站内请求与授权弹窗网络路径不同 |
| BitTorrent | WebTorrent 连接数、限速、Web Seed、WSS tracker | 不能把浏览器没有的 DHT/TCP/UDP/NAT 能力做成有效开关；普通 BT 需本机/远端下载服务 |
| 存储 | 浏览器空间估计、公开缓存清理 | 无原生文件路径、漫画离线库、完整任务队列；可用浏览器数据库/OPFS 或后端适配 |
| 设置备份 | 共享 `BackupSettingsContent`、两项操作/确认弹窗、`SettingsTransfer`；复制及粘贴恢复 | 剪贴板失败显示手动输入；账号凭据不迁移，未知字段保留不代表该功能已实现 |
| 更新 | 刷新加载服务器静态资源 | 不使用 APK 更新器；生产优化、PWA、缓存升级策略尚未完成 |
| 日志 / 关于 | 说明和项目链接 | 完整日志浏览、反馈上传、开源许可页未迁移；可共享内容和浏览器日志适配 |

## 可以继续复用、但尚未完成的主要功能

| 缺项 | 具体原因 | 技术路径 |
| --- | --- | --- |
| 完整漫画阅读 | 共享 `MangaSourceSearch`、CCC / Comick 搜索和选源界面已编译；完整章节、验证会话和阅读器未接入 | 为共享仓库提供浏览器 HTTP、来源会话、图片解码、存储和验证适配，移植 Compose 阅读布局；不是 Kotlin 无法绘制 |
| 漫画章节与进度 | 目前资料话数不等于来源可读章节；无选章、翻页、缩放、自动续读及读完回写 | 依赖真实来源执行器和图片成功展示事件，再共享原编号及进度逻辑 |
| 漫画离线 | BT 文件下载不等于原生漫画批量图片缓存、持久进度和离线章节库 | 浏览器数据库/OPFS/Cache API 或配套服务；必须处理配额、缓存回收和关闭标签页 |
| 探索数据与完整搜索 | 原生 Room 本机缓存、完整最近播放排序未迁移；搜索建议词/高级筛选/宽屏预览仍有缺项 | 提供浏览器数据库后继续共享仓库；已有多列时间表、进度文案、快捷播放、排行显示不再列为缺失 |
| 完整追番与详情 | 原生 Room 同步、剧集快捷操作、连续播放、人物/角色/讨论/关系图、封面放大未完整接入 | 共享仓库和详情内容，替换数据库与平台依赖；公共资料请求仍需统一到共享服务 |
| 完整播放历史 | 仅云端读取与原生列表显示，没有本机合并队列、删除、续播和网页播放上传 | 接入共享历史仓库、冲突规则和浏览器播放事件；当前只读同步不冒充完整双向同步 |
| 完整视频播放 | HTML 媒体可播放受支持文件，不含原生控制 UI 全套、字幕、弹幕与自动匹配 | 共享内容/业务，替换 mpv/VLC 引擎；浏览器编码覆盖和后台运行能力不完全等效 |
| 完整 BT 选源 | 展示层共享，RSS 标题过滤不等于 App 季度/OVA/别名匹配 | 继续移植原生解析与选择策略、偏好持久化和状态流 |
| 长期登录与跨账号存储 | Animeko 会话存入 localStorage，重开网页可恢复；退出清除本地与旧标签页存储。原生 DataStore / Room 未移植 | 持久化与旧会话迁移、退出清理有回归测试；存储不会改变 CORS 要求 |
| 动效 / 无障碍 / 多语言 | 共享部分主题与资源，但未完整接入 AniMotionScheme、语言切换或全量辅助技术验证 | 可继续移植和验证；编译通过不等于行为相同 |

## 结构与跨端配置约束

- 共享展示函数保留在原模块、原包名；原生包装器继续调用它们。根工程 settings 和 native target 不为 Web 重组。
- 新增共享点为评论行/标题、订阅/来源行、观看偏好行、完结日期规则、在线来源解析模型、手动线路/剧集和探索轮播/继续观看卡片；此前共享收藏菜单/按钮、昵称校验、文本设置行、播放历史行；预览单独放入 Preview 文件，避免将原生预览依赖引入 Wasm。
- `client/src/commonMain/gen` 的 API、模型、鉴权与序列化直接参加 Web 编译，没有手工修改生成客户端。`WebAnimekoApi.kt` 只负责浏览器引擎、会话和 UI 数据适配。
- `MangaRequestRouting` 是漫画网络规则的共同来源，收藏、评分/短评/私密属性按其规则走书籍代理。网络传输仍分别是 App Ktor 与浏览器 fetch。
- 备份兼容当前 App 的 `SettingsBackup` / `ExportedMediaSourceDataList`。未知字段和 64 位颜色值以 Kotlin JsonElement 保留，不经过 JS Number；token 和 PikPak 登录信息排除。
- `node web/audit-shared-sources.mjs --list` 输出真实共享路径与未纳入 Web 的修改文件；审计文件在 `web/build/shared-source-audit.json`。其中未共享文件也包含测试和原生专属代码，不能按文件数计算缺失功能数。

## 验证记录与范围

本轮执行：

- Node 80 项测试通过：鉴权隔离、同源/路径/字段约束、Animeko 搜索/资料/只读历史、漫画代理、配置兼容及既有媒体适配回归。
- Kotlin/Wasm 浏览器 16 项测试通过（Edge headless + Ktor MockEngine）：收藏/评价/MAIN 集更新、昵称和搜索协议、观看偏好与未知备份字段、365 天完结边界、在线解析器与推荐 API 协议。
- App `ui-settings`、`ui-subject`、`ui-mediaselect`、`ui-comment`、`ui-exploration` 的 desktop Kotlin 编译通过；观看偏好 2 项原生桌面 UI 测试通过；用于确认共享提取没有破坏原生调用，不等于原生 App 真机交互测试。
- Wasm 开发静态资源构建通过。完整生产优化和发布未验证。
- 此前真实接口验证：收藏状态写入与刷新读回、追番列表/进度、账号资料读取、搜索、热榜、播放历史。本轮没有再次写入收藏；新增验证为探索推荐/时间表、在线搜索/线路/实际播放及观看偏好刷新持久化。昵称仅验证表单；评分/评论/剧集更新使用 MockEngine，不宣称本轮真实写入。
- 手机 470×892、桌面 1060×892 检查布局，1060×450 验证历史列表滚动与条目进入详情；这是桌面浏览器视口模拟，不是平板/手机真机验证。没有修改平板订阅或设置。
- 截图及构建日志在仓库外 `../artifacts/web-verification/`；`animeko-collection-shared-menu.png`、`animeko-history-mobile.png` 等为本轮证据。

已有但本轮未重跑的证据：设置备份 App → Web → App 格式及 64 位值保留，原生共享备份 UI 测试；BT 桌面/手机/低高度选择、自有小文件下载保存 hash 一致、WAV 播放结束。不能用这些结果宣称任意真实媒体、来源或所有 App 版本已经验证。

## 运行与纯静态边界

前端为静态 Wasm/JS 资源，但完整系统依赖 Node 同源接口：`oauth-relay.mjs`、`animeko-relay.mjs`、`bangumi-relay.mjs`、`online-relay.mjs`。只上传静态文件不能消除上游 CORS/Origin 拒绝。可部署同源 API/边缘函数，或由上游正式支持浏览器；前端不保存 OAuth client secret。

`animeko-relay.mjs` 固定 Animeko 上游并限制路径/方法，不转发 Cookie，不记录 token，不重试写入。只读请求在固定的 4 个 App 服务节点故障转移，写入不重试；公开搜索/热榜/时间表允许匿名；账号接口必须持有 Animeko 会话；历史同步仅允许空 ops。

WebTorrent 在浏览器中只能使用 WebRTC 节点或支持 CORS/Range 的 Web Seed，普通 TCP/UDP 种子需要本机或远端下载后端。RSS 服务也须支持 CORS或受限同源转发；导入文件不能解决后续链接的跨域问题。

预览服务由 `start.ps1` 启动为独立隐藏进程，保持运行，但没有配置开机自启。当前监听 localhost；手机实机访问还需要局域网监听、HTTPS 和网络规则。TLS 校验保持开启。环境代理可按主机配置 NO_PROXY；本站同源转发与浏览器授权页并非相同网络路径。

## 2026-10-12 本轮补充审查

- 追番 502：加入有限超时、固定节点的只读故障转移，覆盖连接失败、5xx、非 JSON；不隐藏 401/403，不重放收藏写入。现场未复现原来的单次 502，不能断言上游故障消失。
- 在线来源默认订阅为 App 仓库的 `https://sub.creamycake.org/v1/css1.json`。当前 18 个来源中，17 个 HTTPS 规则符合执行条件，1 个 HTTP 来源保留配置但不执行。规则可解析不代表 17 个站点均可播放。
- 同源在线读取固定来源目录、公共 IPv4 DNS 校验、连接地址固定、有限重定向、15 秒/2 MiB 限制，不发送账号凭据或 Cookie。仅将 HTML 作为字符串交给原 App 解析器，不执行返回脚本。
- 播放器由 API 提取 JSON `player_*`、video/source、静态播放器配置与嵌套 iframe 中的明确媒体地址，不执行页面脚本。失败时提供重试和切换来源；不嵌入原站播放器。
- 漫画非 BT 来源可搜索并保存选择；完整章节和正文阅读器仍未完成，验证会话、图片解码与缓存需要浏览器适配，不能将“导入配置成功”算成阅读可用。技术上可继续移植。
- 按用户要求，普通 BT 节点兼容暂缓；保留现有 WebTorrent 能力。未操作平板，也没有修改云端收藏或评论。
- 新番时间表修正 Wasm 固定偏移时区与 API 区域名的差异：使用浏览器 Intl 的 IANA 区域名发送请求，日期/时间按当地时区显示。

### 本轮真实浏览器证据

- `artifacts/web-verification/explore-mobile.png`（470×892）和 `explore-desktop.png`（1280×900）：最高热度、继续观看和推荐均为真实账号/上游数据。
- `explore-schedule.png`：新番时间表正常返回并显示当天剧集；浏览器 IANA 时区适配解决本轮发现的 400。
- `online-episodes.png`：稀饭动漫《葬送的芙莉莲》的两条线路和 28 集，使用 App 原选集组件。
- `online-playback.png`：备用线路第 1 集播放；直接读取 DOM 媒体状态为 currentTime=16.87、duration=1559.93、paused=false、error=null。验证后已关闭视频，没有下载整个视频或回写观看进度。主线未播放成功，不能将其算作已验证。
- 上述截图保存在仓库同级的 `artifacts/web-verification/`，不属于源码。尺寸由浏览器视口模拟，不是真机验证。

- `explore-landscape.png`（1060×450）：滚动后顶栏收起，推荐卡片无横向溢出；点击《鬼灭之刃》进入本站 `#subject/245665`。
- `settings-watching-mobile.png`：共享观看偏好行；隐藏无字幕开关改动后刷新仍保留，已恢复测试前值。
- `settings-comments-mobile.png`：470×892 真实漫画评论，复用 App 的评价标题、圆形头像、评分星、内容/时间布局。
- 运行服务健康检查通过（`/api/health`）；保持隐藏独立进程运行。在线订阅已添加到当前网页，原有 RSS/漫画配置保留。

## 在线播放页与会话（2026-10-13）

- 直接共享 App 原目录 `EpisodeScreenLayout`、`EpisodePlayerTitle`、`TextWithBorder`、`ManualEpisodeGrid`。窄屏视频在上、详情与选集在下；宽屏视频在左、340–460 dp 详情栏在右。切换布局保留同一个视频实例。
- `WebPlayerScreen` 装配浏览器状态与 Compose 控件；暂停、进度、下一集、倍速、静音、全屏、数据源切换是网页平台实现。HTML 视频通过 `HtmlElementView` 放入共享布局，不使用网站 iframe。选源关闭后会销毁媒体、取消提取请求并回收 HLS。
- 自动和手动选源均传递完整线路剧集表。自动模式切集后返回选源，会按详情 API 的集身份重新匹配当前集；无法唯一映射时进入手动选源。手动选择的站点剧集不冒充 Bangumi 集身份。
- 未接入：剧集评论/回复、弹幕、外挂字幕、自动 OP/ED、云端播放进度写入、全套原生播放器手势和音轨面板。这些需要独立业务或媒体适配；不能据此宣称完整 EpisodeViewModel 已共享。
- 登录会话持久化、标签页会话迁移与退出清除已覆盖自动测试；没有在本次验证中使用真实账号登录。


## 追番卡片与操作（2026-10-13）

- App 与 Web 共同编译原目录 `SubjectCollectionItemLayout`：标题省略、更多菜单位置、放送信息槽、选集及进度按钮布局。原生 `SubjectCollectionItem` 也调用此组件。
- 共用 `SubjectStatusStrings` 及剧集编号格式、`CollectionPageLayout`、分类标签、`SubjectCollectionCard`、`EditCollectionTypeDropDown`、`SubjectProgressButtonContent`、`EpisodeListDialogContent`、剧集按钮、下拉刷新和 NSFW 遮罩。列表采用 360 dp 自适应列宽、紧凑 16 dp/宽屏 24 dp 间距及滚动加载。
- 浏览器数据适配保留生成客户端返回的完整剧集，选集和播放进入 Web 播放页，数据源选择由播放页弹层承载；统计、收藏状态、单集观看状态使用生成的 Animeko API 客户端。状态修改失败展示错误，不提前宣称成功。
- 观看进度与周更新文案调用原生 `SubjectProgressInfo`、`SubjectProgressState`、`WeekFormatter`；API 数据映射由 Web 适配器提供。原生 Room/ViewModel、离线缓存和 Bangumi 全量同步状态未进入 Web，因此共享卡片不表示整套原生收藏业务已完全复用。

### 选集播出状态（2026-10-13）

- App 与 Web 共同编译 `EpisodeBroadcastSchedule`、`SubjectRecurrence` 和生成 API 的周期转换器；原生 `EpisodeCompletionContext` 与 `EpisodeListUiState` 委托给同一个计算实现。未到精确播出时刻的剧集使用未播出状态，包含深夜档跨日、UTC+9 日期回退及无日期时的原生回退规则。
- 两端共用 `EpisodeStateButton` 和 `EpisodeListDefaults`/`EpisodeListColors`：未播出灰色，已播未看与已看遵从 App 的 ACTION/LIGHT_UP 偏好；已看状态优先于播出状态。灰色不禁止点击，与原生选集一致。
- 追番选集与详情选集均使用上述共享状态按钮；详情不再丢弃 API 的放送周期。弹窗打开期间更新时间，跨过播出时刻会更新颜色。
- Web 浏览器测试 32 项通过（含精确时刻、深夜档、日期缺失、颜色优先级、详情保留周期）；App 原生相关测试 39 项通过。此项复用不代表原生收藏 ViewModel、播放历史和整页业务已全部共享。

### 播放器和弹幕过滤设置（本地版本，2026-10-13）

- App 与 Web 共编译 `PlayerGroupContent`：设置顺序、原始文案、倍速范围滑块、记住倍速、默认倍速与长按速度联动；原生 `PlayerGroup` 将配置和平台专属设置传入该组件。
- 共用 `VideoScaffoldConfig`、`DanmakuFilterConfig`、`DanmakuRegexFilter`，以及 `DanmakuRegexFilterContent`、规则标签、正则校验和新增弹窗。浏览器通过 `retainedSettings` 保存原生字段，设置备份直接包含这些字段，保留其他平台与未知配置。
- 网页在线播放器适配已生效：默认倍速、0.25 步进的用户倍速范围、记住倍速、长按临时倍速及松开恢复、全屏悬浮按钮显示模式、播放结束自动连播。记住倍速由 Kotlin 更新配置，避免 JavaScript 重写备份损失大整数精度。
- 弹幕规则可新增、禁用、删除、导入和导出 App JSON。网页没有弹幕获取/渲染/发送引擎，启用过滤和编辑弹幕暂停开关禁用；保存规则可供 App 使用，不代表网页已过滤弹幕。
- 超分、OP/ED 识别、自动标记已看、选源后隐藏、自动切源、HLS 广告过滤及视频帧预览未接入网页运行时，相应控件禁用。Android 折叠屏、刷新率、WSOLA 和原生内核配置由原生平台槽提供，不显示为网页可用能力。
- 本轮只更新本地静态文件，GitHub 发布需用户明确要求。


### 番剧播放页（本地版本，2026-10-13）

| 部分 | 实现与复用 | 当前边界 |
| --- | --- | --- |
| 页面入口 | 点击剧集直接进入播放页；资源选择作为播放页上的弹层，关闭选源保留播放页 | 网站来源自动查询仍由 Web 适配器驱动 |
| 响应式布局 | 原目录 `EpisodeScreenLayout`、`episodeScreenLayoutMode`、`EpisodeSidebarState` | 手机上下布局、电脑左右布局；全屏与窗口分别记住侧栏状态 |
| 详情与选集 | 原生页面和 Web 共同调用 `EpisodeDetailsScaffold`、`EpisodePlaybackCard`、`EpisodePlaybackListItem`，共用剧照、已看角标、播放标记 | 收藏快捷按钮、相关推荐及全量播放历史没有接入此 Web 页面 |
| 控制栏 | 原生和 Web 共编译 `PlayerControllerBar`；标题使用 `EpisodePlayerTitle` | HTML 视频由浏览器播放；HTML interop 位于 Compose canvas 上方，视频区域为共享标题栏和控制栏预留空间，不能声称与 App 覆盖式控制栏逐像素一致 |
| 数据源摘要 | 共用 `MediaSelectorSummaryBannerContent`；状态来自实际选中的订阅和线路 | 桌面暂用共用摘要条，未使用原生完整查询统计卡 |
| 剧集身份 | 番剧 ID、剧集 ID、连续编号、分季编号随选源传递；未明确匹配的来源使用来源标题，评论不会请求错误的剧集 | 无原生系列关系图时不自动推断跨季度映射 |
| 剧集评论 | App 生成的 `EpisodesAniApi.listEpisodeComments` 和共用评论内容布局；网关源码已补齐剧集评论 GET 路由和 `includeBangumi`/`after` 参数 | 本地网关连接真实上游验证为 HTTP 200（590553 第 1 集，51 条评论）；新服务器尚未部署该修复，仍返回 HTTP 404；回复、发送、表情、BBCode 完整渲染未接入 |
| 视频与弹幕 | HTML video/HLS 桥接保留倍速、连播、长按与释放逻辑 | 当前 `/online/resolve` 实测 HTTP 404，未验证真实视频首帧；网页弹幕引擎未接入 |

验证：Web 39 项测试、播放器 JavaScript 9 项、原生布局与视频保留测试 7 项通过，App desktop 编译通过。浏览器尺寸验证使用桌面、手机和低高度横屏视口，属于浏览器模拟，不是真机测试。本地服务保持运行，未发布 GitHub Pages。

## 2026-10-14 探索、下载与时间表专项核查

- 对比基准：已安装的平板 Release，ADB 读取截图；没有更改账号、收藏、订阅或下载。设备本轮 UIAutomator 返回空根节点，因此导航按实际截图定位，结束后熄屏。
- 直接共享组件仍在 `ui-exploration`、`ui-download` 和 `app-data` 原目录。原生调用保留；平台仓库映射、预览与纯展示拆开，Web 使用构建白名单引用同一文件。
- 下载元数据新增作品 ID、封面、创建时间与上传量；旧记录仍可读取，有作品名的归到同组，无作品标识的独立任务不会因重名合并。缺失历史时间/上传量保留未知。
- 浏览器引擎限制未改变：普通 TCP/UDP BT、后台下载、原生逐集缓存、PikPak/文件系统和漫画离线任务没有在本轮移植。可通过相应浏览器存储/下载服务继续适配，不能仅凭界面对齐宣称引擎已共用。
- 构建、测试日志与真机/浏览器截图位于仓库外 `../artifacts/`，文件以 `native-explore`、`native-schedule`、`native-downloads`、`web-explore`、`web-schedule`、`web-download` 和 `explore-` 开头；含 `fixture` 的截图仅为隔离的合成任务。
- 验证通过：Web 浏览器测试 49 项、App 原生相关测试 34 项、JavaScript 测试 112 项；App desktop 编译与 Web 开发分发构建成功。桌面、手机及低高度横屏使用浏览器视口模拟验证；下载筛选、多选用独立来源的合成任务验证，未对真实任务执行批量操作，也未验证真实 P2P 传输。
- 仅更新本地 4173/4180，没有发布 GitHub Pages。
