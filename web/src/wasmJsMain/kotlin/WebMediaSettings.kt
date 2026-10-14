import kotlinx.serialization.json.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

@Composable fun SettingsScope.WebMediaSettings() {
    val media = rememberBrowserMedia()
    val s = media.state.settings
    var editing by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    Group(title = { Text("WebTorrent") }) {
        TextItem(description = { Text("当前页保持打开时传输。只连接 WebRTC / Web Seed；连接数和速度限制实时生效，Tracker 与 Web Seed 设置用于新启动任务。") }) { Text("浏览器 BT 引擎") }
        listOf("maxConns" to "最大连接数", "downloadLimit" to "下载限速（KiB/s）", "uploadLimit" to "上传限速（KiB/s）").forEach { (key,label) ->
            val number = when(key) { "maxConns" -> s.maxConns.toLong(); "downloadLimit" -> if(s.downloadLimit < 0) -1 else s.downloadLimit / 1024; else -> if(s.uploadLimit < 0) -1 else s.uploadLimit / 1024 }
            TextItem(onClick = { editing = key; value = number.toString(); inputError = null }, description = { Text(if(number < 0) "不限速" else number.toString()) }) { Text(label) }
        }
        SwitchItem(s.webSeeds, { media.settings(s.copy(webSeeds = it)) }, title = { Text("启用 Web Seed") }, description = { Text("允许通过种子内的 HTTP(S) 链接获取文件；远程服务须允许跨域。") }, enabled = !media.busy)
        TextItem(onClick = { editing = "trackers"; value = s.trackers.joinToString("\n"); inputError = null }, description = { Text("额外 ${s.trackers.size} 个 WSS Tracker；种子自带的可用 Tracker 仍会使用。") }) { Text("Tracker 列表") }
        MediaStatus(media)
    }
    editing?.let { key -> AlertDialog(onDismissRequest = { editing = null }, title = { Text(if(key == "trackers") "WSS Tracker（每行一个）" else "输入数值") }, text = { Column {
        OutlinedTextField(value, { value = it }, modifier = Modifier.fillMaxWidth(), supportingText = { Text(if(key == "maxConns") "1–100" else if(key == "trackers") "仅支持 wss://，最多 20 个" else "-1 不限速，0 停止该方向传输") })
        inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }; MediaStatus(media)
    } }, confirmButton = { TextButton({
        val n = value.toLongOrNull()
        val next = when {
            key == "trackers" -> s.copy(trackers = value.lines().map { it.trim() }.filter { it.isNotEmpty() })
            n == null || (key == "maxConns" && n !in 1..100) || (key != "maxConns" && n !in -1..1048576) -> null
            key == "maxConns" -> s.copy(maxConns = n.toInt())
            key == "downloadLimit" -> s.copy(downloadLimit = if(n < 0) -1 else n * 1024)
            else -> s.copy(uploadLimit = if(n < 0) -1 else n * 1024)
        }
        if(next == null) inputError = "数值超出范围" else media.action("mediaSettings", buildJsonObject { put("settings", webJson.encodeToJsonElement(BrowserMediaSettings.serializer(), next)) }) { editing = null }
    }, enabled = !media.busy) { Text("保存") } }, dismissButton = { TextButton({ editing = null }) { Text("取消") } }) }
}

@Composable fun SettingsScope.WebMediaStorage() {
    val media = rememberBrowserMedia()
    var usage by remember { mutableStateOf<BrowserMediaState?>(null) }
    TextItem(onClick = { media.action("mediaStorage") { usage = media.state } }, description = { Text(usage?.let { "已用 ${mediaBytes(it.usage)} / 可用配额 ${mediaBytes(it.quota)}（浏览器估计值）" } ?: "统计本站的浏览器存储占用和配额") }) { Text("浏览器存储空间") }
    TextItem(description = { Text("任务记录与订阅保存在本浏览器。下载缓存可能被浏览器自动回收；刷新后不会自动下载，需点击继续重新校验。清理站点数据会同时清除这些内容。") }) { Text("下载缓存") }
    MediaStatus(media)
}
