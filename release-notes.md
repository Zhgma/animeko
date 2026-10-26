Animecomic 0.3.2-beta.1 测试版（Android ARM64 / Windows x64）

- 修复手机和平板开启 VPN 后，GitHub 接口因共享出口限流导致检查更新失败的问题；接口异常时使用公开发布页面继续检查，限流期间减少重复请求。
- 修复小说阅读记录缺少封面的问题；保存阅读记录时保留封面，并自动补齐已有记录的缺失封面，保留阅读进度和排序。

Android：正式签名 Release APK，versionCode 50426。已有同签名版本可直接覆盖升级，请勿卸载或清除数据。
Windows：完整解压 ZIP 后运行 Ani/Ani.exe，保留同目录运行库。Windows 可执行文件未使用 Authenticode 签名。

本版本为测试版。应用内更新通道选择“测试版”后可检查到更新，正式版通道保持 0.3.1。
