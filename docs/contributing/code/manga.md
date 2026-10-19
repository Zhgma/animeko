# Bangumi 漫画收藏

追漫从 Bangumi 读取当前登录账号的漫画收藏。入口位于追番页顶部，与追番并列。

来源协议、自动选源、手动来源记忆与下载错误处理详见 [漫画来源与选源](manga-sources.md)。

## 接入边界

- `CollectionPage.kt` 是收藏页面的接入文件：保存追番 / 追漫选择，将顶部标题交给 `TrackingCollectionTabs`，在选择追漫时显示 `MangaCollectionPage`。
- `app-data/.../data/repository/manga/BangumiMangaRepository.kt` 封装 Bangumi 的收藏读取与独立漫画模型；`MangaLibraryRepository` 负责收藏分类和话 / 卷进度写入。
- `ui-subject/.../ui/manga/` 包含追漫界面和 ViewModel。
- 翻译位于 `app-lang/src/androidMain/res/*/manga.xml`。

追漫收藏页直接使用追番的 `CollectionPageLayout`、`CollectionTypeScrollableTabRow`、`CollectionTypeTabLabel` 和 `CollectionPager`。分类顺序取自 `COLLECTION_TABS_SORTED`，对应“抛弃 / 想读 / 在读 / 搁置 / 读过”；数字使用同款 Badge。触摸左右滑动与点击标签切换同一个 Pager，各分类保留独立的列表与滚动位置。漫画卡片保留竖向封面和话数、卷数信息。移动端通过下拉刷新，头像旁不增加刷新或设置按钮；桌面使用共享布局自带的刷新入口。页面不额外显示总数说明栏。

详情展示直接共用上游页面组件：`MultiColumnScaffold` 和 `RailCard` 具有模块内可见性，`SubjectInfoTable` 提供通用键值对重载，`SubjectCollectionStatsRow` 接受“在读 / 想读”标签；现有动画调用保留默认参数。更新上游时，保留新增漫画文件及这些小型接入点。漫画数据独立于追番的 Room 表、剧集模型和收藏同步任务；阅读界面复用播放器的通用布局与控制栏框架。

## 数据与登录

复用 `SessionManager.sessionFlow` 中的 Bangumi access token，以及独立的 `MangaBangumiNetwork`。使用 Bangumi 官方 v0 API：

追漫的授权按钮直接打开 Animeko 的 Bangumi OAuth 页面；已登录账号使用绑定流程，返回的凭据由共享 `SessionManager` 保存。头像保留账号面板入口。漫画账号请求返回 401 时，先通过 Animeko 刷新会话取得 Bangumi token，并重试一次原请求。并发失败复用同一次刷新结果，刷新尝试间隔至少 30 秒；退出或更换账号后不应用旧会话的刷新结果。403 和公开请求不触发刷新。刷新后仍无有效授权时才提示关联 Bangumi，网络错误保留重试入口。

1. `GET /v0/me` 取得当前 Bangumi 用户名。
2. `GET /v0/users/{username}/collections?subject_type=1&type=...&limit=30&offset=...` 分页读取书籍收藏，包括当前账号有权访问的私密收藏。
3. `GET /v0/subjects/{id}` 读取书籍类型，只展示 `type=1` 且 `platform=漫画` 的条目。

分页偏移按原始书籍条目数推进，避免只有小说的页提前终止漫画列表。条目详情最多同时请求四个。列表按页逐步呈现，失败后提供重试，不将错误当作空收藏。优先加载当前分类，再加载其余分类；每个分类全部读取并筛出漫画后才显示数量，不使用含小说的书籍总数。切换分类使用各自缓存，更换账号取消旧请求并清空缓存。下拉刷新保留已显示内容并重新加载各分类。收藏状态分别映射为 1 想读、3 在读、2 读过、4 搁置、5 抛弃。

阅读进度取 `ep_status` 和 `vol_status`。总话数 / 总卷数未填写时显示 `—`。封面进入应用内漫画详情。应用内收藏菜单复用 `EditCollectionTypeDropDown`，使用“想读 / 在读 / 读过 / 搁置 / 抛弃”标签。`POST /v0/users/-/collections/{id}` 只写入 `type`；进度编辑通过 PATCH 只写入 `ep_status` / `vol_status`，保留评价、标签和隐私。成功后通过漫画独立的 revision 通知追漫列表刷新。未收藏时进度编辑禁用；授权与网络错误保留编辑内容并显示错误。当前 v0 接口没有删除收藏能力，菜单不提供无效的删除动作。

