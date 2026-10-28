package nl.nielsnotsmurf.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.NavController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.webOneRepMaxJson
import nl.nielsnotsmurf.cleanui.theme.CleanTheme
import nl.nielsnotsmurf.cleanui.ui.CleanApp
import nl.nielsnotsmurf.cleanui.ui.ClassicScreen

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        webOneRepMaxJson = fetchText("one_rep_max_map.json")
        val library = ExerciseLibrary.parse(fetchText("exercise_dump.json") ?: "[]")
        val gateway = WebTrainerGateway(library)
        val symbols = fetchBytes("fonts/symbols.ttf")
        hideLoadingText()
        ComposeViewport {
            // Symbols the default web font lacks (→ ✓ ‹ ›) come from a small fallback font.
            val fonts = LocalFontFamilyResolver.current
            var ready by remember { mutableStateOf(symbols == null) }
            LaunchedEffect(Unit) {
                if (symbols != null) runCatching { fonts.preload(FontFamily(Font("symbols", symbols))) }
                ready = true
            }
            if (!ready) return@ComposeViewport
            var intro by remember { mutableStateOf(!introSeen()) }
            CleanTheme {
                if (intro) return@CleanTheme WebIntro { intro = false }
                CleanApp(
                    gateway = gateway,
                    activeWorkoutContent = { nav -> NotOnWebYet("This lifting screen", nav) },
                    justLiftContent = { nav -> NotOnWebYet("Just Lift", nav) },
                    classicContent = { screen, nav ->
                        if (screen == ClassicScreen.SETTINGS) WebSettings() else NotOnWebYet(screen.title ?: "This screen", nav)
                    },
                    heartRateContent = { lifterId, onDismiss -> HeartRatePairing(gateway, lifterId, onDismiss) }
                )
            }
        }
    }
}

/** The screens the Android app still borrows from the classic layout (see clean-ui/FEATURES.md). */
@Composable
private fun NotOnWebYet(what: String, nav: NavController) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$what is in the Android app only for now.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Button(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}
