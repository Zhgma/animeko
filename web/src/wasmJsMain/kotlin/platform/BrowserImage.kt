@file:OptIn(ExperimentalWasmJsInterop::class)

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.suspendCancellableCoroutine
import me.him188.ani.utils.platform.BrowserPlatform
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.js.ExperimentalWasmJsInterop

val LocalPlatform = staticCompositionLocalOf { BrowserPlatform }
private val imageCache = linkedMapOf<String, ImageBitmap>()

/** Drawing in Compose preserves the native page's clipping and overlay order. */
@Composable
fun AsyncImage(model: String?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    error: Painter? = null, fallback: Painter? = null,
    alignment: Alignment = Alignment.Center, colorFilter: ColorFilter? = null,
) {
    val bitmap by produceState<ImageBitmap?>(imageCache[model], model) {
        value = imageCache[model]
        if (value == null) {
            val encoded = suspendCancellableCoroutine<String> { continuation ->
                loadBrowserImage(model.orEmpty()) { result -> if (continuation.isActive) continuation.resume(result) }
            }
            value = runCatching { Base64.decode(encoded).decodeToImageBitmap() }.getOrNull()
            value?.let {
                if (imageCache.size >= 80) imageCache.remove(imageCache.keys.first())
                imageCache[model.orEmpty()] = it
            }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, contentDescription, Modifier.fillMaxSize(), alignment = alignment, contentScale = contentScale, colorFilter = colorFilter)
        else if (error != null || fallback != null) Image((error ?: fallback)!!, contentDescription, Modifier.fillMaxSize(), alignment = alignment, contentScale = contentScale, colorFilter = colorFilter)
        else Icon(Icons.Rounded.BrokenImage, "封面暂不可用", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@JsFun("""(url, callback) => {
    if (!(url.startsWith('https://') || /^samples\/covers\/[\w.-]+$/.test(url))) { callback(''); return; }
    const source = globalThis.aniApi.image(url);
    fetch(source, {mode:source.startsWith('https://') ? 'cors' : 'same-origin', credentials:'omit', referrerPolicy:'no-referrer', signal:AbortSignal.timeout(20000)})
        .then(r => { if (!r.ok) throw new Error('Image unavailable'); return r.arrayBuffer(); })
        .then(buffer => {
            const bytes = new Uint8Array(buffer);
            if (bytes.length > 8 * 1024 * 1024) throw new Error('Image too large');
            let binary = '';
            for (let i = 0; i < bytes.length; i += 32768) binary += String.fromCharCode(...bytes.subarray(i, i + 32768));
            callback(btoa(binary));
        }).catch(() => callback(''));
}""")
private external fun loadBrowserImage(url: String, callback: (String) -> Unit)