### 网络与缓存

书籍搜索、基础详情和公开评论通过 `https://119.28.149.19:8443/api/books` 的公共只读接口读取，
接口保留 BGM v0 JSON 结构，服务端限定 `type=1`（包含漫画和小说），请求不携带 BGM token。
`MangaBangumiHttp` 将书籍身份/收藏查询、收藏状态和话数/卷数更新也路由到该服务，账号请求携带当前用户的 BGM token。
服务端核验本人身份并限制书籍类型，不保存 token、不缓存个人响应。评分写入和关联使用各自的原地址。
番剧使用原有 Animeko/BGM 接口。接口契约、限流、部署及回滚见 [书籍 API](../../../services/books-api/README.md)。

设置 → 网络代理 → 漫画 Bangumi 代理支持直连、系统代理与自定义代理（含账号密码），默认直连。
`MangaBangumiNetwork` 使用独立的 `DefaultHttpClientProvider`，只承载漫画的收藏、书籍搜索、详情、
关联、评论、评分与话 / 卷进度请求；漫画订阅、站点目录和阅读图片继续使用原有客户端。
代理配置持久保存并动态生效，测试连接同时检查 v0 元数据和 p1 评论，测试不读取缓存。

`MangaBangumiCache` 持久保存成功读取的原始响应，以凭据 SHA-256 摘要分区，缓存键不保存 token。
网络错误、429 或 5xx 可以回退同一分区的旧数据，取消操作、401/403 不回退；首次失败后同分区
30 秒内优先使用缓存，避免逐个条目等待超时。下拉刷新和代理设置操作
解除退避；列表与详情提示正在使用缓存。成功读取覆盖旧缓存，最多 400 个响应 / 400 万字符，
超大响应不缓存。账号和评论接口的客户端总超时为 20 秒，其他公开请求为 8 秒。没有缓存的首次请求仍显示错误，不能生成不存在的收藏或章节。

收藏列表、作品信息、关联、评论、搜索结果与个人评价可从缓存恢复；来源绑定和阅读位置使用独立的
`MangaReadingStore`，因此 Bangumi 不可用时仍能继续选源和阅读。写入始终请求网络，失败不通知同步成功，
成功后更新缓存中该作品的评价和进度；离线列表分组沿用上次完整读取的结果，联网刷新后反映分类变动。收藏和评价编辑保留原来的失败提示与重试入口，不建立自动提交队列。

## 详情、漫画源与阅读器

详情页使用动画详情同一套 `SubjectDetailsLayoutParams.calculate`：小于 600dp 为单栏，600–1599dp 为双栏，1600dp 起为三栏，尺寸依据页面实际可用宽度。窄屏直接使用 `SubjectDetailsSingleColumnPage` 的封面和嵌套滚动、`SubjectDetailsContentTabRow` 的吸顶标签、`CollectionData` 的单行收藏统计、`Rating` 的评分入口，以及 `SelectEpisodeButtons` 的目录 / 阅读按钮布局。收藏使用 `SubjectCollectionTypeButton`，仅替换为阅读标签并隐藏不支持的删除操作；话 / 卷进度编辑位于章节下方。详情区按章节、简介、标签、作品信息、关联作品排列，评价页使用同款悬浮写评价按钮。

双栏和三栏直接使用 `MultiColumnScaffold`、`SubjectDetailsCover` 和 `SubjectDetailsTitleBlock`，封面、阅读按钮及作品信息位于左侧，中间显示标题、简介和章节，三栏时评分与评论位于右侧 `RailCard`。封面可点击放大，配色遵循应用的动态主题与渐变背景设置；宽屏渐变使用 `SubjectDetailsGradientBackground`。评分摘要、直方图、收藏统计、标签、作品信息表和简介使用动画的共用组件。`SubjectInfo` 仅用于显示适配，不进入动画仓库或接口。

