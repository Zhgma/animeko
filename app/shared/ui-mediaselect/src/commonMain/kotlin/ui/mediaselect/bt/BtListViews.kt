/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediaselect.bt

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import me.him188.ani.app.domain.media.selector.MediaExclusionReason
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_col_alliance
import me.him188.ani.app.ui.lang.media_selector_bt_col_published
import me.him188.ani.app.ui.lang.media_selector_bt_col_resolution
import me.him188.ani.app.ui.lang.media_selector_bt_col_size
import me.him188.ani.app.ui.lang.media_selector_bt_col_subtitle
import me.him188.ani.app.ui.lang.media_selector_bt_col_title
import me.him188.ani.app.ui.lang.media_selector_bt_collection
import me.him188.ani.app.ui.lang.media_selector_bt_empty
import me.him188.ani.app.ui.lang.media_selector_bt_reason_below_preference
import me.him188.ani.app.ui.lang.media_selector_bt_reason_episode_mismatch
import me.him188.ani.app.ui.lang.media_selector_bt_show_excluded
import me.him188.ani.app.ui.lang.media_selector_item_no_subtitle
import me.him188.ani.app.ui.lang.media_selector_item_season_mismatch
import me.him188.ani.app.ui.lang.media_selector_item_single_episode_resource
import me.him188.ani.app.ui.lang.media_selector_item_subject_title_mismatch
import me.him188.ani.app.ui.lang.media_selector_item_unsupported_playback
import me.him188.ani.app.ui.lang.subject_episode_cached
import me.him188.ani.app.ui.media.MediaDetailsRenderer
import me.him188.ani.app.ui.media.MediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.isSingleEpisode
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * 行内标签的文案, 一次解析供列表复用.
 */
@Immutable
internal class BtLabelStrings(
    val cached: String,
    val collection: String,
    val noSubtitle: String,
    val episodeMismatch: String,
    val belowPreference: String,
    val singleEpisode: String,
    val unsupportedPlayback: String,
    val seasonMismatch: String,
    val subjectTitleMismatch: String,
    val details: MediaDetailsStrings,
) {
    fun exclusion(exclusion: BtRowExclusion): String = when (exclusion) {
        BtRowExclusion.BelowPreference -> belowPreference
        is BtRowExclusion.Reason -> when (exclusion.reason) {
            is MediaExclusionReason.EpisodeMismatch -> episodeMismatch
            MediaExclusionReason.MediaWithoutSubtitle -> noSubtitle
            is MediaExclusionReason.SingleEpisodeForCompleteSubject -> singleEpisode
            MediaExclusionReason.UnsupportedByPlatformPlayer -> unsupportedPlayback
            MediaExclusionReason.FromSequelSeason, MediaExclusionReason.FromSeriesSeason -> seasonMismatch
            MediaExclusionReason.SubjectNameMismatch -> subjectTitleMismatch
        }
    }

    /**
     * 紧凑列表第二行的标签: 已缓存 → 原因 → 分辨率 → 字幕语言 → 字幕形式 → 合集.
     */
    fun compactLabels(row: BtRow): List<String> = buildList {
        val media = row.media
        if (row.isCached) add(cached)
        row.exclusion?.let { add(exclusion(it)) }
        media.properties.resolution.takeIf { it.isNotBlank() }?.let { add(it) }
        for (id in media.properties.subtitleLanguageIds) add(renderSubtitleLanguage(id, details))
        MediaDetailsRenderer.renderSubtitleKind(media.properties.subtitleKind, details)?.let { add(it) }
        if (media.episodeRange?.isSingleEpisode() == false) add(collection)
    }

    /**
     * 表格「字幕」列: 语言 + 形式; 没有字幕语言时显示「无字幕」.
     */
    fun subtitleCell(row: BtRow): String {
        val media = row.media
        val parts = buildList {
            for (id in media.properties.subtitleLanguageIds) add(renderSubtitleLanguage(id, details))
            MediaDetailsRenderer.renderSubtitleKind(media.properties.subtitleKind, details)?.let { add(it) }
        }
        return if (parts.isEmpty()) noSubtitle else parts.joinToString(" ")
    }
}

