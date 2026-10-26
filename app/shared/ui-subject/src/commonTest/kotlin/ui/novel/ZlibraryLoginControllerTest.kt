/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*

class ZlibraryLoginControllerTest {
    private fun preferences() = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
    }
    private val browsers = object : CaptchaBrowserFactory {
        override val isSupported = true
        override suspend fun create(): CaptchaBrowser = error("UI creates the browser")
    }
    @Test fun successfulLoginResumesOriginalOperationExactlyOnce() = runTest {
        val client = HttpClient(MockEngine { error("No network expected") })
        try {
            val controller = ZlibraryLoginController(ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(preferences())), browsers)
            var attempts = 0
            val task = async { controller.retryWithLogin { if (++attempts == 1) throw ZlibraryLoginRequired("https://zlib.test"); "result" } }
            runCurrent()
            assertEquals("https://zlib.test", controller.pending.value?.origin)
            assertEquals(1, attempts)
            controller.pending.value!!.result.complete(Unit)
            assertEquals("result", task.await())
            assertEquals(2, attempts)
            assertNull(controller.pending.value)
        } finally { client.close() }
    }
    @Test fun cancellingOperationDismissesLoginAndDoesNotRetry() = runTest {
        val client = HttpClient(MockEngine { error("No network expected") })
        try {
            val controller = ZlibraryLoginController(ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(preferences())), browsers)
            var attempts = 0
            val task = async { controller.retryWithLogin { attempts++; throw ZlibraryLoginRequired("https://zlib.test") } }
            runCurrent(); assertNotNull(controller.pending.value)
            task.cancelAndJoin()
            assertNull(controller.pending.value)
            assertEquals(1, attempts)
        } finally { client.close() }
    }
    @Test fun searchChallengeOpensWebVerificationAndRetriesOnce() = runTest {
        val client = HttpClient(MockEngine { error("No network expected") })
        try {
            val controller = ZlibraryLoginController(ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(preferences())), browsers)
            var attempts = 0
            val task = async { controller.retryWithLogin { if (++attempts == 1) throw ZlibraryVerificationRequired("https://zlib.test"); "books" } }
            runCurrent()
            assertTrue(controller.pending.value!!.verification)
            controller.pending.value!!.result.complete(Unit)
            assertEquals("books", task.await())
            assertEquals(2, attempts)
            assertNull(controller.pending.value)
        } finally { client.close() }
    }
}
