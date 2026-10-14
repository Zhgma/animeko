import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.settings.danmaku.*
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.settings.tabs.app.PlayerGroupContent
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

internal fun decodeWebPlayerConfig(retained: JsonObject): VideoScaffoldConfig {
    val config = retained["videoScaffoldConfig"]?.takeUnless { it == JsonNull }
        ?.let { webJson.decodeFromJsonElement<VideoScaffoldConfig>(it) } ?: VideoScaffoldConfig.Default
    val range = VideoScaffoldConfig.normalizePlaybackSpeedRange(config.minPlaybackSpeed..config.maxPlaybackSpeed)
    return config.withPlaybackSpeedRange(range)
}

fun readWebPlayerConfig(): VideoScaffoldConfig = decodeWebPlayerConfig(readWatchingSettings())

@Composable
fun SettingsScope.WebPlayerSettings() {
    var error by remember { mutableStateOf<String?>(null) }
    var config by remember { mutableStateOf(runCatching { readWebPlayerConfig() }.getOrElse {
        error = "播放器配置无效，请重新导入设置备份。"; VideoScaffoldConfig.Default
    }) }
    var filters by remember { mutableStateOf(runCatching {
        readWatchingSettings()["danmakuRegexFilters"]?.takeUnless { it == JsonNull }
            ?.let { webJson.decodeFromJsonElement<List<DanmakuRegexFilter>>(it) }.orEmpty()
    }.getOrElse { error = "弹幕过滤规则无效，请重新导入。"; emptyList() }) }
    val filterConfig = remember { runCatching {
        readWatchingSettings()["danmakuFilterConfig"]?.takeUnless { it == JsonNull }
            ?.let { webJson.decodeFromJsonElement<DanmakuFilterConfig>(it) } ?: DanmakuFilterConfig.Default
    }.getOrDefault(DanmakuFilterConfig.Default) }
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    fun save(key: String, value: JsonElement, done: () -> Unit) {
        try {
            val retained = readWatchingSettings()
            val next = if (value is JsonObject) mergeWatchingSettings(retained,key,value)
                else JsonObject(retained + (key to value))
            check(aniWrite("retainedSettings",next.toString())) { "保存失败，请检查浏览器存储权限。" }
            done(); error = null
        } catch (_: Exception) { error = "配置保存失败，请检查浏览器存储权限。" }
    }
    fun saveFilters(next: List<DanmakuRegexFilter>) = save("danmakuRegexFilters", webJson.encodeToJsonElement(next)) { filters = next }
    PlayerGroupContent(
        config = config,
        onConfigChange = { value -> save("videoScaffoldConfig",webJson.encodeToJsonElement(value)) { config = value } },
        filterConfig = filterConfig, onFilterChange = {},
        supports = { it in setOf("fullscreenSwitchMode", "autoPlayNext") },
        regexFilters = {
            DanmakuRegexFilterContent(filters, { adding = true },
                { item -> saveFilters(filters.filterNot { it.id == item.id }) },
                { item -> saveFilters(filters.map { if (it.id == item.id) it.copy(enabled = !it.enabled) else it }) },
                onExport = {
                    text = webJson.encodeToString(filters)
                    scope.launch {
                        try { request("configCopy",buildJsonObject { put("content",text) }.toString()) }
                        catch (_: Exception) { transfer = "export" }
                    }
                },
                onImport = { text = ""; transfer = "import" },
            )
        },
    )
    Group(title = { Text("网页支持情况") }) {
        TextItem(description = { Text("倍速范围、记住倍速、默认倍速、长按倍速、全屏按钮及自动连播在网页生效。灰色选项所需的弹幕引擎、超分、片头片尾识别、自动标记、自动切源、HLS 广告过滤和帧预览尚未接入。弹幕规则可编辑、导入与导出，并与 App 设置备份互通；网页当前不渲染弹幕。") }) { Text("播放器与弹幕") }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    if (adding) AddRegexFilterDialog({ adding = false }, { saveFilters(filters + it) }, { Text(stringResource(Lang.settings_danmaku_add_regex_filter)) })
    if (transfer.isNotEmpty()) AlertDialog(
        onDismissRequest = { transfer = "" },
        title = { Text(stringResource(if (transfer == "import") Lang.settings_danmaku_import_title else Lang.settings_danmaku_export_to_clipboard)) },
        text = { Column {
            Text("与 App 相同的弹幕过滤规则 JSON；导入会替换当前规则。")
            OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),readOnly=transfer=="export",maxLines=8)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({
            if (transfer == "export") transfer = ""
            else try {
                val next = webJson.decodeFromString<List<DanmakuRegexFilter>>(text)
                require(next.all { isValidRegex(it.regex) })
                saveFilters(next)
                if (error == null) transfer = ""
            } catch (_: Exception) { error = "规则格式或正则表达式无效，未修改现有规则。" }
        }) { Text(stringResource(Lang.settings_danmaku_confirm)) } },
        dismissButton = { TextButton({transfer=""}) { Text(stringResource(Lang.settings_danmaku_cancel)) } },
    )
}