作品信息和评分来自 Bangumi v0；评论通过书籍服务的 `/p1/subjects/{id}/comments` 分页获取，由服务器核验书籍类型后直连 `next.bgm.tv`，预览与完整评论列表均可在应用内查看。p1 接口可能变化，其解析集中在 `MangaDetailsRepository`。评价预览直接使用 `ReviewsPreviewSection` / `HotReviewsCardContent`，完整评价使用 `SubjectCommentColumn` / `SubjectCommentsSheet`，由 `MangaCommentPaging` 提供分页和刷新。窄屏详情 / 评价可左右滑动，评价页内有写评价入口。阅读区未完全展开时，下拉优先展开阅读区，再刷新评论。Bangumi 评论沿用共享组件的原文入口，不调用仅支持 Animeko 评论的举报接口。

章节列表通过显示适配使用 `PagedEpisodesGrid`，适配 ID 只用于界面索引，不写入番剧数据层；支持分页、分组、阅读进度和长按标记读过。详情与阅读器共用选源、章节和缓存动作。

章节列表默认定位到正在阅读话所在的分组与分页；未打开阅读器时依次使用当前来源的本地阅读记录、
Bangumi 话 / 卷进度匹配目录，找不到对应章节时显示首个分组的第一页。每页容量随布局变化，
例如每页六章、阅读第 61 话时显示 61–66。手动翻页在普通状态更新时保持；切换作品、来源、
分组或当前阅读话，以及每页容量变化时重新确定分页。番剧共用分页组件的行为保持原样。

`MangaSourceRepository` 提供 Komiic、漫画柜、G站漫画、漫画人、优酷漫画、CCC追漫台和 Comick 的维护来源，包子漫画保留手动导入兼容，完整协议与可访问性说明见 [漫画来源与选源](manga-sources.md)。Komiic 与漫画柜的协议参考：

- https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js
- https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuagui.js

订阅地址展示在数据源管理中，详情和阅读器控制栏都可进入共享选源容器。适配器随应用发布，不执行远程 JavaScript，也不承诺兼容任意 Venera 插件或脚本热更新。Komiic 使用 GraphQL 获取搜索、章节和图片；漫画柜解析 HTML 与 LZ-string 压缩数据。用户通过搜索或作品链接明确选择来源，绑定按 Bangumi 条目 ID 保存。单行本、普通章节和番外分组，数字章节按数值排列，翻到上一话 / 下一话时限定同一分组。站点登录要求、限额及网络错误直接提示，不将错误当成空章节。

`MangaReaderScreen`、ViewModel、网络解析和阅读记录均位于 `commonMain`，使用 Compose Multiplatform、Ktor、现有 `AsyncImage` 与 `SystemPath`。阅读器使用纵向连续滚动：竖屏每行一页，横屏每行两页，默认先右后左。可以切换先左后右，也可以通过“配对”切换首张单独显示，首张按原始比例占满整行宽度，从第二张开始每行两页，修正双页配对偏移。奇数末页保留在阅读顺序的起始侧。窗口尺寸变化时保留当前页所在的行；按钮、键盘上下方向键 / PageUp / PageDown 和滑块按当前单双页布局定位。支持目录、双击与双指缩放。双击以点击位置为锚点放大，双指缩放保持初始中点对应的图片内容跟随手势中点，双指移动同时平移图片。手势坐标取自未变换的页面容器，缩放时补偿位移，缩放范围为 1–5 倍；回到原始比例时位移归零，平移受页面边界约束。图片按需请求并携带来源站点要求的 Referer，写入应用缓存后交给共用图片加载器解码。当前话与同一分组的下一话自动在后台完整缓存，优先处理当前话。前台图片请求和预加载共用互斥缓存，避免重复下载。下一话的图片目录也缓存，因此翻到已缓存章节无需重新获取目录。详情栏显示两话的缓存进度与失败信息，重试只补未完成的图片；取消操作会传播，网络错误不会伪装成缓存完成。两话预加载完成后清理更早章节，退出作品详情时释放本次会话的缓存文件。

