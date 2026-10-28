package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.await
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.theme.CleanColors

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val current by rememberUpdatedState(onBack)
    val entry = remember { BrowserBack.Entry() }
    entry.enabled = enabled
    entry.onBack = { current() }
    DisposableEffect(entry) {
        BrowserBack.add(entry)
        onDispose { BrowserBack.remove(entry) }
    }
}

/**
 * The browser's back button (and Android's back gesture in the installed web app) does what
 * Android's back does in the app: the innermost enabled handler gets it. One extra history
 * entry is kept while something can go back, so the page itself is left only from the root.
 */
private object BrowserBack {
    class Entry {
        var enabled = true
        var onBack: () -> Unit = {}
    }

    private val entries = mutableListOf<Entry>()
    private var guarded = false

    fun add(entry: Entry) {
        if (entries.isEmpty() && !guarded) { jsListen { onPop() }; jsPushGuard(); guarded = true }
        entries += entry
    }

    fun remove(entry: Entry) { entries -= entry }

    private fun onPop() {
        val top = entries.lastOrNull { it.enabled }
        if (top != null) { top.onBack(); jsPushGuard() } else { guarded = false; jsBack() }
    }
}

private fun jsListen(onPop: () -> Unit): Unit = js("window.addEventListener('popstate', () => onPop())")

private fun jsPushGuard(): Unit = js("history.pushState({ vitruvian: true }, '')")

private fun jsBack(): Unit = js("history.back()")

private fun jsFetchBytes(url: String): kotlin.js.Promise<JsAny?> =
    js("fetch(url).then(r => r.ok ? r.arrayBuffer() : null).then(b => b ? new Uint8Array(b) : null).catch(() => null)")

private fun jsLength(bytes: JsAny): Int = js("bytes.length")

private fun jsByte(bytes: JsAny, i: Int): Int = js("bytes[i]")

private suspend fun loadImage(url: String): ImageBitmap? = runCatching {
    val bytes = jsFetchBytes(url).await<JsAny?>() ?: return null
    val data = ByteArray(jsLength(bytes)) { jsByte(bytes, it).toByte() }
    org.jetbrains.skia.Image.makeFromEncoded(data).toComposeImageBitmap()
}.getOrNull()

@Composable
actual fun RemoteImage(url: String?, contentDescription: String?, modifier: Modifier, contentScale: ContentScale) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) { bitmap = url?.let { loadImage(it) } }
    bitmap?.let { Image(it, contentDescription, modifier, contentScale = contentScale) }
}

/** The browser shows the poster; a tap opens the clip itself in a new tab. */
@Composable
actual fun DemoVideo(url: String, modifier: Modifier, thumbnailUrl: String?) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2937))
            .clickable { uriHandler.openUri(url) },
        contentAlignment = Alignment.Center
    ) {
        RemoteImage(thumbnailUrl ?: muxThumbnail(url), null, Modifier.fillMaxSize(), ContentScale.Fit)
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Play demo video",
            tint = Color.White,
            modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
actual fun rememberScreenLockCheck(title: String, onConfirmed: () -> Unit): () -> Unit {
    val confirmed by rememberUpdatedState(onConfirmed)
    var ask by remember { mutableStateOf(false) }
    if (ask) {
        AlertDialog(
            onDismissRequest = { ask = false },
            modifier = Modifier.clickSounds(),
            containerColor = CleanColors.Background,
            title = { Text(title) },
            text = { Text("A browser has no screen lock, so the PIN can be removed without one.") },
            confirmButton = { TextButton(onClick = { ask = false; confirmed() }) { Text("Remove PIN", color = CleanColors.Danger) } },
            dismissButton = { TextButton(onClick = { ask = false }) { Text("Cancel") } }
        )
    }
    return { ask = true }
}

private fun jsUserAgent(): String = js("navigator.userAgent")

private fun jsEncode(value: String): String = js("encodeURIComponent(value)")

@Composable
actual fun rememberFeedbackUrl(): String = remember {
    "https://github.com/NielsNotsmurf/Cable-4-Vitruvian/issues/new?template=feedback.yml" +
        "&version=${jsEncode("web beta")}&device=${jsEncode(jsUserAgent())}"
}

internal actual fun pathContains(path: Path, p: Offset): Boolean = path.asSkiaPath().contains(p.x, p.y)
