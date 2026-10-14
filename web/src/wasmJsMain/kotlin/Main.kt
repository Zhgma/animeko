@file:OptIn(ExperimentalComposeUiApi::class)

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import animecomic.web.resources.Res
import animecomic.web.resources.NotoSansSC
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.CancellationException
import me.him188.ani.app.data.repository.manga.BangumiMangaCollectionLoader
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuite
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteDefaults
import me.him188.ani.app.ui.adaptive.navigation.AniNavigationSuiteLayout
import me.him188.ani.app.ui.foundation.input.ActiveInputSourceState
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastMedium
import me.him188.ani.app.ui.manga.MangaCollectionPageContent
import me.him188.ani.app.ui.manga.MangaCollectionPageState
import me.him188.ani.app.ui.manga.MangaCollectionUiState
import me.him188.ani.app.ui.manga.TrackingCollectionTabs
import me.him188.ani.app.ui.theme.DefaultSeedColor
import org.jetbrains.compose.resources.Font
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import me.him188.ani.app.ui.foundation.theme.isSystemInDarkThemeDetected
import me.him188.ani.app.ui.foundation.theme.modifyColorSchemeForBlackBackground
import me.him188.ani.app.ui.settings.account.AccountLogoutDialog
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.settings.account.ProfilePopupContent
import me.him188.ani.app.ui.settings.account.ProfilePopupContainer
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.layout.ContentScale
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.ui.foundation.PlatformFontFamily
import me.him188.ani.app.ui.foundation.copyWithPlatformFontFamily
import me.him188.ani.app.ui.foundation.LocalEpisodeProgressSettings
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.session.SelfAvatarContent
import me.him188.ani.app.ui.foundation.theme.LocalBrowserThemeSettings
import me.him188.ani.app.ui.manga.MangaCollectionController
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.encodeToString

import me.him188.ani.app.data.models.preference.UISettings

fun main() { installSharedMangaApi(); ComposeViewport { AnimeComicWeb() } }