阅读页面使用正式的 `NavRoutes.MangaDetail` 导航，因此不占用追番主页的导航栏区域。搜索宽屏侧栏的章节动作通过该路由携带 `initialChapterId`，在独立页面恢复来源并打开指定章节，全屏尺寸不受搜索侧栏限制。视频页的 `EpisodeScreenLayout` 与布局模式定义位于 `video-player/.../PlayerScreenLayout.kt`，动漫和漫画共用同一份布局实现；视频页的侧栏状态及模式选择仍由视频模块管理。漫画手机竖屏使用初始占页面高度 70% 的阅读区与下方“详情 / 评论”。漫画区域内滚动只翻页；详情或评论向下浏览时优先连续收起阅读区，直到高度为零，再滚动列表。反向滚动优先返回列表顶部，剩余下拉距离逐步展开阅读区，最大恢复到 70%。高度比例在全屏切换和旋转时保留，阅读内容持续组合；横屏窄窗口使用 16:9 阅读区，宽屏使用阅读区与右侧详情栏，全屏仅显示阅读区，切换时不重建阅读内容。单双页依据整个阅读页面的横竖方向，不依据嵌入阅读区的 16:9 比例。

阅读器控制层直接使用 `VideoScaffold` 和 `PlayerControllerState`。初始和进出全屏时隐藏，单击阅读区域切换显示，五秒无操作后自动隐藏；拖动进度条和打开阅读设置期间保持显示。控制栏覆盖在图片上，显隐不改变阅读区高度；左右顺序与首张配对位于阅读设置。全屏使用现有 `setRequestFullScreen` / `setSystemBarVisible` 跨平台接口，保留设备横竖方向，返回优先退出全屏。桌面支持 F 切换全屏、Escape 退出、空格切换控制栏。末行保留至少一个阅读视口的高度；末行图片下方的黑色留白在全屏且存在下一话时可点击切换到同分组下一话，点击不切换控制栏，拖动仍用于滚动。非全屏或没有下一话时，留白保持普通阅读区域的控制栏交互。

`MangaReadingStore` 在已有 Preferences DataStore 中使用 `manga.reader.{subjectId}` 命名空间，保存来源绑定、章节、页码、左右阅读顺序与首张配对方式。本地页码独立保存；章节末行图片成功解码并显示两秒后，仅对明确的正整数主章节 / 单行本编号递增 Bangumi 话数 / 卷数。小数话、番外和无明确编号的章节由用户手动编辑进度，图片加载失败不计为读完。漫画模块不写入番剧数据库。Android、桌面和 iOS 共用阅读器实现；iOS 编译与真机验证需要 macOS / Xcode。

## 评价与关联作品

漫画详情和阅读器使用同一组评分、评论入口。`EditableRatingDialogsHost` / `RatingEditorDialog` 显示十星评分、短评和隐私选项；评论使用番剧的 `CommentItem`，包含头像、星级、时间、BBCode 富文本、图片查看器与复制菜单。`GET /v0/users/{username}/collections/{subjectId}` 读取本人的完整评价，未收藏时提示先收藏；`PATCH /v0/users/-/collections/{subjectId}` 只提交 `rate`、`comment`、`private`，保留收藏分类、话数、卷数和标签。提交失败保留编辑内容并显示错误。

`GET /v0/subjects/{id}/subjects` 提供直接关联作品。详情复用 `RelatedSubjectCard`，关系图复用 `SubjectRelationGraphScreen` 的宽窄屏布局，以当前作品为根显示直接关联，不推断传递关系或前后篇次序。可选 `relationLabel` 保留 Bangumi 的“动画”“单行本”等原始关系名；原番剧图继续使用默认枚举标签。书籍跳转漫画详情，动画跳转原番剧详情，其他类型打开 Bangumi。

图片下载由详情会话的独立协程管理，前台页面只等待共享任务；LazyColumn 回收页面或取消等待不会中止下载。并发上限为三张，后台持续预取本话和下一话，重试只补缺页。关闭作品详情会取消该会话任务并释放缓存。

## Fork 发布与应用更新

Fork 版本从 `0.1.0` 开始，Android `versionCode` 从 `50407` 递增，支持覆盖已有 `50406` 开发安装。旧的 `4.9.0-dev` 属于上游版本序列，首次切换到 `0.1.0` 需要手动覆盖安装，后续按 `0.x.x` 正常检查更新。所有公开版本只发布使用独立发布签名的 Release APK。历史调试安装的 `.debug2` 应用 ID 与签名不同，不能用 Release APK 覆盖。

