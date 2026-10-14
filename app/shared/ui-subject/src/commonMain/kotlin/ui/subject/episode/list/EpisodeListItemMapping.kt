// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.subject.episode.list

import me.him188.ani.app.data.models.episode.EpisodeCollectionInfo

/**
 * @param isBroadcast 是否已经开播, 见 [EpisodeListUiState.isEpisodeBroadcast]
 */
fun EpisodeListItem.Companion.from(
    collection: EpisodeCollectionInfo,
    isBroadcast: Boolean,
//            cacheStatus: EpisodeCacheStatus?,
    playProgress: Float? = null,
): EpisodeListItem {
    return EpisodeListItem(
        episodeId = collection.episodeId,
        sort = collection.episodeInfo.sort,
        ep = collection.episodeInfo.ep,
        name = collection.episodeInfo.name,
        nameCn = collection.episodeInfo.nameCn,
        collectionType = collection.collectionType,
//                cacheStatus = cacheStatus,
//                airTime = collection.episodeInfo.airDate.toLocalDateOrNull()?,
        isBroadcast = isBroadcast,
        imageMedium = collection.episodeInfo.imageMedium,
        imageLarge = collection.episodeInfo.imageLarge,
        playProgress = playProgress,
    )
}
