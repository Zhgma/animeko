// SPDX-License-Identifier: AGPL-3.0-only
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.repository.subject.toSubjectRecurrence
import me.him188.ani.app.domain.episode.EpisodeBroadcastSchedule
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.SubjectProgressState
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.client.models.AniEpisodeCollectionType
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.time.Instant

internal data class WebSubjectStatus(val label: AiringLabelState, val progress: SubjectProgressState)

/** Map generated API records to the same progress model consumed by the native UI. */
internal fun webSubjectStatus(subject: Subject, now: Instant): WebSubjectStatus {
    val catalog = subject.animeCollection
    if (catalog == null) return WebSubjectStatus(AiringLabelState(null, null), SubjectProgressState(null))
    val recurrence = catalog.airingInfo?.recurrence?.toSubjectRecurrence()
    val main = catalog.episodes.filter { it.type == AniEpisodeType.MAIN }.sortedBy { it.sort.toDoubleOrNull() ?: 0.0 }
    val dates = main.associate { it.episodeId to PackedDate.parseFromDate(it.airdate.orEmpty()) }
    val times = dates.mapValues { EpisodeBroadcastSchedule.resolveEpisodeAirTime(it.value, recurrence)?.instant }
    val completed = main.filter { times[it.episodeId]?.let { time -> now >= time } == true }
    val upcoming = main.filter { times[it.episodeId]?.let { time -> now < time } == true }
    val airDate = PackedDate.parseFromDate(catalog.airDate)
    val today = PackedDate.parseFromDate(now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
    val kind = when {
        main.isEmpty() -> SubjectAiringKind.UPCOMING
        completed.size == main.size -> SubjectAiringKind.COMPLETED
        upcoming.size == main.size -> SubjectAiringKind.UPCOMING
        completed.isNotEmpty() -> SubjectAiringKind.ON_AIR
        airDate.isValid && airDate <= today -> SubjectAiringKind.COMPLETED
        else -> SubjectAiringKind.UPCOMING
    }
    val progress = SubjectProgressInfo.compute(today > airDate, main.map { value ->
        val ep = value.webEpisode().playbackEpisode()
        SubjectProgressInfo.Episode(ep.id,
            when(value.collectionType) {
                AniEpisodeCollectionType.DONE -> UnifiedCollectionType.DONE
                else -> UnifiedCollectionType.NOT_COLLECTED
            }, ep.ep, ep.sort, dates.getValue(value.episodeId), value in completed)
    }, airDate, recurrence)
    val latest = completed.lastOrNull()?.webEpisode()?.playbackEpisode()
    val airing = SubjectAiringInfo(kind, main.size, airDate,
        main.firstOrNull()?.webEpisode()?.playbackEpisode()?.sort,
        latest?.sort, latest?.sort,
        if(kind == SubjectAiringKind.COMPLETED) null else
            (upcoming.firstOrNull() ?: main.firstOrNull { dates.getValue(it.episodeId).isInvalid })?.webEpisode()?.playbackEpisode()?.sort)
    return WebSubjectStatus(AiringLabelState(airing, progress), SubjectProgressState(progress, WeekFormatter { now }))
}
