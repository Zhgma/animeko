/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.reading

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderStatusSettingsStoreTest {
    @Test fun preferenceIsSharedAndPersistsWithoutChangingReadingData() = runTest {
        val historyKey = stringPreferencesKey("reading.history.entries")
        val dataStore = object : DataStore<Preferences> {
            override val data = MutableStateFlow(preferencesOf(historyKey to "existing reading data"))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                transform(data.value).also { data.value = it }
        }
        val manga = ReaderStatusSettingsStore(dataStore)
        val novel = ReaderStatusSettingsStore(dataStore)
        assertTrue(manga.enabled.first())
        manga.setEnabled(false)
        assertFalse(novel.enabled.first())
        assertFalse(ReaderStatusSettingsStore(dataStore).enabled.first())
        novel.setEnabled(true)
        assertTrue(manga.enabled.first())
        assertEquals("existing reading data", dataStore.data.value[historyKey])
    }
}
