import me.him188.ani.app.data.models.preference.UISettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.RectangleShape
import me.him188.ani.app.navigation.SettingsTab as SettingsCategory
import me.him188.ani.app.ui.settings.SettingsCategoryItems
import me.him188.ani.app.ui.settings.settingsTabName
import me.him188.ani.app.ui.settings.settingsTabIcon
import me.him188.ani.app.ui.settings.account.SelfInfoBannerContent
import me.him188.ani.app.ui.settings.tabs.theme.ThemeGroupContent
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.*
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.adaptive.TopAppBarSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.tabs.theme.DarkModeSelectPanel
import me.him188.ani.app.ui.settings.tabs.theme.ThemePalette
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.vector.ImageVector
import me.him188.ani.app.ui.settings.SettingsNavigationItem
import kotlinx.coroutines.launch

private data class WebSettingsEntry(val key: String, val label: String, val icon: ImageVector)

@Composable
fun WebSettings(settings: ThemeSettings, onChange: (ThemeSettings) -> Unit, session: WebResult, onAccount: () -> Unit,
    onBack: () -> Unit, route: String, onRoute: (String) -> Unit, originalTitle: Boolean, onOriginalTitle: (Boolean) -> Unit, uiSettings: UISettings, onUISettings: (UISettings) -> Unit, onSession: (WebResult) -> Unit) {
    val selected = route.substringAfter('/', "")
    val routeTabs = mapOf("account" to SettingsCategory.PROFILE, "appearance" to SettingsCategory.APPEARANCE,
        "theme" to SettingsCategory.THEME, "player" to SettingsCategory.PLAYER, "sources" to SettingsCategory.MEDIA_SOURCE,
        "selector" to SettingsCategory.MEDIA_SELECTOR, "danmaku" to SettingsCategory.SERVER, "proxy" to SettingsCategory.PROXY,
        "bt" to SettingsCategory.BT, "storage" to SettingsCategory.STORAGE, "backup" to SettingsCategory.SETTINGS_BACKUP,
        "update" to SettingsCategory.UPDATE, "about" to SettingsCategory.ABOUT, "log" to SettingsCategory.LOG)
    var confirmClear by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val listWidth = if (maxWidth >= 1200.dp) 412.dp else 360.dp
        val active = selected.ifEmpty { if (wide) "appearance" else "" }
        val activeTab = routeTabs[active] ?: SettingsCategory.Default
        val entry = WebSettingsEntry(active, settingsTabName(activeTab), settingsTabIcon(activeTab))
        val size = currentWindowAdaptiveInfo1().windowSizeClass
        val barSize = when {
            size.isHeightAtLeastExpanded -> TopAppBarSize.LARGE
            size.isHeightAtLeastMedium -> TopAppBarSize.MEDIUM
            else -> TopAppBarSize.SMALL
        }
        val pageColor = MaterialTheme.colorScheme.surfaceContainerLowest
        Surface(color = pageColor) {
            Row(Modifier.fillMaxSize()) {
                if (wide || active.isEmpty()) {
                    Column(if (wide) Modifier.width(listWidth).fillMaxHeight() else Modifier.fillMaxSize()) {
                        AniTopAppBar(title = { AniTopAppBarDefaults.Title(stringResource(Lang.settings)) },
                            navigationIcon = { BackNavigationIconButton(onBack) }, size = barSize)
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = size.paneHorizontalPadding - 8.dp)
                            .padding(top = size.paneVerticalPadding - 8.dp, bottom = size.paneVerticalPadding)) {
                            SelfInfoBannerContent(session.signedIn, session.nickname, "", wide && active == "account",
                                { onRoute("settings/account") }, onAccount,
                                avatar = { AvatarImage(session.avatar.takeIf { it.isNotBlank() }, it) },
                                modifier = Modifier.fillMaxWidth(), containerColor = pageColor)
                            SettingsCategoryItems { tab ->
                                SettingsNavigationItem(settingsTabName(tab), settingsTabIcon(tab), wide && tab == activeTab,
                                    { onRoute("settings/" + routeTabs.entries.first { it.value == tab }.key) })
                            }
                        }
                    }
                }
                if (wide || active.isNotEmpty()) {
                    val detailColor = if (wide) MaterialTheme.colorScheme.surfaceContainerLow else pageColor
                    Surface(Modifier.weight(1f).fillMaxHeight(), color = detailColor,
                        shape = if (wide) MaterialTheme.shapes.extraLarge.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)) else RectangleShape) {
                    Column {
                        AniTopAppBar(title = { AniTopAppBarDefaults.Title(entry.label) }, size = barSize,
                            navigationIcon = { if (!wide) BackNavigationIconButton({ onRoute("settings") }) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = if (wide) MaterialTheme.colorScheme.surfaceContainer else pageColor))
                        SettingsTab(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = size.paneHorizontalPadding - 16.dp, vertical = size.paneVerticalPadding)) {
                        when (active) {
                            "account" -> if (session.signedIn) {
                                WebAccountProfile(session, emptyList(), { onAccount() }, null, onSession = onSession)
                            } else {
                                TextItem(onClick = onAccount) { Text(stringResource(Lang.settings_account_login_register)) }
                            }
                            "appearance" -> WebAppearance(uiSettings.copy(subjectAppearance = uiSettings.subjectAppearance.copy(useOriginalTitle = originalTitle)), onUISettings)
                            "theme" -> {
                                ThemeGroupContent(settings, onChange, supportsDynamicColors = false,
                                    showFrostedGlass = !wide, supportsPageEffects = false)
                                Group(title = { Text("网页支持情况") }) {
                                    TextItem(description = { Text("灰色选项依赖播放器或详情特效，网页尚未接入；深浅色、纯黑和主题色可用。") }) { Text("页面效果") }
                                }
                            }
                            "sources" -> WebSubscriptions()
                            "selector" -> WebWatchingPreferences()
                            "bt" -> WebMediaSettings()
                            "player" -> WebPlayerSettings()
                            "proxy" -> Group(title = { Text(entry!!.label) }) {
                                TextItem(description = { Text("网页通过远程 HTTPS API 访问 Animeko、Bangumi、订阅和网站来源；漫画使用 App 的漫画 API。无需本机转发进程。") }) { Text("网络路径") }
                                TextItem(onClick = { scope.launch { result = try { request("networkCheck").raw } catch (e: Exception) { e.message } } }) { Text("检查漫画 API") }
                                TextItem(description = { Text("若 bgm.tv 出现 ERR_CERT_COMMON_NAME_INVALID，请修复 DNS / 代理路由后重新发起授权。不要关闭证书校验。") }) { Text("Bangumi 授权证书错误") }
                            }
                            "storage" -> Group(title = { Text(entry.label) }) {
                                WebMediaStorage()
                                TextItem(description = { Text("仅缓存公开作品资料。登录状态保存在此网站的浏览器本地存储中，重开网页可恢复，退出账号时清除。图片缓存由浏览器管理。") }) { Text("网页存储") }
                                TextItem(onClick = { confirmClear = true }, description = { Text("清除本站保存的公开搜索和详情缓存，不清除账号会话与主题。") }) { Text("清理资料缓存") }
                            }
                            "backup" -> WebSettingsBackup(settings, uiSettings, onChange, onUISettings)
                            "about" -> Group(title = { Text("AnimeComic Web") }) {
                                TextItem(onClick = { aniOpen("./source.zip") }, description = { Text("本次部署对应源码与构建说明 · AGPL-3.0") }) { Text("下载源码") }
                                TextItem(description = { Text("Kotlin/Wasm · Compose Multiplatform · AGPL-3.0。界面直接编译仓库内共享页面和组件。") }) { Text("网页开发版") }
                                TextItem(description = { Text("当前可浏览作品、搜索、查看追番追漫和编辑收藏。已接入订阅、WebTorrent 和浏览器媒体播放；漫画正文及完整原生设置仍有缺项，详细状态见 web/PARITY.md。") }) { Text("功能状态") }
                            }
                            else -> Group(title = { Text(entry?.label ?: "设置") }) {
                                TextItem(description = { Text(when(active) {
                                    "selector" -> "网页已有手动 RSS 种子选源；App 的自动选源优先级、字幕偏好和回退规则尚未接入。"
                                    "danmaku" -> "本站目前使用固定 Animeko 服务列表并自动故障转移。服务器区域选择、测速与配置持久化尚未接入，可以沿用 App 的服务选择接口继续适配。"
                                    "log" -> "网页尚未接入 App 日志浏览与反馈上传。网络错误会在对应页面显示。"
                                    "update" -> "网页刷新时加载服务器部署的资源，不使用 App 安装包更新器。当前为开发预览，尚未配置离线 PWA 更新。"
                                    else -> "该分类尚未接入。"
                                }) }) { Text("尚未实现") }
                            }
                        }
                    }
                }
            }
        }
    }
    }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清理资料缓存？") },
        text = { Text("离线缓存的公开作品资料会被删除，下次查看时重新获取。") },
        confirmButton = { TextButton({ confirmClear = false; scope.launch { request("clearCache"); result = "资料缓存已清理" } }) { Text("清理") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("取消") } })
    result?.let { AlertDialog(onDismissRequest = { result = null }, title = { Text("设置") }, text = { Text(it) }, confirmButton = { TextButton({ result = null }) { Text("关闭") } }) }
}
