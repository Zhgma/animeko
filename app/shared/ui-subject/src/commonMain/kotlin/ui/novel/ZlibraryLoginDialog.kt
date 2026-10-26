/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.novel.ZlibraryApi
import me.him188.ani.app.data.repository.novel.ZlibraryLoginRequired
import me.him188.ani.app.data.repository.novel.ZlibrarySession
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory

internal class ZlibraryLoginRequest(val origin: String, val result: CompletableDeferred<Unit> = CompletableDeferred())

internal class ZlibraryLoginController(val api: ZlibraryApi, val browsers: CaptchaBrowserFactory) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<ZlibraryLoginRequest?>(null)
    val pending = mutable.asStateFlow()
    suspend fun login(origin: String) = mutex.withLock {
        check(browsers.isSupported) { "当前平台暂不支持 Z-Library 网页登录" }
        val request = ZlibraryLoginRequest(origin)
        mutable.value = request
        try { request.result.await() } finally { mutable.value = null }
    }
    suspend fun <T> retryWithLogin(block: suspend () -> T): T {
        return try { block() } catch (e: ZlibraryLoginRequired) {
            login(e.origin)
            block()
        }
    }
}

/** Only the selected site's session cookies are read; form inputs and passwords remain inside the browser. */
@Composable
internal fun ZlibraryLoginDialog(controller: ZlibraryLoginController) {
    val request by controller.pending.collectAsState()
    val current = request ?: return
    key(current) {
        var browser by remember { mutableStateOf<CaptchaBrowser?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        LaunchedEffect(current, refresh) {
            var opened: CaptchaBrowser? = null
            try {
                val b = controller.browsers.create(); opened = b; browser = b
                b.navigate(current.origin + "/login")
                var rejected: String? = null
                while (!current.result.isCompleted) {
                    delay(1000)
                    val cookies = b.collectCookies(listOf(current.origin)).associate { it.name to it.value }
                    val id = cookies["remix_userid"] ?: continue
                    val secret = cookies["remix_userkey"] ?: continue
                    val session = ZlibrarySession(current.origin, id, secret, b.userAgent)
                    if (!session.isValid() || rejected == "$id:$secret") continue
                    try {
                        controller.api.accept(session)
                        current.result.complete(Unit)
                    } catch (e: CancellationException) { throw e }
                    catch (e: ZlibraryLoginRequired) {
                        rejected = "$id:$secret"
                        error = "登录尚未完成，请在网页中重新登录"
                    } catch (e: Exception) {
                        error = "暂时无法验证登录，请检查网络或刷新重试"
                        delay(4000)
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = "登录页面加载失败，请刷新重试" }
            finally { browser = null; opened?.close() }
        }
        Dialog(onDismissRequest = { current.result.completeExceptionally(IllegalStateException("已取消 Z-Library 登录")) },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
            Surface(Modifier.fillMaxSize().testTag("zlibrary-login")) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ current.result.completeExceptionally(IllegalStateException("已取消 Z-Library 登录")) }) { Text("返回") }
                        Text("登录 Z-Library", modifier = Modifier.padding(12.dp))
                        TextButton({ error = null; refresh++ }) { Text("刷新") }
                    }
                    Text(current.origin, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    Text("登录成功后会自动返回并保存登录状态", Modifier.padding(16.dp))
                    error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                    val active = browser
                    if (active == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else Box(Modifier.weight(1f).fillMaxWidth()) { active.View(Modifier.fillMaxSize()) }
                }
            }
        }
    }
}
