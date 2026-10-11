/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/** Polls the authorization result once per second until available or cancelled. */
suspend fun <T : Any> awaitOAuthResult(getResult: suspend () -> T?): T {
    while (true) {
        delay(1.seconds)
        getResult()?.let { return it }
    }
}
