/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.data.repository.reading

import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.io.*
import kotlin.test.*

class ReadingAutoCacheTest {
    @Test fun jumpsReopenAndRestartKeepExactlyThreeSlots() = runTest {
        val root = SystemPaths.createTempDirectory("auto-window")
        val cache = ReadingAutoCache(root) { 1000L }
        cache.enter()
        cache.opened("10", "11")
        cache.put("10", "ten"); cache.put("11", "eleven")
        cache.opened("20", "21")
        cache.put("20", "twenty"); cache.put("21", "next")
        assertEquals("ten", cache.read("10"))
        assertNull(cache.read("11"))
        val restarted = ReadingAutoCache(root) { 2000L }
        restarted.enter(); restarted.opened("20", "21")
        assertEquals("ten", restarted.read("10"))
        restarted.opened("30", null)
        assertNull(restarted.read("10")); assertNull(restarted.read("21"))
        assertEquals("twenty", restarted.read("20"))
        assertFalse(restarted.put("21", "late download"))
        root.deleteRecursively()
    }

    @Test fun leaseRenewsOnEntryAndExpiredDataCannotBeResurrected() = runTest {
        var time = 1000L
        val root = SystemPaths.createTempDirectory("auto-ttl")
        val cache = ReadingAutoCache(root) { time }
        cache.enter(); cache.opened("one", "two"); cache.put("one", "body", setOf("image"))
        val image = cache.images.resolve("one.image")
        cache.images.createDirectories(); image.writeBytes(byteArrayOf(1))
        assertTrue(cache.putAsset("image", image))
        time += ReadingAutoCache.TTL_MILLIS - 1
        cache.enter()
        time += ReadingAutoCache.TTL_MILLIS - 1
        assertEquals("body", cache.read("one")); assertNotNull(cache.asset("image"))
        time++
        cache.enter()
        assertNull(cache.read("one")); assertFalse(image.exists())
        root.deleteRecursively()
    }

    @Test fun openingOrDownloadingDoesNotExtendLeaseAndSweepProtectsOtherDirectories() = runTest {
        var time = 100L
        val root = SystemPaths.createTempDirectory("auto-sweep")
        val cache = ReadingAutoCache(root.resolve("auto")) { time }
        val offline = root.resolve("offline.json").also { it.writeText("manual") }
        cache.enter(); cache.opened("a", "b"); cache.put("a", "a")
        time += ReadingAutoCache.TTL_MILLIS - 1
        cache.opened("b", "c"); cache.put("b", "b")
        time++
        cache.sweep()
        assertFalse(cache.directory.exists()); assertTrue(offline.exists())
        root.deleteRecursively()
    }

    @Test fun evictionRemovesOwnedAssetsButPreservesSharedImagesAndRejectsLateWrites() = runTest {
        val root = SystemPaths.createTempDirectory("auto-assets")
        val cache = ReadingAutoCache(root) { 1L }
        cache.enter(); cache.opened("a", "b")
        cache.put("a", "a", setOf("shared", "old")); cache.put("b", "b", setOf("shared"))
        cache.images.createDirectories()
        val shared = cache.images.resolve("shared.image").also { it.writeBytes(byteArrayOf(1)) }
        val old = cache.images.resolve("old.image").also { it.writeBytes(byteArrayOf(2)) }
        cache.putAsset("shared", shared); cache.putAsset("old", old)
        cache.opened("b", "c"); cache.opened("c", "d")
        assertFalse(old.exists()); assertTrue(shared.exists())
        assertFalse(cache.putAsset("old", old))
        shared.delete(); assertNull(cache.asset("shared"))
        root.deleteRecursively()
    }
}
