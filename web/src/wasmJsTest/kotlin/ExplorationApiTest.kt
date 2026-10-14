import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import me.him188.ani.client.apis.HomeAniApi
import kotlin.test.*

class ExplorationApiTest {
    @Test fun homeRecommendationsUseAppProtocolAndIgnoreNonSubjectLinks() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/animeko/v2/home/recommendations", request.url.encodedPath)
            assertEquals("30", request.url.parameters["offset"])
            assertEquals("30", request.url.parameters["limit"])
            assertEquals("Bearer test-session", request.headers[HttpHeaders.Authorization])
            respond("""{"total":61,"items":[
                {"subjectId":355555,"subjectName":"original","subjectNameCn":"中文名","imageUrl":"cover","desc1":"","desc2":""},
                {"subjectName":"external","subjectNameCn":"","imageUrl":"","desc1":"","desc2":"","uri":"https://example.com"},
                {"subjectId":2,"subjectName":"original fallback","subjectNameCn":"","imageUrl":"","desc1":"","desc2":""}
            ]}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { install(ContentNegotiation) { json() } }
        try {
            val api = HomeAniApi("https://api.example/api/animeko", client).apply { setBearerToken("test-session") }
            val page = animekoRecommendations(30, api)
            assertEquals(listOf(355555, 2), page.items.map { it.id })
            assertEquals(listOf("中文名", "original fallback"), page.items.map { it.title })
            assertEquals(61, page.total)
        } finally { client.close() }
    }
}
