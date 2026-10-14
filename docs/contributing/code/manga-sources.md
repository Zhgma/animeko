# 漫画来源与选源

漫画来源适配器位于 `app-data` 的 `data/repository/manga`，界面与选源状态位于
`ui-subject` 的 `ui/manga`。网络、解析、记忆与选源逻辑都在 `commonMain`，各平台共享。

## 来源

`MangaSourceRepository` 提供 Komiic、漫画柜、G站漫画、优酷漫画、漫画人、CCC追漫台和 Comick 的维护来源；包子漫画适配器兼容用户自行导入的旧订阅。
订阅地址对应 [Venera configs](https://github.com/venera-app/venera-configs) 中的协议参考；
应用不执行远程 JavaScript，适配器更新随应用发布。

- Komiic：通过独立的 [Komiic API](../../../services/komiic-api/README.md) 搜索、读取目录和图片列表；服务端只生成固定查询，封面、站点图标和阅读图片通过受限图片端点加载，图片请求使用章节阅读页 Referer。客户端不向服务端发送 Komiic Cookie，其他来源保持原有会话和网络路径。
- 漫画柜：HTML 搜索/目录与受限的 LZ 字典数据解码。搜索标题优先取链接 title/封面 alt，
  封面更新提示不能作为作品标题。图片携带源站 Referer 与页面返回的签名。
- 包子漫画：不包含在维护订阅和内置回退清单中；手动导入仍兼容其公开 HTML/App 阅读页，网站验证可能阻止匿名访问。
- G站漫画：HTML 搜索，API 目录及图片元数据；API 和图片请求使用 G站 Referer。
  图片列表只按固定格式解码为 JSON，不执行脚本。
- 优酷漫画：默认停用，可在订阅中手动启用。HTML 搜索/目录，章节 `chapterImages` JSON 数组；图片主机读取站点 `config.js`，图片带移动站 Referer。
- 漫画人：HTML 搜索/目录，受限字典解码提取公开章节图片数组；图片带章节 Referer。不执行远程 JavaScript。
  部分旧章节可能已失效，404 明确报错。

- CCC追漫台：公开 API 搜索和目录，读取匿名可访问章节；使用站点的免费阅读图片封装并在客户端解码，付费或登录章节明确提示到原站阅读。
- Comick：HTML 内嵌 JSON 搜索、分页目录和分页图片列表，以英文为主；章节标题标出语言，读取同站分页，不执行远程脚本。

可访问性由运行时网络和站点策略决定。HTTP 401/403/429、网页验证和没有搜索结果分别显示；
不绕过登录、验证码或阅读额度。图片只对临时传输错误及服务器 5xx 自动重试一次。
遇到访问限制时停止当前/下一话的批量请求，等待用户处理或换源。

Komiic 阅读图片在所有详情会话与离线下载之间共用串行队列，每张请求结束后至少间隔一秒。
429 响应中的 `komiic_reading_limit` 显示为源站阅读额度已用完，其余限流显示等待时间；
图片队列遵守服务返回的 `Retry-After`（至少 30 秒），期间后续请求直接返回同一限制提示，
不继续发送，也不自动恢复已停止的整话下载。已缓存图片仍可阅读，当前话和下一话完整预取保持启用。
冷却结束只允许用户重新尝试，不代表源站阅读额度已恢复。

## API 地址与旧图片兼容

App 的书籍和 Komiic API 使用 `https://43.131.255.74:8443`。
旧服务器返回的签名图片 URL，以及本地响应缓存、来源绑定和阅读页记录中的旧图片地址，
在读取时迁移至新主机，完整保留路径和签名。只识别旧主机的 `/api/books/images/` 与
`/api/komiic/images/`；评论正文、自定义来源和其他网址保持原值。账号、阅读进度和离线文件保持原值。

## 自动选择与记忆

首次打开条目，`findReadableMangaSource` 按来源顺序查询，只对标题匹配的候选尝试加载目录及下载第一页。
每个来源有 30 秒总预算；失败后继续下一个，全部失败时保留各来源状态供手动搜索。
选源探测与阅读器共享图片缓存，成功探测的第一页不重复下载。

手动选择作品会先将 `MangaReadingMemory.sourcePinned=true` 写入该 Bangumi 条目的本地记忆，
再加载目录。暂时断网也保留选择，后续打开只使用指定来源，不静默切换。
点击“自动选择”会解除该条目的固定来源。旧记忆默认视为手动选择。
切换来源下拉框只浏览搜索结果，点击作品才改变阅读来源。

搜索结果按来源保存；切到已有相同关键词的来源不重复请求，搜索/刷新按钮明确触发重试。
选择器通过番剧的 `MediaSelectorModeChip` 区分“自动匹配”和“手动查找”。自动页使用 `MediaSelectorWebSourcesColumn` 展示全部来源及匹配作品，并提供手动查找入口。手动页使用 `ManualSourceSelector`、`MediaSelectorSearchField` 与 `ManualResultsList`，只展示当前来源的结果；进入手动页和换源时按当前关键词搜索，已有相同关键词的结果（包括失败）直接复用，编辑文字不发请求，提交或重试时重新搜索。点击结果绑定对应漫画并关闭选择器，保留本地来源记忆。弹窗顶栏共用 `MediaSelectorTopBar`，支持模式切换、关闭和漫画来源管理；容器沿用 `MediaSelectorDialog` 的宽窄屏布局。
章节目录优先显示现有分组，只有单行本的作品不会停留在空的“章节”分组。

整话重试会刷新图片目录，以取得有效的签名地址；后台缓存继续独立于列表项生命周期。

## 验证

常规回归包括 `MangaSourceRepositoryTest`、`MangaAdditionalSourcesTest`、`MangaReadingStoreTest`、
`MangaSourceSelectionTest`、`MangaSourcePanelTest` 和 `MangaReadAheadCacheTest`。
`MangaSourceLiveTest` 为可选 Desktop 网络检查：设置 `ANIMEKO_MANGA_LIVE=1`，
可选 `ANIMEKO_MANGA_PROXY`，验证既有来源的搜索、目录、图片列表、真实图片下载及 Skia 图片解码。
未设置环境变量时不会访问外部网站。`VerifiedMangaSourcesTest` 覆盖 CCC 免费章节和图片封装、Comick 目录/阅读分页与订阅边界；`VerifiedMangaSourcesLiveTest` 验证两个新增来源的实际搜索、目录、图片和 Skia 解码。

## 共享管理与交互验证

设置 → 数据源中的“漫画源订阅”使用番剧设置的 `SettingsScope.Group` / `Item` 和菜单样式，
支持新增、编辑名称与 URL、启停、单项刷新、刷新全部已启用订阅、复制链接和删除。
默认订阅为本仓库的 [`subs/manga.json`](../../../subs/manga.json)，公开地址为
`https://raw.githubusercontent.com/Zhgma/animeko/main/subs/manga.json`。
文件复用番剧的 `SubscriptionUpdateData` / `ExportedMediaSourceDataList`，每项的
`factoryId=manga-native`、`version=1`，`arguments` 包含 `name`、`key` 与 `url`。
该 factory 仅供漫画订阅解析，不进入番剧数据源注册表。
`MangaSubscriptionRepository` 同时兼容 Venera 的 `index.json` 或单源 `.js`，静态读取 `key`，
只将本版本已有原生适配器的来源加入选源列表；索引中的其他 key 显示为暂不支持。
脚本不会被执行，刷新只更新来源清单，不更新站点解析规则。错误格式、HTML 验证页、
无已适配来源的订阅、重复 URL 和 HTTP 错误会明确提示。sub 中显式的空 `mediaSources`
会清空该订阅的来源；完全不兼容的适配器或协议版本保留旧清单。

应用启动时在独立协程检查已启用订阅，运行期间每小时刷新，与番剧订阅使用相同默认周期和生命周期。
关闭应用后没有系统后台定时任务。刷新使用普通网络客户端，与漫画 Bangumi 专用代理隔离。
成功时间和尝试时间分别持久化；失败后保留上次来源与链接并等待下一周期，可随时手动重试。
设置页和后台共用一个 repository，互斥刷新防止重复下载；停用、编辑和删除不被进行中的刷新覆盖。

配置保存在 `manga.sources.settings` 的 `subscriptions` 中。维护清单和内置回退均为七个来源，优酷漫画默认停用；包子漫画只供自定义订阅兼容。
旧版原始内置链接一次性合并为仓库订阅，移除 MangaDex；保留自定义和已编辑的链接、原排序、
源级停用状态与已删除来源的停用偏好。已删除的仓库订阅和空配置不会自动补回。
新增和修改先下载验证，失败保留原配置与编辑内容；刷新失败
保留上次来源并记录错误。多个已启用订阅的来源合并去重，删除最后一个提供者才移除来源。
空订阅列表保持为空，不自动恢复内置来源。阅读记录和离线文件不随订阅删除。
设置使用 DataStore 原子更新，详情与阅读器观察同一 Flow；停用或删除的来源不会参与新搜索
与自动选源。详情内排序/启停写入仅更新对应字段，避免覆盖另一页面编辑的订阅。
`MangaSubscriptionTest` 与 `MangaSubscriptionAutoRefreshTest` 覆盖迁移、解析、保存、定时更新、
去重、错误保留及并发删除，`MangaMaintainedSubscriptionTest` 验证仓库文件与内置回退清单一致，
`MangaSubscriptionGroupTest` 覆盖表单、编辑失败、启停、刷新与删除确认。

详情与阅读器均通过 `MediaSelectorDialog` 打开同一来源面板。管理入口支持启用 / 停用、优先级上移、来源站点与订阅链接；状态独立保存在 `manga.sources.settings`，不修改番剧源设置。自动选择按启用来源的保存顺序尝试；每部漫画的手动绑定优先于自动选择。

HTML 访问使用番剧的 `PageEvaluator` 判断验证码，`WebSessionManager` 提供交互验证。来源行显示相同的验证入口与平台不支持提示；验证成功后重试搜索或目录。原生请求复用 `WebSourceCookieJar` 和 `WebSourceIdentityRegistry` 的按域 Cookie / User-Agent，避免浏览器验证成功后 HTTP 请求仍使用另一身份。HTTP 401 / 429 等访问限制不会伪装成验证码，也不会自动绕过额度。

`MangaSourceSessionTest` 验证验证码请求的生成、已验证的 Cookie / User-Agent 应用于原站，以及不发送到其他来源。

MangaDex 的枚举值仅用于兼容已保存的离线记录；不参与订阅导入、选源、搜索和网络读取。
移除的来源绑定不自动恢复，已有离线文件保留。空订阅配置保持为空，用户删除的订阅不会自动补回。

## Web 搜索宿主

`MangaSourceModels` 与 `MangaSourceSearch` 提供跨平台来源模型、查询和解析；原生 `MangaSourceRepository.search` 委托同一适配器，保留原生 HTTP、Cookie、验证及图片限流。Web 编译这些文件并注入浏览器 HTTP。`WebMangaSourceSession` 管理每个来源的查询代次、缓存、取消和按 BGM 条目持久化的来源选择。来源禁用时立即移除结果；旧查询即使晚返回也不能覆盖新查询。自动页展示匹配候选，不探测正文可读性；选中后明确提供原站入口。

`MediaSelectorTopBar` 和 `ManualResultsList` 是原生与 Web 直接共编译的纯展示文件。网页来源卡片和下拉框也复用番剧展示层。Web 的漫画搜索转发只接受固定适配器的 HTTPS 搜索路径，不转发用户凭据或跟随未知重定向。
