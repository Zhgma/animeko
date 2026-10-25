/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.domain.search.SubjectType
import me.him188.ani.utils.ktor.asScopedHttpClient
import kotlin.test.*

class BangumiNovelFilteringTest {
    @Test fun classificationNeverGuessesFromTitlesOrUnknownPlatforms() {
        assertTrue(BangumiBookKind.NOVEL.accepts(1, "小说"))
        assertFalse(BangumiBookKind.NOVEL.accepts(2, "小说"))
        assertFalse(BangumiBookKind.NOVEL.accepts(1, "漫画"))
        assertFalse(BangumiBookKind.NOVEL.accepts(1, ""))
        assertFalse(BangumiBookKind.MANGA.accepts(1, "画集"))
    }

    @Test fun bothSearchKindsUseBooksApiButReturnDisjointResultsAndRawOffsets() = runTest {
        val client = HttpClient(MockEngine { respond("""{"total":8,"data":[
            {"id":1,"type":1,"platform":"漫画","name":"三体 漫画"},
            {"id":2,"type":1,"platform":"小说","name":"三体"},
            {"id":3,"type":1,"platform":"画集","name":"画集"}]}""") })
        try {
            val repository = BangumiBookSearchRepository(client.asScopedHttpClient())
            val manga = repository.searchPage(SubjectSearchQuery("三体", type = SubjectType.BOOK), null, 0, 20)
            val novel = repository.searchPage(SubjectSearchQuery("三体", type = SubjectType.NOVEL), null, 0, 20)
            assertEquals(listOf(1), manga.items.map { it.id })
            assertEquals(listOf(2), novel.items.map { it.id })
            assertEquals(3, novel.nextOffset)
        } finally { client.close() }
    }

    @Test fun novelCollectionsContinueAfterMangaOnlyPageAndKeepCountsAndProgress() = runTest {
        val offsets = mutableListOf<Int>()
        val loader = BangumiMangaCollectionLoader(BangumiBookKind.NOVEL) { path, _, _, offset -> when {
            path == "me" -> """{"username":"reader"}"""
            path.startsWith("users/") -> {
                offsets += offset
                """{"total":2,"data":[{"subject_id":${offset + 1},"subject_type":1,"ep_status":12,"vol_status":3}]}"""
            }
            else -> """{"type":1,"platform":"${if (path.endsWith("1")) "漫画" else "小说"}","name":"书"}"""
        } }
        val pages = mutableListOf<List<MangaCollection>>()
        loader.loadCollections("token", MangaCollectionType.DOING) { _, items -> pages += items }
        assertEquals(listOf(0, 1), offsets)
        assertTrue(pages.first().isEmpty())
        assertEquals(2, pages.last().single().subjectId)
        assertEquals(12, pages.last().single().chaptersRead)
        assertEquals(3, pages.last().single().volumesRead)
    }
}
