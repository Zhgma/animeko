# 漫画数据源订阅

订阅链接：<https://raw.githubusercontent.com/Zhgma/animeko/main/subs/manga.json>

在 Animecomic 的「设置 → 数据源 → 漫画源订阅」中添加此链接。
应用默认订阅此文件，运行期间每小时检查更新，启动时检查是否已到更新时间。
停用的订阅不自动请求；失败保留上次清单，下一周期重试，也可手动刷新。
应用退出后没有系统定时任务，与番剧订阅生命周期一致。

## 文件格式

`manga.json` 复用番剧 sub 的 `SubscriptionUpdateData` / `ExportedMediaSourceDataList` 格式：

```json
{
  "exportedMediaSourceDataList": {
    "mediaSources": [
      {
        "factoryId": "manga-native",
        "version": 1,
        "arguments": {
          "name": "Komiic",
          "key": "komiic",
          "url": "https://cdn.jsdelivr.net/gh/venera-app/venera-configs@main/komiic.js"
        }
      }
    ]
  }
}
```

`factoryId` 标识漫画原生适配器；`version` 是参数协议版本。`name` 为来源名称，
`key` 匹配应用内的适配器，`url` 为该漫画源的订阅/协议参考链接。
每个 key 只能出现一次，URL 必须是无凭据的 HTTP(S) 地址。
番剧订阅管理器不注册 `manga-native`，因此此文件应导入漫画源订阅。

已支持的 key 为 `komiic`、`manhuagui`、`baozi`、`goda`、`manhuaren`、`ykmh`。
没有 MangaDex。优酷漫画在新安装中默认停用；包子漫画可能返回访问验证或 403。
网站可用性受网络和站点策略影响，维护此清单不保证每个章节始终可读。

## 维护

在 `mediaSources` 中增加、删除来源或修改链接，提交至本仓库 `main` 分支。
客户端成功刷新后保存清单及链接，已有的源级排序和启停偏好继续保留。
空 `mediaSources` 表示清空该订阅的来源；格式错误或仅包含未知适配器的文件不会替换缓存。
未知适配器与不支持的版本显示为暂不支持。

该文件更新来源清单和参考链接，不下发可执行代码。原生适配器负责搜索、目录与图片解析；
新增站点协议或修改解析规则需要发布包含对应适配器的应用版本。
