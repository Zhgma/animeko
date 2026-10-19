/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.domain.session

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.user.AccessTokenSession
import me.him188.ani.app.data.repository.user.TokenRepository
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.app.domain.session.auth.OAuthResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BangumiSessionRefreshTest {
    private fun store() = MemoryDataStore(TokenSave("refresh-old", TokenSave.AccessTokens("bgm-old", "ani-old", Long.MAX_VALUE)))
    private fun result(bgm: String? = "bgm-new") = OAuthResult(AccessTokenPair("ani-new", Long.MAX_VALUE, bgm), 3600, "refresh-new")

    @Test fun sessionObserverCancellationDoesNotLoseRotatedRefreshToken() = runTest {
        val backing = store()
        lateinit var refresh: Deferred<String?>
        val dataStore = object : DataStore<TokenSave> {
            override val data = backing.data
            override suspend fun updateData(transform: suspend (TokenSave) -> TokenSave): TokenSave {
                val saved = backing.updateData(transform)
                if (saved.accessTokens?.aniAccessToken == "ani-new") {
                    refresh.cancel()
                    yield()
                }
                return saved
            }
        }
        val manager = SessionManager(TokenRepository(dataStore), backgroundScope, SessionManager.SessionRefresher { result() })
        refresh = async { manager.refreshBangumiAccessToken("bgm-old") }
        refresh.join()
        assertEquals("refresh-new", backing.data.value.refreshToken)
        assertEquals("bgm-new", backing.data.value.accessTokens?.bangumiAccessToken)
    }

    @Test fun concurrentRejectionsShareRefreshAndPersistBothCredentials() = runTest {
        val store = store()
        val repository = TokenRepository(store)
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val manager = SessionManager(repository, backgroundScope, SessionManager.SessionRefresher {
            calls++
            assertEquals("refresh-old", it)
            entered.complete(Unit)
            finish.await()
            result()
        })
        val first = async { manager.refreshBangumiAccessToken("bgm-old") }
        entered.await()
        val second = async { manager.refreshBangumiAccessToken("bgm-old") }
        finish.complete(Unit)
        assertEquals("bgm-new", first.await())
        assertEquals("bgm-new", second.await())
        assertEquals(1, calls)
        assertEquals("refresh-new", repository.refreshToken.first())
        assertEquals("ani-new", store.data.value.accessTokens?.aniAccessToken)
        assertNull(manager.refreshBangumiAccessToken("bgm-new"))
        assertEquals(1, calls)
    }

    @Test fun accountChangedDuringRefreshIsNotOverwritten() = runTest {
        val repository = TokenRepository(store())
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val manager = SessionManager(repository, backgroundScope, SessionManager.SessionRefresher {
            entered.complete(Unit)
            finish.await()
            result()
        })
        val refresh = async { manager.refreshBangumiAccessToken("bgm-old") }
        entered.await()
        val other = AccessTokenSession(AccessTokenPair("ani-other", Long.MAX_VALUE, "bgm-other"))
        manager.setSession(other, "refresh-other", isNewLogin = false)
        finish.complete(Unit)
        assertNull(refresh.await())
        assertEquals(other, repository.session.first())
        assertEquals("refresh-other", repository.refreshToken.first())
        assertNull(manager.refreshBangumiAccessToken("bgm-old"))
    }

    @Test fun absentBangumiAuthorizationIsNotReplacedWithAniToken() = runTest {
        val repository = TokenRepository(store())
        val manager = SessionManager(repository, backgroundScope, SessionManager.SessionRefresher { result(null) })
        assertNull(manager.refreshBangumiAccessToken("bgm-old"))
        assertNull((repository.session.first() as AccessTokenSession).tokens.bangumiAccessToken)
        assertEquals("refresh-new", repository.refreshToken.first())
    }
}
