// SPDX-License-Identifier: AGPL-3.0-only
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import me.him188.ani.app.data.repository.subject.toSubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeBroadcastSchedule
import me.him188.ani.app.ui.subject.episode.list.EpisodeListDefaults
import me.him188.ani.app.ui.subject.episode.list.EpisodeStateButton
import me.him188.ani.client.models.AniAnimeRecurrence
import me.him188.ani.datasources.api.PackedDate
import kotlin.time.Clock
import kotlin.time.Instant

internal fun webEpisodeBroadcast(airDate: String?, recurrence: AniAnimeRecurrence?, now: Instant): Boolean =
    EpisodeBroadcastSchedule.isBroadcast(recurrence?.toSubjectRecurrence(), PackedDate.parseFromDate(airDate.orEmpty()), now)

@Composable
fun rememberWebEpisodeTime(): Instant {
    val now by produceState(Clock.System.now()) {
        while (true) {
            delay(1000)
            value = Clock.System.now()
        }
    }
    return now
}

@Composable
fun WebEpisodeStateButton(
    subject: Subject,
    episode: JsonObject,
    now: Instant,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EpisodeStateButton(
        text = episode.playbackEpisode().sort.toString(),
        isDoneOrDropped = episode["watched"]?.jsonPrimitive?.booleanOrNull == true,
        isBroadcast = webEpisodeBroadcast(
            episode["airdate"]?.jsonPrimitive?.contentOrNull,
            subject.animeCollection?.airingInfo?.recurrence, now,
        ),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        colors = EpisodeListDefaults.colors(LocalWebUISettings.current.episodeProgress.theme),
    )
}
