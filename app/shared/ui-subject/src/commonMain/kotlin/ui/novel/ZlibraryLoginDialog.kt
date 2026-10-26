/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.novel

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.data.repository.novel.ZlibraryApi
import me.him188.ani.app.data.repository.novel.ZlibraryLoginRequired
import me.him188.ani.app.data.repository.novel.ZlibraryLoginFailed
import me.him188.ani.app.data.repository.novel.ZlibrarySession
import me.him188.ani.app.data.repository.novel.ZlibraryVerificationRequired
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.ui.foundation.dialogs.PlatformDialogProperties

internal class ZlibraryLoginRequest(val origin: String, val result: CompletableDeferred<Unit> = CompletableDeferred(), val verification: Boolean = false)

internal class ZlibraryLoginController(val api: ZlibraryApi, val browsers: CaptchaBrowserFactory) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<ZlibraryLoginRequest?>(null)
    val pending = mutable.asStateFlow()
    suspend fun login(origin: String, verification: Boolean = false) = mutex.withLock {
        check(!verification || browsers.isSupported) { "该平台暂不支持站点网页验证" }
        val request = ZlibraryLoginRequest(origin, verification = verification)
        mutable.value = request
        try { request.result.await() } finally { mutable.value = null }
    }
    suspend fun <T> retryWithLogin(block: suspend () -> T): T {
        return try { withVerification(block) } catch (e: ZlibraryLoginRequired) {
            login(e.origin)
            withVerification(block)
        }
    }
    private suspend fun <T> withVerification(block: suspend () -> T): T = try { block() }
    catch (e: ZlibraryVerificationRequired) { login(e.origin, verification = true); block() }
}

/** Account login persists a validated session; the password only lives in this dialog. */
@Composable
internal fun ZlibraryLoginDialog(controller: ZlibraryLoginController) {
    val request by controller.pending.collectAsState()
    val current = request ?: return
    key(current) {
        val scope = rememberCoroutineScope()
        var webLogin by remember { mutableStateOf(current.verification) }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var submitting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        Dialog(onDismissRequest = { current.result.completeExceptionally(IllegalStateException("已取消 Z-Library 登录")) },
            properties = PlatformDialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false,
                decorFitsSystemWindows = false, usePlatformInsets = false, animateTransition = false)) {
            Surface(Modifier.fillMaxSize().testTag("zlibrary-login")) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton({ current.result.completeExceptionally(IllegalStateException("已取消 Z-Library 登录")) }) { Text("返回") }
                        Text("登录 Z-Library", modifier = Modifier.padding(12.dp))
                        if (webLogin) TextButton({ refresh++ }) { Text("刷新") }
                        else Spacer(Modifier.width(64.dp))
                    }
                    Text(current.origin, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                    if (webLogin) {
                        TextButton({ webLogin = false }) { Text("使用账号登录") }
                        key(refresh) { ZlibraryWebLogin(controller, current, Modifier.weight(1f).fillMaxWidth()) }
                    } else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("使用 Z-Library 账号登录", style = MaterialTheme.typography.titleLarge)
                            Text("登录后自动保存登录状态，密码不会保存到设备。")
                            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().testTag("zlibrary-email"),
                                label = { Text("邮箱") }, singleLine = true, enabled = !submitting,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().testTag("zlibrary-password"),
                                label = { Text("密码") }, singleLine = true, enabled = !submitting,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("zlibrary-login-error")) }
                            if (submitting) LinearProgressIndicator(Modifier.fillMaxWidth())
                            Button({
                                submitting = true; error = null
                                scope.launch {
                                    try {
                                        controller.api.login(current.origin, email, password)
                                        current.result.complete(Unit)
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: ZlibraryLoginFailed) { error = e.message }
                                    catch (e: Exception) { error = "登录验证失败，请检查网络后重试，或使用网页登录" }
                                    finally { password = ""; submitting = false }
                                }
                            }, enabled = !submitting && email.isNotBlank() && password.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().testTag("zlibrary-submit")) { Text("登录") }
                            if (controller.browsers.isSupported) TextButton({ password = ""; error = null; webLogin = true },
                                enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text("使用网页登录") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZlibraryWebLogin(controller: ZlibraryLoginController, current: ZlibraryLoginRequest, modifier: Modifier) {
    var browser by remember { mutableStateOf<CaptchaBrowser?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
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
                val session = ZlibrarySession(current.origin, id, secret, b.userAgent,
                    cookies.filterKeys { it in setOf("__diamwall", "_dwa", "_dwbi", "c_token", "dwid", "bsrv") })
                if (!session.isValid() || rejected == "$id:$secret") continue
                try {
                    controller.api.accept(session, verifySearch = true)
                    current.result.complete(Unit)
                } catch (e: CancellationException) { throw e }
                catch (e: ZlibraryVerificationRequired) {
                    error = "账号已登录，搜索仍需站点网页验证；完成验证后会自动重试"
                    delay(4000)
                }
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
        finally { opened?.close() }
    }
    Column(modifier) {
        Text("登录及搜索验证通过后会自动返回并保存登录状态", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        val active = browser
        if (active == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else key(active) {
            val loading by active.isLoading.collectAsState()
            Box(Modifier.height(4.dp).fillMaxWidth()) { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { active.View(Modifier.fillMaxSize()) }
        }
    }
}
