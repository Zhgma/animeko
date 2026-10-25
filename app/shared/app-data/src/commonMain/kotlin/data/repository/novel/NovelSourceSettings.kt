/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.novel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class NovelSourceSettings(
    val order: List<NovelSource> = emptyList(),
    val disabled: Set<NovelSource> = emptySet(),
    val subscriptions: List<NovelSubscription> = NovelSubscription.defaults(),
) {
    val definitions get() = subscriptions.filter { it.enabled }.flatMap { it.sources }.distinctBy { it.key }
    val all get() = (order + definitions.map { it.source }).distinct().filter { source -> definitions.any { it.source == source } }
    val enabled get() = all.filterNot { it in disabled || definition(it)?.unavailableReason?.isNotBlank() == true }
    fun definition(source: NovelSource) = definitions.firstOrNull { it.source == source }
    fun title(source: NovelSource) = definition(source)?.name ?: source.title
    fun subscriptionUrl(source: NovelSource) = subscriptions.firstOrNull { it.enabled && it.sources.any { d -> d.source == source } }?.url
}

class NovelSourceSettingsStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("novel.sources.settings")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(p: Preferences) = p[key]?.let { json.decodeFromString<NovelSourceSettings>(it) } ?: NovelSourceSettings()
    val flow = store.data.map(::decode).distinctUntilChanged()
    suspend fun read() = flow.first()
    suspend fun update(transform: (NovelSourceSettings) -> NovelSourceSettings) {
        store.edit { it[key] = json.encodeToString(transform(decode(it))) }
    }
    suspend fun savePreferences(value: NovelSourceSettings) = update { it.copy(order = value.order, disabled = value.disabled) }
}
