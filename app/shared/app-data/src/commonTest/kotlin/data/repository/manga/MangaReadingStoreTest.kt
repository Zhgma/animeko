/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaReadingStoreTest {
    @Test
    fun sourceBindingsSurviveStoreRecreationAndStayLocalToTheManga() = runTest {
        val dataStore = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val store = MangaReadingStore(dataStore)
        val first = MangaReadingMemory(MangaSourceBook(MangaSource.GODA, "a", "A"), chapterId = "12", page = 5)
        store.save(1, first)
        store.save(2, MangaReadingMemory(MangaSourceBook(MangaSource.KOMIIC, "b", "B"), sourcePinned = false))
        val reopened = MangaReadingStore(dataStore)
        assertEquals(first, reopened.read(1))
        assertTrue(reopened.read(1)!!.sourcePinned)
        assertFalse(reopened.read(2)!!.sourcePinned)
        assertEquals(MangaSource.KOMIIC, reopened.read(2)!!.book.source)
    }
}