本地发布使用不带 `v` 的标签（如 `0.1.0`），避免触发需要上游私有凭据的 `v*` 发布工作流。标签指向主分支的源码提交。本地签名 APK、摘要、`release.json` 和发布脚本保存在独立的 `release-artifacts` 分支；向该分支推送会触发 GitHub Actions，校验签名、版本和源码标签，上传草稿并下载比对附件后公开 Release。主分支不存放二进制安装包。Actions 使用仓库自带的 `GITHUB_TOKEN`，本机只需具有写权限的 SSH Deploy key。签名库和密码保存在仓库外并需要独立备份。Git 标签、应用显示版本和附件文件名的版本部分保持一致。

HTTP User-Agent 的 `open-ani/ani` 产品版本对应上游代码基线 `4.9.0-dev`，用于上游接口兼容性判断；附加 `Animecomic/0.x.x` 产品版本与 fork 仓库地址标识本项目。合并上游时同步核对兼容版本。应用显示与 OTA 比较使用独立的 fork 版本，避免上游将 `0.x.x` 当作旧版 Animeko 而返回 426。

更新检查使用应用配置的 `HttpClientProvider` 请求 `https://api.github.com/repos/Zhgma/animeko/releases`，按版本号、渠道、平台、架构与 Android 发行类型选择安装包。没有匹配 Release 时不提示更新，不回退到上游发行源。版本标签使用 `X.Y.Z` 或 `X.Y.Z-beta.N` 等三段版本号；版本必须高于当前安装版本。设置页的更新日志、版本号入口和更新提示的 Release 详情链接均指向本仓库，不自动添加 `v`。更新检查保留 API 返回的实际标签与附件下载地址，兼容已有的带 `v` 标签。草稿不参与更新，预发行版遵守设置中的更新渠道。

正式安装包沿用 `ci-helper` 命名：

- Android：`ani-<version>-arm64-v8a.apk`、`ani-<version>-armeabi-v7a.apk`、`ani-<version>-x86_64.apk`，通用包 `ani-<version>-universal.apk`。
- Android TV：前缀 `ani-tv-`；普通版与 TV 分别匹配。
- Android 调试版仅用于本地开发，不发布；历史调试安装不会自动选择正式签名包。
- Windows：`ani-<version>-windows-x86_64.zip`（ARM64 为 `aarch64`）。
- macOS：`ani-<version>-macos-aarch64.dmg`，Intel 为 `ani-<version>-macos-x86_64.zip`。
- Linux：`ani-<version>-linux-x86_64.appimage`，自动更新还需上游安装器要求的 `.zsync` 伴随文件。

Release 中上传对应安装包及可选同名 `.sha1`；Android 后续版本保持应用 ID、签名证书，并递增 `android.version.code`。GitHub SSH Deploy key 用于推送源码，不用于 APK 签名，也不能调用 GitHub Release 上传 API。私钥和本机构建工具不存入仓库。iOS 保留现有安装渠道，更新检查不提供 APK 或桌面安装包。

## 调试网络

正式 APK 沿用上游网络与代理默认设置，不预设 USB 调试代理。若测试设备直连 Bangumi API 超时，可临时执行 `adb reverse tcp:7890 tcp:7890`，并在该设备应用设置中选择自定义代理 `http://127.0.0.1:7890`，借用电脑上已运行的 HTTP 代理。此设置仅保存在设备应用数据中，需要保持 USB 连接和电脑代理运行；ADB 连接重建后可能需要重新建立转发。

结束调试时，在应用内恢复系统代理，并执行 `adb reverse --remove tcp:7890`。覆盖安装会保留应用数据，不能依赖换装 APK 自动清除设备上的调试代理设置。

## 验证

- `BangumiMangaRepositoryTest` 覆盖跨小说页分页、阅读进度、私密标识、空页终止、授权错误与取消传播。
- `MangaCollectionPageTest` 使用 `runAniComposeUiTest` 验证顶部双向切换、漫画卡片及分类动作。
- `MangaDetailsRepositoryTest`、`MangaSourceRepositoryTest` 验证评论分页、评分与作品信息解析、来源边界、章节排序、错误和取消传播。
- `commonTest` 中的 `MangaReaderTest` 验证详情内导航、单 / 双 / 三栏布局、图片文件解码、恢复页码、纵向滚动、双页左右顺序、配对偏移、窗口尺寸切换、章节动作、点击及自动隐藏控制栏、全屏和详情评论布局。
- `MangaReadAheadCacheTest` 验证整话与下一话预加载、前台请求去重、下一话目录及图片复用、缓存淘汰、错误重试、页面等待取消后的持续下载和取消传播。
- 真机截图与构建工具放在仓库外。部署与 UI 操作遵循 `.agents/skills/android-ui-verify/SKILL.md`。

