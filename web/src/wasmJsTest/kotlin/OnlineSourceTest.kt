import kotlin.test.*
import kotlinx.serialization.json.*

class OnlineSourceTest {
    @Test fun appSelectorsParseSearchNamesAndAbsoluteLinks() {
        val config=Json.parseToJsonElement("""{"subjectFormatId":"a","selectorSubjectFormatA":{"selectLists":".results a"}}""").jsonObject
        val results=parseOnlineSubjects(config,"<div class='results'><a title='作品一' href='/detail/11.html'>其他文字</a></div>","https://example.com/search?wd=test")
        assertEquals("作品一",results.single().name)
        assertEquals("https://example.com/detail/11.html",results.single().fullUrl)
    }
    @Test fun appSelectorsKeepRepeatedChannelGroupsAndSpecialEpisodes() {
        val config=Json.parseToJsonElement("""{"channelFormatId":"index-grouped","selectorChannelFormatFlattened":{"selectChannelNames":".names a","matchChannelName":"","selectEpisodeLists":".episodes","selectEpisodesFromList":"a","matchEpisodeSortFromName":"第(?<ep>.+)集"}}""").jsonObject
        val channels=parseOnlineChannels(config,"<div class='names'><a>线路一</a><a>线路一</a></div><div class='episodes'><a href='/play/1'>第1集</a></div><div class='episodes'><a href='/play/sp'>OVA</a></div>","https://example.com/detail/1")
        assertEquals(2,channels.size)
        assertEquals("https://example.com/play/1",channels.first().episodes.single().url)
        assertEquals(1f,channels.first().episodes.single().episodeSort?.number)
        assertEquals("OVA",channels.last().episodes.single().name)
    }
    @Test fun malformedRulesReportFailureInsteadOfEmptyResults() {
        val config=Json.parseToJsonElement("""{"subjectFormatId":"indexed","selectorSubjectFormatIndexed":{"selectNames":"","selectLinks":"a"}}""").jsonObject
        assertFailsWith<IllegalStateException>{parseOnlineSubjects(config,"<a href='/1'>A</a>","https://example.com")}
    }
}
