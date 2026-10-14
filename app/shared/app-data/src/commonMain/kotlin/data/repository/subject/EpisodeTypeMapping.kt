// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.repository.subject
import me.him188.ani.client.models.AniEpisodeType
import me.him188.ani.datasources.api.EpisodeType

fun AniEpisodeType.toEpisodeType(): EpisodeType? {
    return when (this) {
        AniEpisodeType.MAIN -> EpisodeType.MainStory
        AniEpisodeType.SPECIAL -> EpisodeType.SP
        AniEpisodeType.OP -> EpisodeType.OP
        AniEpisodeType.ED -> EpisodeType.ED
        AniEpisodeType.TRAILER -> EpisodeType.PV
        AniEpisodeType.MAD -> EpisodeType.MAD
        AniEpisodeType.OTHER -> null
    }
}

