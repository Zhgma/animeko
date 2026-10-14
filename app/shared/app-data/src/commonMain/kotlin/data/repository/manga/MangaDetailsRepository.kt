/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.request.get
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.data.models.subject.SelfRatingInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import me.him188.ani.app.data.models.subject.RatingCounts
import me.him188.ani.app.data.models.subject.RatingInfo
import me.him188.ani.utils.ktor.ScopedHttpClient

data class MangaDetails(
    val title: String, val originalTitle: String, val cover: String, val summary: String,
    val rating: RatingInfo, val info: List<Pair<String, String>>, val tags: List<String>,
    val collections: Map<String, Int>, val aliases: List<String>,
)

data class MangaRelation(val id: Int, val type: Int, val title: String, val originalTitle: String,
    val cover: String, val relation: String)

/** Public metadata and comments are independent of the anime server and its collection cache. */
class MangaDetailsRepository(private val http: MangaBangumiHttp) {
    constructor(client: ScopedHttpClient) : this(MangaBangumiHttp(client))
    suspend fun details(id: Int, token: String?): MangaDetails {
        val root = Json.parseToJsonElement(http.get("https://api.bgm.tv/v0/subjects/$id", token).requireSuccess()).jsonObject
        check(root.number("type") == 1) { "该条目不是书籍" }
        val rating = root.obj("rating")
        val counts = rating.obj("count")
        val info = root.array("infobox").map { it.jsonObject }.map { it.text("key") to flatten(it["value"]) }
        val aliases = info.filter { it.first == "别名" }.flatMap { it.second.split(" / ") }
        return MangaDetails(
            root.text("name_cn").ifBlank { root.text("name") }, root.text("name"),
            root.obj("images").text("large"), root.text("summary"),
            RatingInfo(rating.number("rank"), rating.number("total"), RatingCounts(
                counts.number("1"), counts.number("2"), counts.number("3"), counts.number("4"), counts.number("5"),
                counts.number("6"), counts.number("7"), counts.number("8"), counts.number("9"), counts.number("10"),
            ), rating.text("score").ifBlank { "0" }), info,
            root.array("tags").take(12).map { it.jsonObject.text("name") },
            root.obj("collection").mapValues { (it.value as? JsonPrimitive)?.intOrNull ?: 0 }, aliases,
        )
    }

    suspend fun comments(id: Int, offset: Int): MangaComments = parseMangaComments(
        http.get("https://next.bgm.tv/p1/subjects/$id/comments", parameters = mapOf("limit" to 20, "offset" to offset)).requireSuccess())

    suspend fun relations(id: Int, token: String?): List<MangaRelation> {
        val response = http.get("https://api.bgm.tv/v0/subjects/$id/subjects", token).requireSuccess()
        return Json.parseToJsonElement(response).jsonArray.map { item ->
            val value = item.jsonObject
            MangaRelation(value.number("id"), value.number("type"), value.text("name_cn").ifBlank { value.text("name") },
                value.text("name"), value.obj("images").text("common"), value.text("relation"))
        }.distinctBy { it.id }.filter { it.id != id }
    }

    /** Null means an authenticated account has not collected this subject. */
    suspend fun selfRating(id: Int, token: String): SelfRatingInfo? {
        val me = http.get("https://api.bgm.tv/v0/me", token).requireSuccess()
        val username = Json.parseToJsonElement(me).jsonObject.text("username")
        check(username.isNotBlank()) { "Bangumi 用户信息为空" }
        val response = http.get("https://api.bgm.tv/v0/users/${username.encodeURLPathPart()}/collections/$id", token)
        if (response.status == 404) return null
        val root = Json.parseToJsonElement(response.requireSuccess()).jsonObject
        return SelfRatingInfo(root.number("rate"), root.text("comment"), root.array("tags").map { (it as JsonPrimitive).content },
            (root["private"] as? JsonPrimitive)?.booleanOrNull ?: false)
    }

    /** PATCH touches only the rating fields; collection status, progress and tags stay on Bangumi. */
    suspend fun rate(id: Int, token: String, rating: SelfRatingInfo) {
        require(rating.score in 0..10)
        http.write(HttpMethod.Patch, "https://api.bgm.tv/v0/users/-/collections/$id", token, buildJsonObject {
            put("rate", rating.score); put("comment", rating.comment.orEmpty()); put("private", rating.isPrivate)
        }.toString())
    }

    private fun flatten(value: JsonElement?): String = when (value) {
        is JsonPrimitive -> value.contentOrNull.orEmpty()
        is JsonArray -> value.joinToString(" / ") { flatten(it) }
        is JsonObject -> value["v"]?.let(::flatten) ?: value.values.joinToString(" / ") { flatten(it) }
        else -> ""
    }
}
