package nl.nielsnotsmurf.cleanui.data

import androidx.compose.runtime.Composable

/** The app's one [CleanUiStore]. */
@Composable
expect fun rememberCleanUiStore(): CleanUiStore
