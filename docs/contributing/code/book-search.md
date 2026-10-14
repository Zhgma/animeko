# 漫画与书籍搜索

搜索页的放大镜后提供“番剧 / 漫画”选择器，默认 `SubjectType.ANIME`。漫画模式使用 `SubjectType.BOOK`，对应 Bangumi 书籍类型 `1`，包含漫画系列、单行本和小说。

`BangumiBookSearchRepository` 通过 `MangaBangumiHttp` 读取书籍 API。`MangaRequestRouting` 共享 App / Web 的书籍服务地址、路由、凭据和超时规则：公开搜索、详情、评论不携带 Bangumi token；本人收藏查询与状态 / 阅读进度写入使用当前账号 token；评分和关联保留原接口。

`BookSearchRequest.kt` 共享搜索 JSON 构造与 `BOOK_SEARCH_PAGE_SIZE=20`；匹配、排名与收藏人数分别映射 `match`、`rank`、`heat`，日期排序明确不支持。关键词、NSFW 设置在接口处理。分页按服务端返回的原始条数推进，空页或总数到达后结束；取消传播给分页调用者，网络错误由既有分页错误和重试界面展示。浏览器传输使用 fetch，原生使用 Ktor，二者编译同一份路由与参数源文件。

`SearchViewModel` 根据查询类型选择原有番剧搜索或书籍仓库。切换类型保留关键词、清除类型专属筛选和旧详情、取消旧查询。书籍不使用动画季度筛选，也不提供 Bangumi 搜索 API 未支持的日期排序。搜索建议使用当前类型的数据源，搜索历史沿用现有存储。

结果复用 `SearchPage`、`SearchResultColumn`、`SubjectCoverCard`、`SubjectPreviewItem` 和响应式列表 / 详情布局。每个结果携带类型，过期类型的点击不能进入错误的详情服务。窄屏打开 `NavRoutes.MangaDetail`，宽屏在现有详情侧栏显示漫画详情；番剧的详情和播放行为保持原有路径。

验证：`BangumiBookSearchRepositoryTest` 覆盖请求类型、认证、分页、小说结果、错误、取消和类型切换；`BookSearchUiTest` 覆盖搜索栏类型切换。

接口定义：[Bangumi v0 API](https://github.com/bangumi/api/blob/master/open-api/v0.yaml)。
