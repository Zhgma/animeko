/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.data.models.subject.displayName
import me.him188.ani.app.data.models.subject.nameOrNameCn
import me.him188.ani.app.domain.episode.EpisodeWithAiringTime
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

fun EpisodeWithAiringTime.toPresentation(timeZone: TimeZone): AiringScheduleItemPresentation {
    // 时间未定时 airingTime 是放送日期在 timeZone 的 00:00, 日期仍然可用, 只是不显示时刻.
    val dateTime = airingTime.toLocalDateTime(timeZone)
    // Return the item
    return AiringScheduleItemPresentation(
        subjectId = subject.subjectId,
        subjectTitle = subject.displayName,
        subjectOriginalTitle = subject.nameOrNameCn,
        imageUrl = subject.imageLarge,
        episodeId = episode.episodeId,
        episodeSort = episode.sort,
        episodeEp = episode.ep,
        episodeName = episode.displayName,
        episodeOriginalName = episode.nameOrNameCn,
        subjectCollectionType = UnifiedCollectionType.NOT_COLLECTED,
        dayOfWeek = dateTime.dayOfWeek,
        time = if (timeKnown) dateTime.time else null,
    )
}

