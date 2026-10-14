import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import me.him188.ani.client.apis.SubjectsAniApi
import me.him188.ani.app.ui.settings.account.isValidProfileNickname
import kotlin.test.*
import kotlin.time.Instant
import me.him188.ani.app.ui.subject.SubjectStatusStrings

class AnimekoCollectionTest {
    private val strings = SubjectStatusStrings("继续观看 %s","已看完","未开播","%s 开播","开始观看","%s 更新","看到 %s","未知","未开播","连载中","连载至第 %s 话","已完结","全 %s 话","预定全 %s 话")
    private val stats = """{"favorite":{"wish":1,"done":0,"doing":0,"onHold":0,"dropped":0},"scoreDetails":{}}"""
    private fun subject(type: String = "\"DOING\"") = """{
      "id":430699,"type":"ANIME","name":"Medalist","nameCn":"金牌得主","summary":"","nsfw":false,
      "airDate":"2025-01-04","airingInfo":{"recurrence":{"startTime":"2025-01-04T23:00:00+09:00","intervalMillis":604800000}},"aliases":[],"favorite":{"wish":1,"done":0,"doing":0,"onHold":0,"dropped":0},
      "tags":[],"metaTags":[],"scoreDetails":{},"selfRating":{"score":7,"tags":["保留标签"],"isPrivate":true,"comment":"原评论"},
      "episodes":[
        {"episodeId":11,"subjectId":430699,"sort":"1","type":"MAIN","name":"","nameCn":"","description":"","collectionType":"DONE"},
        {"episodeId":12,"subjectId":430699,"sort":"2","type":"MAIN","name":"","nameCn":"","description":""},
        {"episodeId":13,"subjectId":430699,"sort":"1","type":"SPECIAL","name":"","nameCn":"","description":"","collectionType":"DONE"}],
      "relations":{"subjectId":430699,"seriesMainSubjectIds":[],"seriesMainSubjectNames":[],"sequelSubjects":[],"sequelSubjectNames":[]},
      "imageLarge":"","imageThumb":"","collectionType":$type}
    """
    private class Fixture(val response: (String, String) -> String) {
        val calls = mutableListOf<Triple<String, String, String>>()
        val queries = mutableListOf<Parameters>()
        val client = HttpClient(MockEngine { request ->
            val body = (request.body as? TextContent)?.text.orEmpty()
            calls += Triple(request.method.value, request.url.encodedPath, body)
            queries += request.url.parameters
            assertEquals("Bearer animeko-test", request.headers[HttpHeaders.Authorization])
            respond(response(request.url.encodedPath, request.method.value), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType,"application/json"))
        }) { expectSuccess = true; install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val api = SubjectsAniApi("https://api.example/api/animeko",client).apply { setBearerToken("animeko-test") }
    }
    private suspend fun write(f: Fixture, fields: String) = animekoCollectionRequest("libraryWrite",Json.parseToJsonElement("""{"id":430699,"type":2,"fields":$fields}""").jsonObject,f.api)

    @Test fun collectionCardsKeepEpisodesAndUseLastWatchedIdentity() = runTest {
        val f=Fixture { _,_ -> """{"items":[${subject()}],"total":1}""" }
        try {
            val result=animekoCollectionRequest("collection",buildJsonObject { put("status",3) },f.api)
            val item=result.items.single()
            assertEquals(3,item.animeCollection!!.episodes.size)
            val progress=collectionProgress(item,Instant.parse("2026-10-13T00:00:00Z"),strings)
            assertEquals(12,progress.episode!!.episodeId.toInt())
            assertEquals("继续观看 02",progress.button)
            assertTrue(progress.primary)
            val future=collectionProgress(item.copy(animeCollection=item.animeCollection!!.copy(airDate="2099-01-01", episodes=item.animeCollection!!.episodes.map { it.copy(collectionType=null) })),Instant.parse("2026-10-13T00:00:00Z"),strings)
            assertTrue(future.button.endsWith("开播"))
            assertFalse(future.primary)
        } finally { f.client.close() }
    }

    @Test fun allTabCountsUseTheAppStatisticsEndpoint() = runTest {
        val f=Fixture { _,_ -> """{"wish":19,"done":81,"doing":10,"onHold":2,"dropped":0}""" }
        try {
            assertEquals(mapOf(1 to 19,2 to 81,3 to 10,4 to 2,5 to 0),webCollectionCounts(f.api))
            assertEquals(1,f.calls.size)
            assertEquals("GET",f.calls.single().first)
        } finally { f.client.close() }
    }

    @Test fun episodeUpdateUsesExactIdAndDoesNotOverwriteOtherEpisodes() = runTest {
        val f=Fixture { _,_ -> "{}" }
        try {
            updateWebEpisode(430699,13,true,f.api)
            assertEquals(1,f.calls.size)
            val payload=Json.parseToJsonElement(f.calls.single().third).jsonObject
            assertEquals("[13]",payload["episodeIds"].toString())
            assertTrue(f.calls.single().second.contains("430699"))
        } finally { f.client.close() }
    }

