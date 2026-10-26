/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.player

import kotlinx.coroutines.CancellationException

/** A history screen session attempts each missing subject once; reopening permits retry after a network failure. */
class ReadingHistoryCoverLoader(
    private val store: ReadingHistoryStore,
    private val resolveCover: suspend (Int) -> String,
) {
    private val attempted = mutableSetOf<Int>()

    suspend fun restore(entries: List<ReadingHistoryEntry>) {
        val known = entries.filter { it.coverUrl.isNotBlank() }.associate { it.subjectId to it.coverUrl }
        for (entry in entries) {
            if (entry.subjectId <= 0 || entry.coverUrl.isNotBlank() || !attempted.add(entry.subjectId)) continue
            try {
                store.fillMissingCover(entry.subjectId, known[entry.subjectId] ?: resolveCover(entry.subjectId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Missing metadata must not hide readable history during a network failure.
            }
        }
    }
}
