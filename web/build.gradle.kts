import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "animecomic.js" }
            testTask { useKarma { useChromeHeadless() } }
        }
        binaries.executable()
    }
    sourceSets {
        wasmJsTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        commonMain {
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/media/cache/engine")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/tools")
            kotlin.srcDir("../utils/platform/src/commonMain/kotlin")
            kotlin.include("MediaCacheEngineKey.kt", "Progress.kt", "topic/FileSize.kt", "Strings.kt")
            kotlin.srcDir("../datasource/api/src/commonMain/kotlin")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/episode")
            kotlin.include("PackedDate.kt", "CompletedEpisodeDates.kt")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/mediasource/web")
            kotlin.include("source/Browse.kt")
            kotlin.srcDir("../utils/xml/src/commonMain/kotlin")
            kotlin.srcDir("../utils/serialization/src/commonMain/kotlin")
            kotlin.srcDir("../utils/ktor-client/src/commonMain/kotlin")
            kotlin.srcDir("../utils/jsonpath/src/commonMain/kotlin")
            kotlin.srcDir("../utils/intellij-annotations/src/commonMain/kotlin")
            kotlin.include("format/SelectorSubjectFormat.kt", "format/SelectorChannelFormat.kt", "format/SelectorFormat.kt", "WebBrowseModels.kt",
                "Xml.kt", "DocumentPort.kt", "BigNum.kt", "EpisodeSort.kt", "EpisodeType.kt", "topic/EpisodeRange.kt", "UrlHelpers.kt", "JsonPath.kt", "Language.kt")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui/foundation/layout")
            kotlin.include("WindowAdaptiveInfo.kt", "PaddingValues.kt", "animecomic/web/resources/**")
            dependencies {
                implementation(compose.runtime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.ktor.client.core)
                implementation(libs.ksoup)
                implementation(libs.ksoup.kotlinx)
                implementation(libs.jsonpathkt.kotlinx)
                implementation(compose.foundation)
                implementation(compose.components.resources)
                implementation(libs.compose.material3.adaptive.core)
            }
        }
        wasmJsMain {
            kotlin.include("MangaSourceModels.kt", "MangaSourceSearch.kt", "MangaJson.kt", "MangaApiImageMigration.kt",
                "AdditionalMangaSources.kt", "VerifiedMangaSources.kt", "ManualResultsList.kt", "MediaSelectorTopBar.kt")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/models/danmaku")
            kotlin.srcDir("../app/shared/app-platform/src/commonMain/kotlin/utils")
            kotlin.srcDir("../utils/platform/src/commonMain/kotlin")
            kotlin.include("VideoScaffoldConfig.kt", "DanmakuFilterConfig.kt", "DanmakuRegexFilter.kt",
                "tabs/app/PlayerGroupContent.kt", "danmaku/DanmakuRegexFilterContent.kt",
                "framework/components/SliderItem.kt", "SteppedSlider.kt", "NumberFormats.kt")
            kotlin.srcDir("../app/shared/video-player/src/commonMain/kotlin/ui")
            kotlin.srcDir("../app/shared/src/commonMain/kotlin/ui/subject/episode/video/topbar")
            kotlin.srcDir("../app/shared/src/commonMain/kotlin/ui/subject/episode/details")
            kotlin.srcDir("../app/shared/src/commonMain/kotlin/ui/subject/episode")
            kotlin.include("EpisodePlaybackItems.kt", "EpisodeStill.kt", "icons/PlayingIcon.kt", "EpisodeDetailsScaffold.kt", "EpisodeScreenLayout.kt", "progress/PlayerControllerBarContent.kt", "KeepLayoutWhenHidden.kt")
            kotlin.include("PlayerScreenLayout.kt", "EpisodePlayerTitle.kt", "foundation/TextWithBorder.kt")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/repository/media")
            kotlin.include("DefaultMediaSubscriptions.kt", "tabs/media/source/MangaSubscriptionPresentation.kt", "tabs/media/source/AddMediaSubscriptionDialog.kt")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/repository/subject")
            kotlin.include("EpisodeTypeMapping.kt")
            kotlin.srcDir("../app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediaselect")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/episode/list")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/collection/progress")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/media/selector")
            kotlin.include("summary/MediaSelectorSummaryBannerContent.kt", "MediaSelectorMode.kt", "selector/WebSourceCardContent.kt", "common/MediaSelectorChrome.kt", "MediaSelectorChrome.kt", "StandardBrowseEpisodeIndex.kt",
                "tabs/media/SourceKindPreferenceItem.kt", "SelectEpisodeButtonsContent.kt", "SubjectProgressButtonContent.kt",
                "EpisodeListDialogContent.kt", "EpisodeSortSquareButtonContent.kt", "LongClickButtons.kt")
            kotlin.srcDir("../app/shared/ui-exploration/src/commonMain/kotlin/ui/exploration/trends")
            kotlin.srcDir("../app/shared/ui-exploration/src/commonMain/kotlin/ui/exploration/followed")
            kotlin.srcDir("../app/shared/ui-exploration/src/commonMain/kotlin/ui/exploration/schedule")
            kotlin.srcDir("../app/shared/thirdparty/placeholder/src/commonMain/kotlin")
            kotlin.include("Placeholder.kt", "PlaceholderMaterial3.kt", "PlaceholderHighlight.kt", "PlaceholderHighlightMaterial3.kt")
            kotlin.include("ScheduleLayout.kt", "ScheduleDayColumn.kt", "AiringScheduleItemPresentation.kt", "AiringScheduleDateRange.kt",
                "HorizontalScrollControlScaffold.kt", "HorizontalScrollControlScaffoldOnDesktop.kt", "PagerTabIndicatorOffset.kt")
            kotlin.srcDir("../app/shared/ui-download/src/commonMain/kotlin/ui/download/components")
            kotlin.srcDir("../app/shared/ui-download/src/commonMain/kotlin/ui/download/subject")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/media/cache/engine")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/tools")
            kotlin.include("DownloadRowContent.kt", "DownloadOverallStats.kt", "DownloadFilterAndSortBar.kt", "DownloadSelectionState.kt", "DownloadSelectionToolbar.kt",
                "SubjectDownloadGroupCard.kt", "SubjectDownloadGroup.kt", "DownloadItem.kt", "DownloadStatus.kt", "SubjectDownloadsSummary.kt",
                "MediaStats.kt")
            kotlin.include("TrendingRankDecorations.kt", "TrendingSubjectsCarouselContent.kt", "FollowedSubjectItemContent.kt", "ScheduleItem.kt", "NavTitleHeader.kt")
            kotlin.include("me/him188/ani/client/apis/EpisodesAniApi.kt", "me/him188/ani/client/apis/HomeAniApi.kt", "me/him188/ani/client/apis/ScheduleAniApi.kt")
            kotlin.srcDir("../app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediaselect/manual")
            kotlin.include("ManualEpisodeContent.kt", "ManualSourceSelectorContent.kt")
            kotlin.srcDir("../utils/xml/src/nativeMain/kotlin")
            kotlin.include("DocumentPort.native.kt")
            // Compile the App's generated API client and models without maintaining a second protocol.
            kotlin.srcDir("../client/src/commonMain/gen")
            kotlin.include("me/him188/ani/client/models/**", "me/him188/ani/client/infrastructure/**", "me/him188/ani/client/auth/**", "me/him188/ani/client/apis/SubjectsAniApi.kt")
            kotlin.include("me/him188/ani/client/apis/UserAniApi.kt", "me/him188/ani/client/apis/UserProfileAniApi.kt")
            kotlin.include("components/SubjectCollectionTypeButton.kt", "components/EditCollectionTypeMenu.kt", "components/SubjectCollectionAction.kt", "framework/components/TextFieldItem.kt", "effects/Focus.kt", "account/ProfileNickname.kt")
            kotlin.include("me/him188/ani/client/apis/TrendsAniApi.kt", "me/him188/ani/client/apis/PlaybackHistoryAniApi.kt")
            kotlin.srcDir("../app/shared/src/commonMain/kotlin/ui/playback")
            kotlin.srcDir("../app/shared/app-platform/src/commonMain/kotlin/tools")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/tools")
            kotlin.include("PlaybackHistoryItemContent.kt", "TimeFormatter.kt", "WeekFormatter.kt", "formatDateTime.kt")
            kotlin.srcDir("../app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediaselect/common")
            kotlin.srcDir("../app/shared/ui-mediaselect/src/commonMain/kotlin/ui/mediaselect/bt")
            kotlin.srcDir("../app/shared/ui-comment/src/commonMain/kotlin/ui/comment")
            kotlin.include("CommentItemLayout.kt", "SubjectCommentsHeader.kt", "tabs/media/source/SubscriptionItemContent.kt", "tabs/media/source/MediaSourceItemContent.kt")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui")
            kotlin.srcDir("../app/shared/thirdparty/reorderable/src/commonMain/kotlin")
            kotlin.srcDir("../utils/platform/src/commonMain/kotlin/annotations")
            kotlin.srcDir("../datasource/api/src/commonMain/kotlin/source")
            kotlin.include("media/MediaPreferenceLabels.kt", "org/burnoutcrew/reorderable/**", "SerializationOnlyConstructor.kt", "TestOnly.kt", "MediaSourceKind.kt")
            kotlin.include("Resolution.kt", "SubtitleLanguage.kt", "MediaPreference.kt", "MediaSelectionSeetings.kt", "framework/components/SorterItem.kt", "tabs/media/MediaPreferenceItems.kt")
            // Reference a small allowlist in the original App directories; the native build is unchanged.
            kotlin.srcDir("../app/shared/ui-settings/src/commonMain/kotlin/ui/settings")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/search")
            kotlin.srcDir("../app/shared/app-platform/src/commonMain/kotlin/navigation")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui/subject")
            kotlin.srcDir("../app/shared/ui-comment/src/commonMain/kotlin/ui/rating")
            kotlin.srcDir("../app/shared/ui-exploration/src/commonMain/kotlin/ui/exploration/search")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/details/components")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui/theme")
            kotlin.srcDir("../app/shared/ui-onboarding/src/commonMain/kotlin/ui/login")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/models/preference")
            kotlin.srcDir("../app/shared/app-platform/src/commonMain/kotlin/ui/theme")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/manga")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/details/layout")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/details/sections")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/repository/manga")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/session/auth")
            kotlin.srcDir("../app/shared/ui-subject/src/commonMain/kotlin/ui/subject/collection")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui/subject")
            kotlin.include("SubjectStatusStrings.kt", "AiringLabel.kt", "SubjectAiringInfo.kt", "SubjectTotalEpisodeText.kt", "SubjectProgressInfo.kt", "SubjectRendering.kt", "State.kt")
            kotlin.include("subject/SubjectProgressState.kt", "LocalEpisodeProgressSettings.kt", "LocalSubjectAppearanceSettings.kt", "EpisodeListItem.kt", "EpisodesSection.kt", "EpisodePaging.kt")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/domain/episode")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/models/subject")
            kotlin.srcDir("../app/shared/app-data/src/commonMain/kotlin/data/repository/subject")
            kotlin.include("EpisodeBroadcastSchedule.kt", "SubjectRecurrence.kt", "SubjectRecurrenceMapping.kt", "EpisodeListColors.kt")
            kotlin.srcDir("../app/shared/ui-adaptive/src/commonMain/kotlin/ui/adaptive")
            kotlin.srcDir("../app/shared/ui-foundation/src/commonMain/kotlin/ui/foundation")
            kotlin.srcDir("../datasource/api/src/commonMain/kotlin/topic")
            kotlin.include("DefaultSeedColor.kt", "TrackingCollectionLayout.kt", "SubjectDetailsLayoutParams.kt",
                "MangaRequestRouting.kt", "BookSearchRequest.kt", "BangumiMangaCollectionLoader.kt", "MangaCommentsParser.kt", "OAuthPolling.kt", "animecomic/web/resources/**",
                "MangaCollectionContent.kt", "MangaCollectionUiState.kt", "CollectionPageLayout.kt",
                "AniTopAppBar.kt", "navigation/Items.kt", "navigation/NavigationSuite.kt",
                "navigation/AniNavigationSuiteScaffold.kt", "Modifiers.kt", "TopAppBarColor.kt",
                "layout/WindowSizeClasses.kt", "layout/paddingIfNotEmpty.kt",
                "input/ActiveInputSource.kt", "widgets/PullToRefresh.kt", "UnifiedCollectionType.kt",
                "BtResourceListContent.kt", "BtPanelContent.kt", "BtResourcesPageTestTags.kt", "WatchingEpisode.kt", "WatchingEpisodeContent.kt", "MediaSelectorSearchField.kt", "Main.kt", "Browser.kt", "Web*.kt", "platform/**",
                "tabs/media/BackupSettingsContent.kt", "SettingsTransfer.kt", "SourceConfigurationTransfer.kt", "UISettings.kt", "MainScreenPage.kt", "tabs/app/AppearanceGroupContent.kt", "tabs/media/source/MediaSourceSubscriptionGroupFrame.kt", "widgets/NsfwMask.kt", "SearchOptions.kt", "SettingsTab.kt", "SettingsCategoryItems.kt", "account/SelfInfoBannerContent.kt", "account/ProfileAccountItems.kt", "rendering/P2p.kt", "tabs/theme/ThemeGroupContent.kt",
                "SubjectSearchInput.kt", "SearchSubjectTypeSelector.kt", "SearchResultsToolbar.kt", "SearchPageColumn.kt",
                "SubjectScoreLabel.kt", "SubjectCoverCardContent.kt", "SubjectGridLayoutParams.kt", "layout/CarouselItem.kt", "IconButton.kt",
                "widgets/SelectableDropdownMenuItem.kt", "icons/Background_dot_large_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.kt", "icons/Gallery_thumbnail_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.kt",
                "SubjectCollectionCard.kt", "FiveRatingStars.kt", "RatingHistogramValues.kt", "SubjectRatingValues.kt", "SubjectItem.kt", "SubjectDetailsScaffold.kt", "SubjectTextSections.kt", "SubjectDetailsHeaderContent.kt", "SettingsNavigationItem.kt",
                "EmailLoginStartContent.kt", "EmailLoginVerifyContent.kt", "EmailLoginScreenLayout.kt", "ThirdPartyLoginMethods.kt",
                "OAuthPlatform.kt", "ThemeSettings.kt", "PlatformTypography.kt",
                "icons/OAuthPlatformIcon.kt", "icons/AniIcons.kt", "icons/BangumiNext.kt", "icons/GithubMark.kt",
                "text/ProvideTextStyleContentColor.kt", "widgets/TopAppBarGoBackButton.kt", "MangaCollectionController.kt", "SettingsTabContent.kt", "tabs/theme/DarkModeSelectPanel.kt", "tabs/theme/ThemePreviewPanel.kt",
                "tabs/theme/ThemePalette.kt", "tabs/theme/ColorButton.kt", "framework/components/SettingsScope.kt",
                "framework/components/DropdownItem.kt", "framework/components/TextItem.kt", "framework/components/SwitchItem.kt", "theme/Colors.kt", "theme/BlackBackground.kt", "theme/SystemDarkTheme.kt", "ThemeColorOptions.kt", "widgets/HeroIcon.kt", "session/SelfAvatarContent.kt", "avatar/AvatarImage.kt", "account/ProfilePopupContent.kt", "account/AccountLogoutDialog.kt", "account/ProfilePopupContainer.kt")
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(compose.components.resources)
                implementation(libs.compose.material3)
                implementation(libs.compose.material.icons.extended)
                implementation(libs.materialkolor)
                implementation(libs.compose.window.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation("io.ktor:ktor-client-js:${libs.versions.ktor.get()}")
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.compose.material3.adaptive.core)
                implementation(libs.compose.material3.adaptive.navigation.suite)
                implementation(project(":app-language"))
            }
        }
    }
}
compose.resources { packageOfResClass = "animecomic.web.resources" }
tasks.named<Copy>("wasmJsProcessResources") {
    from("../app/desktop/src/main/composeResources/drawable/a_round.png") { rename { "app-icon.png" } }
}
kotlin.compilerOptions.optIn.addAll("androidx.compose.material3.ExperimentalMaterial3Api",
    "kotlin.contracts.ExperimentalContracts",
    "androidx.compose.foundation.ExperimentalFoundationApi", "androidx.compose.foundation.layout.ExperimentalLayoutApi", "kotlin.experimental.ExperimentalTypeInference",
    "androidx.compose.ui.ExperimentalComposeUiApi", "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
