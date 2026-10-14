/* Copyright (C) 2026 OpenAni and contributors. SPDX-License-Identifier: AGPL-3.0-only */
package me.him188.ani.app.ui.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.AnitorrentConfig
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.data.repository.user.TokenSave
import me.him188.ani.danmaku.ui.DanmakuConfig
import me.him188.ani.app.domain.mediasource.codec.ExportedMediaSourceDataList
import me.him188.ani.app.domain.mediasource.rss.RssMediaSourceArguments
import kotlin.test.*

class SettingsTransferTest {
    private val json = SettingsTransfer.json
    private val empty = JsonObject(emptyMap())

    @Test fun webBackupDecodesWithActualNativeSchema() {
        val theme = ThemeSettings(darkMode = DarkMode.DARK, seedColorValue = 18446744069414584320uL)
        val text = SettingsTransfer.export(empty, theme, UISettings(), empty)
        val native = json.decodeFromString<SettingsBackup>(text)
        assertEquals(theme, native.themeSettings)
        assertEquals(UISettings(), native.uiSettings)
        assertNull(native.tokenStore)
        assertNull(native.proxySettings) // Native restore skips null instead of resetting unsupported settings.
    }

    @Test fun nativeToWebToNativePreservesPlatformSettingsAndUnsignedColors() {
        val baseline = json.decodeFromString<SettingsBackup>(SettingsTransfer.export(empty, ThemeSettings(), UISettings(), empty))
        val native = baseline.copy(danmakuEnabled = false,
            themeSettings = ThemeSettings(useDynamicTheme = true, seedColorValue = 18446744069414584320uL))
        val imported = SettingsTransfer.parse(json.encodeToString(native))
        val theme = json.decodeFromJsonElement<ThemeSettings>(imported.getValue("themeSettings"))
        val result = json.decodeFromString<SettingsBackup>(SettingsTransfer.export(imported, theme.copy(darkMode = DarkMode.LIGHT), UISettings(), empty))
        assertEquals(false,result.danmakuEnabled)
        assertEquals(native.themeSettings!!.seedColorValue,result.themeSettings!!.seedColorValue)
        assertTrue(result.themeSettings!!.useDynamicTheme)
        assertEquals(DarkMode.LIGHT,result.themeSettings!!.darkMode)
    }

    @Test fun opaqueFieldsRetainPrecisionAndCredentialsAreNotStored() {
        val parsed = SettingsTransfer.parse("""{"themeSettings":{"future":18446744073709551615},"futureSetting":{"count":18446744073709551615},"tokenStore":{"secret":"test"},"pikpakConfig":{"password":"test"}}""")
        assertEquals(JsonNull,parsed["tokenStore"])
        assertEquals(JsonNull,parsed["pikpakConfig"])
        val result = Json.parseToJsonElement(SettingsTransfer.export(parsed, ThemeSettings(), UISettings(),empty)).jsonObject
        assertEquals(parsed["futureSetting"],result["futureSetting"])
        assertEquals("18446744073709551615", result.getValue("themeSettings").jsonObject.getValue("future").jsonPrimitive.content)
    }

    @Test fun legacyWebBackupAndInvalidInputs() {
        val imported = SettingsTransfer.parse("""{"version":1,"originalTitle":true,"uiSettings":{},"mediaSettings":{"webSeeds":false}}""")
        assertTrue(json.decodeFromJsonElement<UISettings>(imported.getValue("uiSettings")).subjectAppearance.useOriginalTitle)
        listOf("[]", "{}", "{\"mediaSources\":[]}", "{\"uiSettings\":42}", "{\"themeSettings\":{\"darkMode\":\"bad\"}}", "{\"danmakuEnabled\":\"true\"}").forEach {
            assertFails { SettingsTransfer.parse(it) }
        }
    }

    @Test fun appSerializerWithDefaultOmissionAndPlatformFieldsRoundTrips() {
        val baseline = json.decodeFromString<SettingsBackup>(SettingsTransfer.export(empty, ThemeSettings(), UISettings(), empty))
        val native = baseline.copy(
            danmakuEnabled = true,
            danmakuConfig = DanmakuConfig.Default,
            danmakuRegexFilters = emptyList(),
            anitorrentConfig = AnitorrentConfig.Default,
            videoScaffoldConfig = VideoScaffoldConfig.Default,
            tokenStore = TokenSave.Initial,
        )
        // SettingsViewModel uses encodeDefaults=false on Android and desktop.
        val appJson = Json { ignoreUnknownKeys = true }
        val imported = SettingsTransfer.parse("\uFEFF  " + appJson.encodeToString(native))
        val restored = appJson.decodeFromString<SettingsBackup>(
            SettingsTransfer.export(imported, ThemeSettings(), UISettings(), empty),
        )
        assertEquals(
            appJson.encodeToJsonElement(native.copy(tokenStore = null)),
            appJson.encodeToJsonElement(restored),
        )
    }

    @Test fun clipboardDiagnosticsDistinguishSourcesAndNeverEchoBackupContents() {
        val source = assertFailsWith<IllegalArgumentException> { SettingsTransfer.parse("""{"mediaSources":[]}""") }
        assertTrue(source.message!!.contains("数据源配置"))
        val unrelated = assertFailsWith<IllegalArgumentException> { SettingsTransfer.parse("""{"other":"private"}""") }
        assertFalse(unrelated.message!!.contains("数据源"))
        for (text in listOf(
            """{"tokenStore":{"secret":"private"},"uiSettings":""",
            """{"themeSettings":{"darkMode":"private"}}""",
        )) {
            val failure = assertFails { SettingsTransfer.parse(text) }
            assertFalse(failure.message!!.contains("private"))
            assertFalse(failure.message!!.contains("tokenStore"))
        }
    }

    @Test fun sourceClipboardUsesNativeEnvelopeAndRssRequiredArguments() {
        val text = combineSourceConfigurations(listOf("""{"exportedMediaSourceDataList":{"mediaSources":[{"factoryId":"rss","version":1,"arguments":{"name":"Test","searchConfig":{"searchUrl":"https://example.com/?q={keyword}"}}}]}}"""))
        val native = json.decodeFromString<ExportedMediaSourceDataList>(text)
        val rss = json.decodeFromJsonElement<RssMediaSourceArguments>(native.mediaSources.single().arguments)
        assertEquals("Test",rss.name)
        assertEquals("https://example.com/?q={keyword}",rss.searchConfig.searchUrl)
        assertEquals("",rss.description)
    }

    @Test fun unknownSourceConfigurationIsPreservedExactly() {
        val text = """{"mediaSources":[{"factoryId":"future","version":99,"arguments":{"value":18446744073709551615,"regex":"\\d+"},"extra":true}]}"""
        assertEquals(Json.parseToJsonElement(text),Json.parseToJsonElement(combineSourceConfigurations(listOf(text))))
    }
}