    @Test fun episodeListUsesAppApiAndPreservesSpecialEpisodeIdentity() = runTest {
        val f=Fixture { _,_ -> subject() }
        try {
            val episodes=animekoEpisodes(430699,f.api)
            assertEquals("/api/animeko/v2/subjects/430699",f.calls.single().second)
            assertEquals(3,episodes.size)
            assertTrue(episodes[0].webEpisode().getValue("watched").jsonPrimitive.boolean)
            assertFalse(episodes[1].webEpisode().getValue("watched").jsonPrimitive.boolean)
            assertNotEquals(episodes[0].webEpisode().playbackEpisode().sort,episodes[2].webEpisode().playbackEpisode().sort)
            assertEquals(13,episodes[2].webEpisode().playbackEpisode().id)
        } finally { f.client.close() }
    }

    @Test fun detailEpisodeCatalogRetainsBroadcastSchedule() = runTest {
        val f = Fixture { _, _ -> subject() }
        try {
            val catalog = animekoEpisodeSubject(430699, f.api)
            assertEquals("2025-01-04T23:00:00+09:00", catalog.airingInfo?.recurrence?.startTime)
            assertEquals(604800000L, catalog.airingInfo?.recurrence?.intervalMillis)
            assertEquals(3, catalog.episodes.size)
        } finally { f.client.close() }
    }

    @Test fun allStatusesUseGeneratedEnumsAndOnlyChangeStatus() = runTest {
        val f = Fixture { _, _ -> stats }
        try {
            for ((i,name) in listOf("WISH","DONE","DOING","ON_HOLD","DROPPED").withIndex()) {
                write(f,"""{"type":${i+1}}""")
                assertEquals(Triple("PATCH","/api/animeko/v2/subjects/430699","""{"collectionType":"$name"}"""),f.calls.last())
            }
            assertEquals(5,f.calls.size)
        } finally { f.client.close() }
    }
    @Test fun ratingPatchPreservesTagsCommentAndPrivacy() = runTest {
        val f = Fixture { _, method -> if(method == "GET") subject() else stats }
        try {
            write(f,"""{"rate":8}""")
            val body = Json.parseToJsonElement(f.calls.last().third).jsonObject
            assertFalse("collectionType" in body)
            val rating = body.getValue("selfRating").jsonObject
            assertEquals(8,rating.getValue("score").jsonPrimitive.int)
            assertEquals("原评论",rating.getValue("comment").jsonPrimitive.content)
            assertEquals("保留标签",rating.getValue("tags").jsonArray.single().jsonPrimitive.content)
            assertTrue(rating.getValue("isPrivate").jsonPrimitive.boolean)
        } finally { f.client.close() }
    }
    @Test fun progressBatchTouchesOnlyChangedMainEpisodes() = runTest {
        val f = Fixture { _, method -> if(method == "GET") subject() else "{}" }
        try {
            write(f,"""{"ep_status":2}""")
            assertEquals(2,f.calls.size)
            assertEquals("/api/animeko/v2/subjects/430699/episodes/batchUpdate",f.calls.last().second)
            assertEquals(Json.parseToJsonElement("""{"episodeIds":[12],"episodeCollectionType":"DONE"}"""),Json.parseToJsonElement(f.calls.last().third))
        } finally { f.client.close() }
    }
    @Test fun invalidProgressFailsBeforeAnyMutation() = runTest {
        val f = Fixture { _, _ -> subject() }
        try {
            assertFailsWith<IllegalArgumentException> { write(f,"""{"type":1,"ep_status":999}""") }
            assertEquals(listOf("GET"),f.calls.map { it.first })
        } finally { f.client.close() }
    }
    @Test fun noCollectionIsNullAndMainProgressExcludesSpecials() = runTest {
        for (type in listOf("null","\"DOING\"")) {
            val f = Fixture { _,_ -> subject(type) }
            try {
                val result = animekoCollectionRequest("libraryRead",buildJsonObject { put("id",430699) },f.api)
                assertEquals(1,result.subject?.progress)
                if (type == "null") assertEquals("null",result.raw)
                else assertEquals(3,Json.parseToJsonElement(result.raw).jsonObject.getValue("type").jsonPrimitive.int)
            } finally { f.client.close() }
        }
    }
    @Test fun nicknamePolicyMatchesNativeLimits() {
        assertTrue(isValidProfileNickname("张千嘛")); assertTrue(isValidProfileNickname("abcdef"))
        assertFalse(isValidProfileNickname("abc")); assertFalse(isValidProfileNickname("a b c d"))
        assertFalse(isValidProfileNickname("a".repeat(21))); assertTrue(isValidProfileNickname(""))
    }
    @Test fun searchUsesGeneratedQueryNamesAndCollectionFilters() = runTest {
        val f = Fixture { _, _ -> """{"items":[]}""" }
        try {
            val result = animekoSearch(buildJsonObject {
                put("keyword","金牌得主"); put("hideNsfw",true); put("ignoreDone",true); put("sort","rank"); put("offset",24)
            },f.api)
            assertEquals("exclude",f.queries.single()["include_nsfw"])
            assertEquals("24",f.queries.single()["offset"])
            assertEquals("DONE,DROPPED",f.queries.single()["excludeCollectionTypes"])
            assertFalse(result.hasMore)
        } finally { f.client.close() }
    }
}
