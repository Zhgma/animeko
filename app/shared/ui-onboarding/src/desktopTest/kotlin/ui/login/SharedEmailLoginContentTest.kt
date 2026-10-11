package me.him188.ani.app.ui.login

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_continue
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class SharedEmailLoginContentTest {
    @Test
    fun emailContinueUsesTrimmedInput() = runAniComposeUiTest {
        var submitted = ""
        val label = runBlocking { getString(Lang.login_continue) }
        setContent {
            ProvideCompositionLocalsForPreview {
                EmailLoginStartScreenImpl("", { submitted = it }, {}, {}, {})
            }
        }
        onNode(hasSetTextAction()).performTextInput("reader@example.test")
        onNodeWithText(label).performClick()
        assertEquals("reader@example.test", submitted)
    }

    @Test
    fun sixthOtpDigitSubmitsToHost() = runAniComposeUiTest {
        var submitted = ""
        setContent {
            ProvideCompositionLocalsForPreview {
                EmailLoginVerifyScreenImpl("reader@example.test", Clock.System.now() + 30.seconds, true,
                    { submitted = it }, {}, {}, {}, {})
            }
        }
        onNode(hasSetTextAction()).performTextInput("123456")
        assertEquals("123456", submitted)
    }
}
