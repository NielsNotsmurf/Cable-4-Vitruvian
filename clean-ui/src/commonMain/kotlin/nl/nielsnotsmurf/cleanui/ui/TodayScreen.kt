package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.ui.components.cables
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.TrainingStats
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.ConnectionPill
import nl.nielsnotsmurf.cleanui.ui.components.Dot
import nl.nielsnotsmurf.cleanui.ui.components.LifterChip
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Tile
import nl.nielsnotsmurf.cleanui.ui.tour.TourTargets
import nl.nielsnotsmurf.cleanui.ui.tour.tourTarget
import nl.nielsnotsmurf.cleanui.platform.DateFormatter
import nl.nielsnotsmurf.cleanui.platform.nowMillis

private const val MINUTE_MS = 60_000L
private const val DAY_MS = 24 * 60 * MINUTE_MS

/**
 * The home screen: who's training, the training going on now (everything done with less than
 * five hours between exercises), live heart rate, then one main action (build a workout from
 * muscle groups), one exercise, recent exercises to do again, and the 1RMs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    state: CleanUiState,
    history: List<HistoryEntry>,
    heartRate: Int?,
    onToggleLifter: (String) -> Unit,
    onPickExercise: () -> Unit,
    onOpenExercise: (ExerciseCard) -> Unit,
    onBuild: (groups: List<String>, minutes: Int) -> Unit,
    onAssessment: () -> Unit,
    onManageProfiles: () -> Unit,
    onRetest: (lifterId: String, exerciseId: String) -> Unit,
    onRowShown: (ExerciseCard) -> Unit
) {
    val active = state.activeLifters.map { it.id }.toSet()
    // Ticks so the session ends on its own five hours after the last set.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE_MS)
            now = nowMillis()
        }
    }
    val session = remember(history, now) { TrainingStats.currentSession(history, now) }
    val standing = remember(state.oneRepMaxes, active) {
        TrainingStats.longestStanding(state.oneRepMaxes, state.activeLifters.map { it.id })
    }
    val exercisesById = remember(state.exercises) { state.exercises.associateBy { it.id } }
    val liftersById = remember(state.lifters) { state.lifters.associateBy { it.id } }
    val timeFormat = remember { DateFormatter("HH:mm") }
    val dayFormat = remember { DateFormatter("EEEE d MMMM") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        dayFormat.format(now).replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (heartRate != null) HeartRatePill(heartRate)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (session.isEmpty()) "Ready to train" else "Training",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Box(Modifier.tourTarget(TourTargets.CONNECTION)) { ConnectionPill(state.connection) }
                }
            }
        }

        item {
            Card(Modifier.tourTarget(TourTargets.WHO)) {
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

        if (session.isNotEmpty()) {
            item {
                CurrentTraining(
                    session = session,
                    liveHeartRate = heartRate,
                    now = now,
                    timeFormat = timeFormat,
                    lifterFor = { liftersById[it] },
                    cablesFor = { id -> exercisesById[id]?.cables ?: 2 },
                    unit = state.unit
                )
            }
        }

        item {
            WorkoutBuilderCard(state, onBuild, Modifier.tourTarget(TourTargets.PICK_EXERCISE))
        }
        item {
            Row(Modifier.tourTarget(TourTargets.TILES), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("One exercise", "Search the library by name, muscle or equipment", onPickExercise, Modifier.weight(1f))
            }
        }

        if (state.recentExercises.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Do again")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentExercises.forEach { ex ->
                            LaunchedEffect(ex.id, active) { onRowShown(ex) }
                            CleanFilterChip(ex.name, selected = false, onClick = { onOpenExercise(ex) })
                        }
                    }
                }
            }
        }

        item {
            Card(Modifier.tourTarget(TourTargets.ONE_RM_TEST)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Your 1RMs", Modifier.weight(1f))
                    SmallButton("1RM test", filled = true, onClick = onAssessment)
                }
                if (standing.isEmpty()) {
                    Text(
                        "No tested 1RMs yet. The test sets each lifter's max, and every load follows from it.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text("Due for a re-test: your oldest 1RMs.", style = MaterialTheme.typography.bodySmall)
                    standing.forEach { r ->
                        val exercise = exercisesById[r.exerciseId] ?: return@forEach
                        val lifter = liftersById[r.lifterId]
                        StandingRow(
                            exercise = exercise,
                            lifter = lifter,
                            kgText = LoadCalculator.format(r.oneRepMax.kg, state.unit),
                            cables = exercise.cables,
                            age = ageText(now - r.oneRepMax.updatedAt),
                            onRetest = { onRetest(r.lifterId, r.exerciseId) },
                            onTrain = { onOpenExercise(exercise) }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CleanColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
private fun CurrentTraining(
    session: List<HistoryEntry>,
    liveHeartRate: Int?,
    now: Long,
    timeFormat: DateFormatter,
    lifterFor: (String) -> UiLifter?,
    cablesFor: (String) -> Int,
    unit: WeightUnit
) {
    val start = session.first().timestamp
    val minutes = ((now - start) / MINUTE_MS).coerceAtLeast(0)
    val heartRates = session.mapNotNull { it.avgHeartRate }
    val avgHeartRate = if (heartRates.isEmpty()) null else heartRates.average().toInt()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CleanColors.AccentSoft)
            .border(1.5.dp, CleanColors.Accent.copy(alpha = 0.35f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(CleanColors.Accent)
            Spacer(Modifier.width(8.dp))
            Text(
                "Current training".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = CleanColors.AccentInk,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Since ${timeFormat.format(start)} · ${durationText(minutes)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(session.map { it.exerciseName }.distinct().size.toString(), "exercises", Modifier.weight(1f))
            Stat(session.sumOf { it.sets }.toString(), "sets", Modifier.weight(1f))
            Stat(session.sumOf { it.reps }.toString(), "reps", Modifier.weight(1f))
            Stat(
                (liveHeartRate ?: avgHeartRate)?.toString() ?: "–",
                when {
                    liveHeartRate != null -> "bpm now"
                    avgHeartRate != null -> "avg bpm"
                    else -> "bpm"
                },
                Modifier.weight(1f)
            )
        }
        // One line per exercise (newest first), however many sets and lifters it had, so the
        // card stays short when several people train together.
        val byExercise = remember(session) { session.reversed().groupBy { it.exerciseName } }
        var showAll by remember { mutableStateOf(false) }
        Column {
            byExercise.entries.toList().let { if (showAll) it else it.take(4) }.forEach { (name, entries) ->
                val lifters = entries.map { it.lifterId }.distinct().mapNotNull(lifterFor)
                val top = entries.mapNotNull { it.topKgPerCable }.maxOrNull()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        lifters.ifEmpty { listOf(null) }.take(4).forEach { Dot(it?.color ?: CleanColors.Muted) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                "${entries.sumOf { it.sets }} sets",
                                lifters.joinToString(", ") { it.name }.ifBlank { null }
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    top?.let { LoadText(LoadCalculator.format(it, unit), cablesFor(entries.first().exerciseId)) }
                    Spacer(Modifier.width(8.dp))
                    Text(timeFormat.format(entries.first().timestamp), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (byExercise.size > 4) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) "Show less" else "Show all ${byExercise.size} exercises", color = CleanColors.Ink)
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CleanColors.Background)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeartRatePill(bpm: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CleanColors.Background)
            .border(1.dp, CleanColors.Line, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(Icons.Filled.Favorite, contentDescription = "Heart rate", tint = CleanColors.Danger, modifier = Modifier.size(14.dp))
        Text("$bpm bpm", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StandingRow(
    exercise: ExerciseCard,
    lifter: UiLifter?,
    kgText: String,
    cables: Int,
    age: String,
    onRetest: () -> Unit,
    onTrain: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                lifter?.let {
                    Dot(it.color, 8)
                    Spacer(Modifier.width(5.dp))
                }
                lifter?.let { Text("${it.name} · ", style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                LoadText(kgText, cables, style = MaterialTheme.typography.bodySmall)
                Text(
                    " · $age",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        SmallButton("Train", filled = false, onClick = onTrain)
        Spacer(Modifier.width(6.dp))
        SmallButton("Re-test", filled = true, onClick = onRetest)
    }
}

@Composable
private fun SmallButton(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) Color.White else CleanColors.Ink,
        modifier = Modifier
            .clip(shape)
            .background(if (filled) CleanColors.Accent else CleanColors.Background)
            .border(1.dp, if (filled) CleanColors.Accent else CleanColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

private fun durationText(minutes: Long): String =
    if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"

private fun ageText(ms: Long): String {
    val days = (ms / DAY_MS).coerceAtLeast(0)
    return when {
        days == 0L -> "set today"
        days == 1L -> "set yesterday"
        days < 14 -> "set $days days ago"
        days < 60 -> "set ${days / 7} weeks ago"
        else -> "set ${days / 30} months ago"
    }
}