@Composable
fun AnimeComicWeb() {
    var uiSettings by remember { mutableStateOf(runCatching { webJson.decodeFromString<UISettings>(aniRead("uiSettings")) }.getOrElse { UISettings() }) }
    var originalTitle by remember { mutableStateOf(aniRead("originalTitle") == "true") }
    var settings by remember { mutableStateOf(runCatching { webJson.decodeFromString<ThemeSettings>(aniRead("themeSettings")) }.getOrElse { ThemeSettings() }) }
    val dark = when (settings.darkMode) { DarkMode.AUTO -> isSystemInDarkThemeDetected(); DarkMode.DARK -> true; DarkMode.LIGHT -> false }
    val scheme = remember(settings, dark) { dynamicColorScheme(primary = settings.seedColor, isDark = dark, isAmoled = settings.useBlackBackground, style = PaletteStyle.TonalSpot, modifyColorScheme = { modifyColorSchemeForBlackBackground(it, dark, settings.useBlackBackground) }) }
    val font = FontFamily(Font(Res.font.NotoSansSC))
    val typography = Typography().copyWithPlatformFontFamily(PlatformFontFamily(font))
    SideEffect { aniTheme(dark); aniReady() }
    CompositionLocalProvider(LocalBrowserThemeSettings provides settings, LocalWebOriginalTitle provides originalTitle, LocalWebUISettings provides uiSettings, LocalEpisodeProgressSettings provides uiSettings.episodeProgress, LocalSubjectAppearanceSettings provides uiSettings.subjectAppearance.copy(useOriginalTitle = originalTitle)) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        val pageState = rememberSaveableStateHolder()
        var route by remember { mutableStateOf(aniCurrentRoute()) }
        var accountReturnRoute by remember { mutableStateOf("collection") }
        var returnRoute by remember { mutableStateOf("collection") }
        val openSubject: (Int) -> Unit = { id -> if (!route.startsWith("subject/")) returnRoute = route; route = "subject/$id" }
        var profile by remember { mutableStateOf(false) }
        var confirmLogout by remember { mutableStateOf(false) }
        DisposableEffect(Unit) { aniWatchRoute { route = it }; onDispose { aniUnwatchRoute() } }
        LaunchedEffect(route) { aniNavigate(route) }
        var session by remember { mutableStateOf(WebResult()) }
        var revision by remember { mutableIntStateOf(0) }
        var message by remember { mutableStateOf<String?>(null) }
        val input = remember { ActiveInputSourceState() }
        val controller = remember {
            val loader = BangumiMangaCollectionLoader { path, _, type, offset ->
                request("mangaRaw", buildJsonObject { put("path", path); if (type != null) put("status", type); put("offset", offset) }.toString()).raw
            }
            MangaCollectionController(
                load = { _, type, onPage -> loader.loadCollections("", type, onPage) },
                isAuthorizationError = { it is WebRequestException && it.status == 401 },
            )
        }
        val state by controller.state.collectAsState()
        LaunchedEffect(Unit) {
            try { session = request("session") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message }
        }
        LaunchedEffect(session, revision) { controller.refresh(if (session.hasBangumi) session.userId.ifBlank { session.username.ifBlank { "current" } } else null) }
        if (route == "account") {
            AccountScreen(session, { route = accountReturnRoute }, { route = "settings" }, { session = it; revision++ })
            return@MaterialTheme
        }
        if (route == "schedule" || route == "trending") {
            if (route == "schedule") WebExploreSchedule({ route = "explore" }, openSubject)
            else WebTrendingRanking({ route = "explore" }, openSubject)
            return@MaterialTheme
        }
        if (route == "history") {
            WebHistory(session, { route = "collection" }, { accountReturnRoute = "history"; route = "account" }, openSubject)
            return@MaterialTheme
        }
        if (route.startsWith("subject/")) {
            key(route) { WebDetails(route.substringAfter('/').toInt(), session, { route = returnRoute }, openSubject,
                { accountReturnRoute = route; route = "account" }, { revision++ }, { route = "downloads" }, { route = "settings/sources" }) }
            return@MaterialTheme
        }
        if (route == "settings" || route.startsWith("settings/")) {
            WebSettings(settings, { settings = it; aniWrite("themeSettings", webJson.encodeToString(it)) }, session, { route = "account" }, { route = "collection" }, route, { route = it }, originalTitle, { originalTitle = it; aniWrite("originalTitle", it.toString()) }, uiSettings, { uiSettings = it; aniWrite("uiSettings", webJson.encodeToString(it)); originalTitle = it.subjectAppearance.useOriginalTitle; aniWrite("originalTitle", originalTitle.toString()) }, onSession = { session = it })
            return@MaterialTheme
        }
        CompositionLocalProvider(LocalActiveInputSource provides input) {
            val adaptive = currentWindowAdaptiveInfo1()
            val layout = AniNavigationSuiteDefaults.calculateLayoutType(adaptive)
            AniNavigationSuiteLayout(
                modifier = Modifier.fillMaxSize().trackActiveInputSource(input),
                layoutType = layout,
                navigationSuite = {
                    AniNavigationSuite(
                        layoutType = layout,
                        colors = NavigationSuiteDefaults.colors(
                            navigationRailContainerColor = scheme.surfaceContainer,
                            navigationBarContainerColor = scheme.surfaceContainer,
                            navigationDrawerContainerColor = scheme.surfaceContainer,
                        ),
                        navigationRailHeader = {
                            FloatingActionButton(onClick = { route = "search" },
                                modifier = Modifier.padding(vertical = if (adaptive.windowSizeClass.isHeightAtLeastMedium) 48.dp else 0.dp),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)) { Icon(Icons.Rounded.Search, "搜索") }
                        },
                        navigationRailFooter = {
                            NavigationRailItem(selected = false, onClick = { route = "settings" }, icon = { Icon(Icons.Rounded.Settings, "设置") }, label = { Text(stringResource(Lang.settings)) }, colors = itemColors)
                        },
                        navigationRailItemSpacing = 8.dp,
                    ) {
                        item(route == "explore", onClick = { route = "explore" }, icon = { Icon(Icons.Rounded.TravelExplore, null) }, label = { Text(stringResource(Lang.main_screen_page_exploration)) })
                        item(route == "collection" || route == "anime", onClick = { route = "collection" }, icon = { Icon(Icons.Rounded.Star, null) }, label = { Text(stringResource(Lang.main_screen_page_collection)) })
                        item(route == "downloads", onClick = { route = "downloads" }, icon = { Icon(Icons.Rounded.DownloadDone, null) }, label = { Text(stringResource(Lang.main_screen_page_cache_management)) })
                    }
                },
            ) {
                val shape = if (layout == NavigationSuiteType.NavigationBar) RectangleShape else MaterialTheme.shapes.extraLarge.copy(topEnd = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
                Surface(shape = shape, color = scheme.surfaceContainerLowest) {
                    when (route) {
                    "search" -> pageState.SaveableStateProvider("search") { WebSearch(openSubject) }
                    "explore" -> WebBrowse(session, openSubject, { profile = true }, { route = "search" }, { route = "settings" }, { route = "downloads" }, { route = "settings/sources" }, { route = "schedule" }, { route = "trending" })
                    "downloads" -> WebDownloads(session, { profile = true })
                    "anime" -> pageState.SaveableStateProvider("anime") { WebAnimeCollection(session, revision, { revision++ },
                        avatar = { size -> SelfAvatarContent(false, session.signedIn, size, { profile = true }) { modifier -> AvatarImage(session.avatar.takeIf { it.isNotEmpty() }, modifier) } },
                        onManga = { route = "collection" }, onLogin = { route = "account" }, onOpen = openSubject,
                        onDownloads = { route = "downloads" }, onSources = { route = "settings/sources" }) }
                    else -> MangaCollectionPageContent(
                        state = state,
                        avatar = { size -> SelfAvatarContent(false, session.signedIn, size, { profile = true }) { modifier ->
                            AvatarImage(session.avatar.takeIf { it.isNotEmpty() }, modifier)
                        } },
                        title = { TrackingCollectionTabs(true) { manga -> if (!manga) route = "anime" } },
                        onSelect = controller::select, onRefresh = { revision++ },
                        onClickLogin = { profile = true },
                        onOpen = { openSubject(it.subjectId) },
                    )
                    }
                }
            }
        }

        if (profile) ProfilePopupContainer({ profile = false }) {
            ProfilePopupContent(session.signedIn, session.nickname,
                avatar = { AvatarImage(session.avatar.takeIf { session.signedIn && it.isNotEmpty() }, Modifier.fillMaxSize().clip(CircleShape)) },
                onClickLogin = { profile = false; route = "account" },
                onClickEditProfile = { profile = false; route = "account" },
                onClickPlaybackHistory = { profile = false; route = "history" },
                onClickSettings = { profile = false; route = "settings" },
                onClickLogout = { confirmLogout = true },
                modifier = Modifier.padding(
                    vertical = if (currentWindowAdaptiveInfo1().windowSizeClass.isHeightAtLeastMedium) 24.dp else 16.dp,
                    horizontal = if (currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium) 16.dp else 8.dp))
        }
        if (confirmLogout) AccountLogoutDialog(
            onConfirm = { aniLogout(); session = WebResult(); confirmLogout = false; profile = false },
            onCancel = { confirmLogout = false })
        message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("AnimeComic") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("关闭") } }) }
    }
    }
}

