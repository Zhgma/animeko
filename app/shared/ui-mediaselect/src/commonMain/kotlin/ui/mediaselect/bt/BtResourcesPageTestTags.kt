/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

object BtResourcesPageTestTags {
    const val ROOT = "bt_page"
    const val SEARCH_FIELD = "bt_search_field"
    const val EPISODE_CHIP = "bt_episode_chip"
    const val SOURCE_CHIP = "bt_source_chip"
    const val FILTER_CHIP = "bt_filter_chip"
    const val COMPACT_LIST = "bt_compact_list"
    const val TABLE = "bt_table"
    const val LOADING = "bt_loading"
    fun row(mediaId: String) = "bt_row_$mediaId"
    const val SHOW_EXCLUDED = "bt_show_excluded"
    const val SOURCE_SHEET = "bt_source_sheet"
    fun sourceItem(mediaSourceId: String) = "bt_source_item_$mediaSourceId"
    const val FILTER_SHEET = "bt_filter_sheet"
    const val FILTER_RESET = "bt_filter_reset"
    const val FILTER_DONE = "bt_filter_done"
}
