/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.MangaBangumiNetwork
import me.him188.ani.app.data.repository.manga.RelayRoutingPolicy
import me.him188.ani.app.data.repository.manga.RelaySettingsStore
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import org.koin.mp.KoinPlatform

@Composable
internal fun SettingsScope.RelaySettingsGroup() {
    val store = remember { KoinPlatform.getKoin().get<RelaySettingsStore>() }
    val current = store.flow.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val relayEnabled by RelayRoutingPolicy.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var address by remember(current.manualOrigin) { mutableStateOf(current.manualOrigin) }
    var subscription by remember(current.subscriptionUrl) { mutableStateOf(current.subscriptionUrl) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun run(refresh: Boolean) {
        scope.launch {
            busy = true
            try {
                store.save(address, subscription)
                if (refresh) store.refresh()
                KoinPlatform.getKoin().get<MangaBangumiNetwork>().cache.retry()
                message = if (refresh && address.isNotBlank()) "订阅已更新，当前优先使用手动地址" else "已生效，返回页面刷新即可"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "更新失败，保留已保存的地址" }
            finally { busy = false }
        }
    }
    Group(title = { Text("漫画与书籍中转") },
        description = { Text("仅在服务器区域选择中国大陆且全局代理禁用（直连）时，用于 Bangumi 书籍和 Komiic。番剧、漫画和小说共用全局代理。手动地址留空时跟随配置订阅，启动后及每小时自动更新。收藏同步所需的 Bangumi 授权会发送到所选服务器，请使用可信地址。") }) {
        TextItem(title = { Text(if (relayEnabled) "中转已启用" else "中转未启用") })
        TextItem(title = { Text("当前地址") }, description = { Text(current.effectiveOrigin) })
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OutlinedTextField(address, { address = it }, enabled = !busy, singleLine = true,
                label = { Text("手动地址（留空跟随订阅）") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subscription, { subscription = it }, enabled = !busy, singleLine = true,
                label = { Text("API 配置订阅链接") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = { run(false) }, enabled = !busy) { Text("保存中转设置") }
            TextButton(onClick = { run(true) }, enabled = !busy) { Text(if (busy) "更新中…" else "立即刷新配置订阅") }
            message?.let { Text(it) }
        }
    }
}
