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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.WorkoutBuilder
import nl.nielsnotsmurf.cleanui.logic.Progression
import nl.nielsnotsmurf.cleanui.logic.Routine
import nl.nielsnotsmurf.cleanui.logic.Routines
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Stepper
import nl.nielsnotsmurf.cleanui.ui.components.cables

/**
 * The built workout or an opened routine, grouped by equipment so the bar or handles change as
 * little as possible. Each exercise can be swapped for one in the same group on other equipment,
 * removed, or added from the library. A routine saves every change and shows its progression.
 */
@Composable
fun WorkoutPlanScreen(
    state: CleanUiState,
    workout: BuiltWorkout,
    /** The saved routine behind [workout], or null for a freshly built workout. */
    routine: Routine?,
    loadFor: (lifterId: String, exercise: ExerciseCard) -> Float,
    onPercent: (Int) -> Unit,
    onSubstitute: (ExerciseCard) -> Unit,
    onRemove: (ExerciseCard) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onSets: (Int) -> Unit,
    onReps: (Int) -> Unit,
    onNudgeLoad: (lifterId: String, exercise: ExerciseCard, steps: Int) -> Unit,
    onAdd: () -> Unit,
    onSaveAsRoutine: (String) -> Unit,
    onRename: (String) -> Unit,
    onProgression: (Progression, step: Int, weeks: Int) -> Unit
) {
    val lifters = state.activeLifters
    var naming by remember { mutableStateOf(false) }
    if (naming) {
        NameDialog(
            title = if (routine == null) "Save as routine" else "Rename routine",
            initial = workout.title,
            onDismiss = { naming = false },
            onDone = { name -> if (routine == null) onSaveAsRoutine(name) else onRename(name); naming = false }
        )
    }
    val minutes = WorkoutBuilder.estimatedMinutes(workout.exercises.size, lifters.size, workout.sets)
    val switches = WorkoutBuilder.equipmentSwitches(workout.exercises)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                workout.title.ifBlank { "New routine" },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (routine != null) TextButton(onClick = { naming = true }) { Text("Rename", color = CleanColors.Accent) }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${workout.exercises.size} exercises · about $minutes min · " +
                    if (switches == 0) "no equipment changes" else "$switches equipment change" + if (switches == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper("Sets", workout.sets.toString(), onMinus = { onSets(workout.sets - 1) }, onPlus = { onSets(workout.sets + 1) }, Modifier.weight(1f))
                Stepper("Reps", workout.reps.toString(), onMinus = { onReps(workout.reps - 1) }, onPlus = { onReps(workout.reps + 1) }, Modifier.weight(1f))
            }
            Stepper(
                "Load for every exercise, % of 1RM",
                "${workout.percent}%",
                onMinus = { onPercent(workout.percent - 1) },
                onPlus = { onPercent(workout.percent + 1) }
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadCalculator.PERCENT_PRESETS.forEach { p ->
                    CleanFilterChip("$p%", selected = workout.percent == p, onClick = { onPercent(p) })
                }
            }
            Text(
                "${WorkoutBuilder.REST_SECONDS}s rest between sets. Without a 1RM, the last load used is taken. Tap − or + next to a load to " +
                    "change it; a new % moves those loads too." + if (routine != null) " Changes are saved in the routine." else "",
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
                PlanRow(ex, state, lifters, loadFor, onSubstitute, onRemove, onNudgeLoad)
            }
            if (workout.exercises.isEmpty()) {
                Text("No exercises yet. Add them from the library.", style = MaterialTheme.typography.bodyMedium)
            }
            GhostButton("Add exercise", onClick = onAdd)

            if (routine != null) ProgressionCard(routine, onProgression)

            workout.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Danger) }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = "Start workout",
                onClick = onStart,
                enabled = workout.exercises.isNotEmpty(),
                loading = workout.isStarting,
                accent = true
            )
            if (routine == null && workout.exercises.isNotEmpty()) GhostButton("Save as routine", onClick = { naming = true })
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
    onRemove: (ExerciseCard) -> Unit,
    onNudgeLoad: (String, ExerciseCard, Int) -> Unit
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onNudgeLoad(l.id, ex, -1) }) { Text("−", color = CleanColors.Ink) }
                        LoadText(prefix + LoadCalculator.format(loadFor(l.id, ex), state.unit), ex.cables, color = l.color)
                        TextButton(onClick = { onNudgeLoad(l.id, ex, 1) }) { Text("+", color = CleanColors.Ink) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onSubstitute(ex) }) { Text("Swap", color = CleanColors.Accent) }
            TextButton(onClick = { onRemove(ex) }) { Text("Remove", color = CleanColors.Muted) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(CleanColors.Line))
    }
}

/** How a routine goes up by itself; applied each time the routine is finished. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionCard(routine: Routine, onProgression: (Progression, Int, Int) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(CleanColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("Progression")
        Text(
            "Raises the % of 1RM for every exercise when you finish this routine. Loads keep following each lifter's 1RM, " +
                "so a new 1RM test moves them as well.",
            style = MaterialTheme.typography.bodySmall,
            color = CleanColors.Muted
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CleanFilterChip("Off", routine.progression == Progression.OFF) { onProgression(Progression.OFF, routine.stepPercent, routine.everyWeeks) }
            CleanFilterChip("Every few weeks", routine.progression == Progression.WEEKS) { onProgression(Progression.WEEKS, routine.stepPercent, routine.everyWeeks) }
            CleanFilterChip("When all reps are done", routine.progression == Progression.ALL_REPS) { onProgression(Progression.ALL_REPS, routine.stepPercent, routine.everyWeeks) }
        }
        if (routine.progression != Progression.OFF) {
            SectionLabel("Step")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Routines.STEPS.forEach { st ->
                    CleanFilterChip("+$st%", routine.stepPercent == st) { onProgression(routine.progression, st, routine.everyWeeks) }
                }
            }
        }
        if (routine.progression == Progression.WEEKS) {
            SectionLabel("Every")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Routines.WEEK_OPTIONS.forEach { wk ->
                    CleanFilterChip("$wk wk", routine.everyWeeks == wk) { onProgression(routine.progression, routine.stepPercent, wk) }
                }
            }
        }
        Text(Routines.describe(routine), style = MaterialTheme.typography.bodyMedium)
        routine.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = CleanColors.Accent) }
    }
}

/** Asks for a routine name. */
@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.clickSounds(),
        containerColor = CleanColors.Background,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                placeholder = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CleanColors.Ink)
            )
        },
        confirmButton = {
            TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save", color = CleanColors.Ink) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
