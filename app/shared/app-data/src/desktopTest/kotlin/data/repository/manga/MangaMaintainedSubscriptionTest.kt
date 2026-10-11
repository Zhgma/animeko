/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.manga

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MangaMaintainedSubscriptionTest {
    @Test
    fun repositoryManifestMatchesSupportedAdaptersAndBundledFallback() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "subs/manga.json").isFile }
        val contents = MangaSubscriptionParser.parse(File(root, "subs/manga.json").readText())
        val fallback = MangaSubscription.defaults().single()
        assertEquals(fallback.sources, contents.sources)
        assertEquals(fallback.sourceLinks, contents.sourceLinks)
        assertTrue(contents.unsupported.isEmpty())
    }
}
