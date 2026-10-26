package me.him188.ani.app.data.repository.player

import androidx.datastore.preferences.core.emptyPreferences
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.persistent.database.dao.createMemoryPlaybackHistoryDao
import me.him188.ani.app.domain.session.*
import me.him188.ani.client.apis.PlaybackHistoryAniApi
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.test.*

class PlaybackHistoryConsentTest {
    @Test fun loginAndManualRefreshKeepLocalHistoryWithoutNetworkUntilEnabled() = runTest {
        val settings = ReadingHistoryStore(MemoryDataStore(emptyPreferences()))
        val repository = EpisodePlayHistoryRepositoryImpl(MemoryDataStore(EpisodeHistories.Empty), createMemoryPlaybackHistoryDao())
        repository.saveOrUpdate(1, 12000, subjectId = 10, durationMillis = 100000)
        val events = MutableSharedFlow<SessionEvent>()
        val session = object : SessionStateProvider {
            override val stateFlow = MutableStateFlow<SessionState>(SessionState.Valid(bangumiConnected = true))
            override val eventFlow: Flow<SessionEvent> = events
        }
        var calls = 0
        val api = PlaybackHistoryAniApi(httpClientEngine = MockEngine.create {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
            calls++
            respond("""{"upserts":[],"deletes":[],"serverTime":"2026-01-01T00:00:00Z","nextSyncAt":"2026-01-01T00:00:00Z"}""",
                headers = headersOf("Content-Type", "application/json"))
            }
        })
        val invoker = object : ApiInvoker<PlaybackHistoryAniApi> {
            override suspend fun <R> invoke(action: suspend PlaybackHistoryAniApi.() -> R): R = api.action()
        }
        val syncer = PlaybackHistorySyncer(repository, invoker, session, backgroundScope,
            ioDispatcher = StandardTestDispatcher(testScheduler), cloudSyncEnabled = settings.cloudSyncEnabled)
        syncer.start()
        runCurrent()
        syncer.syncOnce()
        syncer.requestSync()
        advanceTimeBy(6000); runCurrent()
        assertEquals(0, calls)
        assertEquals(12000L, repository.getResumePositionMillisByEpisodeId(1))
        assertEquals(1, repository.pendingOpsFlow.first().size)

        settings.setCloudSyncEnabled(true)
        advanceTimeBy(5000); runCurrent()
        assertEquals(1, calls)
        assertTrue(repository.pendingOpsFlow.first().isEmpty())
        settings.setCloudSyncEnabled(false)
        repository.saveOrUpdate(1, 20000, subjectId = 10, durationMillis = 100000)
        syncer.requestSync()
        advanceTimeBy(6000); runCurrent()
        syncer.syncOnce()
        assertEquals(1, calls)
        assertEquals(20000L, repository.getResumePositionMillisByEpisodeId(1))
        assertEquals(1, repository.pendingOpsFlow.first().size)
    }
}
