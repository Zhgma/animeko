# Bangumi 漫画收藏

追漫从 Bangumi 读取当前登录账号的漫画收藏。入口位于追番页顶部，与追番并列。

## 接入边界

- `CollectionPage.kt` 是收藏页面的接入文件：保存追番 / 追漫选择，将顶部标题交给 `TrackingCollectionTabs`，在选择追漫时显示 `MangaCollectionPage`。
- `app-data/.../data/repository/manga/BangumiMangaRepository.kt` 封装 Bangumi 的只读收藏接口与独立漫画模型。
- `ui-subject/.../ui/manga/` 包含追漫界面和 ViewModel。
- 翻译位于 `app-lang/src/androidMain/res/*/manga.xml`。

详情展示直接共用上游页面组件：`MultiColumnScaffold` 和 `RailCard` 具有模块内可见性，`SubjectInfoTable` 提供通用键值对重载，`SubjectCollectionStatsRow` 接受“在读 / 想读”标签；现有动画调用保留默认参数。更新上游时，保留新增漫画文件及这些小型接入点。漫画数据独立于追番的 Room 表、剧集模型和收藏同步任务；阅读界面复用播放器的通用布局与控制栏框架。

## 数据与登录

复用 `SessionManager.sessionFlow` 中的 Bangumi access token，以及应用的 `HttpClientProvider`。使用 Bangumi 官方 v0 API：

1. `GET /v0/me` 取得当前 Bangumi 用户名。
2. `GET /v0/users/{username}/collections?subject_type=1&type=...&limit=30&offset=...` 分页读取书籍收藏，包括当前账号有权访问的私密收藏。
3. `GET /v0/subjects/{id}` 读取书籍类型，只展示 `type=1` 且 `platform=漫画` 的条目。

分页偏移按原始书籍条目数推进，避免只有小说的页提前终止漫画列表。条目详情最多同时请求四个。列表按页逐步呈现，失败后提供重试，不将错误当作空收藏。更换账号或分类会取消旧请求并清空旧列表。收藏状态分别映射为 1 想读、3 在读、2 读过、4 搁置、5 抛弃。

阅读进度取 `ep_status` 和 `vol_status`。总话数 / 总卷数未填写时显示 `—`。封面进入应用内漫画详情。收藏管理在 Bangumi 完成，返回后可下拉或点击刷新同步。

## 详情、漫画源与阅读器

详情页使用动画详情同一套 `SubjectDetailsLayoutParams.calculate`：小于 600dp 为单栏，600–1599dp 为双栏，1600dp 起为三栏，尺寸依据页面实际可用宽度。窄屏直接使用 `SubjectDetailsSingleColumnPage` 的封面、吸顶标签与嵌套滚动；双栏和三栏直接使用 `MultiColumnScaffold`，封面、阅读按钮及作品信息位于左侧，中间显示标题、简介和章节，三栏时评分与评论位于右侧 `RailCard`。评分摘要、直方图、收藏统计、标签、作品信息表和简介使用动画的共用组件。`SubjectInfo` 只用作窄屏页头的显示适配对象，不进入动画仓库或接口。

作品信息和评分来自 Bangumi v0；评论通过独立的 `next.bgm.tv/p1/subjects/{id}/comments` 分页获取，预览与完整评论列表均可在应用内查看。p1 接口可能变化，其解析集中在 `MangaDetailsRepository`。章节和来源选择使用 Compose 跨平台底部面板。

`MangaSourceRepository` 提供 Komiic 和漫画柜的原生适配器，参考以下源订阅协议：

- https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js
- https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/manhuagui.js

订阅地址展示在章节页。适配器随应用发布，不执行远程 JavaScript，也不承诺兼容任意 Venera 插件或脚本热更新。Komiic 使用 GraphQL 获取搜索、章节和图片；漫画柜解析 HTML 与 LZ-string 压缩数据。用户通过搜索或作品链接明确选择来源，绑定按 Bangumi 条目 ID 保存。单行本、普通章节和番外分组，数字章节按数值排列，翻到上一话 / 下一话时限定同一分组。站点登录要求、限额及网络错误直接提示，不将错误当成空章节。

`MangaReaderScreen`、ViewModel、网络解析和阅读记录均位于 `commonMain`，使用 Compose Multiplatform、Ktor、现有 `AsyncImage` 与 `SystemPath`。阅读器使用纵向连续滚动：竖屏每行一页，横屏每行两页，默认先右后左。可以切换先左后右，也可以通过“配对”切换首张单独显示，修正双页配对偏移。奇数末页保留在阅读顺序的起始侧。窗口尺寸变化时保留当前页所在的行；按钮、键盘上下方向键 / PageUp / PageDown 和滑块按当前单双页布局定位。支持目录、双击与双指缩放。图片按需请求并携带来源站点要求的 Referer，写入应用缓存后交给共用图片加载器解码。当前话与同一分组的下一话自动在后台完整缓存，优先处理当前话。前台图片请求和预加载共用互斥缓存，避免重复下载。下一话的图片目录也缓存，因此翻到已缓存章节无需重新获取目录。详情栏显示两话的缓存进度与失败信息，重试只补未完成的图片；取消操作会传播，网络错误不会伪装成缓存完成。两话预加载完成后清理更早章节，退出作品详情时释放本次会话的缓存文件。

