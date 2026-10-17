import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.settings.SettingsTransfer
import kotlin.test.*

class PlayerSettingsTest {
    @Test fun absentSettingsUseNativeDefaults() {
        assertEquals(VideoScaffoldConfig.Default,decodeWebPlayerConfig(JsonObject(emptyMap())))
    }
    @Test fun tabletBackupControlsPlaybackWithoutASeparateWebSchema() {
        val backup=SettingsTransfer.parse("""{"videoScaffoldConfig":{"playbackSpeed":1.75,"minPlaybackSpeed":0.5,"maxPlaybackSpeed":3.5,"rememberPlaybackSpeed":false,"autoPlayNext":false}}""")
        val value=decodeWebPlayerConfig(backup)
        assertEquals(1.75f,value.playbackSpeed);assertEquals(3.5f,value.maxPlaybackSpeed)
        assertFalse(value.rememberPlaybackSpeed);assertFalse(value.autoPlayNext)
    }
    @Test fun editingPlayerSettingsKeepsUnknownFieldsAndRegexBackupRoundTrips() {
        val original=SettingsTransfer.parse("""{"videoScaffoldConfig":{"futureOption":9007199254740999},"danmakuRegexFilters":[{"id":"tablet","name":"spoiler","regex":"剧透","enabled":false}]}""")
        val edited=mergeWatchingSettings(original,"videoScaffoldConfig",webJson.encodeToJsonElement(VideoScaffoldConfig.Default.copy(autoPlayNext=false)).jsonObject)
        val exported=SettingsTransfer.parse(SettingsTransfer.export(edited,ThemeSettings.Default,UISettings.Default,JsonObject(emptyMap())))
        assertEquals("9007199254740999",exported["videoScaffoldConfig"]!!.jsonObject["futureOption"]!!.jsonPrimitive.content)
        assertFalse(decodeWebPlayerConfig(exported).autoPlayNext)
        assertEquals(listOf(DanmakuRegexFilter("tablet","spoiler","剧透",false)),webJson.decodeFromJsonElement<List<DanmakuRegexFilter>>(exported["danmakuRegexFilters"]!!))
    }
    @Test fun importedSpeedOutsideRangeIsClampedUsingNativeRule() {
        val config=decodeWebPlayerConfig(Json.parseToJsonElement("""{"videoScaffoldConfig":{"minPlaybackSpeed":1,"maxPlaybackSpeed":2,"playbackSpeed":4,"fastForwardSpeed":3}}""").jsonObject)
        assertEquals(2f,config.playbackSpeed);assertEquals(3f,config.fastForwardSpeed)
    }
}
