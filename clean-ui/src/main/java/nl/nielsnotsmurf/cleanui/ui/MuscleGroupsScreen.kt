package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Tile

/** Body regions, in the order the classic muscle-group picker shows them. */
val BODY_REGIONS = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

/** Pick a body region, then an exercise in it. Loads come from each lifter's 1RM as usual. */
@Composable
fun MuscleGroupsScreen(
    state: CleanUiState,
    onBack: () -> Unit,
    onRegion: (String) -> Unit,
    onTestOneRepMax: () -> Unit
) {
    val counts = remember(state.exercises) { state.exercises.groupingBy { it.region }.eachCount() }
    val active = state.activeLifters.map { it.id }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Muscle groups", style = MaterialTheme.typography.headlineLarge)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Choose a muscle group")
            BODY_REGIONS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { region ->
                        val inRegion = state.exercises.filter { it.region == region }
                        val withMax = inRegion.count { ex -> active.any { state.oneRepMax(it, ex.id)?.isEstimate == false } }
                        Tile(
                            title = region,
                            subtitle = buildString {
                                append("${counts[region] ?: 0} exercises")
                                if (withMax > 0) append(" · $withMax with a 1RM")
                            },
                            onClick = { onRegion(region) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.padding(4.dp))
            Tile(
                title = "No 1RMs yet?",
                subtitle = "Run the 1RM test so starting weights fit each lifter.",
                onClick = onTestOneRepMax,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
