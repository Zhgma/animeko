/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect

import androidx.compose.runtime.Immutable

/**
 * 「正在观看」提示. [sort] 是已格式化的集号 (如 "25"), [name] 是剧集名, 可为空串 (只显示集号).
 */
@Immutable
data class WatchingEpisode(
    val sort: String,
    val name: String,
    /**
     * 本季集号 (EP), 已格式化. 与 [sort] 相同或未知时为 null, 此时只显示 [sort].
     * 续季的 sort 接着上一季编号, 而不少数据源按 EP 编号 (从 01 重新开始), 两者不同时都要显示, 用户才能对上资源标题里的集号.
     */
    val ep: String? = null,
)

