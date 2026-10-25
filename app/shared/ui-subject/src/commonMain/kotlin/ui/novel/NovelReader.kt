/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.novel.NovelBlock
import me.him188.ani.app.data.repository.novel.NovelReaderSettings
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.videoplayer.ui.PlayerControllerState
import me.him188.ani.app.videoplayer.ui.VideoScaffold

@Composable
internal fun NovelReader(
    blocks: List<NovelBlock>, initialParagraph: Int, initialOffset: Int, title: String,
    settings: NovelReaderSettings, onSettings: (NovelReaderSettings) -> Unit,
    onPosition: (Int, Int) -> Unit, onCompleted: () -> Unit,
    onBack: () -> Unit, onChapters: () -> Unit, onSources: () -> Unit,
    onPrevious: (() -> Unit)?, onNext: (() -> Unit)?, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (blocks.isEmpty()) return
    val list = remember(blocks) { LazyListState(initialParagraph.coerceIn(blocks.indices), initialOffset.coerceAtLeast(0)) }
    val scope = rememberCoroutineScope()
    val controller = remember(fullscreen) { PlayerControllerState() }
    var showSettings by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var slider by remember { mutableStateOf<Float?>(null) }
    val currentPosition by rememberUpdatedState(onPosition)
    val currentCompleted by rememberUpdatedState(onCompleted)
    var completed by remember(blocks) { mutableStateOf(false) }
    var failedImages by remember(blocks) { mutableStateOf(emptySet<Int>()) }
    val focus = remember { FocusRequester() }
    val toggle = { controller.toggleFullVisible(); interaction++ }
    val currentNext by rememberUpdatedState(onNext)
    fun move(direction: Int) {
        if (list.isScrollInProgress) return
        interaction++
        scope.launch {
            if (direction > 0 && !list.canScrollForward) currentNext?.invoke()
            else list.scrollBy((list.layoutInfo.viewportEndOffset - list.layoutInfo.viewportStartOffset) * 0.85f * direction)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex.coerceAtMost(blocks.lastIndex) to list.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (paragraph, offset) -> currentPosition(paragraph, offset) }
    }
    LaunchedEffect(list, failedImages) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.index == blocks.lastIndex && it.offset + it.size <= list.layoutInfo.viewportEndOffset } }
            .distinctUntilChanged().collectLatest { atEnd ->
                if (atEnd && !completed && failedImages.isEmpty()) {
                    delay(2000)
                    val stillVisible = list.layoutInfo.visibleItemsInfo.any { it.index == blocks.lastIndex && it.offset + it.size <= list.layoutInfo.viewportEndOffset }
                    if (stillVisible) { completed = true; currentCompleted() }
                }
            }
    }
    LaunchedEffect(controller.visibility, showSettings, slider, interaction) {
        if (controller.visibility.bottomBar && !showSettings && slider == null) { delay(5000); controller.toggleFullVisible(false) }
    }
    val background = when (settings.theme) { 1 -> Color(0xffeee2c6); 2 -> Color(0xff17191c); else -> MaterialTheme.colorScheme.surface }
    val foreground = when (settings.theme) { 1 -> Color(0xff332d22); 2 -> Color(0xffcecece); else -> MaterialTheme.colorScheme.onSurface }
    Box(modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent {
        if (it.type != KeyEventType.KeyDown) false else when (it.key) {
            Key.F -> { onFullscreen(!fullscreen); true }
            Key.Escape -> { if (fullscreen) onFullscreen(false) else onBack(); true }
            Key.Spacebar -> { toggle(); true }
            Key.PageDown, Key.DirectionDown -> { move(1); true }
            Key.PageUp, Key.DirectionUp -> { move(-1); true }
            else -> false
        }
    }.focusable().testTag("novel-reader")) {
        VideoScaffold(expanded = true, maintainAspectRatio = false, controllerState = controller,
            modifier = Modifier.fillMaxSize(), contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回小说详情") }
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onChapters) { Text("目录") }
                TextButton(onSources) { Text("书源") }
                IconButton({ showSettings = true }, Modifier.testTag("novel-settings")) { Icon(Icons.Rounded.Settings, "阅读设置") }
                IconButton({ onFullscreen(!fullscreen) }, Modifier.testTag("novel-fullscreen")) {
                    Icon(if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, "全屏")
                }
            }, video = {
                BoxWithConstraints(Modifier.fillMaxSize().background(background)
                    .pointerInput(controller, fullscreen, list) { detectTapGestures(onTap = { position ->
                        if (fullscreen && (position.x < size.width / 3f || position.x > size.width * 2f / 3f)) {
                            controller.toggleFullVisible(false)
                            move(1)
                        } else toggle()
                    }) }
                    .semantics { onClick("显示或隐藏阅读控制栏") { toggle(); true } }) {
                    LazyColumn(state = list, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("novel-reader-scroll"),
                        contentPadding = PaddingValues(horizontal = if (maxWidth > 700.dp) (maxWidth - 660.dp) / 2 else 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(blocks) { index, block ->
                            if (block.image != null) AsyncImage(block.image, "插图", Modifier.fillMaxWidth().heightIn(min = 160.dp),
                                onError = { failedImages = failedImages + index }, onSuccess = { failedImages = failedImages - index })
                            else Text(block.text, color = foreground, fontSize = settings.fontSize.sp,
                                lineHeight = (settings.fontSize * settings.lineHeight).sp, modifier = Modifier.fillMaxWidth().testTag("novel-paragraph-$index"))
                        }
                        item {
                            Box(Modifier.fillMaxWidth().height(160.dp).testTag("novel-chapter-end").then(if (onNext != null) Modifier.clickable(onClick = onNext) else Modifier), contentAlignment = Alignment.Center) {
                                Text(if (onNext != null) "本章结束 · 点击阅读下一章" else "已到最后一章", color = foreground)
                            }
                        }
                    }
                }
            }, bottomBar = {
                Column(Modifier.padding(horizontal = 12.dp).testTag("novel-reader-controls")) {
                    if (blocks.size > 1) Slider(slider ?: list.firstVisibleItemIndex.coerceAtMost(blocks.lastIndex).toFloat(),
                        { slider = it; interaction++ }, valueRange = 0f..blocks.lastIndex.toFloat(),
                        onValueChangeFinished = { val target = slider?.toInt(); slider = null; if (target != null) scope.launch { list.scrollToItem(target) } })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ onPrevious?.invoke() }, enabled = onPrevious != null) { Text("上一章") }
                        TextButton({ move(-1) }) { Text("上页") }
                        Text("${((list.firstVisibleItemIndex + 1) * 100 / blocks.size).coerceAtMost(100)}%", Modifier.align(Alignment.CenterVertically))
                        TextButton({ move(1) }) { Text("下页") }
                        TextButton({ onNext?.invoke() }, enabled = onNext != null) { Text("下一章") }
                    }
                }
            })
    }
    if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("阅读设置") }, text = {
        Column {
            Text("字号：${settings.fontSize}")
            Slider(settings.fontSize.toFloat(), { onSettings(settings.copy(fontSize = it.toInt())) }, valueRange = 12f..40f, steps = 27)
            Text("行距")
            Slider(settings.lineHeight, { onSettings(settings.copy(lineHeight = it)) }, valueRange = 1.2f..2.4f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("跟随主题", "纸色", "夜间").forEachIndexed { index, label ->
                FilterChip(settings.theme == index, { onSettings(settings.copy(theme = index)) }, label = { Text(label) })
            } }
        }
    }, confirmButton = { TextButton({ showSettings = false }) { Text("完成") } })
}
