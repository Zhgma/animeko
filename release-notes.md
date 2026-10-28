Animecomic 0.3.2-beta.6（Android ARM64 测试版）

- 中转服务器更新为 https://43.131.252.236:8443，涵盖 Bangumi 书籍与 Komiic。
- 设置 → 网络与代理新增“漫画与书籍中转”，可手动修改服务器地址，保存后刷新页面即可使用。
- 支持 API 配置订阅：手动地址留空时跟随订阅，在启动后及运行期间每小时自动检查，也支持立即刷新。订阅失败保留上次地址。
- 缓存中的旧中转图片地址会转换到当前服务器，保留图片签名与阅读数据。
- App 番剧与其他漫画源保持原网络路径。配置订阅下载不携带账号凭据。

默认配置：https://raw.githubusercontent.com/Zhgma/animeko/main/subs/api.json
升级此版本后，更换服务器无需再次安装 APK。请仅使用可信服务器和配置订阅。

本次提供 Android ARM64 Release APK，versionCode 50431；账号、缓存与阅读记录可覆盖升级保留。
