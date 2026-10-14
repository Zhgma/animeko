// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.utils.xml.Element

data class WebSearchSubjectInfo(
    val internalId: String,
    val name: String,
    val fullUrl: String,
    val partialUrl: String,
    val origin: Element?,
)

class WebSearchChannelInfo(
    val name: String,
    val content: Element,
)

data class WebSearchEpisodeInfo(
    /**
     * 播放线路, 与 [name] 一起组成 ID. 如要修改, 考虑 [SelectorMediaSourceEngine.selectMedia]
     */
    val channel: String?,
    /**
     * "第x集" 等原名.
     */
    val name: String,
    /**
     * 解析成功的 [EpisodeSort], 未解析成功则为 `null`.
     * 可能表示 sort, 也可能是 ep.
     */
    val episodeSortOrEp: EpisodeSort?,
    /**
     * 播放地址
     */
    val playUrl: String
)

