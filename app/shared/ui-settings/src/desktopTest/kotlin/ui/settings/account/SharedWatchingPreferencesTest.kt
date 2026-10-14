package me.him188.ani.app.ui.settings.account
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.app.ui.settings.tabs.media.SourceKindPreferenceItem
import me.him188.ani.app.ui.settings.tabs.media.MediaPreferenceItems
import me.him188.ani.app.ui.settings.tabs.media.BtPreferenceItems
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlin.test.*

class SharedWatchingPreferencesTest {
    @Test fun sourceKindDropdownUsesTheSameAppPreference() = runAniComposeUiTest {
        var value: MediaSourceKind? = MediaSourceKind.BitTorrent
        var title = ""; var web = ""
        setContent { ProvideCompositionLocalsForPreview {
            var selected by remember { mutableStateOf(value) }
            title = stringResource(Lang.settings_media_prefer_source_type)
            web = stringResource(Lang.settings_media_source_web)
            SettingsTab { SourceKindPreferenceItem(selected) { value=it;selected=it } }
        } }
        onNodeWithText(title).performClick()
        onNodeWithText(web).performClick()
        runOnIdle { assertEquals(MediaSourceKind.WEB,value) }
        onNodeWithText(web).assertIsDisplayed()
    }

    @Test fun subtitleSelectionSavesAndUpdatesTheSharedRow() = runAniComposeUiTest {
        var value = MediaPreference.PlatformDefault
        var title = ""; var save = ""; var language = ""
        setContent { ProvideCompositionLocalsForPreview {
            var preference by remember { mutableStateOf(value) }
            title = stringResource(Lang.settings_media_subtitle_language)
            save = stringResource(Lang.settings_media_source_save_button)
            language = stringResource(Lang.media_subtitle_language_chinese_traditional)
            SettingsTab(Modifier.verticalScroll(rememberScrollState())) {
                MediaPreferenceItems(preference) { value = it; preference = it }
            }
        } }
        onNodeWithText(title).performClick()
        onAllNodes(isToggleable()).onFirst().performClick()
        onNodeWithText(save).performClick()
        runOnIdle { assertFalse("CHS" in value.fallbackSubtitleLanguageIds.orEmpty()) }
        onNodeWithText(language).assertIsDisplayed()
    }
    @Test fun supportedPreferenceChangesWhileUnavailableSourceSwitchDoesNot() = runAniComposeUiTest {
        var value = MediaPreference.PlatformDefault
        var selectorValue = MediaSelectorSettings.Default
        var title = ""; var disabledTitle = ""
        setContent { ProvideCompositionLocalsForPreview {
            var preference by remember { mutableStateOf(value) }
            var selector by remember { mutableStateOf(selectorValue) }
            title = stringResource(Lang.settings_media_hide_no_subtitle)
            disabledTitle = stringResource(Lang.settings_media_auto_enable_last)
            SettingsTab(Modifier.verticalScroll(rememberScrollState())) {
                BtPreferenceItems(preference,selector,{value=it;preference=it},{selectorValue=it;selector=it}, supportsRememberedSource=false)
            }
        } }
        onNodeWithText(title).performScrollTo().performClick()
        runOnIdle { assertTrue(value.showWithoutSubtitle) }
        onNodeWithText(disabledTitle).performScrollTo().performClick()
        runOnIdle { assertEquals(MediaSelectorSettings.Default.autoEnableLastSelected,selectorValue.autoEnableLastSelected) }
    }
}
