import androidx.compose.ui.graphics.Color
import me.him188.ani.app.ui.subject.episode.list.EpisodeListColors
import me.him188.ani.client.models.AniAnimeRecurrence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class EpisodeBroadcastTest {
    private val weekly = AniAnimeRecurrence("2026-10-06T23:00:00+09:00", 7.days.inWholeMilliseconds)

    @Test fun sameDayEpisodeStaysUnbroadcastUntilItsExactTime() {
        assertFalse(webEpisodeBroadcast("2026-10-13", weekly, Instant.parse("2026-10-13T22:59:59+09:00")))
        assertTrue(webEpisodeBroadcast("2026-10-13", weekly, Instant.parse("2026-10-13T23:00:00+09:00")))
        assertFalse(webEpisodeBroadcast("2026-10-20", weekly, Instant.parse("2026-10-13T23:00:00+09:00")))
    }

    @Test fun deepNightEpisodeUsesTheNextCalendarDaySlot() {
        val deepNight = AniAnimeRecurrence("2026-10-07T01:00:00+09:00", 7.days.inWholeMilliseconds)
        assertFalse(webEpisodeBroadcast("2026-10-13", deepNight, Instant.parse("2026-10-14T00:59:59+09:00")))
        assertTrue(webEpisodeBroadcast("2026-10-13", deepNight, Instant.parse("2026-10-14T01:00:00+09:00")))
    }

    @Test fun missingDatesFollowAppFallbackForScheduledAndUnscheduledSubjects() {
        val now = Instant.parse("2026-10-13T00:00:00Z")
        for (date in listOf(null, "", "2099", "invalid", "2026-02-31")) {
            assertFalse(webEpisodeBroadcast(date, weekly, now), "Scheduled: $date")
            assertTrue(webEpisodeBroadcast(date, null, now), "Unscheduled: $date")
        }
    }

    @Test fun dateOnlyEpisodeUsesJapanMidnightAsInApp() {
        assertFalse(webEpisodeBroadcast("2026-10-13", null, Instant.parse("2026-10-12T14:59:59Z")))
        assertTrue(webEpisodeBroadcast("2026-10-13", null, Instant.parse("2026-10-12T15:00:00Z")))
    }

    @Test fun unbroadcastUsesDisabledColorButExplicitWatchedStateTakesPrecedence() {
        val colors = EpisodeListColors(Color.Blue, Color.Magenta, Color.Gray)
        assertEquals(Color.Gray, colors.containerColor(isDoneOrDropped = false, isBroadcast = false))
        assertEquals(Color.Magenta, colors.containerColor(isDoneOrDropped = false, isBroadcast = true))
        assertEquals(Color.Blue, colors.containerColor(isDoneOrDropped = true, isBroadcast = false))
        assertEquals(Color.Blue, colors.containerColor(isDoneOrDropped = true, isBroadcast = true))
    }
}
