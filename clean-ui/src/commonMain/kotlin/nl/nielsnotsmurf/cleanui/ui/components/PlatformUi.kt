package nl.nielsnotsmurf.cleanui.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale

// Small UI pieces that Android and the browser do with their own APIs.

/** The system back gesture or button (Android); the browser's back button is not wired yet. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** An image from the internet, shown once it has loaded. */
@Composable
expect fun RemoteImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
)

/** A looping demo clip of an exercise, with [thumbnailUrl] while it loads. */
@Composable
expect fun DemoVideo(url: String, modifier: Modifier = Modifier, thumbnailUrl: String? = null)

/**
 * Returns an action that asks for the device's own screen lock and runs [onConfirmed]
 * when it's given; without one, a plain "are you sure" instead.
 */
@Composable
expect fun rememberScreenLockCheck(title: String, onConfirmed: () -> Unit): () -> Unit

/** The GitHub feedback form with the app version and device filled in. */
@Composable
expect fun rememberFeedbackUrl(): String

/** Whether [p] lies inside [path] (body map taps). */
internal expect fun pathContains(path: Path, p: Offset): Boolean
