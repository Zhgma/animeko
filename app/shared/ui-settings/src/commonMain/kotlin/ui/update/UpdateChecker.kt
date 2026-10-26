/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.update

import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.data.network.protocol.ReleaseClass
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.Arch
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

internal const val FORK_RELEASES_URL = "https://github.com/Zhgma/animeko/releases"
internal const val FORK_RELEASE_TAG_PREFIX = "$FORK_RELEASES_URL/tag/"

internal fun forkReleaseDetailsUrl(tag: String): String = "$FORK_RELEASE_TAG_PREFIX$tag"

/** Uses the configured application network client and the fork's published release assets. */
class UpdateChecker(
    private val client: ScopedHttpClient,
    private val nowMillis: () -> Long = ::currentTimeMillis,
) {
    private val apiRetryAfter = atomic(0L)

    suspend fun checkLatestVersion(
        releaseClass: ReleaseClass,
        currentVersion: String = currentAniBuildConfig.versionName,
    ): NewVersion? {
        val releases = loadReleases(releaseClass, currentVersion)
        return selectForkRelease(releases, currentVersion, releaseClass, currentPlatform(),
            currentAniBuildConfig.isDebug, currentAniBuildConfig.distroChannel)
    }

    internal suspend fun loadReleases(releaseClass: ReleaseClass, currentVersion: String): List<ForkRelease> {
        if (nowMillis() >= apiRetryAfter.value) {
            try {
                return loadApiReleases()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ClientRequestException && e.response.status.value in listOf(403, 429)) {
                    val headers = e.response.headers
                    val retryDelay = headers["Retry-After"]?.toLongOrNull()?.coerceIn(0, 86400)?.times(1000)
                    val reset = headers["X-RateLimit-Reset"]?.toLongOrNull()?.times(1000)
                    apiRetryAfter.value = maxOf(nowMillis() + 60_000,
                        retryDelay?.let { nowMillis() + it } ?: reset ?: (nowMillis() + 3_600_000))
                }
                logger<UpdateChecker>().warn { "Release API unavailable; checking public release pages (${e::class.simpleName})" }
            }
        }
        return loadPublicForkReleases(client, currentVersion, releaseClass)
    }

    private suspend fun loadApiReleases(): List<ForkRelease> {
        val releases = mutableListOf<ForkRelease>()
        for (page in 1..5) {
            val batch = client.use {
                val response = get("https://api.github.com/repos/Zhgma/animeko/releases") {
                    expectSuccess = true
                    header("Accept", "application/vnd.github+json")
                    header("X-GitHub-Api-Version", "2022-11-28")
                    parameter("per_page", 100); parameter("page", page)
                }
                releaseJson.decodeFromString<List<ForkRelease>>(response.bodyAsText())
            }
            releases.addAll(batch)
            if (batch.size < 100) break
        }
        return releases
    }
}

internal val releaseJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class ForkRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<ForkAsset> = emptyList(),
)

@Serializable
internal data class ForkAsset(val name: String, @SerialName("browser_download_url") val url: String)

internal fun ForkRelease.releaseClass(): ReleaseClass {
    val version = ForkVersion.parse(tag) ?: return ReleaseClass.ALPHA
    return when {
        !prerelease && version.pre.isEmpty() -> ReleaseClass.STABLE
        version.pre.firstOrNull()?.startsWith("rc") == true -> ReleaseClass.RC
        version.pre.firstOrNull()?.let { it.startsWith("alpha") || it.startsWith("dev") } == true -> ReleaseClass.ALPHA
        else -> ReleaseClass.BETA
    }
}

internal fun selectForkRelease(releases: List<ForkRelease>, current: String, channel: ReleaseClass,
    platform: Platform, debug: Boolean, distro: String): NewVersion? {
    val installed = ForkVersion.parse(current) ?: return null
    val eligible = releases.mapNotNull { release ->
        val version = ForkVersion.parse(release.tag) ?: return@mapNotNull null
        val releaseChannel = release.releaseClass()
        if (release.draft || version <= installed || releaseChannel.ordinal < channel.ordinal) return@mapNotNull null
        val names = forkAssetNames(release.tag.removePrefix("v"), platform, debug, distro)
        val asset = names.firstNotNullOfOrNull { name -> release.assets.firstOrNull { it.name == name &&
            it.url.startsWith("$FORK_RELEASES_URL/download/${release.tag}/") } } ?: return@mapNotNull null
        Triple(version, release, asset)
    }.sortedByDescending { it.first }
    val (_, latest, asset) = eligible.firstOrNull() ?: return null
    return NewVersion(latest.tag.removePrefix("v"), eligible.take(10).map { (_, release, _) ->
        Changelog(release.tag.removePrefix("v"), release.publishedAt.orEmpty(), release.body.orEmpty())
    }, listOf(asset.url), latest.publishedAt.orEmpty(), forkReleaseDetailsUrl(latest.tag))
}

/** Release names match ci-helper; Android debug packages are explicitly separated by suffix. */
internal fun forkAssetNames(version: String, platform: Platform, debug: Boolean, distro: String): List<String> {
    val arch = when (platform.arch) {
        Arch.X86_64 -> "x86_64"
        Arch.AARCH64, Arch.ARMV8A -> "aarch64"
        Arch.ARMV7A -> "armv7a"
    }
    return when (platform) {
        is Platform.Android -> {
            if (distro !in listOf("default", "tv")) return emptyList()
            val abi = when (platform.arch) {
                Arch.ARMV8A, Arch.AARCH64 -> "arm64-v8a"
                Arch.ARMV7A -> "armeabi-v7a"
                Arch.X86_64 -> "x86_64"
            }
            val prefix = if (distro == "tv") "ani-tv" else "ani"
            val suffix = if (debug) "-debug" else ""
            listOf("$prefix-$version-$abi$suffix.apk", "$prefix-$version-universal$suffix.apk")
        }
        is Platform.Windows -> listOf("ani-$version-windows-$arch.zip")
        is Platform.MacOS -> listOf("ani-$version-macos-$arch.${if (arch == "aarch64") "dmg" else "zip"}")
        is Platform.Linux -> listOf("ani-$version-linux-$arch.appimage")
        Platform.Ios -> emptyList()
    }
}

/** Numeric core and SemVer prerelease ordering prevent string-based version downgrades. */
internal data class ForkVersion(val core: List<Int>, val pre: List<String>) : Comparable<ForkVersion> {
    override fun compareTo(other: ForkVersion): Int {
        core.zip(other.core).forEach { (a, b) -> if (a != b) return a.compareTo(b) }
        if (pre.isEmpty() || other.pre.isEmpty()) return when {
            pre.isEmpty() && other.pre.isEmpty() -> 0
            pre.isEmpty() -> 1
            else -> -1
        }
        pre.zip(other.pre).forEach { (a, b) ->
            val an = a.toLongOrNull(); val bn = b.toLongOrNull()
            val comparison = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
            if (comparison != 0) return comparison
        }
        return pre.size.compareTo(other.pre.size)
    }
    companion object {
        fun parse(value: String): ForkVersion? {
            val match = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""").matchEntire(value) ?: return null
            val core = (1..3).map { match.groupValues[it].toIntOrNull() ?: return null }
            val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split('.') ?: emptyList()
            if (pre.any { it.isEmpty() }) return null
            return ForkVersion(core, pre)
        }
    }
}
