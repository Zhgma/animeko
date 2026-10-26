/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.novel.*
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.captcha.*
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.test.*

@OptIn(TestOnly::class)
class ZlibraryLoginDialogTest {
    @Test fun siteLoginAutomaticallyValidatesCookiesClosesDialogAndResumesSearch() = runAniComposeUiTest {
        val prefs = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = transform(data.value).also { data.value = it }
        }
        var validated = false
        val client = HttpClient(MockEngine { req ->
            assertEquals("login.test", req.url.host)
            assertEquals("/eapi/user/profile", req.url.encodedPath)
            assertContains(req.headers["Cookie"].orEmpty(), "remix_userkey=test_key")
            validated = true
            respond("""{"success":1,"user":{"id":123}}""")
        })
        val browser = FakeLoginBrowser()
        val factory = object : CaptchaBrowserFactory {
            override val isSupported = true
            override suspend fun create(): CaptchaBrowser = browser
        }
        val controller = ZlibraryLoginController(ZlibraryApi(client.asScopedHttpClient(), ZlibrarySessionStore(prefs)), factory)
        var attempts = 0
        var resumed = false
        try {
            setContent { ProvideCompositionLocalsForPreview {
                val scope = rememberCoroutineScope()
                Button({ scope.launch {
                    controller.retryWithLogin {
                        if (++attempts == 1) throw ZlibraryLoginRequired("https://login.test")
                        resumed = true
                    }
                } }) { Text("连接 Z-Library") }
                ZlibraryLoginDialog(controller)
            } }
            onNodeWithText("连接 Z-Library").performClick()
            waitUntil(timeoutMillis = 5000) { browser.navigated != null }
            onNodeWithTag("zlibrary-login").assertExists()
            assertEquals("https://login.test/login", browser.navigated)
            assertFalse(validated)
            onNodeWithText("模拟站点登录成功").performClick()
            waitUntil(timeoutMillis = 5000) { resumed && browser.closed }
            onNodeWithTag("zlibrary-login").assertDoesNotExist()
            assertTrue(validated)
            assertEquals(2, attempts)
            assertTrue(prefs.data.value.asMap().values.any { "test_key" in it.toString() })
        } finally { client.close() }
    }

    private class FakeLoginBrowser : CaptchaBrowser {
        override val userAgent = "Test browser"
        override val pageLoads = MutableSharedFlow<LoadedPage>()
        override val isLoading = MutableStateFlow(false)
        var navigated: String? = null
        var closed = false
        var loggedIn = false
        override suspend fun navigate(url: String) { navigated = url }
        override suspend fun currentPage(): LoadedPage? = null
        override suspend fun executeJavaScript(script: String) = Unit
        override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> {
            assertEquals(listOf("https://login.test"), urls)
            return if (loggedIn) listOf(BrowserCookie("remix_userid", "123"), BrowserCookie("remix_userkey", "test_key")) else emptyList()
        }
        override fun setResourceInterceptor(handler: ((String) -> InterceptDecision)?) = Unit
        @Composable override fun View(modifier: Modifier) {
            Button({ loggedIn = true }, modifier) { Text("模拟站点登录成功") }
        }
        override fun close() { closed = true }
    }
}
