@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

external fun aniRelayRead(): String
external fun aniRelaySave(manual: String, subscription: String): String
external fun aniRelayRefresh(callback: (String) -> Unit)

@Composable
fun WebRelaySettings() {
    val initial = remember { webJson.parseToJsonElement(aniRelayRead()).jsonObject }
    var manual by remember { mutableStateOf(initial.getValue("manualOrigin").jsonPrimitive.content) }
    var subscription by remember { mutableStateOf(initial.getValue("subscriptionUrl").jsonPrimitive.content) }
    var current by remember { mutableStateOf(initial.getValue("effectiveOrigin").jsonPrimitive.content) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun saved(): Boolean {
        message = aniRelaySave(manual, subscription)
        if (message.isNotEmpty()) return false
        current = webJson.parseToJsonElement(aniRelayRead()).jsonObject.getValue("effectiveOrigin").jsonPrimitive.content
        message = "已生效，返回页面刷新即可"
        return true
    }
    Column {
        Text("当前中转：$current")
        Text("手动地址留空时跟随配置订阅。账号授权会发送到所选服务器，请使用可信地址。")
        OutlinedTextField(manual, { manual = it }, enabled = !busy, singleLine = true,
            label = { Text("手动地址（留空跟随订阅）") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(subscription, { subscription = it }, enabled = !busy, singleLine = true,
            label = { Text("API 配置订阅链接") }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { saved() }, enabled = !busy) { Text("保存中转设置") }
        TextButton(onClick = {
            if (saved()) {
                busy = true
                aniRelayRefresh { error ->
                    busy = false
                    current = webJson.parseToJsonElement(aniRelayRead()).jsonObject.getValue("effectiveOrigin").jsonPrimitive.content
                    message = error.ifBlank { "订阅已更新，手动地址优先" }
                }
            }
        }, enabled = !busy) { Text(if (busy) "更新中…" else "立即刷新配置订阅") }
        if (message.isNotEmpty()) Text(message)
    }
}
