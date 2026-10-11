package me.him188.ani.app.ui.settings.account

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_account_popup_login_register
import me.him188.ani.app.ui.lang.settings_account_popup_logout
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertTrue

class SharedProfilePopupContentTest {
    @Test
    fun signedOutMenuOffersLoginWithoutLogout() = runAniComposeUiTest {
        var login = false
        val loginLabel = runBlocking { getString(Lang.settings_account_popup_login_register) }
        val logoutLabel = runBlocking { getString(Lang.settings_account_popup_logout) }
        setContent {
            ProvideCompositionLocalsForPreview {
                ProfilePopupContent(false, "", {}, { login = true }, {}, {}, {}, {})
            }
        }
        onNodeWithText(loginLabel).performClick()
        assertTrue(login)
        onNodeWithText(logoutLabel).assertDoesNotExist()
    }
}
