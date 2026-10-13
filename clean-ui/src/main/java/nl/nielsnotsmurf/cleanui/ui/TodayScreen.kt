package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.ConnectionPill
import nl.nielsnotsmurf.cleanui.ui.components.ExerciseRow
import nl.nielsnotsmurf.cleanui.ui.components.Tile
import nl.nielsnotsmurf.cleanui.ui.components.LifterChip
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    state: CleanUiState,
    onToggleLifter: (String) -> Unit,
    onPickExercise: () -> Unit,
    onJustLift: () -> Unit,
    onOpenExercise: (ExerciseCard) -> Unit,
    onMuscleGroups: () -> Unit,
    onAssessment: () -> Unit,
    onManageProfiles: () -> Unit,
    onRowShown: (ExerciseCard) -> Unit
) {
    val active = state.activeLifters.map { it.id }.toSet()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Train", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                ConnectionPill(state.connection)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CleanColors.Surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Who's training", Modifier.weight(1f))
                    TextButton(onClick = onManageProfiles) { Text("Manage", color = CleanColors.Ink) }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.lifters.forEach { l ->
                        LifterChip(l, selected = l.id in active, onClick = { onToggleLifter(l.id) })
                    }
                }
                Text(
                    text = when (state.activeLifters.size) {
                        0 -> "Tap a name to start"
                        1 -> "${state.activeLifters[0].name} is training solo"
                        else -> "Up to 4 people. Sets rotate " + state.activeLifters.joinToString(" → ") { it.name }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { PrimaryButton("Pick an exercise", onClick = onPickExercise) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("Just Lift", "Start lifting now", onJustLift, Modifier.weight(1f))
                Tile("Muscle groups", "Browse by area", onMuscleGroups, Modifier.weight(1f))
                Tile("1RM test", "Find your max", onAssessment, Modifier.weight(1f))
            }
        }

        if (state.recentExercises.isNotEmpty()) {
            item { SectionLabel("Recent", Modifier.padding(top = 8.dp)) }
            items(state.recentExercises, key = { it.id }) { ex ->
                LaunchedEffect(ex.id, active) { onRowShown(ex) }
                ExerciseRow(
                    exercise = ex,
                    subtitle = listOf(ex.equipment, ex.muscleGroup).filter { it.isNotBlank() }.joinToString(" · "),
                    lifters = state.activeLifters,
                    oneRepMaxFor = { id -> state.oneRepMax(id, ex.id) },
                    unit = state.unit,
                    onClick = { onOpenExercise(ex) }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
