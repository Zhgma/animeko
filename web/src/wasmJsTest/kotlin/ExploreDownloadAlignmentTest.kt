import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.ui.download.components.DownloadStatus
import me.him188.ani.app.ui.exploration.schedule.AiringScheduleColumnItem
import me.him188.ani.app.ui.exploration.schedule.ScheduleDay
import me.him188.ani.client.models.*
import kotlin.test.*
import kotlin.time.Instant

class ExploreDownloadAlignmentTest {
    @Test fun batchKeepsOtherTasksRunningAfterOneFailureAndDeduplicates() = runTest {
        val called = mutableListOf<String>()
        val failures = runBrowserDownloadBatch(listOf("a", "b", "a", "c")) {
            called += it
            if (it == "b") error("engine failed")
        }
        assertEquals(1, failures)
        assertEquals(listOf("a", "b", "c"), called)
    }

    @Test fun batchCancellationStopsFurtherActions() = runTest {
        val called = mutableListOf<String>()
        assertFailsWith<CancellationException> {
            runBrowserDownloadBatch(listOf("a", "b")) { called += it; throw CancellationException() }
        }
        assertEquals(listOf("a"), called)
    }
    private fun scheduled(id: Long, time: String, known: Boolean = true) = AniScheduledAnimeEpisode(
        AniScheduledAnimeSubject(id, "Original", "中文", ""),
        AniScheduledAnimeEpisodeInfo(id, "Episode", "剧集", "2026-10-14", 0, "27", "2"), time, known)

    @Test fun timetableUsesAppOrderingTimeZoneAndUnknownTimeGrouping() {
        val records = listOf(AniAiringScheduleForDate("2026-10-14", listOf(
            scheduled(4, "2026-10-14T07:00:00Z"), scheduled(3, "2026-10-13T16:00:00Z", false),
            scheduled(1, "2026-10-14T01:00:00Z"), scheduled(2, "2026-10-14T01:00:00Z"))))
        val result = webScheduleColumns(records, Instant.parse("2026-10-14T05:30:00Z"), TimeZone.of("UTC+08:00")).single()
        assertEquals(LocalDate(2026, 10, 14), result.date)
        assertEquals(listOf(1, 2, null, 4, 3), result.episodes.map { (it as? AiringScheduleColumnItem.Data)?.item?.subjectId })
        assertEquals(listOf(true, false, true, true), result.episodes.filterIsInstance<AiringScheduleColumnItem.Data>().map { it.showTime })
        assertEquals(LocalTime(13, 30), result.episodes.filterIsInstance<AiringScheduleColumnItem.CurrentTimeIndicator>().single().currentTime)
        assertNull((result.episodes.last() as AiringScheduleColumnItem.Data).item.time)
        assertEquals("02", (result.episodes.first() as AiringScheduleColumnItem.Data).item.episodeEp.toString())
        assertTrue(webScheduleColumns(records, Instant.parse("2026-10-15T05:30:00Z"), TimeZone.of("UTC+08:00"))
            .single().episodes.none { it is AiringScheduleColumnItem.CurrentTimeIndicator })
    }

    @Test fun scheduleDateWindowTracksTodayAcrossMidnight() {
        val today = LocalDate(2026, 10, 14)
        val days = ScheduleDay.generateForRecentTwoWeeks(today)
        assertEquals(15, days.size)
        assertEquals(today, days.single { it.kind == ScheduleDay.Kind.TODAY }.date)
        assertEquals(LocalDate(2026, 10, 7), days.first().date)
        assertEquals(LocalDate(2026, 10, 21), days.last().date)
    }

    @Test fun downloadsGroupBySubjectAndKeepUnidentifiedTasksSeparate() {
        val tasks = listOf(
            TorrentTask("a", subjectId = 42, subjectTitle = "作品", episode = "1", length = 100, progress = 0.5, state = "downloading"),
            TorrentTask("b", subjectId = 42, subjectTitle = "作品", episode = "2", length = 100, progress = 1.0, state = "stopped"),
            TorrentTask("c", name = "独立任务", state = "stopped"), TorrentTask("d", name = "独立任务", state = "error"))
        val groups = browserDownloadGroups(tasks)
        assertEquals(3, groups.size)
        val series = groups.single { it.subjectId == 42 }
        assertEquals(2, series.entries.size)
        assertEquals(1, series.finishedCount)
        assertEquals(1, series.activeDownloadCount)
        assertEquals(DownloadStatus.COMPLETED, series.entries.last().status)
        assertEquals(setOf(DownloadStatus.PAUSED, DownloadStatus.FAILED), groups.filter { it.subjectId != 42 }.map { it.entries.single().status }.toSet())
        assertTrue(browserDownloadStats(tasks).uploaded.isUnspecified)
        assertEquals(0L, browserDownloadStats(emptyList()).uploaded.inBytes)
    }

    @Test fun legacyDownloadsWithTitleStillShareAGroup() {
        val groups = browserDownloadGroups(listOf(TorrentTask("a", subjectTitle = "旧作品"), TorrentTask("b", subjectTitle = "旧作品")))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().entries.size)
        assertNull(groups.single().entries.first().creationTime)
    }
}


