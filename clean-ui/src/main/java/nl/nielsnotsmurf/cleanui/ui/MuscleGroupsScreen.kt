package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.WorkoutBuilder
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Tile

/** Body regions, in the order the classic muscle-group picker shows them. */
val BODY_REGIONS = listOf("Chest", "Back", "Shoulders", "Arms", "Legs", "Core")

/**
 * Pick one or more body regions and a time, and the app builds the workout. A single
 * exercise can still be picked from one region below.
 */
@Composable
fun MuscleGroupsScreen(
    state: CleanUiState,
    onBack: () -> Unit,
    onRegion: (String) -> Unit,
    onBuild: (groups: List<String>, minutes: Int) -> Unit,
    onTestOneRepMax: () -> Unit
) {
    val counts = remember(state.exercises) { state.exercises.groupingBy { it.region }.eachCount() }
    val active = state.activeLifters.map { it.id }
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var minutes by rememberSaveable { mutableIntStateOf(45) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Muscle groups", style = MaterialTheme.typography.headlineLarge)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("What do you want to train?")
            BODY_REGIONS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { region ->
                        val inRegion = state.exercises.filter { it.region == region }
                        val withMax = inRegion.count { ex -> active.any { state.oneRepMax(it, ex.id)?.isEstimate == false } }
                        val on = region in selected
                        Tile(
                            title = if (on) "✓ $region" else region,
                            subtitle = buildString {
                                append("${counts[region] ?: 0} exercises")
                                if (withMax > 0) append(" · $withMax with a 1RM")
                            },
                            onClick = { selected = if (on) selected - region else selected + region },
                            modifier = Modifier
                                .weight(1f)
                                .then(if (on) Modifier.border(2.dp, CleanColors.Accent, RoundedCornerShape(18.dp)) else Modifier)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            SectionLabel("How long?")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkoutBuilder.DURATIONS.forEach { m ->
                    CleanFilterChip("$m min", selected = minutes == m, onClick = { minutes = m })
                }
            }
            PrimaryButton(
                text = if (selected.isEmpty()) "Pick one or more groups" else "Build my workout",
                onClick = { onBuild(BODY_REGIONS.filter { it in selected }, minutes) },
                enabled = selected.isNotEmpty(),
                accent = true
            )
            Spacer(Modifier.padding(4.dp))
            SectionLabel("Or pick one exercise")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BODY_REGIONS.forEach { region -> CleanFilterChip(region, selected = false, onClick = { onRegion(region) }) }
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
