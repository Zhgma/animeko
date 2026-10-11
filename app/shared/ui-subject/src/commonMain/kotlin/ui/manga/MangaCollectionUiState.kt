/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType

data class MangaCollectionPageState(
    val items: List<MangaCollection> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val count: Int? = null,
)

data class MangaCollectionUiState(
    val type: MangaCollectionType = MangaCollectionType.DOING,
    val pages: Map<MangaCollectionType, MangaCollectionPageState> = emptyMap(),
    val username: String? = null,
    val needsLogin: Boolean = false,
)

internal fun MangaCollectionUiState.page(type: MangaCollectionType) = pages[type] ?: MangaCollectionPageState(
    loading = !needsLogin,
)
