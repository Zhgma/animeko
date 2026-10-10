/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.BangumiMangaRepository
import me.him188.ani.app.data.repository.manga.MangaAuthorizationException
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

data class MangaCollectionUiState(
    val type: MangaCollectionType = MangaCollectionType.DOING,
    val items: List<MangaCollection> = emptyList(),
    val username: String? = null,
    val loading: Boolean = false,
    val needsLogin: Boolean = false,
    val failed: Boolean = false,
)

class MangaCollectionViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager = get<SessionManager>()
    private val repository = BangumiMangaRepository(get<HttpClientProvider>().get())
    private data class Query(val type: MangaCollectionType = MangaCollectionType.DOING, val revision: Int = 0)
    private val query = MutableStateFlow(Query())
    private val mutableState = MutableStateFlow(MangaCollectionUiState(loading = true))
    val state = mutableState.asStateFlow()

    init {
        backgroundScope.launch {
            combine(sessionManager.sessionFlow, query) { session, request -> session to request }
                .collectLatest { (session, request) ->
                    // A new session cancels outstanding requests and discards the previous account's data.
                    val token = (session as? AccessTokenSession)?.tokens?.bangumiAccessToken
                    mutableState.value = MangaCollectionUiState(type = request.type, needsLogin = token == null)
                    if (token == null) return@collectLatest
                    mutableState.update { it.copy(loading = true) }
                    try {
                        repository.loadCollections(token, request.type) { username, items ->
                            mutableState.update { it.copy(username = username, items = items) }
                        }
                        mutableState.update { it.copy(loading = false) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: MangaAuthorizationException) {
                        mutableState.value = MangaCollectionUiState(type = request.type, needsLogin = true)
                    } catch (e: Exception) {
                        mutableState.update { it.copy(loading = false, failed = true) }
                    }
                }
        }
    }

    fun select(type: MangaCollectionType) {
        query.update { it.copy(type = type) }
    }

    fun refresh() {
        query.update { it.copy(revision = it.revision + 1) }
    }
}
