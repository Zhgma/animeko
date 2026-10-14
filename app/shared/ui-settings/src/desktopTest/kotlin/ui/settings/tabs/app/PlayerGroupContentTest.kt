package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.danmaku.AddRegexFilterDialog
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import androidx.compose.material3.Text
import kotlin.test.*

class PlayerGroupContentTest {
    @Test fun sharedAutoPlaySwitchUpdatesTheNativeConfig() = runAniComposeUiTest {
        val config = mutableStateOf(VideoScaffoldConfig.Default)
        setContent { ProvideCompositionLocalsForPreview { Column(Modifier.verticalScroll(rememberScrollState())) { SettingsTab {
            PlayerGroupContent(config.value,{config.value=it},DanmakuFilterConfig.Default,{}, {},supports={it=="autoPlayNext"})
        } } } }
        onNodeWithText("自动连播").performScrollTo().performClick()
        runOnIdle { assertFalse(config.value.autoPlayNext) }
    }

    @Test fun sharedRegexDialogRejectsInvalidExpression() = runAniComposeUiTest {
        var saved: DanmakuRegexFilter? = null
        setContent { ProvideCompositionLocalsForPreview {
            AddRegexFilterDialog({}, {saved=it}, {Text("添加规则")})
        } }
        onNode(hasSetTextAction()).performTextInput("[")
        onNodeWithText("确认").performClick()
        runOnIdle { assertNull(saved) }
        onNode(hasSetTextAction()).performTextReplacement("剧透.*")
        onNodeWithText("确认").performClick()
        runOnIdle { assertEquals("剧透.*",saved?.regex);assertTrue(saved!!.enabled) }
    }
}
