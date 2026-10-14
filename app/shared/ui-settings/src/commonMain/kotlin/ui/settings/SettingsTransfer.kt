/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings

/** Cross-platform backup envelope. Opaque platform settings and integer precision are preserved. */
object SettingsTransfer {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // Nullable fields without defaults in SettingsBackup must be present for native decoding.
    val nativeFields = setOf(
        "danmakuEnabled", "danmakuConfig", "danmakuFilterConfig", "danmakuRegexFilters",
        "mediaSelectorSettings", "defaultMediaPreference", "profileSettings", "proxySettings",
        "mediaCacheSettings", "danmakuSettings", "uiSettings", "themeSettings", "updateSettings",
        "videoScaffoldConfig", "playerKernelConfig", "videoResolverSettings", "anitorrentConfig",
        "torrentPeerConfig", "oneshotActionConfig", "analyticsSettings", "debugSettings", "pikpakConfig", "tokenStore",
    )

    fun parse(text: String): JsonObject {
        require(text.length <= 2 * 1024 * 1024) { "配置超过 2 MiB" }
        val element = try {
            json.parseToJsonElement(text.trim().removePrefix("\uFEFF").trim())
        } catch (_: IllegalArgumentException) {
            // Serialization errors can contain source excerpts, including account credentials.
            error("配置不是完整的 JSON，请重新复制 App 的应用设置备份")
        }
        val root = element as? JsonObject
            ?: error("需要 App 设置备份 JSON 对象")
        require(root.keys.any { it in nativeFields }) {
            if ("mediaSources" in root || "exportedMediaSourceDataList" in root) {
                "当前内容是数据源配置，请在数据源页面导入；恢复设置请复制 App 的应用设置备份"
            } else {
                "当前内容不是 App 设置备份，请在 App 设置备份页重新复制"
            }
        }
        for (key in nativeFields) {
            val value = root[key] ?: continue
            if (value == JsonNull) continue
            require(when (key) {
                "danmakuEnabled" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
                "danmakuRegexFilters" -> value is JsonArray
                else -> value is JsonObject
            }) { "配置字段格式错误：$key" }
        }
        for (key in listOf("themeSettings", "uiSettings")) {
            root[key]?.takeUnless { it == JsonNull }?.let {
                try {
                    if (key == "themeSettings") json.decodeFromJsonElement<ThemeSettings>(it)
                    else json.decodeFromJsonElement<UISettings>(it)
                } catch (_: IllegalArgumentException) {
                    error("配置字段无法读取：$key，请检查 App 与网页版本是否兼容")
                }
            }
        }
        return JsonObject(root.toMutableMap().apply {
            // Credentials belong to each platform's login session, not transferable settings.
            put("tokenStore", JsonNull)
            put("pikpakConfig", JsonNull)
            if (root["version"] == JsonPrimitive(1) && root["originalTitle"] is JsonPrimitive) {
                val original = root["originalTitle"]!!.jsonBoolean()
                val ui = get("uiSettings") as? JsonObject ?: JsonObject(emptyMap())
                put("uiSettings", merge(ui, JsonObject(mapOf("subjectAppearance" to JsonObject(mapOf("useOriginalTitle" to JsonPrimitive(original)))))))
            }
        })
    }

    fun export(retained: JsonObject, theme: ThemeSettings, ui: UISettings, web: JsonObject): String {
        val fields = nativeFields.associateWith { JsonNull as JsonElement }.toMutableMap()
        fields.putAll(retained)
        fields["themeSettings"] = merge(retained["themeSettings"], json.encodeToJsonElement(theme))
        fields["uiSettings"] = merge(retained["uiSettings"], json.encodeToJsonElement(ui))
        fields["animecomicWebSettings"] = web
        fields["tokenStore"] = JsonNull
        fields["pikpakConfig"] = JsonNull
        fields.remove("originalTitle")
        fields.remove("mediaSettings")
        return JsonObject(fields).toString()
    }

    private fun JsonElement.jsonBoolean(): Boolean = (this as JsonPrimitive).booleanOrNull
        ?: error("originalTitle 必须是布尔值")

    private fun merge(original: JsonElement?, current: JsonElement): JsonElement {
        if (original !is JsonObject || current !is JsonObject) return current
        return JsonObject(original.toMutableMap().apply {
            current.forEach { (key, value) -> put(key, merge(original[key], value)) }
        })
    }
}
