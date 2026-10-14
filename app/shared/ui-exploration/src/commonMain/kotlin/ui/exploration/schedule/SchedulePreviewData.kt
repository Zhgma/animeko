/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import me.him188.ani.app.domain.episode.AiringScheduleDateRange

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import me.him188.ani.utils.platform.collections.ImmutableEnumMap

@TestOnly
val TestAiringScheduleItemPresentations
    get() = buildList {
        var id = 0
        repeat(50) { i ->
            repeat(if (i % 8 == 0) 2 else 1) {
                add(
                    AiringScheduleItemPresentation(
                        subjectId = ++id,
                        subjectTitle = "Subject $id",
                        subjectOriginalTitle = "オリジナル $id",
                        imageUrl = "https://example.com/image.jpg",
                        episodeId = id,
                        episodeSort = EpisodeSort(if (i % 3 == 0) 13 else 1),
                        episodeEp = EpisodeSort(1),
                        episodeName = "Episode 1",
                        episodeOriginalName = "エピソード 1",
                        subjectCollectionType = UnifiedCollectionType.entries[i % UnifiedCollectionType.entries.size],
                        dayOfWeek = DayOfWeek.entries[i % DayOfWeek.entries.size],
                        // 每隔几个放一个时间未定的项目, 预览里能看到 "时间未定" 的样式
                        time = if (i % 11 == 10) null else LocalTime(i % 24, 0),
                    ),
                )

            }
        }
    }

/**
 * 时间未定 (`time == null`) 的项目排在已知时间的项目之后.
 */
private val testPresentationComparator =
    compareBy<AiringScheduleItemPresentation, LocalTime?>(nullsLast()) { it.time }
        .thenBy { it.subjectTitle }

/**
 * @see TestSchedulePageData
 */
@TestOnly
val TestAiringScheduleItemPresentationData: ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>>
    get() = ImmutableEnumMap<DayOfWeek, List<AiringScheduleItemPresentation>> { day ->
        TestAiringScheduleItemPresentations.filter { it.dayOfWeek == day }
            .sortedWith(testPresentationComparator)
    }


@TestOnly
val TestSchedulePageData: List<AiringSchedule>
    get() {
        val currentTime = LocalTime(12, 0)
        val list = TestAiringScheduleItemPresentations.filter { it.dayOfWeek == DayOfWeek.MONDAY }
            .sortedWith(testPresentationComparator)


        return ScheduleDay.generateForRecentTwoWeeks(LocalDate(2025, 12, 10)).map {
            AiringSchedule(
                date = it.date,
                SchedulePageDataHelper.toColumnItems(list, addIndicator = true, currentTime),
            )
        }
    }

