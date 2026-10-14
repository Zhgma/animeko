import kotlin.test.*
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.selector.standardBrowseEpisodeIndex
import me.him188.ani.app.domain.media.selector.browseEpisodeIndex
import me.him188.ani.app.ui.mediaselect.*
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.*

class WebPlaybackSelectorTest {
    @Test fun importedAppPreferenceControlsInitialMode() {
        for ((kind,mode) in listOf("WEB" to MediaSelectorMode.AUTO,"BitTorrent" to MediaSelectorMode.BT)) {
            val settings=webJson.decodeFromString<MediaSelectorSettings>("""{"preferKind":"$kind","future":true}""")
            assertEquals(mode,initialMediaSelectorMode(settings.preferKind,true))
            assertEquals(MediaSelectorMode.AUTO,initialMediaSelectorMode(settings.preferKind,false))
        }
        assertEquals(MediaSelectorMode.AUTO,initialMediaSelectorMode(MediaSelectorSettings.Default.preferKind,true))
        assertEquals(MediaSelectorMode.AUTO,initialMediaSelectorMode(null,true))
    }
    @Test fun editingSourcePreferencePreservesAppSettings() {
        val original=Json.parseToJsonElement("""{"mediaSelectorSettings":{"preferKind":"BitTorrent","fastSelectWebKind":false,"future":9223372036854775807},"other":{"x":true}}""").jsonObject
        val decoded=webJson.decodeFromJsonElement<MediaSelectorSettings>(original.getValue("mediaSelectorSettings"))
        val result=mergeWatchingSettings(original,"mediaSelectorSettings",webJson.encodeToJsonElement(decoded.copy(preferKind=MediaSourceKind.WEB)).jsonObject)
        assertEquals(original["other"],result["other"])
        assertEquals("9223372036854775807",result.getValue("mediaSelectorSettings").jsonObject.getValue("future").jsonPrimitive.content)
        assertFalse(webJson.decodeFromJsonElement<MediaSelectorSettings>(result.getValue("mediaSelectorSettings")).fastSelectWebKind)
    }
    @Test fun currentEpisodeRetainsIdTitleAndBothNumbers() {
        val episode=Json.parseToJsonElement("""{"id":123,"sort":13.5,"ep":1.5,"name_cn":"特别篇","name":"Special"}""").jsonObject.playbackEpisode()
        assertEquals(123,episode.id);assertEquals(EpisodeSort("13.5"),episode.sort);assertEquals(EpisodeSort("1.5"),episode.ep);assertEquals("特别篇",episode.name)
    }
    @Test fun nativeEpisodeLookupPrefersContinuousNumberAndNeverPosition() {
        val episodes=listOf(BrowseEpisode("第一集","one",EpisodeSort(1)),BrowseEpisode("第十三集","thirteen",EpisodeSort(13)))
        assertEquals(1,standardBrowseEpisodeIndex(episodes,EpisodeSort(13),EpisodeSort(1)))
        assertEquals(0,standardBrowseEpisodeIndex(episodes,EpisodeSort(14),EpisodeSort(1)))
        assertNull(standardBrowseEpisodeIndex(episodes,EpisodeSort(2),null))
        assertEquals(4,browseEpisodeIndex(3,EpisodeSort(13),EpisodeSort(14)))
        assertNull(browseEpisodeIndex(3,EpisodeSort(13),EpisodeSort("13.5")))
    }
    @Test fun disabledAndUnsupportedSourcesCannotBeAutomaticallyUsed() {
        val web=FeedSource("web","Web",kind="web",supported=true)
        val bt=FeedSource("bt","BT",supported=true)
        val state=BrowserMediaState(subscriptions=listOf(BrowserSubscription("a","A",sources=listOf(web,bt,web.copy(id="off",enabled=false),web.copy(id="unsupported",supported=false))),BrowserSubscription("b","B",enabled=false,sources=listOf(web))))
        assertEquals(listOf("a/web"),browserOnlineSources(state).map{it.id})
        assertTrue(browserHasBt(state))
        assertFalse(browserHasBt(state.copy(subscriptions=state.subscriptions.map{it.copy(enabled=false)})))
    }
    @Test fun automaticLookupDoesNotConfuseOtherSeasons() {
        assertTrue(exactOnlineSubject(" 葬送的芙莉莲 ",listOf("葬送的芙莉莲")))
        assertFalse(exactOnlineSubject("葬送的芙莉莲 第二季",listOf("葬送的芙莉莲")))
        assertFalse(exactOnlineSubject("葬送的芙莉莲",listOf("葬送的芙莉莲 第二季")))
    }
}
