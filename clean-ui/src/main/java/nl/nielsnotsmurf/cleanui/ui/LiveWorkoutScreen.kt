package nl.nielsnotsmurf.cleanui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.TrainingStats
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.LivePhase
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.TrainerSearchHint

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** True when the Clean lifting screen can show [live]; anything else goes to the classic screen. */
fun LiveWorkout?.isCleanReady(): Boolean = this != null && phase != LivePhase.OTHER && !bodyweight

/**
 * The Clean lifting screen: one big rep count, the load, where the handles are and how this
 * set compares with the lifter's averages. Rest, set done and finished are calm full-screen
 * states with one obvious next step. In [attemptMode] (a 1RM attempt) the summary and
 * finished states are skipped and [onDone] returns straight to the test.
 */
@Composable
fun LiveWorkoutScreen(
    live: LiveWorkout,
    unit: WeightUnit,
    heartRate: Int?,
    history: List<HistoryEntry>,
    personalRecord: String?,
    connection: TrainerConnection,
    attemptMode: Boolean,
    onSkipRest: () -> Unit,
    onContinue: () -> Unit,
    onNextExercise: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    onClassicView: () -> Unit
) {
    val fmt = { kg: Float -> LoadCalculator.format(kg, unit) }
    var confirmStop by remember { mutableStateOf(false) }
    val busy = live.phase in setOf(LivePhase.STARTING, LivePhase.COUNTDOWN, LivePhase.LIFTING, LivePhase.RESTING)
    BackHandler { if (busy) confirmStop = true else onDone() }

    // A 3RM attempt is one short set: as soon as it's done, go straight back to the test to record
    // how it went. Waiting for the trainer's own "finished" state left a blank screen.
    LaunchedEffect(attemptMode, live.phase) {
        if (!attemptMode) return@LaunchedEffect
        when (live.phase) {
            LivePhase.SET_DONE -> { onContinue(); onDone() }
            LivePhase.FINISHED, LivePhase.RESTING -> onDone()
            else -> Unit
        }
    }

    Column(Modifier.fillMaxSize().background(CleanColors.Background)) {
        TopBar(live, heartRate, onClose = { if (busy) confirmStop = true else onDone() }, onClassicView = onClassicView)
        AnimatedVisibility(visible = personalRecord != null) {
            Text(
                "New record · ${personalRecord.orEmpty()}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp)).background(CleanColors.Accent).padding(12.dp)
            )
        }
        AnimatedContent(
            targetState = live.phase,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "phase",
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { phase ->
            Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                when (phase) {
                    LivePhase.STARTING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Waiting("Getting the trainer ready")
                        TrainerSearchHint(connection, waiting = true, modifier = Modifier.padding(top = 16.dp))
                    }
                    LivePhase.COUNTDOWN -> BigNumber(live.countdown.toString(), "Get set")
                    LivePhase.LIFTING -> Lifting(live, fmt, history)
                    LivePhase.RESTING -> Resting(live)
                    LivePhase.SET_DONE -> if (attemptMode) Waiting("Set done") else SetDone(live)
                    LivePhase.FINISHED -> if (attemptMode) Waiting("Back to the test") else Finished(live, fmt, history)
                    LivePhase.OTHER -> Waiting("")
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (live.phase) {
                LivePhase.RESTING -> PrimaryButton("Skip rest", onClick = onSkipRest)
                LivePhase.SET_DONE -> if (!attemptMode) PrimaryButton("Continue", onClick = onContinue, accent = true)
                LivePhase.FINISHED -> if (!attemptMode) {
                    if (!live.isLastExercise) GhostButton("Next exercise", onClick = onNextExercise)
                    PrimaryButton("Done", onClick = onDone, accent = true)
                }
                LivePhase.LIFTING, LivePhase.COUNTDOWN -> GhostButton("End set", onClick = { confirmStop = true })
                else -> Unit
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("End this workout?") },
            text = { Text("Sets you've finished are saved.") },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text("End", color = CleanColors.Danger) } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going", color = CleanColors.Ink) } }
        )
    }
}

@Composable
private fun TopBar(live: LiveWorkout, heartRate: Int?, onClose: () -> Unit, onClassicView: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "✕",
            fontSize = 20.sp,
            color = CleanColors.Ink,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClose).padding(12.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                live.exerciseName.ifBlank { "Workout" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildList {
                live.lifterName?.let { add(it) }
                if (live.totalSets > 0) add("Set ${live.setNumber} of ${live.totalSets}")
                heartRate?.let { add("♥ $it") }
            }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
        }
        live.lifterColorArgb?.let {
            Box(Modifier.padding(end = 8.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(it)))
        }
        Text(
            "Classic",
            style = MaterialTheme.typography.labelMedium,
            color = CleanColors.Muted,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClassicView).padding(8.dp)
        )
    }
}

