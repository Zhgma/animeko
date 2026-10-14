import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.settings.tabs.media.SourceKindPreferenceItem
import me.him188.ani.app.ui.settings.tabs.media.MediaPreferenceItems
import me.him188.ani.app.ui.settings.tabs.media.BtPreferenceItems
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

fun readWatchingSettings(): JsonObject = aniRead("retainedSettings").takeIf { it.isNotBlank() }
    ?.let { webJson.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())
fun readMediaPreference(): MediaPreference = readWatchingSettings()["defaultMediaPreference"]
    ?.takeUnless { it == JsonNull }?.let { webJson.decodeFromJsonElement<MediaPreference>(it) } ?: MediaPreference.PlatformDefault
fun readMediaSelectorSettings(): MediaSelectorSettings = readWatchingSettings()["mediaSelectorSettings"]
    ?.takeUnless { it == JsonNull }?.let { webJson.decodeFromJsonElement<MediaSelectorSettings>(it) } ?: MediaSelectorSettings.Default

fun mergeWatchingSettings(retained: JsonObject, key: String, value: JsonObject): JsonObject =
    JsonObject(retained + (key to JsonObject((retained[key] as? JsonObject).orEmpty() + value)))

@Composable
fun SettingsScope.WebWatchingPreferences() {
    var error by remember { mutableStateOf<String?>(null) }
    var preference by remember { mutableStateOf(runCatching { readMediaPreference() }.getOrElse { error = "观看偏好配置无效，请重新导入设置备份。"; MediaPreference.PlatformDefault }) }
    var selector by remember { mutableStateOf(runCatching { readMediaSelectorSettings() }.getOrElse { error = "选源配置无效，请重新导入设置备份。"; MediaSelectorSettings.Default }) }
    fun save(key: String, value: JsonObject, done: () -> Unit) {
        try {
            val next = mergeWatchingSettings(readWatchingSettings(),key,value)
            check(aniWrite("retainedSettings",next.toString())) { "保存失败，请检查浏览器存储权限。" }
            done(); error = null
        } catch (e: Exception) { error = e.message }
    }
    val onPreference: (MediaPreference) -> Unit = { value ->
        if (value.alliancePatterns.orEmpty().any { it.isNotBlank() && runCatching { Regex(it) }.isFailure }) error = "字幕组正则表达式无效。"
        else save("defaultMediaPreference",webJson.encodeToJsonElement(value).jsonObject) { preference = value }
    }
    val onSelector: (MediaSelectorSettings) -> Unit = { value -> save("mediaSelectorSettings",webJson.encodeToJsonElement(value).jsonObject) { selector = value } }
    Group(title = { Text(stringResource(Lang.settings_media_preference_title)) }, description = {
        Text(stringResource(Lang.settings_media_preference_description) + "\n" + stringResource(Lang.settings_media_preference_override_notice))
    }) {
        MediaPreferenceItems(preference,onPreference)
        Group(title = { Text(stringResource(Lang.settings_media_advanced_settings)) },
            description = { Text(stringResource(Lang.settings_media_advanced_settings_description)) }, useThinHeader = true) {
            SourceKindPreferenceItem(selector.preferKind) { onSelector(selector.copy(preferKind = it)) }
            BtPreferenceItems(preference,selector,onPreference,onSelector,
                supportsDisabledSources = false, supportsCompletedSubjects = true, supportsRememberedSource = false)
            TextItem(description = { Text("灰色项需要禁用来源临时启用和来源记忆，网页尚未接入。偏好作用于手动选源列表；完整自动选源仍未接入。") }) { Text("网页支持情况") }
        }
        error?.let { Text(it,color = MaterialTheme.colorScheme.error) }
    }
}

private fun FeedItem.languageIds() = subtitles.map { when(it) { "简中" -> "CHS"; "繁中" -> "CHT"; "英语" -> "ENG"; "日语" -> "JPN"; else -> it } }
fun watchingExclusion(item: FeedItem, preference: MediaPreference, selector: MediaSelectorSettings, finished: Boolean?): String? = when {
    selector.hideSingleEpisodeForCompleted && finished == true && (item.episodeStart < 0 || item.episodeStart == item.episodeEnd) -> "完结番单集资源"
    !preference.showWithoutSubtitle && item.subtitles.isEmpty() -> "无字幕"
    else -> null
}
fun watchingComparator(preference: MediaPreference, selector: MediaSelectorSettings, finished: Boolean? = null): Comparator<FeedItem> {
    fun rank(value: String, ordered: List<String>?): Int = ordered?.indexOf(value)?.takeIf { it >= 0 } ?: if(ordered == null) 0 else Int.MAX_VALUE
    val patterns = preference.alliancePatterns.orEmpty().filter { it.isNotBlank() }.mapNotNull { runCatching { Regex(it) }.getOrNull() }
    return compareBy<FeedItem> { item ->
        item.languageIds().minOfOrNull { rank(it,preference.fallbackSubtitleLanguageIds) } ?: Int.MAX_VALUE
    }.thenBy { rank(it.resolution,preference.fallbackResolutions) }
        .thenBy { item -> patterns.indexOfFirst { it.containsMatchIn(item.alliance) }.takeIf { it >= 0 } ?: if(patterns.isEmpty()) 0 else Int.MAX_VALUE }
        .thenBy { if(finished == true && selector.preferSeasons && it.episodeEnd > it.episodeStart && it.episodeStart >= 0) 0 else 1 }
}
