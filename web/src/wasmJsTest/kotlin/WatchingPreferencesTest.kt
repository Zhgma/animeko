import kotlin.test.*
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.ui.settings.SettingsTransfer
import me.him188.ani.app.domain.episode.areEpisodeDatesCompleted
import me.him188.ani.datasources.api.PackedDate

class WatchingPreferencesTest {
    private val plain = FeedItem("plain","test",episodeStart=1.0,episodeEnd=1.0)
    @Test fun hidesMissingSubtitleOnlyWhenConfigured() {
        assertEquals("无字幕",watchingExclusion(plain,MediaPreference.PlatformDefault,MediaSelectorSettings.Default,false))
        assertNull(watchingExclusion(plain,MediaPreference.PlatformDefault.copy(showWithoutSubtitle=true),MediaSelectorSettings.Default,false))
        assertNull(watchingExclusion(plain.copy(subtitles=listOf("简中")),MediaPreference.PlatformDefault,MediaSelectorSettings.Default,false))
    }
    @Test fun hidesSinglesOnlyForKnownCompletedSubjects() {
        val preference = MediaPreference.PlatformDefault.copy(showWithoutSubtitle=true)
        assertNull(watchingExclusion(plain,preference,MediaSelectorSettings.Default,null))
        assertEquals("完结番单集资源",watchingExclusion(plain,preference,MediaSelectorSettings.Default,true))
        assertNull(watchingExclusion(plain.copy(episodeEnd=12.0),preference,MediaSelectorSettings.Default,true))
        assertNull(watchingExclusion(plain,preference,MediaSelectorSettings.Default.copy(hideSingleEpisodeForCompleted=false),true))
    }
    @Test fun orderUsesSubtitleResolutionAllianceAndSeasonPreferences() {
        val entries = listOf(plain.copy(id="720",resolution="720P",subtitles=listOf("简中")),
            plain.copy(id="1080",resolution="1080P",subtitles=listOf("简中")),
            plain.copy(id="english",resolution="2160P",subtitles=listOf("英语")))
        assertEquals(listOf("1080","720","english"),entries.sortedWith(watchingComparator(MediaPreference.PlatformDefault,MediaSelectorSettings.Default)).map { it.id })
        val changed = MediaPreference.PlatformDefault.copy(fallbackResolutions=listOf("720P","1080P"))
        assertEquals("720",entries.sortedWith(watchingComparator(changed,MediaSelectorSettings.Default)).first().id)
        val season = plain.copy(id="season",episodeEnd=12.0)
        assertEquals("season",listOf(plain,season).sortedWith(watchingComparator(changed,MediaSelectorSettings.Default,true)).first().id)
        assertEquals("plain",listOf(plain,season).sortedWith(watchingComparator(changed,MediaSelectorSettings.Default,false)).first().id)
        assertEquals("plain",listOf(plain,season).sortedWith(watchingComparator(changed,MediaSelectorSettings.Default.copy(preferSeasons=false))).first().id)
    }
    @Test fun completionUsesAppOneYearBoundary() {
        val dates = sequenceOf(PackedDate.parseFromDate("2025-10-12"))
        assertFalse(areEpisodeDatesCompleted(dates,PackedDate.parseFromDate("2026-10-11")))
        assertTrue(areEpisodeDatesCompleted(dates,PackedDate.parseFromDate("2026-10-12")))
        assertFalse(areEpisodeDatesCompleted(emptySequence()))
    }
    @Test fun preferenceEditingPreservesUnknownAppFieldsAndPrecision() {
        val retained = Json.parseToJsonElement("""{"defaultMediaPreference":{"futureField":18446744073709551615},"mediaSelectorSettings":{"fastSelectWebKind":false}}""").jsonObject
        val edited = mergeWatchingSettings(retained,"defaultMediaPreference",webJson.encodeToJsonElement(MediaPreference.PlatformDefault.copy(showWithoutSubtitle=true)).jsonObject)
        assertEquals("18446744073709551615",edited.getValue("defaultMediaPreference").jsonObject.getValue("futureField").jsonPrimitive.content)
        assertEquals(retained["mediaSelectorSettings"],edited["mediaSelectorSettings"])
        val roundtrip = SettingsTransfer.parse(edited.toString())
        assertTrue(webJson.decodeFromJsonElement<MediaPreference>(roundtrip.getValue("defaultMediaPreference")).showWithoutSubtitle)
    }
}
