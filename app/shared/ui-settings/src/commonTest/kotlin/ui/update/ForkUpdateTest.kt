/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForkUpdateTest {
    private val android = Platform.Android(Arch.ARMV8A)
    private fun release(version: String, debug: Boolean = false, prerelease: Boolean = false, draft: Boolean = false): ForkRelease {
        val tag = "v$version"
        val asset = forkAssetNames(version, android, debug, "default").first()
        return ForkRelease(tag, draft, prerelease, "Changes", "2026-10-10", listOf(ForkAsset(asset, "$FORK_RELEASES_URL/download/$tag/$asset")))
    }
    @Test fun comparesNumbersAndPrereleaseIdentifiersWithoutDowngrades() {
        assertTrue(ForkVersion.parse("4.10.0")!! > ForkVersion.parse("4.9.9")!!)
        assertTrue(ForkVersion.parse("4.9.0-beta.10")!! > ForkVersion.parse("4.9.0-beta.2")!!)
        assertTrue(ForkVersion.parse("4.9.0")!! > ForkVersion.parse("4.9.0-rc.1")!!)
        assertEquals(ForkVersion.parse("v4.9.0+build"), ForkVersion.parse("4.9.0"))
        assertNull(ForkVersion.parse("main"))
        assertNull(selectForkRelease(listOf(release("4.9.0")), "4.9.0", ReleaseClass.BETA, android, false, "default"))
    }
    @Test fun selectsNewestCompatibleAssetAndHonorsChannelAndDrafts() {
        val list = listOf(release("5.0.0"), release("6.0.0", draft = true), release("5.1.0-beta.1", prerelease = true))
        assertEquals("5.0.0", selectForkRelease(list, "4.9.0", ReleaseClass.STABLE, android, false, "default")?.name)
        assertEquals("5.1.0-beta.1", selectForkRelease(list, "4.9.0", ReleaseClass.BETA, android, false, "default")?.name)
    }
    @Test fun neverCrossInstallsAndroidDebugReleaseOrTvPackages() {
        val list = listOf(release("5.0.0"), release("5.1.0", debug = true))
        assertEquals("5.0.0", selectForkRelease(list, "4.9.0", ReleaseClass.STABLE, android, false, "default")?.name)
        assertEquals("5.1.0", selectForkRelease(list, "4.9.0", ReleaseClass.STABLE, android, true, "default")?.name)
        assertNull(selectForkRelease(list, "4.9.0", ReleaseClass.STABLE, android, false, "tv"))
        assertNull(selectForkRelease(list, "4.9.0", ReleaseClass.STABLE, Platform.Android(Arch.X86_64), false, "default"))
    }
    @Test fun supportsUniversalFallbackAndRejectsForeignDownloadUrls() {
        val original = release("5.0.0")
        val universal = forkAssetNames("5.0.0", android, false, "default").last()
        val fallback = original.copy(assets = listOf(ForkAsset(universal, "$FORK_RELEASES_URL/download/v5.0.0/$universal")))
        assertTrue(selectForkRelease(listOf(fallback), "4.9.0", ReleaseClass.STABLE, android, false, "default")!!.downloadUrlAlternatives.single().endsWith(universal))
        assertNull(selectForkRelease(listOf(original.copy(assets = original.assets.map { it.copy(url = "https://example.com/app.apk") })),
            "4.9.0", ReleaseClass.STABLE, android, false, "default"))
    }
    @Test fun desktopAssetNamesMatchReleasePackagingAndEmptyRepositoryMeansNoUpdate() {
        assertEquals(listOf("ani-5.0.0-windows-x86_64.zip"), forkAssetNames("5.0.0", Platform.Windows(Arch.X86_64), false, "default"))
        assertEquals(listOf("ani-5.0.0-macos-aarch64.dmg"), forkAssetNames("5.0.0", Platform.MacOS(Arch.AARCH64), false, "default"))
        assertEquals(listOf("ani-5.0.0-linux-x86_64.appimage"), forkAssetNames("5.0.0", Platform.Linux(Arch.X86_64), false, "default"))
        assertNull(selectForkRelease(emptyList(), "4.9.0", ReleaseClass.STABLE, android, false, "default"))
    }
}
