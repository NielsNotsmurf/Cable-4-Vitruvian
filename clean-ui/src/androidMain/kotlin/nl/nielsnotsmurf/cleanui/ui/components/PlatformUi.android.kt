package nl.nielsnotsmurf.cleanui.ui.components

import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
actual fun RemoteImage(url: String?, contentDescription: String?, modifier: Modifier, contentScale: ContentScale) =
    AsyncImage(model = url, contentDescription = contentDescription, contentScale = contentScale, modifier = modifier)

@Composable
actual fun rememberFeedbackUrl(): String {
    val context = LocalContext.current
    return remember(context) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
        Uri.parse("https://github.com/NielsNotsmurf/Cable-4-Vitruvian/issues/new").buildUpon()
            .appendQueryParameter("template", "feedback.yml")
            .appendQueryParameter("version", version)
            .appendQueryParameter("device", "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            .build().toString()
    }
}

// Regions are small in figure units; scale up so the integer region keeps its shape.
private const val HIT_SCALE = 8f

internal actual fun pathContains(path: Path, p: Offset): Boolean {
    val scaled = android.graphics.Path(path.asAndroidPath()).apply {
        transform(android.graphics.Matrix().apply { setScale(HIT_SCALE, HIT_SCALE) })
    }
    val clip = android.graphics.Region(-1000, -1000, 4000, 4000)
    val region = android.graphics.Region().apply { setPath(scaled, clip) }
    return region.contains((p.x * HIT_SCALE).toInt(), (p.y * HIT_SCALE).toInt())
}
