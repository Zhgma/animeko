import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import io.ktor.client.plugins.ResponseException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isHeightAtLeastExpanded
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_logout
import me.him188.ani.app.ui.lang.settings_account_profile_nickname
import me.him188.ani.app.ui.lang.settings_account_profile_nickname_hint
import me.him188.ani.app.ui.lang.settings_account_profile_email
import me.him188.ani.app.ui.lang.settings_account_profile_third_party_accounts
import me.him188.ani.app.ui.settings.account.ProfileLinkedAccountItem
import me.him188.ani.app.ui.settings.account.ProfileUserIdItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.framework.components.TextFieldItem
import me.him188.ani.app.ui.settings.account.isValidProfileNickname
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.WebAccountProfile(
    session: WebResult,
    providers: List<OAuthPlatform>,
    onBind: (OAuthPlatform) -> Unit,
    onLogout: (() -> Unit)?,
    enabled: Boolean = true,
    onSession: (WebResult) -> Unit = {},
) {
    var current by remember(session.userId) { mutableStateOf(session) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() { current = refreshAnimekoProfile(current); onSession(current) }
    fun failure(e: Exception) = if (e is ResponseException) "Animeko 资料接口返回 HTTP ${e.response.status.value}" else "无法更新账号资料，请重试。"
    LaunchedEffect(session.userId, retry) {
        loading = true; error = null
        try { refresh() } catch (e: CancellationException) { throw e } catch (e: Exception) { error = failure(e) }
        finally { loading = false }
    }
    val size = currentWindowAdaptiveInfo1().windowSizeClass
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (size.isWidthCompact) Alignment.CenterHorizontally else Alignment.Start) {
        HeroIcon(Modifier.padding(vertical = if (size.isHeightAtLeastExpanded) 36.dp else 24.dp)) {
            AvatarImage(current.avatar.takeIf { it.isNotBlank() }, Modifier.clip(CircleShape).fillMaxSize())
        }
        Column(Modifier.fillMaxWidth()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton({ retry++ }) { Text("重试") } }
            TextFieldItem(current.nickname, title = { Text(stringResource(Lang.settings_account_profile_nickname)) },
                inverseTitleDescription = true, exposedItem = { Text("@$it") },
                textFieldDescription = { Text(stringResource(Lang.settings_account_profile_nickname_hint)) },
                isErrorProvider = { !isValidProfileNickname(it) }, sanitizeValue = { it.trim() },
                onValueChangeCompleted = { name -> if (!loading && enabled && name != current.nickname) scope.launch {
                    loading = true; error = null
                    try { saveAnimekoNickname(name); refresh() }
                    catch (e: CancellationException) { throw e } catch (e: Exception) { error = failure(e) }
                    finally { loading = false }
                } })
            TextItem(description = { Text(stringResource(Lang.settings_account_profile_email)) }) { Text(current.email.ifBlank { "未绑定" }) }
            if (current.userId.isNotBlank()) ProfileUserIdItem(current.userId)
            Group({ Text(stringResource(Lang.settings_account_profile_third_party_accounts)) }) {
                ProfileLinkedAccountItem(OAuthPlatform.BANGUMI,
                    current.username.takeIf { it.isNotBlank() } ?: if (session.hasBangumi) "已关联，资料暂时不可用" else null,
                    if (!session.hasBangumi && enabled) ({ onBind(OAuthPlatform.BANGUMI) }) else null)
                // Missing profile data must not be presented as a definitely unbound account.
                (providers + current.externalAccounts.keys.mapNotNull(OAuthPlatform::fromId)).distinct().filter { it != OAuthPlatform.BANGUMI }.forEach { provider ->
                    val linked = current.externalAccounts[provider.id]
                    ProfileLinkedAccountItem(provider, if (current.profileLoaded) linked else "关联状态暂不可用", if (enabled && linked == null) ({ onBind(provider) }) else null)
                }
            }
            if (session.bangumiError.isNotBlank()) Text(session.bangumiError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            if (onLogout != null) TextItem(onClick = onLogout, onClickEnabled = enabled) { Text(stringResource(Lang.settings_account_logout)) }
        }
    }
}
