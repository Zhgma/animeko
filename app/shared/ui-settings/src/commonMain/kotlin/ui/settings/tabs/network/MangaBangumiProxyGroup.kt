/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.data.repository.manga.MangaBangumiNetwork
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import org.koin.mp.KoinPlatform

@Composable
internal fun SettingsScope.MangaBangumiProxyGroup(
    network: MangaBangumiNetwork = remember { KoinPlatform.getKoin().get<MangaBangumiNetwork>() },
) {
    val settings = network.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val current = settings.value ?: return
    val scope = rememberCoroutineScope()
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    Group(title = { Text("漫画 Bangumi 代理") },
        description = { Text("仅用于漫画收藏、搜索、详情、评价和进度。漫画订阅源及阅读图片使用原网络设置。无法连接时使用已保存的数据继续选源阅读。") },
        actions = {
            TextButton(enabled = !testing, onClick = {
                scope.launch {
                    testing = true
                    result = null
                    try { network.http.probe(); result = "Bangumi 数据与评论连接成功" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { result = "连接失败，请检查代理地址及端口；已有缓存仍可用于阅读" }
                    finally { testing = false }
                }
            }) { Text(if (testing) "测试中…" else "测试连接") }
        }) {
        ProxyMode.entries.forEach { mode ->
            val selectMode = { scope.launch {
                network.settings.save(current.copy(default = current.default.copy(mode = mode)))
                network.cache.retry()
                result = null
            }; Unit }
            TextItem(onClick = selectMode, title = { Text(when (mode) {
                ProxyMode.DISABLED -> "直连"
                ProxyMode.SYSTEM -> "使用系统代理"
                ProxyMode.CUSTOM -> "自定义代理"
            }) }, icon = {
                RadioButton(current.default.mode == mode,
                    onClick = selectMode)
            })
        }
        if (current.default.mode == ProxyMode.CUSTOM) {
            val customState = remember(current) {
                SettingsState(mutableStateOf(current), onUpdate = { value: ProxySettings ->
                    network.settings.save(value); network.cache.retry()
                }, placeholder = ProxySettings.Disabled, backgroundScope = scope)
            }
            CustomProxyConfig(current, customState)
        }
        result?.let { TextItem(title = { Text(it) }) }
    }
}
