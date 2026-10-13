/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Preserve signed paths from migrated API responses and persisted reader records. */
internal fun migrateMangaApiImageUrl(url: String): String {
    val oldOrigin = "https://119.28.149.19:8443"
    return if (listOf("books", "komiic").any { url.startsWith("$oldOrigin/api/$it/images/") }) {
        "https://43.131.255.74:8443" + url.removePrefix(oldOrigin)
    } else url
}

/** Only image fields are migrated; review text and user-provided links stay intact. */
internal fun migrateMangaApiImages(body: String): String {
    if (!body.contains("119.28.149.19")) return body
    fun visit(value: JsonElement, imageField: Boolean = false): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            visit(child, imageField || key in setOf("images", "avatar", "imageUrl"))
        })
        is JsonArray -> JsonArray(value.map { visit(it, imageField) })
        is JsonPrimitive -> if (imageField && value.isString) JsonPrimitive(migrateMangaApiImageUrl(value.content)) else value
    }
    return runCatching { visit(Json.parseToJsonElement(body)).toString() }.getOrDefault(body)
}
