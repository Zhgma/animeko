// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.ui.mediaselect.manual
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.lang.*
import me.him188.ani.datasources.api.source.BrowseSubject
import org.jetbrains.compose.resources.stringResource

sealed interface ManualLoadState<out T> {
    data object Idle : ManualLoadState<Nothing>
    data object Loading : ManualLoadState<Nothing>
    data class Success<T>(val value: T) : ManualLoadState<T>

    /**
     * 限流、网络错误、验证码交互解决一次仍失败都归此; 页面显示错误文本 + 重试.
     * [captchaUnsupported] = 被验证码挡且 WebSessionManager.isInteractiveSupported == false (iOS):
     * 文案 media_selector_manual_captcha_unsupported, 无重试按钮.
     */
    data class Failed(val error: Throwable, val captchaUnsupported: Boolean = false) : ManualLoadState<Nothing>
}

/**
 * 结果列表三态 + 「共 N 条结果」标签. 调用方给 [modifier] 加 `weight(1f)`.
 * [openedSubject] 对应的行高亮 (双栏版式里表示右栏正在显示它).
 */
@Composable
fun ManualResultsList(
    results: ManualLoadState<List<BrowseSubject>>,
    openedSubject: BrowseSubject?,
    onOpen: (BrowseSubject) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (results) {
        ManualLoadState.Idle -> Box(modifier)
        ManualLoadState.Loading -> ManualLoadingBox(modifier)
        is ManualLoadState.Failed -> ManualFailedBox(results, onRetry, modifier)
        is ManualLoadState.Success -> Column(modifier) {
            ManualSectionLabel(
                stringResource(Lang.media_selector_manual_result_count, results.value.size),
                Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp),
            )
            if (results.value.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Lang.media_selector_manual_no_result),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(results.value) { index, subject ->
                        val opened = subject == openedSubject
                        ListItem(
                            headlineContent = {
                                Text(subject.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpen(subject) }
                                .testTag(ManualBrowsePageTestTags.result(index)),
                            trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                            colors = ListItemDefaults.colors(
                                // Unspecified 会被 ListItem 解析为主题 surface (一张浅色卡片), 透明才是跟随宿主容器的平铺行.
                                containerColor = if (opened) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ManualLoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * 错误文本 + 重试; 验证码不受支持时换文案且无重试.
 */
@Composable
internal fun ManualFailedBox(
    failed: ManualLoadState.Failed,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            if (failed.captchaUnsupported) {
                stringResource(Lang.media_selector_manual_captcha_unsupported)
            } else {
                stringResource(Lang.media_selector_load_failed)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (!failed.captchaUnsupported) {
            TextButton(onRetry, Modifier.testTag(ManualBrowsePageTestTags.RETRY)) {
                Text(stringResource(Lang.media_selector_retry))
            }
        }
    }
}
