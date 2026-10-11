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
    val order: List<MangaSource> = MangaSource.available,
    val disabled: Set<MangaSource> = setOf(MangaSource.YKMH),
    val builtinsRevision: Int = 0,
    val subscriptions: List<MangaSubscription> = MangaSubscription.defaults(),
) {
    val all: List<MangaSource> get() {
        val installed = subscriptions.filter { it.enabled }.flatMap { it.sources }.distinct()
        return (order + installed).distinct().filter { it in installed && it in MangaSource.available }
    }
    val enabled: List<MangaSource> get() = all.filter { it !in disabled }
    fun subscriptionUrl(source: MangaSource): String? = subscriptions.firstOrNull { it.enabled && source in it.sources }
        ?.let { it.sourceLinks[source] ?: it.url }
}

class MangaSourceSettingsStore(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("manga.sources.settings")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(preferences: Preferences): MangaSourceSettings = preferences[key]?.let {
        runCatching { json.decodeFromString<MangaSourceSettings>(it) }.getOrNull()
    }?.let(::migrate) ?: MangaSourceSettings(builtinsRevision = 2)

    private fun migrate(settings: MangaSourceSettings): MangaSourceSettings {
        val legacyAdditions = if (settings.builtinsRevision < 1 && settings.subscriptions.any { it.id.startsWith("builtin-") }) {
            listOf(MangaSource.MANHUAREN, MangaSource.YKMH).filter { source ->
                settings.subscriptions.none { it.url == source.subscriptionUrl }
            }.map { MangaSubscription("builtin-${it.name}", it.title, it.subscriptionUrl,
                enabled = it != MangaSource.YKMH, sources = listOf(it)) }
        } else emptyList()
        val subscriptions = (settings.subscriptions + legacyAdditions).mapNotNull { sub ->
            val sources = sub.sources.filter { it in MangaSource.available }
            if (sources.isEmpty() && MangaSource.MANGADEX in sub.sources) null else sub.copy(sources = sources)
        }
        // Only untouched legacy built-in links migrate. Edited URLs and custom subscriptions remain intact.
        val builtins = if (settings.builtinsRevision < 2) subscriptions.filter { sub ->
            MangaSource.available.any { sub.id == "builtin-${it.name}" && sub.url == it.subscriptionUrl }
        } else emptyList()
        val disabled = settings.disabled.filter { it in MangaSource.available }.toSet() +
            if (settings.builtinsRevision < 2 && settings.subscriptions == MangaSubscription.defaults()) {
                setOf(MangaSource.YKMH)
            } else emptySet()
        if (builtins.isEmpty()) return settings.copy(builtinsRevision = 2, subscriptions = subscriptions,
            disabled = disabled, order = settings.order.filter { it in MangaSource.available })
        val previouslyEnabled = subscriptions.filter { it.enabled }.flatMap { it.sources }.toSet() - disabled
        val replacement = MangaSubscription.defaults().single().copy(enabled = builtins.any { it.enabled })
        val merged = buildList {
            for (sub in subscriptions) {
                if (sub == builtins.first() && subscriptions.none { it.url == replacement.url }) add(replacement)
                if (sub !in builtins) add(sub)
            }
        }
        return settings.copy(builtinsRevision = 2, subscriptions = merged,
            disabled = disabled + (MangaSource.available - previouslyEnabled),
            order = settings.order.filter { it in MangaSource.available })
    }
    val flow = store.data.map(::decode).distinctUntilChanged()
    suspend fun read(): MangaSourceSettings = flow.first()
    suspend fun save(settings: MangaSourceSettings) { store.edit { it[key] = json.encodeToString(settings.copy(builtinsRevision = 2)) } }
    suspend fun update(transform: (MangaSourceSettings) -> MangaSourceSettings) {
        store.edit { it[key] = json.encodeToString(transform(decode(it))) }
    }
    suspend fun savePreferences(settings: MangaSourceSettings) = update {
        it.copy(order = settings.order, disabled = settings.disabled)
    }
}
