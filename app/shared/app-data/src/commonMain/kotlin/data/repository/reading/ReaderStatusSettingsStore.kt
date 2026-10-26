/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.reading

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 漫画与小说共用的全屏阅读状态栏偏好. */
class ReaderStatusSettingsStore(private val store: DataStore<Preferences>) {
    private val key = booleanPreferencesKey("reading.showBatteryAndTime")
    val enabled = store.data.map { it[key] ?: true }.distinctUntilChanged()
    suspend fun setEnabled(enabled: Boolean) {
        store.edit { it[key] = enabled }
    }
}
