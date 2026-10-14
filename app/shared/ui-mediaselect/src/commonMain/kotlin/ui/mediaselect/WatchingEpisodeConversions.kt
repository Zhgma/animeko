// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect

import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.data.models.episode.displayName
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.MediaFetchRequest

/**
 * `EpisodeSort.Normal.toString()` 已补零 ("01").
 */
fun EpisodeInfo.toWatchingEpisode(): WatchingEpisode = watchingEpisode(sort, ep, displayName)

fun MediaFetchRequest.toWatchingEpisode(): WatchingEpisode = watchingEpisode(episodeSort, episodeEp, episodeName)

private fun watchingEpisode(sort: EpisodeSort, ep: EpisodeSort?, name: String): WatchingEpisode =
    WatchingEpisode(sort.toString(), name, ep?.takeIf { it != sort }?.toString())