@Composable
private fun Waiting(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(color = CleanColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        if (text.isNotEmpty()) Text(text, color = CleanColors.Muted)
    }
}

@Composable
private fun BigNumber(value: String, caption: String, color: Color = CleanColors.Ink) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically { it / 3 } + fadeIn(tween(180))) togetherWith (slideOutVertically { -it / 3 } + fadeOut(tween(150)))
            },
            label = "number"
        ) { v ->
            Text(v, fontSize = 128.sp, fontWeight = FontWeight.Bold, color = color, lineHeight = 128.sp)
        }
        Text(caption, style = MaterialTheme.typography.titleMedium, color = CleanColors.Muted)
    }
}

@Composable
private fun Lifting(live: LiveWorkout, fmt: (Float) -> String, history: List<HistoryEntry>) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (live.warmupDone) {
                    BigNumber(live.reps.toString(), live.targetReps?.let { "of $it reps" } ?: "reps")
                } else {
                    BigNumber(live.warmupReps.toString(), "Warm-up of ${live.warmupTarget}", CleanColors.Muted)
                }
            }
            PositionBar(live.position)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Load", fmt(live.loadKg), Modifier.weight(1f))
            Stat("Now", fmt(live.liveLoadKg), Modifier.weight(1f))
        }
        Averages(live, fmt, history)
    }
}

/** How high the handles are in the learned rep range. Hidden until the range is learned. */
@Composable
private fun PositionBar(position: Float?) {
    val p by animateFloatAsState(position ?: 0f, tween(120), label = "position")
    val accent = CleanColors.Accent
    val track = CleanColors.Line
    Box(Modifier.width(10.dp).fillMaxHeight(0.7f)) {
        if (position != null) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                drawLine(track, Offset(x, 0f), Offset(x, size.height), strokeWidth = size.width, cap = StrokeCap.Round)
                drawLine(accent, Offset(x, size.height), Offset(x, size.height * (1 - p)), strokeWidth = size.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Averages(live: LiveWorkout, fmt: (Float) -> String, history: List<HistoryEntry>) {
    val now = remember { System.currentTimeMillis() }
    val month = remember(history, live.exerciseName, live.lifterId) {
        TrainingStats.exerciseAverages(history, live.exerciseName, live.lifterId, now - 30 * DAY_MS)
    }
    val allTime = remember(history, live.exerciseName, live.lifterId) {
        TrainingStats.exerciseAverages(history, live.exerciseName, live.lifterId)
    }
    if (allTime == null) return
    val avg = { a: TrainingStats.Averages? -> a?.avgKg?.let(fmt) ?: "–" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("30-day avg", avg(month), Modifier.weight(1f))
        Stat("All-time avg", avg(allTime), Modifier.weight(1f))
        Stat("Best", allTime.bestKg?.let(fmt) ?: "–", Modifier.weight(1f))
    }
    Text(
        "Usually %.0f reps a set · %d workouts".format(allTime.avgRepsPerSet, allTime.workouts),
        style = MaterialTheme.typography.labelMedium,
        color = CleanColors.Muted,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CleanColors.Surface).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CleanColors.Muted)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Resting(live: LiveWorkout) {
    // The trainer only reports what's left, so the first value seen is the full rest.
    var total by remember { mutableIntStateOf(live.restSeconds) }
    if (live.restSeconds > total) total = live.restSeconds
    val progress by animateFloatAsState(
        if (total > 0) live.restSeconds.toFloat() / total else 0f, tween(900), label = "rest"
    )
    val accent = CleanColors.Accent
    val track = CleanColors.Line
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                drawArc(accent, -90f, 360f * progress, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%d:%02d".format(live.restSeconds / 60, live.restSeconds % 60), fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("Rest", color = CleanColors.Muted)
            }
        }
        val next = live.nextName.ifBlank { live.exerciseName }
        if (next.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Up next", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
                Text(next, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SetDone(live: LiveWorkout) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BigNumber(live.doneReps.toString(), "reps · set done", CleanColors.Accent)
        if (live.peakPower > 0f) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Peak power", "%.0f W".format(live.peakPower), Modifier.width(140.dp))
                Stat("Avg power", "%.0f W".format(live.avgPower), Modifier.width(140.dp))
            }
        }
    }
}

@Composable
private fun Finished(live: LiveWorkout, fmt: (Float) -> String, history: List<HistoryEntry>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(CleanColors.Accent),
            contentAlignment = Alignment.Center
        ) { Text("✓", color = Color.White, fontSize = 40.sp) }
        Text("Nice work", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Averages(live, fmt, history)
    }
}
