package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import nl.nielsnotsmurf.cleanui.ui.components.Stepper

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.nielsnotsmurf.cleanui.sound.CleanSounds
import nl.nielsnotsmurf.cleanui.sound.Sfx
import nl.nielsnotsmurf.cleanui.sound.clickSounds
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
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.LivePhase
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.ExerciseDemo
import nl.nielsnotsmurf.cleanui.ui.components.hasDemo
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.TrainerSearchHint
import nl.nielsnotsmurf.cleanui.ui.components.ConnectionPill
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.platform.formatClock
import nl.nielsnotsmurf.cleanui.platform.formatDecimal
import nl.nielsnotsmurf.cleanui.ui.components.PlatformBackHandler

private const val DAY_MS = 24 * 60 * 60 * 1000L

/** Before the working reps: getting ready, counting down or doing the warm-up reps. */
private fun LiveWorkout.isGettingReady(): Boolean =
    phase == LivePhase.STARTING || phase == LivePhase.COUNTDOWN || (phase == LivePhase.LIFTING && !warmupDone)

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
    connection: TrainerConnection,
    cables: Int,
    attemptMode: Boolean,
    onSkipRest: () -> Unit,
    onContinue: () -> Unit,
    onNextExercise: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    guide: ExerciseGuide? = null,
    onAdjustWeight: (Float) -> Unit = {},
    /** % of 1RM for the coming sets; null hides the slider. */
    percent: Int? = null,
    onPercent: (Int) -> Unit = {},
    /** New 1RM (kg per cable) when the load was set away from the %: offered as "Update 1RM". */
    oneRepMaxUpdate: Float? = null,
    onUpdateOneRepMax: () -> Unit = {},
    /** Echo exercises: the level of the coming set, shown instead of weight and %; null otherwise. */
    echoLevel: EchoLevelChoice? = null,
    onEchoLevel: (EchoLevelChoice) -> Unit = {},
    /** Switches the coming (and later) sets of this exercise to another mode; Echo also takes its eccentric %. */
    onMode: (TrainingMode, Int) -> Unit = { _, _ -> },
    /** The coming set only: as many reps as possible, or not. */
    onAmrap: (Boolean) -> Unit = {},
    /** The routine's name when this exercise was changed by hand and the routine doesn't have it yet. */
    routineNudge: String? = null,
    onUpdateRoutine: () -> Unit = {},
    /** Connects to the trainer again when the link dropped during the workout. */
    onConnect: () -> Unit = {},
    onFinishSet: () -> Unit = {},
    onRestartSet: () -> Unit = {},
    onMoveSet: (Int) -> Unit = {},
    onMoveExercise: (Int) -> Unit = {},
    /** Target reps of the coming set, changed on the Continue screen. */
    onAdjustReps: (Int) -> Unit = {},
    /** Autoplay off: after the rest a Continue screen waits. On, the weight, reps and % show during the rest. */
    holdAfterRest: Boolean = true,
    /** On the finished screen: offer to keep the reps and loads set by hand in this routine. */
    saveToRoutine: String? = null,
    onSaveToRoutine: () -> Unit = {},
    onDismissSaveToRoutine: () -> Unit = {},
    /** Back to the app while the workout keeps going; it's picked up again from the bar at the bottom. */
    onLeave: () -> Unit = onDone
) {
    val fmt = { kg: Float -> LoadCalculator.format(kg, unit) }
    var confirmStop by remember { mutableStateOf(false) }
    val busy = live.phase in setOf(LivePhase.STARTING, LivePhase.COUNTDOWN, LivePhase.LIFTING, LivePhase.RESTING) ||
        (live.phase == LivePhase.SET_DONE && !attemptMode)
    // Back leaves the workout running (pick it up from home); ✕ asks whether to end it.
    PlatformBackHandler { if (!busy) onDone() else if (attemptMode) confirmStop = true else onLeave() }

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

    // Bops and boops for what happens on the trainer (Settings › Workout beeps).
    LaunchedEffect(live.phase) {
        when (live.phase) {
            LivePhase.LIFTING -> CleanSounds.play(Sfx.GO)
            LivePhase.SET_DONE -> CleanSounds.play(Sfx.SET_DONE)
            LivePhase.FINISHED -> CleanSounds.play(Sfx.SUCCESS)
            else -> Unit
        }
    }
    LaunchedEffect(live.phase, live.countdown) {
        if (live.phase == LivePhase.COUNTDOWN && live.countdown > 0) CleanSounds.play(Sfx.TICK)
    }
    LaunchedEffect(live.reps, live.warmupReps) {
        if (live.phase == LivePhase.LIFTING && (live.reps > 0 || live.warmupReps > 0)) CleanSounds.play(Sfx.REP)
    }
    LaunchedEffect(live.warmupDone) {
        if (live.warmupDone && live.phase == LivePhase.LIFTING && live.warmupTarget > 0) CleanSounds.play(Sfx.WARMUP_DONE)
    }
    LaunchedEffect(live.phase, live.restSeconds) {
        if (live.phase == LivePhase.RESTING && live.restSeconds in 1..3) CleanSounds.play(Sfx.TICK)
    }

    Column(Modifier.fillMaxSize().background(CleanColors.Background)) {
        TopBar(live, heartRate, onClose = { if (busy) confirmStop = true else onDone() })
        // The exercise demo shows while the trainer gets ready, counts down and during warm-up reps.
        // It stays composed (folded away) the rest of the time, so the clip is already loaded and
        // playing when it's shown instead of starting to buffer when the countdown begins.
        if (guide != null && guide.hasDemo) {
            val shown by animateFloatAsState(if (live.isGettingReady()) 1f else 0f, tween(250), label = "video")
            Box(
                Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                    val h = (p.height * shown).roundToInt()
                    layout(p.width, h) { p.place(0, 0) }
                }
            ) {
                ExerciseDemo(guide, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
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
                        if (connection !is TrainerConnection.Connected) {
                            Box(Modifier.padding(top = 16.dp)) { ConnectionPill(connection, onConnect = onConnect) }
                        }
                    }
                    LivePhase.COUNTDOWN -> BigNumber(live.countdown.toString(), "Get set")
                    LivePhase.LIFTING -> Lifting(live, fmt, history, cables)
                    LivePhase.RESTING -> Box(
                        // Swipe left or right for the next or previous exercise, as with the buttons.
                        Modifier.fillMaxSize().then(if (attemptMode) Modifier else Modifier.swipeExercises(onMoveExercise)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (live.restSeconds > 0) Resting(live) else UpNext(live, fmt, cables, echoLevel)
                    }
                    LivePhase.SET_DONE -> if (attemptMode) Waiting("Set done") else SetDone(live)
                    LivePhase.FINISHED -> if (attemptMode) Waiting("Back to the test") else Finished(live, fmt, history, cables, saveToRoutine, onSaveToRoutine, onDismissSaveToRoutine)
                    LivePhase.OTHER -> Waiting("")
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (live.phase) {
                LivePhase.RESTING -> {
                    if (!attemptMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("‹ Back a set", onClick = { onMoveSet(-1) }, modifier = Modifier.weight(1f))
                            GhostButton("Skip a set ›", onClick = { onMoveSet(1) }, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GhostButton("‹ Exercise", onClick = { onMoveExercise(-1) }, modifier = Modifier.weight(1f))
                            GhostButton("Exercise ›", onClick = { onMoveExercise(1) }, modifier = Modifier.weight(1f))
                        }
                    }
                    if (!attemptMode && connection !is TrainerConnection.Connected) {
                        // The trainer went to sleep or out of reach: connect again and carry on at this set.
                        Text(
                            if (connection == TrainerConnection.Connecting) "Connecting to your Vitruvian…"
                            else "Trainer disconnected. Switch it on and tap Connect: you'll carry on at this set.",
                            style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted
                        )
                        if (connection == TrainerConnection.Connecting) GhostButton("Connecting…", onClick = {})
                        else PrimaryButton("Connect trainer", onClick = onConnect, accent = true)
                    } else if (live.restSeconds > 0) PrimaryButton("Skip rest", onClick = onSkipRest)
                    else PrimaryButton("Continue", onClick = onSkipRest, accent = true)
                }
                LivePhase.SET_DONE -> if (!attemptMode) PrimaryButton("Continue", onClick = onContinue, accent = true)
                LivePhase.FINISHED -> if (!attemptMode) {
                    if (!live.isLastExercise) GhostButton("Next exercise", onClick = onNextExercise)
                    PrimaryButton("Done", onClick = onDone, accent = true)
                }
                // Stopping short (6 of 8 reps) still counts the set and moves on to the next one.
                LivePhase.LIFTING -> if (attemptMode) GhostButton("End set", onClick = { confirmStop = true })
                    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Range of motion set wrong: the set starts over with fresh warm-up reps.
                        GhostButton("Reset ROM", onClick = onRestartSet, modifier = Modifier.weight(1f))
                        GhostButton("End set here", onClick = onFinishSet, modifier = Modifier.weight(1f))
                    }
                LivePhase.COUNTDOWN -> GhostButton("End set", onClick = { confirmStop = true })
                else -> Unit
            }
            // The coming set is set up on the Continue screen (or during the rest with autoplay on).
            val settingUp = !attemptMode && live.phase == LivePhase.RESTING && (live.restSeconds == 0 || !holdAfterRest)
            if (settingUp) {
                val step = if (unit == WeightUnit.LB) 0.45359f else 0.5f
                // Mode and AMRAP of the coming set, e.g. Echo and as many reps as possible for a hard last set.
                live.mode?.let { mode ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrainingMode.entries.forEach { m ->
                            CleanFilterChip(m.label, selected = mode == m, onClick = { if (m != mode) onMode(m, live.eccentricPercent) })
                        }
                        CleanFilterChip("AMRAP", selected = live.amrap, onClick = { onAmrap(!live.amrap) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (echoLevel != null) {
                        // Echo sets no weight: the trainer pushes back as hard as the level says.
                        val levels = EchoLevelChoice.entries
                        Stepper(
                            "Echo level", echoLevel.label,
                            onMinus = { levels.getOrNull(echoLevel.ordinal - 1)?.let(onEchoLevel) },
                            onPlus = { levels.getOrNull(echoLevel.ordinal + 1)?.let(onEchoLevel) },
                            modifier = Modifier.weight(1f)
                        )
                    } else Stepper(
                        "Weight per cable", fmt(live.loadKg),
                        onMinus = { onAdjustWeight((live.loadKg - step).coerceAtLeast(0.5f)) },
                        onPlus = { onAdjustWeight((live.loadKg + step).coerceAtMost(100f)) },
                        onTyped = { v -> onAdjustWeight((if (unit == WeightUnit.LB) v / 2.20462f else v).coerceIn(0.5f, 100f)) },
                        typedSuffix = if (unit == WeightUnit.LB) "lb" else "kg",
                        modifier = Modifier.weight(1f)
                    )
                    live.targetReps?.let { reps ->
                        Stepper(
                            "Reps", reps.toString(),
                            onMinus = { onAdjustReps((reps - 1).coerceAtLeast(1)) },
                            onPlus = { onAdjustReps((reps + 1).coerceAtMost(50)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (echoLevel != null && live.mode == TrainingMode.ECHO) {
                    // The trainer's eccentric steps.
                    val steps = listOf(0, 50, 75, 100, 125, 150)
                    val ecc = live.eccentricPercent
                    Stepper(
                        "Eccentric (on the way down)", "$ecc%",
                        onMinus = { steps.lastOrNull { it < ecc }?.let { onMode(TrainingMode.ECHO, it) } },
                        onPlus = { steps.firstOrNull { it > ecc }?.let { onMode(TrainingMode.ECHO, it) } }
                    )
                }
                if (percent != null && echoLevel == null) PercentSlider(percent, onPercent)
                if (percent != null && echoLevel == null && oneRepMaxUpdate != null) {
                    GhostButton("Update 1RM for this exercise to ${fmt(oneRepMaxUpdate)} (weight ÷ $percent%)", onClick = onUpdateOneRepMax)
                }
                if (routineNudge != null) {
                    GhostButton("You changed this exercise. Update \"$routineNudge\" too?", onClick = onUpdateRoutine)
                }
            }
            if (live.isGettingReady() && !attemptMode && echoLevel == null) {
                // kg moves in 0.5 kg steps, lb in whole pounds.
                val step = if (unit == WeightUnit.LB) 0.45359f else 0.5f
                Stepper(
                    if (live.phase == LivePhase.LIFTING) "Weight per cable · warm-up restarts" else "Weight per cable",
                    fmt(live.loadKg),
                    onMinus = { onAdjustWeight((live.loadKg - step).coerceAtLeast(0.5f)) },
                    onPlus = { onAdjustWeight((live.loadKg + step).coerceAtMost(100f)) },
                    onTyped = { v -> onAdjustWeight((if (unit == WeightUnit.LB) v / 2.20462f else v).coerceIn(0.5f, 100f)) },
                    typedSuffix = if (unit == WeightUnit.LB) "lb" else "kg"
                )
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            modifier = Modifier.clickSounds(),
            title = { Text("End this workout?") },
            text = {
                Text(
                    if (attemptMode) "Sets you've finished are saved."
                    else "Sets you've finished are saved. Or leave it running and pick it up again from home."
                )
            },
            confirmButton = { TextButton(onClick = { confirmStop = false; onStop() }) { Text("End", color = CleanColors.Danger) } },
            dismissButton = {
                Row {
                    if (!attemptMode) TextButton(onClick = { confirmStop = false; onLeave() }) { Text("Leave for now", color = CleanColors.Ink) }
                    TextButton(onClick = { confirmStop = false }) { Text("Keep going", color = CleanColors.Ink) }
                }
            }
        )
    }
}

/** % of 1RM for the coming sets, always at hand. Applies when let go, to every lifter. */
@Composable
private fun PercentSlider(percent: Int, onPercent: (Int) -> Unit) {
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("% of 1RM", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onPercent(value.roundToInt()) },
            valueRange = 30f..100f,
            steps = 13,
            colors = SliderDefaults.colors(thumbColor = CleanColors.Accent, activeTrackColor = CleanColors.Accent),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("${value.roundToInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TopBar(live: LiveWorkout, heartRate: Int?, onClose: () -> Unit) {
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
private fun Lifting(live: LiveWorkout, fmt: (Float) -> String, history: List<HistoryEntry>, cables: Int) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (live.warmupDone) {
                    // The rep being lifted now, not the reps already done.
                    val current = live.targetReps?.let { (live.reps + 1).coerceAtMost(it) } ?: (live.reps + 1)
                    BigNumber(current.toString(), live.targetReps?.let { "rep of $it" } ?: "rep")
                    live.autoStopSeconds?.let {
                        Text(
                            "Stopping in ${it}s, the load lets go",
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            color = CleanColors.Muted
                        )
                    }
                } else {
                    val current = (live.warmupReps + 1).coerceAtMost(live.warmupTarget.coerceAtLeast(1))
                    BigNumber(current.toString(), "Warm-up of ${live.warmupTarget}", CleanColors.Muted)
                }
            }
            // What the cables pull right now, big enough to read from the handles.
            Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LoadText(fmt(live.liveLoadKg), cables, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                Text("pulling now", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
            }
            PositionBar(live.position)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Set load", fmt(live.loadKg), Modifier.weight(1f), cables)
        }
        Averages(live, fmt, history, cables)
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
private fun Averages(live: LiveWorkout, fmt: (Float) -> String, history: List<HistoryEntry>, cables: Int) {
    val now = remember { nowMillis() }
    val month = remember(history, live.exerciseName, live.lifterId) {
        TrainingStats.exerciseAverages(history, live.exerciseName, live.lifterId, now - 30 * DAY_MS)
    }
    val allTime = remember(history, live.exerciseName, live.lifterId) {
        TrainingStats.exerciseAverages(history, live.exerciseName, live.lifterId)
    }
    if (allTime == null) return
    val avg = { a: TrainingStats.Averages? -> a?.avgKg?.let(fmt) ?: "–" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("30-day avg", avg(month), Modifier.weight(1f), cables)
        Stat("All-time avg", avg(allTime), Modifier.weight(1f), cables)
        Stat("Best", allTime.bestKg?.let(fmt) ?: "–", Modifier.weight(1f), cables)
    }
    Text(
        "Usually ${formatDecimal(allTime.avgRepsPerSet.toDouble(), 0)} reps a set · ${allTime.workouts} workouts",
        style = MaterialTheme.typography.labelMedium,
        color = CleanColors.Muted,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, cables: Int? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(CleanColors.Surface).padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CleanColors.Muted)
        if (cables != null && value != "–") {
            LoadText(value, cables, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        } else {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
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
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                drawArc(track, 0f, 360f, false, style = stroke)
                drawArc(accent, -90f, 360f * progress, false, style = stroke)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatClock(live.restSeconds), fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("Rest", color = CleanColors.Muted)
            }
        }
        live.lastReps?.let { Text("Last set: $it reps", style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted) }
        val next = live.nextName.ifBlank { live.exerciseName }
        if (next.isNotBlank()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Up next", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
                Text(next, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
        }
    }
}

/** After the rest (and before the first set): the coming set, to check and change before Continue. */
@Composable
private fun UpNext(live: LiveWorkout, fmt: (Float) -> String, cables: Int, echoLevel: EchoLevelChoice?) {
    // Scrolls on short screens instead of running over the buttons below.
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(if (live.lastReps == null) "Ready to start" else "Ready for the next set", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
        Text(
            live.exerciseName.ifBlank { live.nextName },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        val detail = buildList {
            if (live.totalSets > 0) add("Set ${live.setNumber} of ${live.totalSets}")
            add(live.targetReps?.let { "$it reps" } ?: "As many reps as you can")
        }.joinToString(" · ")
        Text(detail, style = MaterialTheme.typography.titleMedium, color = CleanColors.Muted, textAlign = TextAlign.Center)
        if (echoLevel != null) {
            Text("Echo · ${echoLevel.label}", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        } else {
            LoadText(fmt(live.loadKg), cables, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
        }
        live.lastReps?.let { Text("Last set: $it reps", style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted) }
        Text("Swipe or use the buttons to pick another set or exercise.", style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted, textAlign = TextAlign.Center)
    }
}

/** A sideways swipe moves to the next (left) or previous (right) exercise. */
private fun Modifier.swipeExercises(onMoveExercise: (Int) -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 80.dp.toPx()
    var dragged = 0f
    detectHorizontalDragGestures(
        onDragStart = { dragged = 0f },
        onDragEnd = {
            if (dragged <= -threshold) onMoveExercise(1) else if (dragged >= threshold) onMoveExercise(-1)
            dragged = 0f
        },
        onHorizontalDrag = { _, amount -> dragged += amount }
    )
}

@Composable
private fun SetDone(live: LiveWorkout) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BigNumber(live.doneReps.toString(), "reps · set done", CleanColors.Accent)
        if (live.peakPower > 0f) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Peak power", "${formatDecimal(live.peakPower.toDouble(), 0)} W", Modifier.width(140.dp))
                Stat("Avg power", "${formatDecimal(live.avgPower.toDouble(), 0)} W", Modifier.width(140.dp))
            }
        }
    }
}

@Composable
private fun Finished(
    live: LiveWorkout,
    fmt: (Float) -> String,
    history: List<HistoryEntry>,
    cables: Int,
    saveToRoutine: String? = null,
    onSave: () -> Unit = {},
    onDismissSave: () -> Unit = {}
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(44.dp)).background(CleanColors.Accent),
            contentAlignment = Alignment.Center
        ) { Text("✓", color = Color.White, fontSize = 40.sp) }
        Text("Nice work", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (saveToRoutine != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CleanColors.Surface).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("You changed weights or reps during this workout.", style = MaterialTheme.typography.bodyMedium)
                Text("Keep them in \"$saveToRoutine\" for next time?", style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Not now", onClick = onDismissSave, modifier = Modifier.weight(1f))
                    PrimaryButton("Save to routine", onClick = onSave, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Averages(live, fmt, history, cables)
    }
}
