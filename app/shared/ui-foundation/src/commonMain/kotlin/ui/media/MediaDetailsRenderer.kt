package me.him188.ani.app.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.cache_unknown
import me.him188.ani.app.ui.lang.media_subtitle_kind_closed
import me.him188.ani.app.ui.lang.media_subtitle_kind_closed_or_external_discovered
import me.him188.ani.app.ui.lang.media_subtitle_kind_embedded
import me.him188.ani.app.ui.lang.media_subtitle_kind_external_discovered
import me.him188.ani.app.ui.lang.media_subtitle_kind_external_provided
import me.him188.ani.app.ui.lang.media_subtitle_language_chinese_cantonese
import me.him188.ani.app.ui.lang.media_subtitle_language_chinese_simplified
import me.him188.ani.app.ui.lang.media_subtitle_language_chinese_traditional
import me.him188.ani.app.ui.lang.media_subtitle_language_english
import me.him188.ani.app.ui.lang.media_subtitle_language_japanese
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.topic.FileSize.Companion.Unspecified
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.jetbrains.compose.resources.stringResource
import kotlin.jvm.JvmName

object MediaDetailsRenderer {
    @JvmName("renderSubtitleKindNotNull")
    fun renderSubtitleKind(subtitleKind: SubtitleKind, strings: MediaDetailsStrings): String =
        renderSubtitleKind(subtitleKind as SubtitleKind?, strings)!!

    fun renderSubtitleKind(
        subtitleKind: SubtitleKind?,
        strings: MediaDetailsStrings,
    ): String? {
        return when (subtitleKind) {
            SubtitleKind.EMBEDDED -> strings.subtitleKindEmbedded
            SubtitleKind.CLOSED -> strings.subtitleKindClosed
            SubtitleKind.EXTERNAL_PROVIDED -> strings.subtitleKindExternalProvided
            SubtitleKind.EXTERNAL_DISCOVER -> strings.subtitleKindExternalDiscovered
            SubtitleKind.CLOSED_OR_EXTERNAL_DISCOVER -> strings.subtitleKindClosedOrExternalDiscovered
            null -> null
        }
    }

    fun renderSubtitleLanguages(
        subtitleKind: SubtitleKind?,
        subtitleLanguageIds: List<String>,
        strings: MediaDetailsStrings,
    ): String = buildString {
        if (subtitleKind != null) {
            append("[")
            append(renderSubtitleKind(subtitleKind, strings))
            append("] ")
        } else {
            if (subtitleLanguageIds.isEmpty()) {
                append(strings.unknown)
            }
        }

        for ((index, subtitleLanguageId) in subtitleLanguageIds.withIndex()) {
            append(renderSubtitleLanguage(subtitleLanguageId, strings))
            if (index != subtitleLanguageIds.size - 1) {
                append(" ")
            }
        }
    }

    @JvmName("renderSubtitleLanguagesTyped")
    fun renderSubtitleLanguages(
        subtitleKind: SubtitleKind?,
        subtitleLanguages: List<SubtitleLanguage>,
        strings: MediaDetailsStrings,
    ): String = buildString {
        if (subtitleKind != null) {
            append("[")
            append(renderSubtitleKind(subtitleKind, strings))
            append("] ")
        } else {
            if (subtitleLanguages.isEmpty()) {
                append(strings.unknown)
            }
        }

        for ((index, subtitleLanguage) in subtitleLanguages.withIndex()) {
            append(renderSubtitleLanguage(subtitleLanguage, strings))
            if (index != subtitleLanguages.size - 1) {
                append(" ")
            }
        }
    }
}

@Stable
fun Media.renderProperties(strings: MediaDetailsStrings): String {
    val properties = this.properties
    return listOfNotNull(
        properties.resolution,
        properties.subtitleLanguageIds.joinToString("/") { renderSubtitleLanguage(it, strings) }
            .takeIf { it.isNotBlank() },
        properties.size.takeIf { it != 0.bytes && it != Unspecified },
        properties.alliance,
    ).joinToString(" · ")
}
