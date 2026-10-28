package nl.nielsnotsmurf.webapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.NavController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.webOneRepMaxJson
import nl.nielsnotsmurf.cleanui.ui.CleanApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    MainScope().launch {
        webOneRepMaxJson = fetchText("one_rep_max_map.json")
        val library = ExerciseLibrary.parse(fetchText("exercise_dump.json") ?: "[]")
        val gateway = WebTrainerGateway(library)
        hideLoadingText()
        ComposeViewport {
            CleanApp(
                gateway = gateway,
                activeWorkoutContent = { nav -> NotOnWebYet("This lifting screen", nav) },
                justLiftContent = { nav -> NotOnWebYet("Just Lift", nav) },
                classicContent = { screen, nav -> NotOnWebYet(screen.title ?: "This screen", nav) }
            )
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
        Text("The web beta gets it in a later phase.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}