运行接口测试：

```shell
./gradlew :app:shared:app-data:testAndroidHostTest --tests '*BangumiMangaRepositoryTest'
```

运行界面测试（Android 11 或以上设备）：

```shell
./gradlew :app:shared:ui-subject:connectedAndroidDeviceTest -Pandroid.min.sdk=30 -Pandroid.testInstrumentationRunnerArguments.package=me.him188.ani.app.ui.manga
```

测试命令的 SDK 覆盖仅用于允许该模块现有公共测试中的带空格方法名生成 DEX 040；不需要调整应用的默认最低 SDK。

可用于确认类型的公开条目：`118165`（GRANDBLUE 碧蓝之海）和 `355555`（薰香花朵凛然绽放）。这些条目不会自动加入用户收藏。

`ForkUpdateTest` 验证版本比较、渠道、草稿、架构、调试 / 正式 / TV 隔离、通用包回退和空仓库。

官方接口定义：https://github.com/bangumi/api/blob/master/open-api/v0.yaml

## 离线缓存与来源管理

`MangaPageControls` 统一收藏、评价、分页章节与来源管理控件。`MangaSourcesDialog` 使用番剧的 `MediaSelectorDialog` 自适应容器，`MangaSourceSettingsStore` 在 Preferences 中保存来源启用状态和自动选源顺序。停用不会静默解除用户的条目来源绑定；再次打开已停用的固定来源时提示重新启用或换源。阅读中的来源切换仅在分组和章节号唯一匹配时自动续读，无法确认时回到选章。

阅读器整话预加载仍使用 `MangaReadAheadCache`。用户主动选择缓存的章节则由 `MangaOfflineDownloads` 管理，使用与应用下载管理器相同生命周期的协程，关闭详情页后继续下载。`MangaOfflineStore` 在应用数据目录下保存独立图片和原子替换的索引；选中的队列、已完成页及错误可跨重启保留。重启后未完成项目由用户继续 / 重试，不静默启动网络传输。已完成章节直接用本地图片阅读；支持批量选择、暂停、缺页续传及删除确认。离线文件不参与会话预读的淘汰。

`MangaLibraryRepositoryTest` 验证收藏写入字段、失败不触发刷新、未收藏与授权错误的区别。`MangaOfflineStoreTest` 验证部分失败后恢复、重新打开索引后的离线阅读和删除。`MangaAlignmentTest` 验证收藏菜单、共享评价动作、来源管理、换源歧义与失败页不计为读完。接口写入使用 MockEngine 测试，不改动测试账号的真实收藏。

## 书籍 API 请求边界

漫画详情页、单行本与书架共用 `MangaRequestRouting`。搜索、详情、公开评论、直接关联作品均走书籍服务且不携带用户凭据。本人收藏读取、状态、话数/卷数、评分/短评/隐私写入携带当前 Bangumi token；服务端校验本人身份和目标 `type=1`。配置书籍服务后，未支持的请求明确失败，不回退直连 Bangumi。

漫画搜索结果（包括单行本）、漫画关联卡片和关系图中的书籍节点进入 `NavRoutes.MangaDetail`，关联动画进入原动画详情。封面、漫画源图片和用户明确打开的原站页面不属于书籍 JSON API。公开接口无法读取需要 Bangumi 身份权限的受限条目；404 不代表没有走书籍服务。

`BooksApiRoutingTest` 通过真实仓库方法覆盖系列和六个单行本的详情、评论、关联、评分、收藏和进度请求，并断言目标主机与凭据策略。服务端测试覆盖非书籍拒绝、关联结果保留动画信息、写入字段保留及评论缓存失效。

漫画章节目录默认倒序，可切换正序。排序只影响目录显示，切换后分页定位到当前阅读章节；阅读器的下一话顺序保持章节顺序。全屏阅读时，画面中间三分之一区域单击显示或隐藏控制条，两侧单击前进下一组；双击缩放与底部留白点击下一话各自保留。

全屏阅读在本话最后一组图片之后保留至少 96dp 的黑色区域；图片不足一屏时补足剩余高度。该区域随内容滚动，不覆盖图片，有下一话时点击切换章节。
