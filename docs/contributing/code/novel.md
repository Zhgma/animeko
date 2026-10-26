# 追文与文字阅读器

追番页的“追番 / 追漫 / 追文”分别显示动画、漫画和小说。漫画与小说共同使用原有 Bangumi 书籍搜索、详情和收藏通道；请求仍是 `type=1` / `subject_type=1`。客户端用 `BangumiBookKind` 检查 `type` 与 `platform`，分别接受“漫画”和“小说”，不通过书名、标签或关联关系猜测类型。未知平台及画集不混入任一分类。搜索缺失平台时读取条目详情，分页偏移始终按原始结果计算；空的过滤页继续向后查找。两个分类使用独立收藏 ViewModel，避免切换分类后显示另一类收藏或数量。

小说通过 `NavRoutes.NovelDetail` 打开详情与阅读页面。搜索右侧预览使用同一个完整详情组件并占满窗格；点击继续阅读或具体章节时，通过 `startReading` / `initialChapterId` 导航到独立阅读页，避免阅读器和全屏布局被限制在搜索窗格中。详情展示复用番剧的 `SubjectDetailsSingleColumnPage`、`MultiColumnScaffold`、封面动态配色、收藏与评分组件；按共享断点展示单栏、双栏和三栏。小说章节使用 `PagedEpisodesGrid`，进度单位为章 / 卷；导入电子书、书源、目录与缓存位于章节区域。BGM 的收藏分类、章 / 卷进度、评分、短评和评论复用现有书籍实现，读取正文与收藏不依赖新的后台。未登录用户可从“书籍 → 本地”导入文件；BGM 收藏操作仍需要账号。

## 文件与书源

Android 和桌面版支持 TXT、EPUB、MOBI、AZW3（也接受 AZW 扩展名）。TXT 识别 UTF-8、UTF-16 BOM 和 GB18030，按中文章节标题拆分。EPUB 按 OPF spine 顺序读取正文、目录标题和本地插图。MOBI 支持 PalmDOC 与 Huff/CDIC 解压；AZW3 重组 KF8 skeleton / fragment 索引。加密文件返回明确错误。文件内容仅转成文字与图片，不执行脚本、嵌入页面或样式中的远程代码。导入文件最大 96 MB，解压大小、单个资源大小、索引范围和资源路径均有检查。iOS 当前文件导入仅支持 UTF-8 TXT，其他格式的解析器在 JVM 源集；需要 macOS 平台实现和验证后才能宣称同等支持。

`NovelSourceRepository` 从 `NovelSourceSettingsStore` 读取订阅规则。默认固定订阅为 `https://raw.githubusercontent.com/Zhgma/animeko/main/subs/novel.json`；应用内置同一份首次启动缓存，启动及运行期间每小时刷新，刷新失败保留已导入来源。设置 → 数据源 → 小说源订阅支持新增、编辑、启停、刷新、复制和删除；来源优先级与禁用设置独立保存，刷新不覆盖阅读记录或离线缓存。

订阅使用 `exportedMediaSourceDataList.mediaSources`，每项 `factoryId=novel-rules`、`version=1`，`arguments` 对应 `NovelSourceDefinition`。静态 HTML 引擎支持 GET / POST 搜索、CSS 选择器、相对地址、目录分页、章节分页及文本清理；新增站点标识、改地址或改静态选择器只需更新订阅文件。全本 AMP、幻梦公开 API 使用原生协议辅助器，地址模板也由订阅维护；新增不同协议引擎仍需要应用更新。兼容的阅读 JSON 可直接导入；JavaScript、XPath 与登录凭据不会执行或导入，不兼容项在设置中说明。

目录包含全本小说、幻梦轻小说、就爱文学、书海阁、五八书阁和书海阁子。订阅可用 `unavailableReason` 暂停故障来源并在管理界面说明原因，远程修复后恢复可选。书海阁子搜索目前返回空结果，暂不参加选源。书海阁的直连可用性取决于网络。带 VIP / 收费标记的章节会被排除；源站拒绝访问、缺少正文或要求登录时显示失败。

