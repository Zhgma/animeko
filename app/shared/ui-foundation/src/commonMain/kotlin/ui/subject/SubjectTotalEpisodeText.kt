// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject

import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectAiringKind

fun renderTotalEpisodeText(
    airingInfo: SubjectAiringInfo,
    strings: SubjectStatusStrings,
): String? {
    return if (
        airingInfo.kind == SubjectAiringKind.UPCOMING &&
        airingInfo.mainEpisodeCount == 0
    ) {
        null
    } else {
        when (airingInfo.kind) {
            SubjectAiringKind.COMPLETED ->
                strings.totalEpisodesCompleted(airingInfo.mainEpisodeCount)

            SubjectAiringKind.UPCOMING,
            SubjectAiringKind.ON_AIR,
                ->
                strings.totalEpisodesScheduled(airingInfo.mainEpisodeCount)
        }
    }
}

