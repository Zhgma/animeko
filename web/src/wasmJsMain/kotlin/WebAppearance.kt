import androidx.compose.runtime.*
import androidx.compose.material3.*
import me.him188.ani.app.data.models.preference.*
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.settings.tabs.app.AppearanceGroupContent
import me.him188.ani.app.ui.foundation.widgets.NsfwMask

val LocalWebUISettings = staticCompositionLocalOf { UISettings() }
@Composable fun SettingsScope.WebAppearance(settings: UISettings, onChange: (UISettings) -> Unit) {
    AppearanceGroupContent(settings, onChange, supportsCollectionAnimation = false,
        supportsEpisodeAppearance = false, supportsCollectionSearchFilter = true,
        languageSettings = { TextItem(description = { Text("中文；网页多语言切换尚未接入") }) { Text("语言") } })
    Group(title = { Text("网页支持情况") }) {
        TextItem(description = { Text("初始页面、NSFW 筛选与遮罩、条目原名及番剧搜索排除看过/抛弃已生效。列表动画及剧集卡片尚未接入。网页窗口由浏览器管理。") }) { Text("显示选项") }
    }
}
@Composable fun WebNsfw(subject: Subject, content: @Composable () -> Unit) {
    val mode = LocalWebUISettings.current.searchSettings.nsfwMode
    var revealed by remember(subject.id, mode) { mutableStateOf(false) }
    NsfwMask(if (!subject.nsfw || revealed) NsfwMode.DISPLAY else mode,
        { revealed = true }, MaterialTheme.shapes.medium, content = content)
}
