/*
 * Copyright (C) 2026 OpenAni and contributors.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package me.him188.ani.app.ui.manga

import me.him188.ani.app.data.repository.manga.MangaBangumiNetwork
import me.him188.ani.app.data.repository.manga.MangaLibraryChanges
import kotlinx.coroutines.flow.MutableStateFlow
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

class MangaCollectionViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager = get<SessionManager>()
    private val repository = BangumiMangaRepository(get<MangaBangumiNetwork>().http)
    private val revision = MutableStateFlow(0)
    private val controller = MangaCollectionController(
        load = { token, type, onPage -> repository.loadCollections(token, type, onPage) },
        isAuthorizationError = { it is MangaAuthorizationException },
    )
    val state = controller.state
    val usingCache = get<MangaBangumiNetwork>().cache.usingCache

    init {
        backgroundScope.launch {
            combine(sessionManager.sessionFlow, revision, MangaLibraryChanges.revision) { session, _, _ -> session }
                .collectLatest { session ->
                    controller.refresh((session as? AccessTokenSession)?.tokens?.bangumiAccessToken)
                }
        }
    }

    fun select(type: MangaCollectionType) { controller.select(type) }
    fun refresh() { backgroundScope.launch { get<MangaBangumiNetwork>().cache.retry(); revision.update { it + 1 } } }
}
