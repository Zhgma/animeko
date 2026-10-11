package me.him188.ani.app.domain.session.auth

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OAuthPollingTest {
    @Test
    fun waits_one_second_between_pending_results() = runTest {
        var calls = 0
        val result = async { awaitOAuthResult { if (++calls == 3) "authorized" else null } }
        runCurrent()
        assertEquals(0, calls)
        advanceTimeBy(2999)
        assertEquals(2, calls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals("authorized", result.await())
        assertEquals(3, calls)
    }

    @Test
    fun cancellation_stops_polling() = runTest {
        var calls = 0
        val result = async { awaitOAuthResult<String> { calls++; null } }
        runCurrent()
        advanceTimeBy(1001)
        assertEquals(1, calls)
        result.cancel()
        advanceTimeBy(10000)
        assertEquals(1, calls)
    }
}
