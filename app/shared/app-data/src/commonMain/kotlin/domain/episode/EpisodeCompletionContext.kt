/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.subject.SubjectRecurrence
import me.him188.ani.datasources.api.PackedDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Native episode-model adapters for the shared broadcast schedule. */
object EpisodeCompletionContext {
    private val clock = Clock.System

    fun resolveEpisodeAirTime(airDate: PackedDate, recurrence: SubjectRecurrence?): EpisodeAirTime? =
        EpisodeBroadcastSchedule.resolveEpisodeAirTime(airDate, recurrence)

    /**
     * [resolveEpisodeAirTime] 的简写, 只返回播出时刻 (无论是否精确). 无法计算时返回 `null`.
     */
    fun SubjectRecurrence?.mapAirDate(
        airDate: PackedDate,
    ): Instant? = resolveEpisodeAirTime(airDate, this)?.instant

    /**
     * 是否一定已经播出了: 能计算出播出时刻 ([mapAirDate]) 且 [now] 不早于它.
     * 无法计算播出时刻 (例如没有上映日期) 时返回 `false`.
     */
    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now >= airTime
    }

    /**
     * 以当前时间判断 [isKnownCompleted].
     */
    fun EpisodeInfo.isKnownCompleted(recurrence: SubjectRecurrence?): Boolean =
        isKnownCompleted(recurrence, clock.now())

    /**
     * 是否一定还未播出: 能计算出播出时刻 ([mapAirDate]) 且 [now] 早于它.
     * 无法计算播出时刻时返回 `false`.
     */
    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?, now: Instant): Boolean {
        val airTime = recurrence.mapAirDate(airDate) ?: return false
        return now < airTime
    }

    /**
     * 以当前时间判断 [isKnownOnAir].
     */
    fun EpisodeInfo.isKnownOnAir(recurrence: SubjectRecurrence?): Boolean =
        isKnownOnAir(recurrence, clock.now())
}
