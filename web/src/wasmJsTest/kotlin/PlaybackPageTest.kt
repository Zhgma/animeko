import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.client.apis.EpisodesAniApi
import me.him188.ani.app.ui.subject.episode.*
import kotlin.test.*

class PlaybackPageTest {
    @Test fun desktopSidebarKeepsSeparateFullscreenPreference() {
        var fullscreen=false
        val state=EpisodeSidebarState {fullscreen}
        assertTrue(state.isVisible)
        fullscreen=true
        assertFalse(state.isVisible)
        state.isVisible=true
        fullscreen=false
        assertTrue(state.isVisible)
        state.isVisible=false
        fullscreen=true
        assertTrue(state.isVisible)
    }
    @Test fun compactFullscreenDoesNotKeepDetailsBesideVideo() {
        assertEquals(EpisodeScreenLayoutMode.COMPACT,episodeScreenLayoutMode(false,false,true,false))
        assertEquals(EpisodeScreenLayoutMode.VIDEO_ONLY,episodeScreenLayoutMode(true,false,true,false))
        assertEquals(EpisodeScreenLayoutMode.WIDE,episodeScreenLayoutMode(true,true,true,true))
    }
    @Test fun commentsUseEpisodeIdentityAndAppGeneratedApi() = runTest {
        val client=HttpClient(MockEngine {request ->
            assertEquals("/api/animeko/v2/episodes/12345/comments",request.url.encodedPath)
            assertEquals("20",request.url.parameters["offset"])
            assertEquals("true",request.url.parameters["includeBangumi"])
            respond("""{"total":0,"items":[],"bangumiUnavailable":false}""",HttpStatusCode.OK,headersOf(HttpHeaders.ContentType,"application/json"))
        }) {install(ContentNegotiation) {json(Json {ignoreUnknownKeys=true})}}
        try {assertEquals(0,webEpisodeComments(12345,20,EpisodesAniApi("https://api.example/api/animeko",client)).items.size)}
        finally {client.close()}
    }
}
