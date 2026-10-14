package me.him188.ani.app.ui.foundation.dialogs
import androidx.compose.ui.window.PopupProperties
fun PlatformPopupProperties(clippingEnabled: Boolean = true): PopupProperties = PopupProperties(focusable = true, clippingEnabled = clippingEnabled)
