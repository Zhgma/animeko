package me.him188.ani.app.ui.settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.him188.ani.app.ui.settings.framework.components.SettingsScope

// a lot of call-sites, don't make it internal
@Composable
fun SettingsTab(
    modifier: Modifier = Modifier,
    content: @Composable SettingsScope.() -> Unit,
) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(SettingsScope.itemVerticalSpacing),
    ) {
        val scope = remember(this) {
            object : SettingsScope(), ColumnScope by this@Column {}
        }
        scope.content()
    }
}
