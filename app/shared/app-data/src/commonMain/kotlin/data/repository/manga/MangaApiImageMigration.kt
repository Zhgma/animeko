/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Preserve signed paths from migrated API responses and persisted reader records. */
internal fun migrateMangaApiImageUrl(url: String): String {
    return RelayEndpoints.migrateImage(url)
}

/** Only image fields are migrated; review text and user-provided links stay intact. */
internal fun migrateMangaApiImages(body: String): String {
    fun visit(value: JsonElement, imageField: Boolean = false): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { (key, child) ->
            visit(child, imageField || key in setOf("images", "avatar", "imageUrl"))
        })
        is JsonArray -> JsonArray(value.map { visit(it, imageField) })
        is JsonPrimitive -> if (imageField && value.isString) JsonPrimitive(migrateMangaApiImageUrl(value.content)) else value
    }
    return runCatching { visit(Json.parseToJsonElement(body)).toString() }.getOrDefault(body)
}
