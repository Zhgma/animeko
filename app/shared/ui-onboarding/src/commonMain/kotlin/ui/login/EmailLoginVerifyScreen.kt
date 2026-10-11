/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import me.him188.ani.app.data.repository.user.UserRepository.SendOtpResult
import me.him188.ani.app.domain.session.auth.OAuthPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.rememberAsyncHandler
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.*
import org.jetbrains.compose.resources.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Composable
fun EmailLoginVerifyScreen(
    onSuccess: () -> Unit,
    onThirdPartyLoginClick: (OAuthPlatform) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: EmailLoginViewModel = viewModel<EmailLoginViewModel> { EmailLoginViewModel() },
) {
    val state by vm.state.collectAsStateWithLifecycle(EmailLoginUiState.Initial)
    val asyncHandler = rememberAsyncHandler()
    val toaster = LocalToaster.current
    val emailAlreadyUsedText = stringResource(Lang.login_email_already_used)
    val invalidOtpText = stringResource(Lang.login_invalid_otp)


    EmailLoginVerifyScreenImpl(
        email = state.email,
        nextResendTime = state.nextResendTime,
        isExistingAccount = state.isExistingAccount,
        onCodeSubmit = { otp ->
            asyncHandler.launch {
                val result = if (state.mode == EmailLoginUiState.Mode.LOGIN) {
                    vm.submitEmailOtp(otp)
                } else {
                    vm.bindOrRebind(otp)
                }

                when (result) {
                    SendOtpResult.EmailAlreadyExist -> toaster.show(emailAlreadyUsedText)
                    SendOtpResult.InvalidOtp -> toaster.show(invalidOtpText)
                    is SendOtpResult.Success -> onSuccess()
                }
            }
        },
        onResendClick = {
            asyncHandler.launch {
                vm.sendEmailOtp()
            }
        },
        onThirdPartyLoginClick,
        onNavigateSettings,
        onNavigateBack,
        modifier = modifier,
        enabled = !asyncHandler.isWorking,
        thirdPartyPlatforms = if (state.mode == EmailLoginUiState.Mode.LOGIN) state.thirdPartyPlatforms else emptyList(),
        title = { EmailPageTitle(state.mode, state.isExistingAccount) },
    )
}


@Composable
@Preview
private fun PreviewEmailLoginVerifyScreen() = ProvideCompositionLocalsForPreview {
    EmailLoginVerifyScreenImpl(
        "test@openani.org",
        nextResendTime = Clock.System.now() + 16.seconds,
        isExistingAccount = true,
        onCodeSubmit = {},
        onResendClick = {},
        {},
        {},
        {},
    )
}
