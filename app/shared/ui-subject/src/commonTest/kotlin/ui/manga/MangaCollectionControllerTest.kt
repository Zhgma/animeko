package me.him188.ani.app.ui.manga

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.repository.manga.MangaCollection
import me.him188.ani.app.data.repository.manga.MangaCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MangaCollectionControllerTest {
    private val manga = MangaCollection(1, "作品", "", "", 1, 0, 10, 0, 0, false)

    @Test
    fun categoryFailureRetainsCachedItemsAndOtherCategoriesContinue() = runTest {
        var fail = false
        val order = mutableListOf<MangaCollectionType>()
        val controller = MangaCollectionController({ _, type, onPage ->
            order += type
            if (fail && type == MangaCollectionType.DOING) error("offline")
            onPage("reader", listOf(manga))
        }, { false })
        controller.refresh("account-a")
        fail = true; order.clear()
        controller.refresh("account-a")
        assertEquals(MangaCollectionType.entries.size, order.size)
        val page = controller.state.value.pages.getValue(MangaCollectionType.DOING)
        assertEquals(listOf(manga), page.items); assertTrue(page.failed); assertFalse(page.loading)
        assertFalse(controller.state.value.pages.getValue(MangaCollectionType.WISH).failed)
    }

    @Test
    fun switchingAccountsDiscardsPreviousPrivateCollectionsAndLogoutClearsEverything() = runTest {
        val controller = MangaCollectionController({ session, _, onPage ->
            if (session == "account-b") error("offline")
            onPage("reader", listOf(manga))
        }, { false })
        controller.refresh("account-a"); controller.refresh("account-b")
        assertTrue(controller.state.value.pages.values.all { it.items.isEmpty() })
        controller.refresh(null)
        assertTrue(controller.state.value.needsLogin); assertTrue(controller.state.value.pages.isEmpty())
    }

    @Test
    fun authorizationFailureReturnsToLoginAndKeepsSelectedCategory() = runTest {
        val controller = MangaCollectionController({ _, _, _ -> error("unauthorized") }, { true })
        controller.select(MangaCollectionType.WISH); controller.refresh("account-a")
        assertTrue(controller.state.value.needsLogin)
        assertEquals(MangaCollectionType.WISH, controller.state.value.type)
        assertTrue(controller.state.value.pages.isEmpty())
    }

    @Test
    fun cancellationPropagatesWithoutBecomingCategoryError() = runTest {
        val controller = MangaCollectionController({ _, _, _ -> throw CancellationException() }, { false })
        assertFailsWith<CancellationException> { controller.refresh("account-a") }
        assertFalse(controller.state.value.pages.getValue(MangaCollectionType.DOING).failed)
    }
}
