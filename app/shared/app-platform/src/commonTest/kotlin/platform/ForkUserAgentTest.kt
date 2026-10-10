/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class ForkUserAgentTest {
    @Test
    fun forkReleaseVersionDoesNotReplaceUpstreamApiCompatibilityVersion() {
        for (version in listOf("0.1.0", "0.2.0-beta.1")) {
            val agent = getAniUserAgent(version, "Android arm64-v8a")
            assertTrue(agent.startsWith("open-ani/ani/4.9.0-dev (Android arm64-v8a)"))
            assertTrue(agent.contains("Animecomic/$version (https://github.com/Zhgma/animeko)"))
        }
    }
}
