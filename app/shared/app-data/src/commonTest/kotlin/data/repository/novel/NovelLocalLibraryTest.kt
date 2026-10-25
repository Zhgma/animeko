/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class NovelLocalLibraryTest {
    @Test fun localCountUpdatesFromPersistedImportsAndExcludesSubjectLibraries() = runTest {
        val store = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val sizes = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            NovelLocalLibrary(store).books.collect { sizes += it.size }
        }
        val books = listOf(NovelBook(NovelSource.LOCAL, "a", "A"), NovelBook(NovelSource.LOCAL, "b", "B"))
        store.edit { it[novelLocalFilesKey(123)] = Json.encodeToString(books) }
        assertEquals(listOf(0), sizes)
        store.edit { it[novelLocalFilesKey(0)] = Json.encodeToString(books) }
        store.edit { it[novelLocalFilesKey(0)] = Json.encodeToString(books.take(1)) }
        assertEquals(listOf(0, 2, 1), sizes)
    }
}
