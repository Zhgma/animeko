// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.app.data.repository.manga
import kotlinx.serialization.json.*

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.number(key: String): Int = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.array(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())
