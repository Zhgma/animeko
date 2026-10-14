/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.domain.search.SearchSort

const val BOOK_SEARCH_PAGE_SIZE = 20

fun bookSearchBody(keyword: String, sort: SearchSort, nsfw: Boolean? = null): String = buildJsonObject {
    put("keyword", keyword.trim())
    put("sort", when (sort) {
        SearchSort.MATCH -> "match"
        SearchSort.RANK -> "rank"
        SearchSort.COLLECTION -> "heat"
        SearchSort.DATE -> error("Bangumi book search does not support date sorting")
    })
    put("filter", buildJsonObject {
        put("type", buildJsonArray { add(JsonPrimitive(1)) })
        nsfw?.let { put("nsfw", it) }
    })
}.toString()
