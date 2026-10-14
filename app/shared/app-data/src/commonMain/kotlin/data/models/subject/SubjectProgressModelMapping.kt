// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.models.subject

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.datasources.api.PackedDate

/** Native repository models adapt to the shared progress calculation. */
fun SubjectProgressInfo.Companion.compute(
    subjectInfo: SubjectInfo,
    episodes: List<EpisodeCollectionInfo>,
    currentDate: PackedDate,
    recurrence: SubjectRecurrence?,
): SubjectProgressInfo {
    return compute(
        subjectStarted = currentDate > subjectInfo.airDate,
        episodes = episodes.map {
            SubjectProgressInfo.Episode(
                it.episodeId,
                it.collectionType,
                it.episodeInfo.ep,
                it.episodeInfo.sort,
                it.episodeInfo.airDate,
                it.episodeInfo.isKnownCompleted(recurrence),
            )
        },
        subjectAirDate = subjectInfo.airDate,
        recurrence = recurrence,
    )
}

