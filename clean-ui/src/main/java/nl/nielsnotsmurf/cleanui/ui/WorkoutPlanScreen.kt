package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.WorkoutBuilder
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.cables

/**
 * The built workout, grouped by equipment so the bar or handles change as little as possible.
 * Each exercise can be swapped for one in the same group on other equipment.
 */
@Composable
fun WorkoutPlanScreen(
    state: CleanUiState,
    workout: BuiltWorkout,
    loadFor: (lifterId: String, exercise: ExerciseCard) -> Float,
    onPercent: (Int) -> Unit,
    onSubstitute: (ExerciseCard) -> Unit,
    onRemove: (ExerciseCard) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    sets: Int = WorkoutBuilder.SETS,
    reps: Int = 10
) {
    val lifters = state.activeLifters
    val minutes = WorkoutBuilder.estimatedMinutes(workout.exercises.size, lifters.size, sets)
    val switches = WorkoutBuilder.equipmentSwitches(workout.exercises)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(workout.groups.joinToString(" & "), style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${workout.exercises.size} exercises · about $minutes min · " +
                    if (switches == 0) "no equipment changes" else "$switches equipment change" + if (switches == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted
            )
            SectionLabel("Load: % of 1RM")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadCalculator.PERCENT_PRESETS.forEach { p ->
                    CleanFilterChip("$p%", selected = workout.percent == p, onClick = { onPercent(p) })
                }
            }
            Text(
                "$sets × $reps each (from Settings), ${WorkoutBuilder.REST_SECONDS}s rest. Without a 1RM, the last load used is taken.",
                style = MaterialTheme.typography.bodySmall,
                color = CleanColors.Muted
            )

            var lastEquipment: String? = null
            workout.exercises.forEach { ex ->
                val equipment = ex.equipment.ifBlank { "Other" }
                if (!equipment.equals(lastEquipment, ignoreCase = true)) {
                    Spacer(Modifier.height(4.dp))
                    SectionLabel(equipment)
                    lastEquipment = equipment
                }
                PlanRow(ex, state, lifters, loadFor, onSubstitute, onRemove)
            }
            if (workout.exercises.isEmpty()) {
                Text("No exercises left. Go back to build a new workout.", style = MaterialTheme.typography.bodyMedium)
            }

            workout.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Danger) }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = "Start workout",
                onClick = onStart,
                enabled = workout.exercises.isNotEmpty(),
                loading = workout.isStarting,
                accent = true
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlanRow(
    ex: ExerciseCard,
    state: CleanUiState,
    lifters: List<UiLifter>,
    loadFor: (String, ExerciseCard) -> Float,
    onSubstitute: (ExerciseCard) -> Unit,
    onRemove: (ExerciseCard) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(ex.region, ex.muscleGroup).filter { it.isNotBlank() }.distinct().joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = CleanColors.Muted,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                lifters.forEach { l ->
                    val orm = state.oneRepMax(l.id, ex.id)
                    val prefix = if (orm == null || orm.isEstimate) "~" else ""
                    LoadText(prefix + LoadCalculator.format(loadFor(l.id, ex), state.unit), ex.cables, color = l.color)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onSubstitute(ex) }) { Text("Swap (other equipment)", color = CleanColors.Accent) }
            TextButton(onClick = { onRemove(ex) }) { Text("Remove", color = CleanColors.Muted) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CleanColors.Line))
    }
}
