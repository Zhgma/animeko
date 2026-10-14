// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.models.subject

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownCompleted
import me.him188.ani.app.domain.episode.EpisodeCompletionContext.isKnownOnAir
import me.him188.ani.datasources.api.EpisodeType
import me.him188.ani.datasources.api.ifInvalid
import me.him188.ani.datasources.api.PackedDate

/**
 * 在有剧集信息的时候使用, 计算最准确的结果
 */
fun SubjectAiringInfo.Companion.computeFromEpisodeList(
    list: List<EpisodeInfo>,
    airDate: PackedDate,
    recurrence: SubjectRecurrence?,
): SubjectAiringInfo {
    // See test SubjectAiringInfoTest
    // Change with care!

    // 仅保留正片
    val mainStoryEpisodes = list.filter { it.type == EpisodeType.MainStory }

    val kind = when {
        mainStoryEpisodes.isEmpty() -> SubjectAiringKind.UPCOMING
        mainStoryEpisodes.all { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.COMPLETED
        mainStoryEpisodes.all { it.isKnownOnAir(recurrence) } -> SubjectAiringKind.UPCOMING
        mainStoryEpisodes.any { it.isKnownCompleted(recurrence) } -> SubjectAiringKind.ON_AIR
        // airDate 有效且不晚于当前时间 => 已完结; 否则即将开播.
        // (原先此处还有一条"播出 10 年后判完结"的分支, 但 `airDate <= now()` 已覆盖所有过去日期,
        //  且其 `10 * 30 * 365.days` ≈ 300 年与注释不符, 属不可达死代码, 已删除.)
        airDate.isValid && airDate <= PackedDate.now() -> SubjectAiringKind.COMPLETED

        else -> SubjectAiringKind.UPCOMING
    }

    return SubjectAiringInfo(
        kind = kind,
        mainEpisodeCount = mainStoryEpisodes.size,
        airDate = airDate.ifInvalid { mainStoryEpisodes.firstOrNull()?.airDate ?: PackedDate.Invalid },
        firstSort = mainStoryEpisodes.firstOrNull()?.sort,
        latestEp = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
        latestSort = mainStoryEpisodes.lastOrNull { it.isKnownCompleted(recurrence) }?.sort,
        upcomingSort = if (kind == SubjectAiringKind.COMPLETED) {
            null
        } else {
            mainStoryEpisodes.firstOrNull { it.isKnownOnAir(recurrence) }?.sort
                ?: mainStoryEpisodes.firstOrNull { it.airDate.isInvalid }?.sort
        },
    )
}

/**
 * 在无剧集信息的时候使用, 估算
 */
fun SubjectAiringInfo.Companion.computeFromSubjectInfo(
    info: SubjectInfo,
    mainEpisodeCount: Int,
): SubjectAiringInfo {
    val kind = when {
        info.completeDate.isValid -> SubjectAiringKind.COMPLETED
        info.airDate < PackedDate.now() -> SubjectAiringKind.ON_AIR
        else -> SubjectAiringKind.UPCOMING
    }
    return SubjectAiringInfo(
        kind = kind,
        mainEpisodeCount = mainEpisodeCount,
        airDate = info.airDate,
        firstSort = null,
        latestEp = null,
        latestSort = null,
        upcomingSort = null,
    )
}