阅读页面使用正式的 `NavRoutes.MangaDetail` 导航，因此不占用追番主页的导航栏区域。搜索宽屏侧栏的章节动作通过该路由携带 `initialChapterId`，在独立页面恢复来源并打开指定章节，全屏尺寸不受搜索侧栏限制。视频页的 `EpisodeScreenLayout` 与布局模式定义位于 `video-player/.../PlayerScreenLayout.kt`，动漫和漫画共用同一份布局实现；视频页的侧栏状态及模式选择仍由视频模块管理。漫画窄屏使用上方 16:9 阅读区与下方“详情 / 评论”，宽屏使用阅读区与右侧详情栏，全屏仅显示阅读区，切换时不重建阅读内容。单双页依据整个阅读页面的横竖方向，不依据嵌入阅读区的 16:9 比例。

阅读器控制层直接使用 `VideoScaffold` 和 `PlayerControllerState`。初始和进出全屏时隐藏，单击阅读区域切换显示，五秒无操作后自动隐藏；拖动进度条和打开阅读设置期间保持显示。控制栏覆盖在图片上，显隐不改变阅读区高度；左右顺序与首张配对位于阅读设置。全屏使用现有 `setRequestFullScreen` / `setSystemBarVisible` 跨平台接口，保留设备横竖方向，返回优先退出全屏。桌面支持 F 切换全屏、Escape 退出、空格切换控制栏。

`MangaReadingStore` 在已有 Preferences DataStore 中使用 `manga.reader.{subjectId}` 命名空间，保存来源绑定、章节、页码、左右阅读顺序与首张配对方式。该本地记录不修改 Bangumi 话数或卷数；漫画模块不写入番剧数据库。Android、桌面和 iOS 共用阅读器实现；iOS 编译与真机验证需要 macOS / Xcode。

## 评价与关联作品

漫画详情和阅读器使用同一组评分、评论入口。`EditableRatingDialogsHost` / `RatingEditorDialog` 显示十星评分、短评和隐私选项；评论使用番剧的 `CommentItem`，包含头像、星级、时间、BBCode 富文本、图片查看器与复制菜单。`GET /v0/users/{username}/collections/{subjectId}` 读取本人的完整评价，未收藏时提示先收藏；`PATCH /v0/users/-/collections/{subjectId}` 只提交 `rate`、`comment`、`private`，保留收藏分类、话数、卷数和标签。提交失败保留编辑内容并显示错误。

`GET /v0/subjects/{id}/subjects` 提供直接关联作品。详情复用 `RelatedSubjectCard`，关系图复用 `SubjectRelationGraphScreen` 的宽窄屏布局，以当前作品为根显示直接关联，不推断传递关系或前后篇次序。可选 `relationLabel` 保留 Bangumi 的“动画”“单行本”等原始关系名；原番剧图继续使用默认枚举标签。书籍跳转漫画详情，动画跳转原番剧详情，其他类型打开 Bangumi。

图片下载由详情会话的独立协程管理，前台页面只等待共享任务；LazyColumn 回收页面或取消等待不会中止下载。并发上限为三张，后台持续预取本话和下一话，重试只补缺页。关闭作品详情会取消该会话任务并释放缓存。

## Fork 发布与应用更新

更新检查使用应用配置的 `HttpClientProvider` 请求 `https://api.github.com/repos/Zhgma/animeko/releases`，按版本号、渠道、平台、架构与 Android 发行类型选择安装包。没有匹配 Release 时不提示更新，不回退到上游发行源。Release 详情链接也指向本仓库。版本标签使用 `vX.Y.Z` 或 `vX.Y.Z-beta.N` 等三段版本号；版本必须高于当前安装版本。草稿不参与更新，预发行版遵守设置中的更新渠道。

正式安装包沿用 `ci-helper` 命名：

- Android：`ani-<version>-arm64-v8a.apk`、`ani-<version>-armeabi-v7a.apk`、`ani-<version>-x86_64.apk`，通用包 `ani-<version>-universal.apk`。
- Android TV：前缀 `ani-tv-`；普通版与 TV 分别匹配。
- Android 调试版：文件名额外含 `-debug`，例如 `ani-5.0.0-arm64-v8a-debug.apk`。调试版不会选择正式签名包。
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
