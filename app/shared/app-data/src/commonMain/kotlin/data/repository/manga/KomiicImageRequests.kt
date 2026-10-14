/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/** Reader, prefetch and offline downloads share one paced queue across repository instances. */
internal class KomiicImageRequests(
    private val nowMillis: () -> Long = monotonicMillis(),
) {
    private val mutex = Mutex()
    private var nextRequestAt = 0L
    private var blockedUntil = 0L
    private var restriction: MangaSourceAccessException? = null

    suspend fun <T> request(block: suspend () -> T): T = mutex.withLock {
        restriction?.let { if (nowMillis() < blockedUntil) throw it }
        delay((nextRequestAt - nowMillis()).coerceAtLeast(0))
        try {
            block()
        } catch (e: MangaSourceAccessException) {
            e.retryAfterMillis?.let {
                restriction = e
                blockedUntil = nowMillis() + it
            }
            throw e
        } finally {
            // Space completions and starts, including retries after a failed transfer.
            nextRequestAt = nowMillis() + 1_000
        }
    }
}

internal val sharedKomiicImageRequests = KomiicImageRequests()

private fun monotonicMillis(): () -> Long {
    val origin = TimeSource.Monotonic.markNow()
    return { origin.elapsedNow().inWholeMilliseconds }
}
