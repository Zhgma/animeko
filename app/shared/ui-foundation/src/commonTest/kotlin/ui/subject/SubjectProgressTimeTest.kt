/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.subject

import kotlinx.datetime.TimeZone
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

class SubjectProgressTimeTest {
    private val now = Instant.parse("2026-10-12T04:00:00Z")
    private val zone = TimeZone.of("Asia/Shanghai")
    private val strings = SubjectStatusStrings(
        continueWatchingFormat = "继续观看 %s", done = "已看完", notOnAir = "还未开播",
        startsOnFormat = "%s开播", startWatching = "开始观看", updatesOnFormat = "%s更新",
        watchedFormat = "看过 %s", unknown = "未知", upcoming = "未开播", onAir = "连载中",
        onAirToFormat = "连载至 %s", completed = "已完结",
        totalEpisodesCompletedFormat = "全 %s 话", totalEpisodesScheduledFormat = "预定全 %s 话",
    )

    private fun state(time: String?, date: PackedDate = PackedDate(2026, 10, 12)): SubjectProgressState =
        SubjectProgressState(
            SubjectProgressInfo(
                ContinueWatchingStatus.Watched(EpisodeSort(1), EpisodeSort(1), date, time?.let(Instant::parse)),
                nextEpisodeIdToPlay = 123,
            ),
            WeekFormatter { now },
        )

    @Test fun todayAndEarlyTomorrowUseThirtyHourTime() {
        for ((time, text) in listOf(
            "2026-10-11T16:00:00Z" to "00:00",
            "2026-10-12T14:30:00Z" to "22:30",
            "2026-10-12T15:59:00Z" to "23:59",
            "2026-10-12T16:00:00Z" to "24:00",
            "2026-10-12T17:00:00Z" to "25:00",
            "2026-10-12T21:59:00Z" to "29:59",
        )) {
            val state = state(time)
            assertEquals(text, state.buttonText(strings, now, zone))
            assertEquals(123, state.episodeIdToPlay)
            assertFalse(state.buttonIsPrimary)
        }
    }

    @Test fun sixAmOtherDatesAndUnknownTimeKeepDateText() {
        assertEquals("明天更新", state("2026-10-12T22:00:00Z", PackedDate(2026, 10, 13)).buttonText(strings, now, zone))
        assertEquals("后天更新", state("2026-10-13T17:00:00Z", PackedDate(2026, 10, 14)).buttonText(strings, now, zone))
        val yesterday = PackedDate(2026, 10, 11)
        assertEquals("10 月 11 日更新", state("2026-10-11T14:30:00Z", yesterday).buttonText(strings, now, zone))
        assertEquals("今天更新", state(null).buttonText(strings, now, zone))
        assertEquals("看过 01", state(null, PackedDate.Invalid).buttonText(strings, now, zone))
    }

    @Test fun usesDeviceTimezoneAndReevaluatesAcrossMidnight() {
        val state = state("2026-10-12T17:00:00Z")
        assertEquals("25:00", state.buttonText(strings, now, zone))
        assertEquals("26:00", state.buttonText(strings, now, TimeZone.of("Asia/Tokyo")))
        assertEquals("17:00", state.buttonText(strings, now, TimeZone.UTC))
        assertEquals("01:00", state.buttonText(strings, Instant.parse("2026-10-12T16:00:00Z"), zone))
    }
}
