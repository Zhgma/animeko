// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.settings.tabs.app

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import me.him188.ani.app.data.models.preference.*
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.ui.foundation.SteppedSlider
import me.him188.ani.app.ui.foundation.quantizeSliderValue
import me.him188.ani.app.ui.settings.framework.components.*
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.utils.formatSpeedValue
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration.Companion.seconds

/** Shared player settings; platform adapters declare the controls their runtime supports. */
@Composable
fun SettingsScope.PlayerGroupContent(
    config: VideoScaffoldConfig,
    onConfigChange: (VideoScaffoldConfig) -> Unit,
    filterConfig: DanmakuFilterConfig,
    onFilterChange: (DanmakuFilterConfig) -> Unit,
    regexFilters: @Composable () -> Unit,
    showMobileOptions: Boolean = false,
    showAndroidOptions: Boolean = false,
    supports: (String) -> Boolean = { true },
    platformSettings: @Composable SettingsScope.() -> Unit = {},
) {
    Group(title = { Text(stringResource(Lang.settings_player)) }) {
        DropdownItem(
            enabled = supports("fullscreenSwitchMode"),
            selected = { config.fullscreenSwitchMode },
            values = { FullscreenSwitchMode.entries },
            itemText = {
                Text(
                    when (it) {
                        FullscreenSwitchMode.ALWAYS_SHOW_FLOATING -> stringResource(Lang.settings_player_fullscreen_always_show)
                        FullscreenSwitchMode.AUTO_HIDE_FLOATING -> stringResource(Lang.settings_player_fullscreen_auto_hide)
                        FullscreenSwitchMode.ONLY_IN_CONTROLLER -> stringResource(Lang.settings_player_fullscreen_only_in_controller)
                    },
                )
            },
            onSelect = {
                onConfigChange(config.copy(fullscreenSwitchMode = it))
            },
            title = { Text(stringResource(Lang.settings_player_fullscreen_button)) },
            description = { Text(stringResource(Lang.settings_player_fullscreen_button_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            enabled = supports("videoEnhancementDefaultMode"),
            selected = { config.videoEnhancementDefaultMode },
            values = {
                listOf(
                    VideoEnhancementDefaultMode.PERFORMANCE,
                    VideoEnhancementDefaultMode.QUALITY,
                    VideoEnhancementDefaultMode.OFF,
                )
            },
            itemText = {
                Text(
                    when (it) {
                        VideoEnhancementDefaultMode.OFF -> stringResource(Lang.video_player_off)
                        VideoEnhancementDefaultMode.PERFORMANCE -> stringResource(Lang.video_player_performance)
                        VideoEnhancementDefaultMode.QUALITY -> stringResource(Lang.video_player_quality)
                    },
                )
            },
            onSelect = {
                onConfigChange(config.copy(videoEnhancementDefaultMode = it))
            },
            title = { Text(stringResource(Lang.settings_player_video_enhancement_default)) },
            description = {
                Text(stringResource(Lang.settings_player_video_enhancement_default_description))
            },
        )
        HorizontalDividerItem()
        SwitchItem(
            filterConfig.enableRegexFilter,
            enabled = supports("enableRegexFilter"),
            onCheckedChange = {
                onFilterChange(filterConfig.copy(enableRegexFilter = it))
            },
            title = { Text(stringResource(Lang.settings_player_enable_regex_filter)) },
        )
        HorizontalDividerItem()
        regexFilters()
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("pauseVideoOnEditDanmaku"),
            checked = config.pauseVideoOnEditDanmaku,
            onCheckedChange = {
                onConfigChange(config.copy(pauseVideoOnEditDanmaku = it))
            },
            title = { Text(stringResource(Lang.settings_player_pause_on_edit_danmaku)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("autoMarkDone"),
            checked = config.autoMarkDone,
            onCheckedChange = {
                onConfigChange(config.copy(autoMarkDone = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_mark_done)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("hideSelectorOnSelect"),
            checked = config.hideSelectorOnSelect,
            onCheckedChange = {
                onConfigChange(config.copy(hideSelectorOnSelect = it))
            },
            title = { Text(stringResource(Lang.settings_player_hide_selector_on_select)) },
        )
        if (showMobileOptions) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.autoFullscreenOnLandscapeMode,
                onCheckedChange = {
                    onConfigChange(config.copy(autoFullscreenOnLandscapeMode = it))
                },
                title = { Text(stringResource(Lang.settings_player_auto_fullscreen_on_landscape)) },
            )
        }
        if (showAndroidOptions) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.enableFoldableHoverMode,
                onCheckedChange = {
                    onConfigChange(config.copy(enableFoldableHoverMode = it))
                },
                title = { Text(stringResource(Lang.settings_player_foldable_hover_mode)) },
                description = { Text(stringResource(Lang.settings_player_foldable_hover_mode_description)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("autoPlayNext"),
            checked = config.autoPlayNext,
            onCheckedChange = {
                onConfigChange(config.copy(autoPlayNext = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_play_next)) },
        )
        if (showMobileOptions) {
            HorizontalDividerItem()
            DropdownItem(
                selected = { config.backgroundBehavior },
                // BACKGROUND_PLAYBACK 为二期后台播放预留, 暂不提供
                values = {
                    listOf(
                        BackgroundBehavior.AUTO_PICTURE_IN_PICTURE,
                        BackgroundBehavior.PAUSE,
                    )
                },
                itemText = {
                    Text(
                        when (it) {
                            BackgroundBehavior.AUTO_PICTURE_IN_PICTURE ->
                                stringResource(Lang.settings_player_background_behavior_auto_pip)

                            BackgroundBehavior.PAUSE ->
                                stringResource(Lang.settings_player_background_behavior_pause)

                            BackgroundBehavior.BACKGROUND_PLAYBACK ->
                                stringResource(Lang.settings_player_background_behavior_background_playback)
                        },
                    )
                },
                onSelect = {
                    onConfigChange(config.copy(backgroundBehavior = it))
                },
                title = { Text(stringResource(Lang.settings_player_background_behavior)) },
                description = { Text(stringResource(Lang.settings_player_background_behavior_description)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("autoSkipOpEd"),
            checked = config.autoSkipOpEd,
            onCheckedChange = {
                onConfigChange(config.copy(autoSkipOpEd = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_skip_op_ed)) },
            description = { Text(stringResource(Lang.settings_player_auto_skip_op_ed_description)) },
        )
        HorizontalDividerItem()
        DropdownItem(
            enabled = supports("opEdSkipDuration"),
            selected = { config.opEdSkipDuration },
            values = { listOf(80.seconds, 85.seconds, 90.seconds) },
            itemText = {
                Text(stringResource(Lang.settings_player_op_ed_skip_duration_seconds, it.inWholeSeconds))
            },
            onSelect = {
                onConfigChange(config.copy(opEdSkipDuration = it))
            },
            title = { Text(stringResource(Lang.settings_player_op_ed_skip_duration)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("autoSwitchMediaOnPlayerError"),
            checked = config.autoSwitchMediaOnPlayerError,
            onCheckedChange = {
                onConfigChange(config.copy(autoSwitchMediaOnPlayerError = it))
            },
            title = { Text(stringResource(Lang.settings_player_auto_switch_media_on_error)) },
        )
        if (showAndroidOptions) {
            HorizontalDividerItem()
            SwitchItem(
                checked = config.enableHighQualityAudioTimeStretch,
                onCheckedChange = {
                    onConfigChange(config.copy(enableHighQualityAudioTimeStretch = it))
                },
                title = { Text(stringResource(Lang.settings_player_audio_time_stretch)) },
                description = { Text(stringResource(Lang.settings_player_audio_time_stretch_description)) },
            )
        }
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("enableHlsAdFiltering"),
            checked = config.enableHlsAdFiltering,
            onCheckedChange = {
                onConfigChange(config.copy(enableHlsAdFiltering = it))
            },
            title = { Text(stringResource(Lang.settings_player_hls_ad_filter)) },
            description = { Text(stringResource(Lang.settings_player_hls_ad_filter_description)) },
        )
        HorizontalDividerItem()
        SwitchItem(
            enabled = supports("enableFramePreview"),
            checked = config.enableFramePreview,
            onCheckedChange = {
                onConfigChange(config.copy(enableFramePreview = it))
            },
            title = { Text(stringResource(Lang.settings_player_frame_preview)) },
            description = { Text(stringResource(Lang.settings_player_frame_preview_description)) },
        )
        HorizontalDividerItem()
        PlaybackSpeedItems(config, onConfigChange)
        platformSettings()
    }
}

/**
 * 倍速范围 + 记住倍速 + 默认倍速 + 长按播放速度.
 *
 * 普通倍速 Slider 共享范围拖动状态: 拖动范围 RangeSlider 期间, 默认倍速的范围和 clamp 后的值
 * 实时跟随, 被 clamp 时通过动画过渡, 避免松手后数值「突变」.
 */
@Composable
private fun SettingsScope.PlaybackSpeedItems(
    config: VideoScaffoldConfig,
    onConfigChange: (VideoScaffoldConfig) -> Unit,
) {
    val persistedRange = config.minPlaybackSpeed..config.maxPlaybackSpeed

    // 范围拖动期间的瞬态值, 提交后清空; 拖动期间默认倍速 Slider 实时使用该范围
    var rangeDragOverride by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    var rangeDragging by remember { mutableStateOf(false) }
    val effectiveRange = rangeDragOverride ?: persistedRange
    LaunchedEffect(persistedRange, rangeDragOverride, rangeDragging) {
        if (!rangeDragging && rangeDragOverride == persistedRange) {
            rangeDragOverride = null
        }
    }

    RangeSliderItem(
        value = effectiveRange,
        onValueChange = {
            rangeDragging = true
            rangeDragOverride = VideoScaffoldConfig.normalizePlaybackSpeedRange(it, effectiveRange)
        },
        onValueChangeFinished = {
            val finalRange = rangeDragOverride ?: return@RangeSliderItem
            // 缩小范围时把相关值一并 clamp 进新区间；保留最终范围直到配置写回，避免短暂回跳
            val committedConfig = config.withPlaybackSpeedRange(finalRange)
            rangeDragging = false
            rangeDragOverride = committedConfig.minPlaybackSpeed..committedConfig.maxPlaybackSpeed
            onConfigChange(committedConfig)
        },
        valueRange = VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED..VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED,
        steps = 14,
        valueIndicator = { Text(it.formatSpeedValue()) },
        valueLabel = {
            Text("${effectiveRange.start.formatSpeedValue()}x–${effectiveRange.endInclusive.formatSpeedValue()}x")
        },
        title = { Text(stringResource(Lang.settings_player_playback_speed_range)) },
        description = { Text(stringResource(Lang.settings_player_playback_speed_range_description)) },
    )

    // 范围变化以动画过渡, 避免 thumb 映射位置瞬移
    val animatedRangeStart by animateFloatAsState(effectiveRange.start, label = "playbackSpeedRangeStart")
    val animatedRangeEnd by animateFloatAsState(effectiveRange.endInclusive, label = "playbackSpeedRangeEnd")
    val displayRange =
        if (animatedRangeStart < animatedRangeEnd) animatedRangeStart..animatedRangeEnd else effectiveRange

    HorizontalDividerItem()

    SwitchItem(
        checked = config.rememberPlaybackSpeed,
        onCheckedChange = {
            onConfigChange(config.copy(rememberPlaybackSpeed = it))
        },
        title = { Text(stringResource(Lang.settings_player_remember_playback_speed)) },
        description = { Text(stringResource(Lang.settings_player_remember_playback_speed_description)) },
    )

    // 此处没有 ColumnScope 接收者, 不显式指定就会落到 fadeIn/fadeOut 的重载上, 高度瞬间撑开、下方条目跳位.
    AnimatedVisibility(
        visible = !config.rememberPlaybackSpeed,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            HorizontalDividerItem()
            SpeedSliderItem(
                value = config.playbackSpeed,
                displayRange = displayRange,
                commitRange = effectiveRange,
                onCommit = { onConfigChange(config.copy(playbackSpeed = it)) },
                title = { Text(stringResource(Lang.settings_player_default_playback_speed)) },
                description = { Text(stringResource(Lang.settings_player_default_playback_speed_description)) },
            )
        }
    }

    HorizontalDividerItem()

    SpeedSliderItem(
        value = config.effectiveFastForwardSpeed,
        displayRange = VideoScaffoldConfig.FAST_FORWARD_SPEED_RANGE,
        commitRange = VideoScaffoldConfig.FAST_FORWARD_SPEED_RANGE,
        onCommit = { onConfigChange(config.copy(fastForwardSpeed = it)) },
        title = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed)) },
        description = { Text(stringResource(Lang.settings_player_long_press_fast_forward_speed_description)) },
    )
}

/**
 * 一条倍速 Slider. 拖动期间使用瞬态值实时预览, 松手后量化到 [commitRange] 再提交.
 *
 * @param displayRange 渲染用范围, 可能正处于动画过渡中
 * @param commitRange 提交用范围, 即用户配置的真实范围
 */
@Composable
private fun SettingsScope.SpeedSliderItem(
    value: Float,
    displayRange: ClosedFloatingPointRange<Float>,
    commitRange: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    title: @Composable RowScope.() -> Unit,
    description: @Composable (() -> Unit)? = null,
) {
    var dragOverride by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val displayValue = (dragOverride ?: value).coerceIn(displayRange)
    LaunchedEffect(value, dragOverride, dragging) {
        if (!dragging && dragOverride == value) {
            dragOverride = null
        }
    }
    SliderItem(
        title = title,
        description = description,
        valueLabel = {
            Text("${quantizeSliderValue(displayValue, displayRange).formatSpeedValue()}x")
        },
    ) {
        SteppedSlider(
            value = displayValue,
            onValueChange = {
                dragging = true
                dragOverride = quantizeSliderValue(it, displayRange)
            },
            onValueChangeFinished = { displayedValue ->
                val finalValue = dragOverride ?: displayedValue
                val committedValue = quantizeSliderValue(finalValue, commitRange)
                dragging = false
                dragOverride = committedValue
                onCommit(committedValue)
            },
            valueRange = displayRange,
            valueIndicator = { Text(it.formatSpeedValue(), maxLines = 1, softWrap = false) },
        )
    }
}

