# 应用更新检查

`UpdateChecker` 通过应用的 `ScopedHttpClient` 查询本仓库的 GitHub Releases。API 不可用时读取同仓库公开发布页面，并从实际的附件列表提取下载地址。两条路径共用代理、版本比较、发布渠道和安装包匹配规则；Android 正式、调试与 TV 包不互相替代。

API 返回 403 或 429 时，当前检查器根据 `Retry-After` / `X-RateLimit-Reset` 暂停 API 请求，缺少这些头时等待一小时；等待期间检查公开发布页面。取消检查会传播取消信号。公开页面访问失败、内容无法识别或分页超过上限时报告检查失败，不将失败显示为没有更新。公开页面最多读取 50 页，只为比当前版本新且符合渠道的发布读取附件。

`UpdateCheckerProxyTest` 覆盖 API 限流及恢复时间、正常 API 请求、公开页面分页、发布渠道、Android 包隔离、下载地址来源、取消以及备用页面失败。`ForkUpdateTest` 覆盖版本和安装包选择。
