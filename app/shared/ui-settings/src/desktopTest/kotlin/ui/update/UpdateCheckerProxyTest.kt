/* SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateCheckerProxyTest {
    private val android = Platform.Android(Arch.ARMV8A)

    @Test fun rateLimitFallsBackAndWaitsForResetBeforeRetryingApi() = runTest {
        for (status in listOf(HttpStatusCode.Forbidden, HttpStatusCode.TooManyRequests)) {
            var now = 1_000_000L
            var apiRequests = 0
            val client = HttpClient(MockEngine { request ->
                when {
                    request.url.host == "api.github.com" -> {
                        apiRequests++
                        respond("""{"message":"API rate limit exceeded"}""", status,
                            headersOf("X-RateLimit-Reset", "4600"))
                    }
                    request.url.encodedPath.endsWith("expanded_assets/0.3.1") -> respond(assets("0.3.1"))
                    else -> respond(page("0.3.1"))
                }
            })
            try {
                val checker = UpdateChecker(client.asScopedHttpClient()) { now }
                repeat(2) {
                    val releases = checker.loadReleases(ReleaseClass.STABLE, "0.3.0")
                    val selected = selectForkRelease(releases, "0.3.0", ReleaseClass.STABLE, android, false, "default")!!
                    assertEquals("0.3.1", selected.name)
                    assertEquals("$FORK_RELEASES_URL/download/0.3.1/ani-0.3.1-arm64-v8a.apk", selected.downloadUrlAlternatives.single())
                }
                assertEquals(1, apiRequests)
                now = 4_600_001
                checker.loadReleases(ReleaseClass.STABLE, "0.3.0")
                assertEquals(2, apiRequests)
            } finally { client.close() }
        }
    }

    @Test fun successfulApiDoesNotRequestPublicPages() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.github.com", request.url.host)
            respond("[]", headers = headersOf("Content-Type", "application/json"))
        })
        try { assertTrue(UpdateChecker(client.asScopedHttpClient()).loadReleases(ReleaseClass.STABLE, "0.3.0").isEmpty()) }
        finally { client.close() }
    }

    @Test fun fallbackPaginatesAndHonorsPublishedChannelAndPackageIdentity() = runTest {
        val assetRequests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            when {
                request.url.host == "api.github.com" -> respond("unavailable", HttpStatusCode.BadGateway)
                "expanded_assets" in request.url.encodedPath -> {
                    assetRequests += request.url.encodedPath
                    respond(assets("0.3.1") + "<a href='https://evil.example/app.apk'>bad</a>")
                }
                request.url.parameters["page"] == "1" -> respond(page("0.4.0", "Pre-release", true) + section("0.5.0", "Draft"))
                else -> respond(page("0.3.1"))
            }
        })
        try {
            val releases = UpdateChecker(client.asScopedHttpClient()).loadReleases(ReleaseClass.STABLE, "0.3.0")
            assertEquals(listOf("/Zhgma/animeko/releases/expanded_assets/0.3.1"), assetRequests)
            assertEquals("0.3.1", selectForkRelease(releases, "0.3.0", ReleaseClass.STABLE, android, false, "default")?.name)
            assertEquals(null, selectForkRelease(releases, "0.3.0", ReleaseClass.STABLE, android, true, "default"))
            assertEquals(null, selectForkRelease(releases, "0.3.0", ReleaseClass.STABLE, android, false, "tv"))
        } finally { client.close() }
    }

    @Test fun cancellationIsNotConvertedIntoFallbackRequest() = runTest {
        var count = 0
        val client = HttpClient(MockEngine { count++; throw CancellationException("cancelled") })
        try {
            assertFailsWith<CancellationException> { UpdateChecker(client.asScopedHttpClient()).loadReleases(ReleaseClass.STABLE, "0.3.0") }
            assertEquals(1, count)
        } finally { client.close() }
    }

    @Test fun inaccessibleOrUnrecognizedFallbackRemainsAnError() = runTest {
        for (status in listOf(HttpStatusCode.OK, HttpStatusCode.ServiceUnavailable)) {
            val client = HttpClient(MockEngine { request ->
                if (request.url.host == "api.github.com") respond("limited", HttpStatusCode.Forbidden)
                else respond("<html>Sign in or verify your browser</html>", status)
            })
            try { assertFailsWith<Exception> { UpdateChecker(client.asScopedHttpClient()).loadReleases(ReleaseClass.STABLE, "0.3.0") } }
            finally { client.close() }
        }
    }

    @Test fun parserKeepsPrefixedTagsDatesAndEscapedTextAndRejectsForeignAssets() {
        val result = parsePublicReleasePage(page("v0.3.1"))
        assertFalse(result.hasNextPage)
        assertEquals("2026-10-26T07:11:56Z", result.releases.single().publishedAt)
        assertEquals("Fixed & improved", result.releases.single().body)
        val links = parsePublicReleaseAssets(assets("v0.3.1") + """
            <a href="https://evil.example/Zhgma/animeko/releases/download/v0.3.1/ani-0.3.1-arm64-v8a.apk">bad</a>
            <a href="/Zhgma/animeko/releases/download/0.1.0/ani-0.1.0-arm64-v8a.apk">old</a>
        """, "v0.3.1")
        assertEquals(1, links.size)
        assertTrue(links.single().url.startsWith("$FORK_RELEASES_URL/download/v0.3.1/"))
    }

    private fun page(tag: String, label: String = "Latest", next: Boolean = false) = section(tag, label) +
        if (next) "<a class='next_page' rel='next' href='?page=2'>Next</a>" else ""

    private fun section(tag: String, label: String) = """
        <section data-release-anchor="release-$tag">
          <a href="/Zhgma/animeko/releases/tag/$tag">Release</a><span class="Label">$label</span>
          <relative-time datetime="2026-10-26T07:11:56Z"></relative-time>
          <div class="markdown-body"><p>Fixed &amp; improved</p></div>
        </section>
    """

    private fun assets(tag: String) = """
        <ul><li><a href="/Zhgma/animeko/releases/download/$tag/ani-${tag.removePrefix("v")}-arm64-v8a.apk">APK</a></li></ul>
    """
}
