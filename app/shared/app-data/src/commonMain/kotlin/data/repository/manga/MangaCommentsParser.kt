/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.json.*

data class MangaComment(val id: Int, val user: String, val avatar: String, val text: String, val rating: Int,
    val username: String = "", val userId: Int = 0, val updatedAt: Long = 0)

data class MangaComments(val items: List<MangaComment>, val total: Int)

/** Parses the public Bangumi comment page for native and browser transports. */
fun parseMangaComments(raw: String): MangaComments {
        val root = Json.parseToJsonElement(raw).jsonObject
        check(root["data"] is JsonArray) { "评论接口返回格式有变" }
        return MangaComments(root.commentArray("data").map {
            val c = it.jsonObject
            val user = c.commentObj("user")
            MangaComment(c.commentNumber("id"), user.commentText("nickname").ifBlank { user.commentText("username") },
                user.commentObj("avatar").commentText("medium"), c.commentText("comment"), c.commentNumber("rate"), user.commentText("username"), user.commentNumber("id"),
                ((c["updatedAt"] as? JsonPrimitive)?.longOrNull ?: 0) * 1000)
        }, root.commentNumber("total"))
}
private fun JsonObject.commentText(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.commentNumber(key: String) = (get(key) as? JsonPrimitive)?.intOrNull ?: 0
private fun JsonObject.commentObj(key: String) = get(key) as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.commentArray(key: String) = get(key) as? JsonArray ?: JsonArray(emptyList())
