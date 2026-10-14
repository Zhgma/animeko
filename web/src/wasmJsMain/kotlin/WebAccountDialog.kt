import me.him188.ani.app.ui.settings.SettingsTab
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import me.him188.ani.app.ui.settings.account.AccountLogoutDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.domain.session.auth.awaitOAuthResult
import me.him188.ani.app.ui.login.EmailLoginStartScreenImpl
import me.him188.ani.app.ui.login.EmailLoginVerifyScreenImpl
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

/** Native account content with browser transport and popup lifecycle. */
@Composable
internal fun AccountScreen(session: WebResult, onBack: () -> Unit, onSettings: () -> Unit, onSession: (WebResult) -> Unit) {
    var email by remember { mutableStateOf("") }
    var otpId by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf<Boolean?>(null) }
    var nextResend by remember { mutableStateOf(Instant.DISTANT_PAST) }
    var providers by remember { mutableStateOf(emptyList<OAuthPlatform>()) }
    var providerError by remember { mutableStateOf<String?>(null) }
    var providerRetry by remember { mutableIntStateOf(0) }
    var logout by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var oauth by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbars.showSnackbar(it); error = null } }
    DisposableEffect(Unit) { onDispose { aniAccountCancel() } }
    LaunchedEffect(providerRetry) {
        providerError = null
        try { providers = request("providers").providers.mapNotNull(OAuthPlatform::fromId) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { providerError = e.message ?: "无法读取登录方式" }
    }
    fun runAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = scope.launch {
            try { block() }
            catch (e: TimeoutCancellationException) { error = "授权等待超时，请重试。" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "请求失败" }
            finally { busy = false; oauth = false; aniOAuthCancel() }
        }
    }
    fun authorize(provider: OAuthPlatform) {
        if (busy) return
        if (!aniOAuthPrepare(provider.id, session.signedIn)) { error = "请允许本站打开授权窗口"; return }
        oauth = true
        runAction {
            request("oauthStart")
            val result = withTimeout(5 * 60 * 1000L) { awaitOAuthResult { request("oauthResult").takeUnless { it.pending } } }
            onSession(result); onBack()
        }
    }
    fun sendOtp(value: String) = runAction {
        if (Clock.System.now() < nextResend) { error = "请稍后重新发送"; return@runAction }
        email = value.trim()
        val result = request("emailOtp", buildJsonObject { put("email", email) }.toString())
        otpId = result.otpId; existing = result.hasExistingUser; nextResend = Clock.System.now() + 30.seconds
    }
    Box(Modifier.fillMaxSize()) {
        if (session.signedIn) {
            Scaffold(topBar = { TopAppBar(title = { Text(stringResource(Lang.settings_account_settings)) }, navigationIcon = { BackNavigationIconButton(onBack) }) }) { padding ->
                SettingsTab(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    WebAccountProfile(session, providers, { authorize(it) }, { logout = true }, !busy, onSession)
                }
            }
        } else if (otpId.isEmpty()) {
            EmailLoginStartScreenImpl(email, ::sendOtp, ::authorize, onSettings, onBack, enabled = !busy, thirdPartyPlatforms = providers)
        } else {
            EmailLoginVerifyScreenImpl(email, nextResend, existing,
                onCodeSubmit = { code -> runAction {
                    val result = request("emailLogin", buildJsonObject { put("otpId", otpId); put("otpValue", code) }.toString())
                    onSession(result); onBack()
                } },
                onResendClick = { sendOtp(email) }, onThirdPartyLoginClick = ::authorize,
                onNavigateSettings = onSettings, onNavigateBack = { otpId = "" }, enabled = !busy, thirdPartyPlatforms = providers)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        Column(Modifier.align(Alignment.BottomCenter)) {
            providerError?.let { message ->
                Snackbar(action = { TextButton({ providerRetry++ }) { Text("重试") } }) { Text(message) }
            }
            SnackbarHost(snackbars)
        }
    }
    if (logout) AccountLogoutDialog({ aniLogout(); onSession(WebResult()); onBack() }, { logout = false })
    if (oauth) AlertDialog(onDismissRequest = { job?.cancel(); aniOAuthCancel() },
        title = { Text("正在等待授权") }, text = { Text("请在授权页面完成登录，完成后将自动返回。") },
        confirmButton = { TextButton(onClick = { aniOAuthReopen() }) { Text("查看授权页面") } },
        dismissButton = { TextButton(onClick = { job?.cancel(); aniOAuthCancel() }) { Text("取消") } })
}
