/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.manga.MangaChapter
import me.him188.ani.app.data.repository.manga.MangaSourceBook
import me.him188.ani.app.data.repository.manga.MangaSourceRepository
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath

internal data class MangaOfflineState(val entries: List<MangaOfflineChapter> = emptyList(),
    val working: Boolean = false, val error: String? = null)

/** Transfers live in the application's download scope; closing a detail page only detaches its observer. */
internal class MangaOfflineDownloads private constructor(
    val store: MangaOfflineStore, private val scope: CoroutineScope, private val sources: MangaSourceRepository,
) {
    private val mutableState = MutableStateFlow(MangaOfflineState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    init { scope.launch { refresh() } }

    private suspend fun refresh() {
        try { val entries = store.list(); mutableState.update { it.copy(entries = entries) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutableState.update { it.copy(error = e.message ?: "缓存索引读取失败") } }
    }
    fun download(book: MangaSourceBook, chapters: List<MangaChapter>) {
        if (mutableState.value.working) return
        mutableState.update { it.copy(working = true, error = null) }
        job = scope.launch {
            try {
                store.enqueue(book, chapters)
                refresh()
                for (chapter in chapters) {
                    if (store.pages(book, chapter) != null) continue
                    store.download(book, chapter, sources.pages(book, chapter), sources::image) { entry ->
                        mutableState.update { it.copy(entries = it.entries.filterNot { old ->
                            old.book.source == book.source && old.book.id == book.id && old.chapter.id == chapter.id
                        } + entry) }
                    }
                    if (mutableState.value.entries.lastOrNull()?.error != null) break
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = e.message ?: "缓存失败") } }
            finally { mutableState.update { it.copy(working = false) } }
        }
    }
    fun pause() { job?.cancel() }
    fun delete(entry: MangaOfflineChapter) {
        if (mutableState.value.working) return
        scope.launch {
            try { store.remove(entry); refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = e.message ?: "删除缓存失败") } }
        }
    }

    companion object {
        private val lock = SynchronizedObject()
        private val instances = mutableMapOf<String, MangaOfflineDownloads>()
        fun get(directory: SystemPath, scope: CoroutineScope, sources: MangaSourceRepository): MangaOfflineDownloads = synchronized(lock) {
            instances.getOrPut(directory.absolutePath) { MangaOfflineDownloads(MangaOfflineStore(directory), scope, sources) }
        }
    }
}
