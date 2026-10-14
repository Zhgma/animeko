// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.domain.media.selector
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseEpisode

/**
 * 线路里第 [pickedIndex] 项被当作第 [pickedAs] 集时, 第 [target] 集按位置对应的下标 k + (t − p).
 * 编号类型不同 (正片与 SP) 或差不是整数时算不出, 返回 null. 不检查下标是否越界.
 * 浏览记忆回放与下载弹窗的手动查找共用这条规则.
 */
fun browseEpisodeIndex(pickedIndex: Int, pickedAs: EpisodeSort, target: EpisodeSort): Int? {
    val sameSeries = when (pickedAs) {
        is EpisodeSort.Normal -> target is EpisodeSort.Normal
        is EpisodeSort.Special -> target is EpisodeSort.Special && pickedAs.type == target.type
        is EpisodeSort.Unknown -> false
    }
    if (!sameSeries) return null
    val offset = (target.number ?: return null) - (pickedAs.number ?: return null)
    if (offset % 1f != 0f) return null
    return pickedIndex + offset.toInt()
}

/** Continuous numbering takes precedence over numbering local to this subject. */
fun standardBrowseEpisodeIndex(episodes: List<BrowseEpisode>, sort: EpisodeSort, ep: EpisodeSort?): Int? =
    episodes.indexOfFirst { it.episodeSort == sort }.takeIf { it >= 0 }
        ?: ep?.let { number -> episodes.indexOfFirst { it.episodeSort == number }.takeIf { it >= 0 } }
