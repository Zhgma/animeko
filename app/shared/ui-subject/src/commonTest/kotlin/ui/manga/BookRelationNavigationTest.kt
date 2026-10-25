/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.manga

import me.him188.ani.app.data.repository.manga.MangaRelation
import me.him188.ani.app.navigation.NavRoutes
import kotlin.test.Test
import kotlin.test.assertEquals

class BookRelationNavigationTest {
    @Test fun relatedVolumesAndAdaptationsOpenTheirOwnInAppDetails() {
        val novel = MangaRelation(140, 1, "第十四卷", "Volume 14", "cover", "单行本", "小说")
        assertEquals(NavRoutes.NovelDetail(140, "第十四卷", "Volume 14", "cover"), bookRelationRoute(novel))
        assertEquals(NavRoutes.MangaDetail(140, "第十四卷", "Volume 14", "cover"), bookRelationRoute(novel.copy(platform = "漫画")))
        assertEquals(NavRoutes.SubjectDetail(140), bookRelationRoute(novel.copy(type = 2, platform = "TV")))
        assertEquals(NavRoutes.SubjectDetail(140), bookRelationRoute(novel.copy(type = 3, platform = "音乐")))
    }
}