自动与手动选源使用漫画共用的 `MediaSelectorTopBar`、`MediaSelectorWebSourcesColumn`、`ManualSourceSelector`、搜索框和结果列表。所有启用来源独立搜索，自动匹配最多同时查询三个来源；每源搜索限时 30 秒，无匹配时尝试最多三个 Bangumi 标题或别名；数字开头的标题另尝试数字简写（如“86”）。单源结果按标题相关性排序、去重并限制为 10 项，自动和手动面板共用这一上限；各来源就近显示搜索中、空结果、登录需求或失败原因。自动选择按来源优先级验证匹配作品的目录和首章正文，每源验证限时 30 秒；确定可读来源后，其余来源仍完成搜索供手动切换。打开选源面板时补齐尚未查询的来源，保留当前作品绑定。手动搜索保留各来源的结果，选中后先保存明确绑定，再读取目录；失败不会自动换源。管理来源支持排序、启停、网站与所属订阅入口。

## 阅读状态与缓存

文字阅读器复用漫画的 `MangaReaderLayout` 和视频控制层：竖屏为 70% 阅读区域加下方详情 / 评论，宽屏为阅读与详情并列，全屏只显示阅读区。非全屏单击切换控制栏；全屏左右两侧各三分之一点击向后翻页，中间切换控制栏，五秒后自动隐藏；设置和进度条交互期间保持显示。继续向后翻页到章末或点击章末提示进入下一章，最后一章显示结束提示。支持目录、章节切换、段落进度条、键盘翻页、F 全屏、Escape 返回；文字使用字号、行距、主题色 / 纸色 / 夜间背景。

阅读位置用段落索引与段内滚动位置保存，不以屏幕宽度计算固定页码。配置使用 `novel.reader.settings`，作品绑定使用 `novel.reader.{subjectId}`，每本书的位置独立保存在 `novel.position.*`。本地导入内容和插图复制到应用数据目录，系统文件选择器的临时授权结束后仍可阅读；已导入文件可以重新选择。

当前章和下一章预读在详情会话中共享，退出时清理临时插图。离线缓存使用应用生命周期队列、完整章节文件和原子替换的索引；支持整书 / 本章缓存、暂停、继续、失败重试和确认删除。中断后保留待下载记录，不在重启时自动进行网络请求。只有完整正文写入后才将章节标记为缓存完成。

缓存索引记录所属插图文件，插图缺失会将章节恢复为可重试状态；删除章节会停止当前下载并删除所属正文和插图。Android 文件选择器允许选择所有文件，再由解析器检查格式，避免系统 MIME 表不认识 AZW3 而将其禁用。

末段真正显示两秒后，只有明确的正整数阿拉伯数字章号才递增已收藏作品的 BGM 话数。前言、番外、模糊编号与本地文件不猜测对应的 BGM 卷数，可通过共用进度编辑入口手动修改。

## 验证

`BangumiNovelFilteringTest` 覆盖分类隔离、原始分页偏移和小说收藏进度；`NovelSourceRepositoryTest` 覆盖 AMP、收费过滤、正文 / 插图解析与取消；`NovelFileReaderTest` 覆盖编码、EPUB 顺序、路径检查、MOBI 解压和加密错误；`NovelReaderTest` 覆盖追文导航、全屏与字号调整后的阅读位置；`NovelStorageTest` 覆盖持久化、未完成下载、重试和书源隔离。

设置 `NOVEL_FIXTURE_DIR` 可用 Project Gutenberg 的 Alice EPUB / MOBI / KF8 文件运行真实格式回归。设置 `NOVEL_LIVE_TEST=1` 会测试先前指定的九本书的搜索、目录与首 / 中 / 末章，使用公开源站连接；这些网络测试默认跳过，避免普通测试依赖源站可用性。

2026-10-25 本地验证：40 项相关测试通过（包含九本书的在线抽查），Android Debug 构建和独立 Web Wasm 兼容编译通过。DBR-W10 实机成功导入四种格式，验证 TXT 滚动、全屏、字号、纸色与覆盖安装后恢复位置，以及 AZW3 正文和 EPUB 窄屏阅读。布局验证使用 2560×1600 / 340 dpi 与临时 1080×2400 / 440 dpi，之后恢复原设备显示设置。实机 BGM 搜索“86”分别得到小说与漫画结果，追文可读取账号原有小说收藏。未对真实账号执行新增收藏或修改进度；这些写入逻辑通过现有仓库测试验证。iOS 和桌面安装包的实际窗口未验证，本次未公开发布。

MOBI Huff/CDIC 与 KF8 重组参考并移植 calibre 的 GPLv3 算法，来源与版权保留在 `MobiReader.kt`，与本项目 AGPLv3 组合发布。

