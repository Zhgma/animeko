/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.xml.Html

/** Public release pages use the same configured network client without requiring REST API quota. */
internal suspend fun loadPublicForkReleases(
    client: ScopedHttpClient,
    currentVersion: String,
    channel: ReleaseClass,
): List<ForkRelease> {
    val releases = mutableListOf<ForkRelease>()
    for (page in 1..50) {
        val html = client.use {
            get(FORK_RELEASES_URL) {
                expectSuccess = true
                header("Accept", "text/html")
                header("Accept-Language", "en-US")
                parameter("page", page)
            }.bodyAsText()
        }
        val parsed = parsePublicReleasePage(html)
        releases.addAll(parsed.releases)
        if (!parsed.hasNextPage) break
        check(page < 50) { "Public release history exceeds the supported page limit" }
    }
    val installed = ForkVersion.parse(currentVersion) ?: return emptyList()
    return releases.distinctBy { it.tag }.filter { release ->
        val version = ForkVersion.parse(release.tag)
        version != null && version > installed && !release.draft && release.releaseClass().ordinal >= channel.ordinal
    }.map { release ->
        val html = client.use {
            get("$FORK_RELEASES_URL/expanded_assets/${release.tag}") {
                expectSuccess = true
                header("Accept", "text/html")
            }.bodyAsText()
        }
        release.copy(assets = parsePublicReleaseAssets(html, release.tag))
    }
}

internal data class PublicReleasePage(val releases: List<ForkRelease>, val hasNextPage: Boolean)

internal fun parsePublicReleasePage(html: String): PublicReleasePage {
    val document = Html.parse(html, FORK_RELEASES_URL)
    val sections = document.select("section[data-release-anchor]")
    check(sections.isNotEmpty()) { "Unrecognized public release page" }
    val releases = sections.mapNotNull { section ->
        val tag = section.attr("data-release-anchor").removePrefix("release-")
        if (ForkVersion.parse(tag) == null) return@mapNotNull null
        val hasReleaseLink = section.select("a[href]").any { it.attr("abs:href") == forkReleaseDetailsUrl(tag) }
        check(hasReleaseLink) { "Public release link does not match its tag" }
        val labels = section.select(".Label").map { it.text().trim() }
        ForkRelease(
            tag = tag,
            draft = "Draft" in labels,
            prerelease = "Pre-release" in labels,
            body = section.select(".markdown-body").firstOrNull()?.text().orEmpty(),
            publishedAt = section.select("relative-time[datetime]").firstOrNull()?.attr("datetime"),
        )
    }
    return PublicReleasePage(releases, document.select("a.next_page[rel=next]").isNotEmpty())
}

internal fun parsePublicReleaseAssets(html: String, tag: String): List<ForkAsset> {
    val document = Html.parse(html, FORK_RELEASES_URL)
    check(document.select("ul").isNotEmpty()) { "Unrecognized public release assets" }
    val prefix = "$FORK_RELEASES_URL/download/$tag/"
    return document.select("a[href]").mapNotNull { anchor ->
        val url = anchor.attr("abs:href")
        if (!url.startsWith(prefix)) return@mapNotNull null
        val name = url.removePrefix(prefix)
        if (name.isEmpty() || '/' in name || '?' in name || '#' in name) return@mapNotNull null
        ForkAsset(name, url)
    }.distinctBy { it.name }
}
