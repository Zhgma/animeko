import kotlin.test.*
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import me.him188.ani.client.models.AniSubjectCollection
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.app.ui.subject.SubjectStatusStrings

class SubjectStatusAlignmentTest {
    private val strings = SubjectStatusStrings("继续观看 %s","已看完","未开播","%s开播","开始观看","%s更新","看过 %s","未知","未开播","连载中","连载至第 %s 话","已完结","全 %s 话","预定全 %s 话")
    private fun subject(nextDate: String = "2026-10-18", recurrence: String = "2026-10-04T15:00:00Z"): Subject {
        val value = Json.decodeFromString<AniSubjectCollection>("""{
          "id":1,"type":"ANIME","name":"test","nameCn":"测试","summary":"","nsfw":false,"airDate":"2026-10-04",
          "airingInfo":{"recurrence":{"startTime":"$recurrence","intervalMillis":604800000}},
          "aliases":[],"favorite":{"wish":0,"done":0,"doing":0,"onHold":0,"dropped":0},"tags":[],"metaTags":[],"scoreDetails":{},"selfRating":{"score":0,"tags":[],"isPrivate":false},
          "episodes":[
            {"episodeId":11,"subjectId":1,"sort":"26","ep":"1","type":"MAIN","name":"","nameCn":"","description":"","airdate":"2026-10-04","collectionType":"DONE"},
            {"episodeId":12,"subjectId":1,"sort":"27","ep":"2","type":"MAIN","name":"","nameCn":"","description":"","airdate":"2026-10-11","collectionType":"DONE"},
            {"episodeId":13,"subjectId":1,"sort":"28","ep":"3","type":"MAIN","name":"","nameCn":"","description":"","airdate":"$nextDate"},
            {"episodeId":14,"subjectId":1,"sort":"1","type":"SPECIAL","name":"","nameCn":"","description":"","airdate":"2026-10-11"}],
          "relations":{"subjectId":1,"seriesMainSubjectIds":[],"seriesMainSubjectNames":[],"sequelSubjects":[],"sequelSubjectNames":[]},"imageLarge":"","imageThumb":""}
        """)
        return Subject(id=1,title="测试",animeCollection=value)
    }
    @Test fun caughtUpUsesUpdateWeekAndRetainsWatchedEpisode() {
        val progress=collectionProgress(subject(),Instant.parse("2026-10-13T17:00:00Z"),strings)
        assertEquals("周日更新",progress.button)
        assertEquals("看过 02 (27) · 预定全 3 话",progress.label)
        assertFalse(progress.primary)
        assertEquals(12L,progress.episode?.episodeId)
    }
    @Test fun exactBroadcastBoundaryChangesFromUpdateTimeToContinue() {
        val value=subject()
        val before=collectionProgress(value,Instant.parse("2026-10-18T14:59:59Z"),strings)
        assertFalse(before.primary)
        assertEquals(12L,before.episode?.episodeId)
        val after=collectionProgress(value,Instant.parse("2026-10-18T15:00:00Z"),strings)
        assertTrue(after.primary)
        assertEquals("继续观看 03 (28)",after.button)
        assertEquals(13L,after.episode?.episodeId)
        assertEquals("看过 02 (27) · 全 3 话",after.label)
    }
    @Test fun missingAirDateDoesNotInventAnUpdateTime() {
        val progress=collectionProgress(subject(nextDate=""),Instant.parse("2026-10-13T17:00:00Z"),strings)
        assertEquals("看过 02 (27)",progress.button)
        assertFalse(progress.primary)
    }
    @Test fun completedWatchedSeriesShowsDoneAndOmitsSpecialCount() {
        val value=subject();val catalog=value.animeCollection!!
        val all=value.copy(animeCollection=catalog.copy(episodes=catalog.episodes.map { it.copy(collectionType=AniEpisodeCollectionType.DONE) }))
        val progress=collectionProgress(all,Instant.parse("2026-11-01T00:00:00Z"),strings)
        assertTrue(progress.done)
        assertEquals("已看完 · 全 3 话",progress.label)
    }
}
