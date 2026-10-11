/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType

/** Shared category loading and error recovery; each platform supplies its authenticated transport. */
class MangaCollectionController(
    private val load: suspend (String, MangaCollectionType, (String, List<MangaCollection>) -> Unit) -> Unit,
    private val isAuthorizationError: (Exception) -> Boolean,
) {
    private val mutableState = MutableStateFlow(MangaCollectionUiState(needsLogin = true))
    val state = mutableState.asStateFlow()
    private var previousSession: String? = null

    fun select(type: MangaCollectionType) { mutableState.update { it.copy(type = type) } }

    /** Callers cancel and join the previous load before changing sessions or refreshing. */
    suspend fun refresh(session: String?) {
        val cachedPages = if (session != null && session == previousSession) mutableState.value.pages else emptyMap()
        previousSession = session
        mutableState.update { it.copy(pages = cachedPages, username = null, needsLogin = session == null) }
        if (session == null) return
        val selected = mutableState.value.type
        for (type in listOf(selected) + MangaCollectionType.entries.filter { it != selected }) {
            updatePage(type) { it.copy(loading = true, failed = false) }
            try {
                load(session, type) { username, items ->
                    mutableState.update { it.copy(username = username) }
                    updatePage(type) { it.copy(items = items) }
                }
                updatePage(type) { it.copy(loading = false, count = it.items.size) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (isAuthorizationError(e)) {
                    mutableState.update { MangaCollectionUiState(type = it.type, needsLogin = true) }
                    return
                }
                updatePage(type) { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun updatePage(type: MangaCollectionType, transform: (MangaCollectionPageState) -> MangaCollectionPageState) {
        mutableState.update { it.copy(pages = it.pages + (type to transform(it.page(type)))) }
    }
}
