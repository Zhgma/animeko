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
import me.him188.ani.datasources.api.topic.Resolution
import me.him188.ani.datasources.api.topic.SubtitleLanguage
import org.jetbrains.compose.resources.stringResource
import kotlin.jvm.JvmName

@Immutable
class MediaDetailsStrings(
    val subtitleKindEmbedded: String,
    val subtitleKindClosed: String,
    val subtitleKindExternalProvided: String,
    val subtitleKindExternalDiscovered: String,
    val subtitleKindClosedOrExternalDiscovered: String,
    val unknown: String,
    val subtitleLanguageChineseCantonese: String,
    val subtitleLanguageChineseSimplified: String,
    val subtitleLanguageChineseTraditional: String,
    val subtitleLanguageJapanese: String,
    val subtitleLanguageEnglish: String,
)

@Composable
fun rememberMediaDetailsStrings(): MediaDetailsStrings {
    val subtitleKindEmbedded = stringResource(Lang.media_subtitle_kind_embedded)
    val subtitleKindClosed = stringResource(Lang.media_subtitle_kind_closed)
    val subtitleKindExternalProvided = stringResource(Lang.media_subtitle_kind_external_provided)
    val subtitleKindExternalDiscovered = stringResource(Lang.media_subtitle_kind_external_discovered)
    val subtitleKindClosedOrExternalDiscovered = stringResource(Lang.media_subtitle_kind_closed_or_external_discovered)
    val unknown = stringResource(Lang.cache_unknown)
    val subtitleLanguageChineseCantonese = stringResource(Lang.media_subtitle_language_chinese_cantonese)
    val subtitleLanguageChineseSimplified = stringResource(Lang.media_subtitle_language_chinese_simplified)
    val subtitleLanguageChineseTraditional = stringResource(Lang.media_subtitle_language_chinese_traditional)
    val subtitleLanguageJapanese = stringResource(Lang.media_subtitle_language_japanese)
    val subtitleLanguageEnglish = stringResource(Lang.media_subtitle_language_english)

    return remember(
        subtitleKindEmbedded,
        subtitleKindClosed,
        subtitleKindExternalProvided,
        subtitleKindExternalDiscovered,
        subtitleKindClosedOrExternalDiscovered,
        unknown,
        subtitleLanguageChineseCantonese,
        subtitleLanguageChineseSimplified,
        subtitleLanguageChineseTraditional,
        subtitleLanguageJapanese,
        subtitleLanguageEnglish,
    ) {
        MediaDetailsStrings(
            subtitleKindEmbedded = subtitleKindEmbedded,
            subtitleKindClosed = subtitleKindClosed,
            subtitleKindExternalProvided = subtitleKindExternalProvided,
            subtitleKindExternalDiscovered = subtitleKindExternalDiscovered,
            subtitleKindClosedOrExternalDiscovered = subtitleKindClosedOrExternalDiscovered,
            unknown = unknown,
            subtitleLanguageChineseCantonese = subtitleLanguageChineseCantonese,
            subtitleLanguageChineseSimplified = subtitleLanguageChineseSimplified,
            subtitleLanguageChineseTraditional = subtitleLanguageChineseTraditional,
            subtitleLanguageJapanese = subtitleLanguageJapanese,
            subtitleLanguageEnglish = subtitleLanguageEnglish,
        )
    }
}

fun renderSubtitleLanguage(id: String, strings: MediaDetailsStrings): String {
    return SubtitleLanguage.tryParse(id)?.let { renderSubtitleLanguage(it, strings) } ?: id
}

fun renderSubtitleLanguage(language: SubtitleLanguage, strings: MediaDetailsStrings): String {
    return when (language) {
        SubtitleLanguage.ChineseCantonese -> strings.subtitleLanguageChineseCantonese
        SubtitleLanguage.ChineseSimplified -> strings.subtitleLanguageChineseSimplified
        SubtitleLanguage.ChineseTraditional -> strings.subtitleLanguageChineseTraditional
        SubtitleLanguage.Japanese -> strings.subtitleLanguageJapanese
        SubtitleLanguage.English -> strings.subtitleLanguageEnglish
        SubtitleLanguage.ParseError -> strings.unknown
        is SubtitleLanguage.Other -> language.displayName
    }
}

fun renderResolution(id: String): String {
    return Resolution.tryParse(id)?.displayName ?: id
}
