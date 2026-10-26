package me.him188.ani.app.data.repository.player

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.persistent.MemoryDataStore
import me.him188.ani.app.data.repository.manga.*
import me.him188.ani.app.data.repository.novel.*
import kotlin.test.*

class ReadingHistoryStoreTest {
    @Test fun coverEnrichmentPreservesProgressAndTimeAndCannotRestoreDeletedRecords() = runTest {
        val store = ReadingHistoryStore(MemoryDataStore(emptyPreferences()))
        val entry = ReadingHistoryEntry(123, "小说", chapterTitle = "第三章", updatedAtMillis = 42, novel = novel("a"))
        store.record(entry)
        store.fillMissingCover(123, "https://example.com/cover.jpg")
        val repaired = store.entries.first().single()
        assertEquals(entry.updatedAtMillis, repaired.updatedAtMillis)
        assertEquals(entry.position, repaired.position)
        assertEquals(entry.novel!!.offset, repaired.novel!!.offset)
        assertEquals("https://example.com/cover.jpg", repaired.coverUrl)
        assertEquals(repaired.coverUrl, repaired.novel.coverUrl)
        store.record(entry.copy(updatedAtMillis = 43))
        assertEquals(repaired.coverUrl, store.entries.first().single().coverUrl)
        store.remove(entry.key)
        store.fillMissingCover(123, repaired.coverUrl)
        assertTrue(store.entries.first().isEmpty())
    }

    @Test fun migrationAndResumeKeepPersistedNovelCover() = runTest {
        val prefs = MemoryDataStore(emptyPreferences())
        val memory = novel("a").copy(coverUrl = "https://example.com/cover.jpg")
        prefs.edit { it[stringPreferencesKey("novel.reader.123")] = Json.encodeToString(memory) }
        val store = ReadingHistoryStore(prefs)
        val entry = store.entries.first().single()
        assertEquals(memory.coverUrl, entry.coverUrl)
        store.prepareResume(entry)
        val resumed = Json.decodeFromString<NovelMemory>(prefs.data.first()[stringPreferencesKey("novel.reader.123")]!!)
        assertEquals(memory.coverUrl, resumed.coverUrl)
    }

    @Test fun coverLoaderReusesKnownCoversSkipsLocalFilesAndContinuesAfterFailure() = runTest {
        val store = ReadingHistoryStore(MemoryDataStore(emptyPreferences()))
        val base = ReadingHistoryEntry(123, "小说", chapterTitle = "第三章", updatedAtMillis = 42, novel = novel("a"))
        val entries = listOf(base, base.copy(coverUrl = "known", novel = novel("b")),
            base.copy(subjectId = 0), base.copy(subjectId = 124), base.copy(subjectId = 125))
        entries.forEach { store.record(it) }
        val requests = mutableListOf<Int>()
        val loader = ReadingHistoryCoverLoader(store) { id -> requests += id; if (id == 124) error("offline") else "loaded" }
        loader.restore(store.entries.first())
        loader.restore(store.entries.first())
        assertEquals(listOf(124, 125), requests)
        val result = store.entries.first()
        assertTrue(result.filter { it.subjectId == 123 }.all { it.coverUrl == "known" })
        assertEquals("loaded", result.single { it.subjectId == 125 }.coverUrl)
        assertTrue(result.single { it.subjectId == 124 }.coverUrl.isEmpty())
        assertEquals(entries.size, result.size)
    }

    private fun novel(bookId: String, paragraph: Int = 12) = NovelMemory(
        NovelBook(NovelSource.LOCAL, bookId, bookId), listOf(NovelChapter("chapter", "第三章")),
        "chapter", paragraph, 45,
    )
    private fun manga(page: Int = 3) = MangaReadingMemory(MangaSourceBook(MangaSource.KOMIIC, "book", "花薰凛然"), "205", page)

    @Test fun cloudSyncDefaultsOffAndPersistsAcrossInstances() = runTest {
        val prefs = MemoryDataStore(emptyPreferences())
        val store = ReadingHistoryStore(prefs)
        assertFalse(store.cloudSyncEnabled.first())
        store.setCloudSyncEnabled(true)
        assertTrue(ReadingHistoryStore(prefs).cloudSyncEnabled.first())
        store.setCloudSyncEnabled(false)
        assertFalse(ReadingHistoryStore(prefs).cloudSyncEnabled.first())
    }

    @Test fun migratePositionsOnceWithoutResurrectingDeletedHistory() = runTest {
        val prefs = MemoryDataStore(emptyPreferences())
        prefs.edit {
            it[stringPreferencesKey("manga.reader.10")] = Json.encodeToString(manga())
            it[stringPreferencesKey("novel.reader.0")] = Json.encodeToString(novel("a"))
            it[stringPreferencesKey("novel.position.0.LOCAL.a")] = Json.encodeToString(novel("a"))
            it[stringPreferencesKey("novel.position.0.LOCAL.b")] = Json.encodeToString(novel("b"))
            it[stringPreferencesKey("novel.reader.settings")] = "{}"
        }
        val store = ReadingHistoryStore(prefs)
        val migrated = store.entries.first()
        assertEquals(3, migrated.size)
        assertTrue(migrated.all { it.updatedAtMillis == 0L })
        assertEquals(2, migrated.count { it.kind == ReadingHistoryKind.NOVEL })
        store.remove(migrated.first().key)
        assertEquals(2, ReadingHistoryStore(prefs).entries.first().size)
        assertNotNull(prefs.data.first()[stringPreferencesKey("manga.reader.10")])
    }

    @Test fun updatesLatestPositionAndRestoresSourcePageAndParagraph() = runTest {
        val prefs = MemoryDataStore(emptyPreferences())
        val store = ReadingHistoryStore(prefs)
        val first = ReadingHistoryEntry(10, "花薰凛然", chapterTitle = "205话", updatedAtMillis = 1, manga = manga())
        val next = first.copy(updatedAtMillis = 3, manga = manga(8))
        val bookA = ReadingHistoryEntry(0, "A", chapterTitle = "第三章", updatedAtMillis = 2, novel = novel("a"))
        val bookB = bookA.copy(title = "B", novel = novel("b"))
        store.record(first); store.record(bookA); store.record(bookB); store.record(next)
        store.record(first) // A slower earlier save must not replace the newer position.
        assertEquals(3, store.entries.first().size)
        assertEquals(next, store.entries.first().first())
        MangaReadingStore(prefs).save(10, manga().copy(rightToLeft = false, firstPageSingle = true))
        store.prepareResume(next)
        val resumed = MangaReadingStore(prefs).read(10)!!
        assertEquals(8, resumed.page)
        assertFalse(resumed.rightToLeft)
        assertTrue(resumed.firstPageSingle)
        store.prepareResume(bookB); store.prepareResume(bookA)
        val novel = Json.decodeFromString<NovelMemory>(prefs.data.first()[stringPreferencesKey("novel.reader.0")]!!)
        assertEquals("a", novel.book.id)
        assertEquals(12, novel.paragraph)
        assertEquals(45, novel.offset)
        assertEquals("chapter", novel.chapterId)
        assertFalse(store.cloudSyncEnabled.first())
    }
}
