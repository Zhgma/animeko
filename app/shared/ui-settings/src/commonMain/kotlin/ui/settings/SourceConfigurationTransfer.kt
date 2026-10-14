/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** The clipboard envelope accepted by MediaSourceCodecManager on native clients. */
fun combineSourceConfigurations(configurations: List<String>): String {
    val sources = configurations.flatMap { text ->
        val root = Json.parseToJsonElement(text) as? JsonObject ?: error("数据源配置必须是 JSON 对象")
        (root["mediaSources"] ?: (root["exportedMediaSourceDataList"] as? JsonObject)?.get("mediaSources")) as? JsonArray
            ?: error("配置缺少 mediaSources")
    }
    require(sources.size <= 300) { "单次最多复制 300 个数据源，请分订阅复制" }
    val compatible = sources.map { source ->
        if (source is JsonObject && source["factoryId"] == JsonPrimitive("rss") && source["version"] == JsonPrimitive(1)) {
            val arguments = source["arguments"] as? JsonObject ?: error("RSS 配置缺少 arguments")
            JsonObject(source + ("arguments" to JsonObject(mapOf(
                "name" to JsonPrimitive("RSS"), "description" to JsonPrimitive(""), "iconUrl" to JsonPrimitive(""),
            ) + arguments)))
        } else source
    }
    return JsonObject(mapOf("mediaSources" to JsonArray(compatible))).toString()
}