`NovelSubscriptionTest` 验证固定地址增补动态书源、更新规则、失败保留缓存、手动管理和旧阅读记录兼容；`NovelSourceSelectionTest` 验证匹配、优先级、超时回退与取消；`NovelSourcePanelTest` 和 `NovelSubscriptionGroupTest` 验证自动 / 手动共用控件与设置操作。`NovelReaderTest` 同时覆盖全屏左右点击、中心控制栏及章末跳转。`NovelCatalogLiveTest` 抽查新增来源首 / 中 / 末章；暂停来源明确跳过，`NOVEL_TEST_PROXY=1` 可使用本机 7890 代理复测书海阁。

本地入口位于收藏状态栏的“读过”后，数量通过 `NovelLocalLibrary` 监听 `novel.files.0` 中的导入列表，反映该入口可打开的电子书。绑定到单个 Bangumi 条目的导入文件仍保存在对应作品的阅读页面，不计入独立本地入口。

关联作品卡片和关系图节点通过 `rememberOpenBookRelation` 打开应用内详情。书籍关系缺少平台时，通过现有书籍通道读取被点击条目的 `platform`，小说进入 `NovelDetail`，漫画进入 `MangaDetail`；番剧使用原有详情路由。请求失败保留当前页面并显示错误。

在线插图请求携带书源主页的 Referer 和 User-Agent；订阅可覆盖这两个字段，账号 Authorization 与 Cookie 不转发到图片域名。阅读与离线缓存共用同一图片请求。正文解析保留普通、延迟加载和 SVG image 引用的顺序，EPUB 资源继续由本地路径校验和解析。`NovelSourceRepositoryTest` 覆盖插图请求头及相对地址，`NovelFileReaderTest` 覆盖 SVG 包装与纯插图章节；设置 `NOVEL_ILLUSTRATION_FIXTURE` 可用真实下载图片运行阅读器绘制测试。

## Z-Library

维护订阅包含 `ZLIBRARY`，协议为 `zlibrary`，主页来自订阅的 HTTPS `home`。`ZlibraryApi` 按 [Zlibrary-API](https://github.com/bipinkrish/Zlibrary-API) 的 EAPI 协议搜索和获取整本文件；支持 EPUB、TXT、MOBI、AZW3 / AZW。搜索结果显示作者、格式、语言和年份，供用户区分版本。

手动搜索或选择书籍缺少有效会话时，`ZlibraryLoginController` 打开登录界面。账号登录按开源协议向 `/eapi/user/login` 提交邮箱和密码，从响应读取 `user.id` / `user.remix_userkey`，再通过 `/eapi/user/profile` 验证后保存到应用私有 DataStore。密码仅保留在当前界面内存中，请求结束或切换方式时清空，不进入保存状态、日志或会话缓存。网页登录选项从该站点 Cookie 获取同一组会话凭据，并同时验证账号和搜索接口；搜索仍要求网页验证时保持页面打开。本站的 DiamWall 验证 Cookie 随会话保存在私有缓存，仅发送给所选站点，不发送给文件下载域名。搜索或下载遇到网页验证时，手动操作进入网页验证并在完成后重试；刷新按浏览器实例重建整个网页区域，旧实例关闭时不会清除新实例状态。验证成功自动关闭登录页并重试原操作一次。来源管理提供“登录／更新登录”入口。自动匹配和后台离线队列不弹出登录页，登录需求显示为可重试错误，用户可通过手动搜索完成登录。

会话绑定 HTTPS origin；订阅域名变化需要重新登录。401 或明确的未登录响应使旧凭据失效，网络失败、HTML 验证页和额度不足保留会话。账号接口禁止自动跟随重定向；下载接口限制 HTTPS 和重定向次数，下载主机不接收登录 Cookie。站点下载额度照常生效。

`ZlibraryBookCache` 在首次选择版本时下载整本文件，复用本地电子书解析器，将完整正文和插图保存到应用私有 `zlibrary-books` 目录。文件上限 96 MB，只有解析完成后才原子发布目录索引；取消或解析失败不标记成功。相同版本的缓存可跨页面和进程复用，不重复消耗下载次数，登录过期不妨碍读取完整本地缓存。阅读进度和章节离线队列使用现有小说实现。

`ZlibraryApiTest` 覆盖账号登录、表单编码、完整登录至文件请求链路、失败保留旧会话、会话验证与恢复、origin 隔离、登录过期、网络及额度错误、搜索格式筛选和下载凭据隔离。`ZlibraryBookCacheTest` 覆盖重启后的无网络阅读；`ZlibraryLoginControllerTest` 验证登录后只重试一次及取消操作清理登录请求。真实账号登录、下载配额与源站可用性需单独实测，模拟接口测试不代表真实账号验证成功。
