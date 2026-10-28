package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import nl.nielsnotsmurf.cleanui.sound.CleanSounds
import nl.nielsnotsmurf.cleanui.sound.Sfx
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxTest
import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.DemoVideo
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.LifterChip
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.TrainerSearchHint
import nl.nielsnotsmurf.cleanui.ui.components.LoadText
import nl.nielsnotsmurf.cleanui.ui.components.cablesOf
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.platform.formatClock
import nl.nielsnotsmurf.cleanui.ui.components.PlatformBackHandler
import nl.nielsnotsmurf.cleanui.ui.components.RemoteImage

/**
 * The strength test: for each exercise, how to do it, then 3-rep attempts that go up after
 * every made set until one is missed. The heaviest made set is the 3RM, and the lifter's 1RM
 * is estimated from it. Loads are per cable, with a mark for how many cables carry them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OneRepMaxTestScreen(
    state: CleanUiState,
    test: TestState,
    onSelectLifter: (String) -> Unit,
    onBegin: () -> Unit,
    onNudge: (Float) -> Unit,
    onRecord: (AttemptOutcome) -> Unit,
    onTryAgain: (Float?) -> Unit,
    onLiftOnTrainer: () -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    val unit = state.unit
    val lifter = state.lifters.firstOrNull { it.id == test.lifterId }
    val exercise = state.exercises.firstOrNull { it.id == test.exerciseId }
    val current = state.oneRepMax(test.lifterId, test.exerciseId)
    // Every load is per cable, like everywhere else in the app; the cable mark says how many.
    val cables = cablesOf(exercise?.cableConfig)
    val fmt = { kg: Float -> LoadCalculator.format(kg, unit) }
    // System back closes the test the same way the arrow does, so who's training is restored.
    PlatformBackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("3RM test", style = MaterialTheme.typography.headlineLarge)
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.lifters.forEach { l ->
                    LifterChip(l, selected = l.id == test.lifterId, onClick = { onSelectLifter(l.id) })
                }
            }
            if (test.exerciseIds.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    test.exerciseIds.forEachIndexed { i, _ ->
                        Box(
                            Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(
                                when {
                                    i < test.index -> CleanColors.Ink
                                    i == test.index -> CleanColors.Accent
                                    else -> CleanColors.Line
                                }
                            )
                        )
                    }
                }
            }
            Text(
                buildString {
                    if (test.exerciseIds.size > 1) append("${test.index + 1} of ${test.exerciseIds.size} · ")
                    append(exercise?.name ?: "Exercise")
                    exercise?.equipment?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.lowercase().replaceFirstChar(Char::uppercase)) }
                    if (current != null && !current.isEstimate) append(" · current 1RM ").append(fmt(current.kg))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted
            )

            when (test.phase) {
                TestPhase.INTRO -> IntroContent(test, current?.takeIf { !it.isEstimate }?.kg)
                TestPhase.ATTEMPT -> AttemptContent(test, lifter?.color ?: CleanColors.Ink, fmt, unit, cables, onNudge)
                TestPhase.RESULT -> ResultContent(test, lifter?.name ?: "", current, fmt, cables, onTryAgain)
            }
            test.error?.let { Text(it, color = CleanColors.Danger, style = MaterialTheme.typography.bodyMedium) }
        }

        HorizontalDivider(color = CleanColors.Line)
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (test.phase) {
                TestPhase.INTRO -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Skip exercise", onClick = onSkip, modifier = Modifier.weight(1f))
                    PrimaryButton("Start attempts", onClick = onBegin, accent = true, modifier = Modifier.weight(2f))
                }
                TestPhase.ATTEMPT -> {
                    PrimaryButton(
                        "Lift ${OneRepMaxTest.TEST_REPS} × ${fmt(test.kg)}" + (if (cables == 2) " per cable" else "") + " on the trainer",
                        onClick = onLiftOnTrainer,
                        loading = test.isStarting
                    )
                    TrainerSearchHint(state.connection, waiting = test.isStarting)
                    Text(
                        "How did the ${OneRepMaxTest.TEST_REPS} reps go?",
                        style = MaterialTheme.typography.labelMedium,
                        color = CleanColors.Muted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutcomeButton("Missed", CleanColors.Danger, Modifier.weight(1f)) { CleanSounds.play(Sfx.MISS); onRecord(AttemptOutcome.MISSED) }
                        OutcomeButton("Made it, tough", CleanColors.Ink, Modifier.weight(1f)) { CleanSounds.play(Sfx.SET_DONE); onRecord(AttemptOutcome.MADE_TOUGH) }
                        OutcomeButton("Made it, easy", CleanColors.Ink, Modifier.weight(1f)) { CleanSounds.play(Sfx.SUCCESS); onRecord(AttemptOutcome.MADE_EASY) }
                    }
                }
                TestPhase.RESULT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (test.best != null) {
                        GhostButton("Don't save", onClick = onSkip, modifier = Modifier.weight(1f))
                        PrimaryButton(if (test.isLast) "Save 1RM" else "Save 1RM · next", onClick = onSave, accent = true, modifier = Modifier.weight(2f))
                    } else {
                        GhostButton("Skip exercise", onClick = onSkip, modifier = Modifier.weight(1f))
                        PrimaryButton("Try ${fmt(test.kg)}", onClick = { onTryAgain(null) }, modifier = Modifier.weight(2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroContent(test: TestState, knownKg: Float?) {
    val guide = test.guide
    val videoUrl = guide?.videoUrl
    if (videoUrl != null) {
        DemoVideo(videoUrl, thumbnailUrl = guide?.thumbnailUrl)
    } else Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1F2937)),
        contentAlignment = Alignment.Center
    ) {
        if (guide?.thumbnailUrl != null) {
            RemoteImage(
                url = guide.thumbnailUrl,
                contentDescription = "Demo video",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text("No demo video for this exercise", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
        }
    }
    InfoCard("How to do it") {
        Text(
            guide?.description?.takeIf { it.isNotBlank() }
                ?: "Set up as you normally would. Move slowly and keep the same range of motion every rep.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
    InfoCard("How the test works") {
        Text(
            "1. Warm up: 10 easy reps, then 3 reps a bit heavier.\n" +
                "2. Lift 3 reps at the start weight" + (if (knownKg != null) " (80% of your 1RM)" else "") + ".\n" +
                "3. Made all 3? The weight goes up for the next attempt.\n" +
                "4. Keep going until you can't finish 3 reps. Your 3RM is the heaviest set you made; your 1RM is estimated from it.\n" +
                "Loads are per cable (the mark shows 1 or 2 cables). The lowering part is the same weight as the lift (no extra eccentric).",
            style = MaterialTheme.typography.bodyMedium,
            color = CleanColors.Muted
        )
    }
}

@Composable
private fun AttemptContent(test: TestState, color: Color, fmt: (Float) -> String, unit: WeightUnit, cables: Int, onNudge: (Float) -> Unit) {
    val n = test.attempts.size + 1
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(test.restEndsAt) {
        while (nowMillis() < test.restEndsAt) {
            now = nowMillis()
            delay(500)
        }
        now = nowMillis()
    }
    val restLeft = ((test.restEndsAt - now) / 1000L).coerceAtLeast(0L)
    // Ticks for the last seconds of rest, then a call to lift.
    var lastLeft by remember { mutableLongStateOf(restLeft) }
    LaunchedEffect(restLeft) {
        if (restLeft != lastLeft) {
            if (restLeft in 1..3) CleanSounds.play(Sfx.TICK)
            if (restLeft == 0L && lastLeft > 0) CleanSounds.play(Sfx.REST_OVER)
        }
        lastLeft = restLeft
    }
    if (restLeft > 0) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CleanColors.AccentSoft).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Rest before attempt $n", fontWeight = FontWeight.Bold, color = CleanColors.AccentInk)
                Text("Full rest makes the next rep count.", style = MaterialTheme.typography.bodySmall, color = CleanColors.AccentInk)
            }
            Text(formatClock(restLeft), style = MaterialTheme.typography.headlineMedium, color = CleanColors.AccentInk)
        }
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.5.dp, CleanColors.Line, shape).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SectionLabel("Attempt $n · ${OneRepMaxTest.TEST_REPS} reps")
        LoadText(fmt(test.kg), cables, style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp), color = color)
        Text(
            if (cables == 2) "per cable · ${LoadCalculator.format(test.kg * 2, unit)} total" else "one cable",
            style = MaterialTheme.typography.labelMedium,
            color = CleanColors.Muted
        )
        Text(
            if (n == 1) startNote(test.startReason) else "Heaviest made so far: ${test.best?.let(fmt) ?: "none"}",
            style = MaterialTheme.typography.bodySmall,
            color = CleanColors.Muted,
            textAlign = TextAlign.Center
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            listOf(-2 * LoadCalculator.STEP_KG, -LoadCalculator.STEP_KG, LoadCalculator.STEP_KG, 2 * LoadCalculator.STEP_KG).forEach { d ->
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp)).background(CleanColors.Surface)
                        .clickable(role = Role.Button) { onNudge(d) },
                    contentAlignment = Alignment.Center
                ) { Text((if (d > 0) "+" else "−") + LoadCalculator.format(kotlin.math.abs(d), unit, withUnit = false), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            }
        }
    }
    AttemptList(test.attempts, fmt)
}

@Composable
private fun ResultContent(
    test: TestState,
    lifterName: String,
    current: nl.nielsnotsmurf.cleanui.model.OneRepMax?,
    fmt: (Float) -> String,
    cables: Int,
    onTryAgain: (Float?) -> Unit
) {
    val best = test.best
    if (best != null) {
        val previous = current?.takeIf { it.source != OneRepMaxSource.RELATED }
        val oneRm = OneRepMaxTest.oneRepMaxFromTest(best)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CleanColors.AccentSoft).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionLabel("New 3RM · $lifterName")
            LoadText(fmt(best), cables, style = MaterialTheme.typography.displayMedium, color = CleanColors.AccentInk)
            Text("Estimated 1RM ${fmt(oneRm)}", style = MaterialTheme.typography.titleMedium, color = CleanColors.AccentInk)
            Text(
                when {
                    previous == null -> "Your first tested 1RM for this exercise"
                    oneRm > previous.kg + 0.01f -> "1RM up from ${fmt(previous.kg)}"
                    oneRm < previous.kg - 0.01f -> "1RM down from ${fmt(previous.kg)}. Save it only if today was a normal day."
                    else -> "Same as before"
                },
                style = MaterialTheme.typography.bodySmall,
                color = CleanColors.AccentInk
            )
        }
    } else {
        InfoCard("No set made yet") {
            Text("Rest, then try lighter. We suggest ${fmt(test.kg)}.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    AttemptList(test.attempts, fmt)
    OneRepMaxTest.midpoint(test.attempts)?.let { mid ->
        GhostButton("One more try at ${fmt(mid)}", onClick = { onTryAgain(mid) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AttemptList(attempts: List<Attempt>, fmt: (Float) -> String) {
    if (attempts.isEmpty()) return
    InfoCard("Attempts") {
        attempts.forEachIndexed { i, a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Attempt ${i + 1}", modifier = Modifier.weight(1f))
                Text(fmt(a.kg) + (a.measuredKg?.let { " · measured ${fmt(it)}" } ?: "") + "  ")
                Text(
                    when (a.outcome) {
                        AttemptOutcome.MADE_EASY -> "✓ easy"
                        AttemptOutcome.MADE_TOUGH -> "✓ tough"
                        AttemptOutcome.MISSED -> "✗ missed"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (a.made) CleanColors.Success else CleanColors.Danger
                )
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CleanColors.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel(label)
        content()
    }
}

@Composable
private fun OutcomeButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier.heightIn(min = 52.dp).clip(shape).border(1.5.dp, CleanColors.Line, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, fontSize = 14.sp) }
}

private fun startNote(reason: OneRepMaxTest.StartReason) = when (reason) {
    OneRepMaxTest.StartReason.KNOWN_ONE_REP_MAX -> "80% of your current 1RM"
    OneRepMaxTest.StartReason.ESTIMATE -> "A careful start, based on your other lifts"
    OneRepMaxTest.StartReason.DEFAULT -> "A light start. It goes up quickly while sets feel easy."
}
