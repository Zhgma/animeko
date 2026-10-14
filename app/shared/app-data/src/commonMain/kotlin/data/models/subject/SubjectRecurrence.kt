// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.models.subject

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * 条目的连载周期, 来自 bangumi-data 的 `broadcast` (`R/<startTime>/<interval>`).
 *
 * 由上映日期计算一集的播出时刻见 [me.him188.ani.app.domain.episode.EpisodeCompletionContext.resolveEpisodeAirTime].
 */
data class SubjectRecurrence(
    /** 首播时间 (UTC) */
    val startTime: Instant,
    /** 两集之间的时间间隔，例如一周 = `7.days`. 可能为 0 (bangumi-data 的 `P0D`, 一次性放送). */
    val interval: Duration,
)

