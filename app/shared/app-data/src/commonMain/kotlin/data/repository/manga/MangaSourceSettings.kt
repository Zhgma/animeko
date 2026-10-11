/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MangaSourceSettings(
    val order: List<MangaSource> = MangaSource.entries,
    val disabled: Set<MangaSource> = emptySet(),
    val subscriptions: List<MangaSubscription> = MangaSubscription.defaults(),
) {
    val all: List<MangaSource> get() {
        val installed = subscriptions.filter { it.enabled }.flatMap { it.sources }.distinct()
        return (order + installed).distinct().filter { it in installed }
    }
    val enabled: List<MangaSource> get() = all.filter { it !in disabled }
    fun subscriptionUrl(source: MangaSource): String? = subscriptions.firstOrNull { it.enabled && source in it.sources }?.url
}

class MangaSourceSettingsStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("manga.sources.settings")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(preferences: Preferences): MangaSourceSettings = preferences[key]?.let {
        runCatching { json.decodeFromString<MangaSourceSettings>(it) }.getOrNull()
    } ?: MangaSourceSettings()
    val flow = store.data.map(::decode).distinctUntilChanged()
    suspend fun read(): MangaSourceSettings = flow.first()
    suspend fun save(settings: MangaSourceSettings) { store.edit { it[key] = json.encodeToString(settings) } }
    suspend fun update(transform: (MangaSourceSettings) -> MangaSourceSettings) {
        store.edit { it[key] = json.encodeToString(transform(decode(it))) }
    }
    suspend fun savePreferences(settings: MangaSourceSettings) = update {
        it.copy(order = settings.order, disabled = settings.disabled)
    }
}
