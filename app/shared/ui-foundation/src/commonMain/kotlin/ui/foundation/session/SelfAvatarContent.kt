/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.foundation.session

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import org.jetbrains.compose.resources.stringResource

/** Shared avatar surface; the host supplies its image loader and account state. */
@Composable
fun SelfAvatarContent(
    isLoading: Boolean,
    isSignedIn: Boolean,
    size: DpSize,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    image: @Composable (Modifier) -> Unit,
) {
    if (!isLoading && !isSignedIn) {
        TextButton(onClick ?: {}, modifier = modifier) { Text(stringResource(Lang.login_sign_in)) }
    } else {
        val content: @Composable () -> Unit = { image(Modifier.size(size).clip(CircleShape)) }
        if (onClick == null) Surface(modifier, shape = CircleShape, content = content)
        else Surface(onClick, modifier, shape = CircleShape, content = content)
    }
}