@Composable
internal fun rememberBtLabelStrings(): BtLabelStrings {
    val details = rememberMediaDetailsStrings()
    return BtLabelStrings(
        cached = stringResource(Lang.subject_episode_cached),
        collection = stringResource(Lang.media_selector_bt_collection),
        noSubtitle = stringResource(Lang.media_selector_item_no_subtitle),
        episodeMismatch = stringResource(Lang.media_selector_bt_reason_episode_mismatch),
        belowPreference = stringResource(Lang.media_selector_bt_reason_below_preference),
        singleEpisode = stringResource(Lang.media_selector_item_single_episode_resource),
        unsupportedPlayback = stringResource(Lang.media_selector_item_unsupported_playback),
        seasonMismatch = stringResource(Lang.media_selector_item_season_mismatch),
        subjectTitleMismatch = stringResource(Lang.media_selector_item_subject_title_mismatch),
        details = details,
    )
}

private fun BtRow.render(strings: BtLabelStrings, timeZone: TimeZone) = BtResourceRow(
    id, media.originalTitle, media.properties.alliance, media.properties.resolution,
    strings.subtitleCell(this), formatBtSize(media.properties.size), formatPublishedMMdd(media.publishedTime, timeZone),
    strings.compactLabels(this), exclusion?.let(strings::exclusion), isSelected, isCached, strings.cached,
)

@Composable
internal fun BtCompactList(
    rows: List<BtRow>, excludedRows: List<BtRow>, revealExcluded: Boolean, onReveal: () -> Unit,
    onClick: (BtRow) -> Unit, onLongClick: (BtRow) -> Unit, timeZone: TimeZone,
    modifier: Modifier = Modifier, listState: LazyListState = rememberLazyListState(),
    horizontalPadding: Dp = 20.dp, isLoading: Boolean = false,
) {
    val strings = rememberBtLabelStrings()
    val originals = (rows + excludedRows).associateBy { it.id }
    BtCompactListContent(rows.map { it.render(strings, timeZone) }, excludedRows.map { it.render(strings, timeZone) },
        revealExcluded, onReveal, { originals[it.id]?.let(onClick) }, { originals[it.id]?.let(onLongClick) },
        modifier, listState, horizontalPadding, isLoading)
}

@Composable
internal fun BtTable(
    rows: List<BtRow>, excludedRows: List<BtRow>, revealExcluded: Boolean, onReveal: () -> Unit,
    onClick: (BtRow) -> Unit, onLongClick: (BtRow) -> Unit, timeZone: TimeZone,
    modifier: Modifier = Modifier, columns: BtTableColumns = BtTableColumns.Default,
    listState: LazyListState = rememberLazyListState(), horizontalPadding: Dp = 24.dp, isLoading: Boolean = false,
) {
    val strings = rememberBtLabelStrings()
    val originals = (rows + excludedRows).associateBy { it.id }
    BtTableContent(rows.map { it.render(strings, timeZone) }, excludedRows.map { it.render(strings, timeZone) },
        revealExcluded, onReveal, { originals[it.id]?.let(onClick) }, { originals[it.id]?.let(onLongClick) },
        modifier, columns, listState, horizontalPadding, isLoading)
}

private val MMdd = LocalDateTime.Format {
    monthNumber()
    char('-')
    day()
}

/**
 * 发布日期 `MM-dd`; 0 → "".
 */
internal fun formatPublishedMMdd(millis: Long, timeZone: TimeZone): String {
    if (millis == 0L) return ""
    return MMdd.format(Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone))
}

/**
 * Zero / Unspecified → ""; 否则 `toString()`.
 */
internal fun formatBtSize(size: FileSize): String {
    if (size == FileSize.Zero || size == FileSize.Unspecified) return ""
    return size.toString()
}
