import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.ui.settings.SettingsTransfer
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.tabs.media.BackupSettingsContent
import me.him188.ani.app.ui.settings.tabs.media.RestoreSettingsConfirmation
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_cancel
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.settings_mediasource_rss_copied_to_clipboard
import me.him188.ani.app.ui.lang.settings_storage_backup_op_backup_title
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore
import me.him188.ani.app.ui.lang.settings_storage_backup_op_restore_succees
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScope.WebSettingsBackup(theme: ThemeSettings, ui: UISettings,
                                   onTheme: (ThemeSettings) -> Unit, onUI: (UISettings) -> Unit) {
    val scope = rememberCoroutineScope()
    val copiedText = stringResource(Lang.settings_mediasource_rss_copied_to_clipboard)
    val restoredText = stringResource(Lang.settings_storage_backup_op_restore_succees)
    var dialog by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = ""
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "配置操作失败" } finally { busy = false } }
    }
    fun exported(): String {
        val retained = aniRead("retainedSettings").takeIf { it.isNotBlank() }?.let(SettingsTransfer::parse) ?: JsonObject(emptyMap())
        val media = webJson.parseToJsonElement(aniRead("mediaSettings").ifBlank { "{}" }).jsonObject
        return SettingsTransfer.export(retained, theme, ui, media)
    }
    suspend fun restore(text: String) {
        val parsed = SettingsTransfer.parse(text)
        val nextTheme = parsed["themeSettings"]?.takeUnless { it == JsonNull }?.let { webJson.decodeFromJsonElement<ThemeSettings>(it) } ?: theme
        val nextUI = parsed["uiSettings"]?.takeUnless { it == JsonNull }?.let { webJson.decodeFromJsonElement<UISettings>(it) } ?: ui
        val media = parsed["animecomicWebSettings"] ?: parsed["mediaSettings"]
        // Validate browser preferences before writing the settings envelope.
        if (media != null && media != JsonNull) request("mediaSettingsValidate", buildJsonObject { put("settings",media) }.toString())
        val previous = aniRead("retainedSettings").takeIf { it.isNotBlank() }?.let(SettingsTransfer::parse) ?: JsonObject(emptyMap())
        val retained = JsonObject(previous + parsed.filterValues { it != JsonNull } + mapOf("tokenStore" to JsonNull, "pikpakConfig" to JsonNull))
        val values = buildJsonObject {
            put("themeSettings",webJson.encodeToJsonElement(nextTheme).toString())
            put("uiSettings",webJson.encodeToJsonElement(nextUI).toString())
            put("originalTitle",nextUI.subjectAppearance.useOriginalTitle.toString())
            put("retainedSettings",retained.toString())
        }
        request("configApply", buildJsonObject { put("values", values); if (media != null && media != JsonNull) put("mediaSettings",media) }.toString())
        onTheme(nextTheme)
        onUI(nextUI)
        dialog = ""
        content = ""
        message = restoredText
    }
    BackupSettingsContent(
        onBackup = { run {
            content = exported()
            try {
                withTimeoutOrNull(5_000) {
                    request("configCopy", buildJsonObject { put("content", content) }.toString())
                } ?: error("剪贴板响应超时")
                content = ""
                message = copiedText
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Manual access is only needed when browser clipboard permission is unavailable.
                dialog = "export"
                message = "浏览器无法写入剪贴板，请全选下方内容并复制。"
            }
        } },
        onRestore = { content = ""; message = ""; dialog = "confirm" },
        enabled = !busy,
    )
    if (message.isNotEmpty() && dialog.isEmpty()) Text(message, Modifier.padding(16.dp))
    if (dialog == "confirm") RestoreSettingsConfirmation(
        onDismissRequest = { if (!busy) dialog = "" },
        onConfirm = { run {
            val clipboardText = try {
                withTimeoutOrNull(5_000) { request("configReadClipboard").raw }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (clipboardText.isNullOrBlank()) {
                dialog = "import"
                message = "浏览器未能读取剪贴板，请在下方粘贴配置。"
            } else {
                try {
                    restore(clipboardText)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Embedded browsers may expose a separate clipboard with stale content.
                    // Do not prefill it: it can contain unrelated private data.
                    content = ""
                    dialog = "import"
                    message = "${e.message ?: "配置导入失败"}。浏览器可能读取了旧的剪贴板内容，请在下方直接粘贴平板复制的备份并重试。"
                }
            }
        } },
        enabled = !busy,
    )
    if (dialog == "import" || dialog == "export") AlertDialog(
        onDismissRequest = { if (!busy) { dialog = ""; content = ""; message = "" } },
        title = { Text(stringResource(if (dialog == "import") Lang.settings_storage_backup_op_restore else Lang.settings_storage_backup_op_backup_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                OutlinedTextField(content, { content = it }, readOnly = dialog == "export", enabled = !busy,
                    label = { Text("配置") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp))
            }
        },
        confirmButton = {
            if (dialog == "import") TextButton({ run { restore(content) } }, enabled = !busy && content.isNotBlank()) {
                Text(stringResource(Lang.settings_danmaku_confirm))
            }
        },
        dismissButton = {
            TextButton({ dialog = ""; content = ""; message = "" }, enabled = !busy) {
                Text(stringResource(Lang.settings_danmaku_cancel))
            }
        },
    )
}
