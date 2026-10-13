package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import kotlin.math.roundToInt
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.SetKind
import nl.nielsnotsmurf.cleanui.model.SetSpec
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.DemoVideo
import nl.nielsnotsmurf.cleanui.ui.components.Dot
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Stepper

private val ECCENTRIC_OPTIONS = listOf(0, 50, 75, 100, 125, 150)

@Composable
fun SetSetupScreen(
    state: CleanUiState,
    setup: SetupState,
    loadFor: (String) -> Float,
    onUpdate: ((SetupState) -> SetupState) -> Unit,
    onPercent: (Int) -> Unit,
    onNudge: (lifterId: String, deltaKg: Float) -> Unit,
    onBack: () -> Unit,
    onTestOneRepMax: (lifterId: String) -> Unit,
    onStart: () -> Unit
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    val bodyweight = setup.exercise.isBodyweight
    val isEcho = setup.mode == TrainingMode.ECHO && !bodyweight
    val unit = state.unit
    val videoUrl = setup.guide?.videoUrl
    var showVideo by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(Modifier.weight(1f)) {
                    Text(setup.exercise.name, style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                    Text(
                        if (bodyweight) "Bodyweight · timed sets, no cable load" else setup.exercise.equipment,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (videoUrl != null) {
                    IconButton(onClick = { showVideo = !showVideo }) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = if (showVideo) "Hide demo video" else "Show demo video", tint = CleanColors.Accent)
                    }
                }
            }

            if (showVideo && videoUrl != null) {
                DemoVideo(videoUrl, Modifier.padding(horizontal = 20.dp), setup.guide?.thumbnailUrl)
            }

            if (!bodyweight) LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TrainingMode.entries) { m ->
                    CleanFilterChip(m.label, selected = setup.mode == m, onClick = { onUpdate { it.copy(mode = m) } })
                }
            }

            if (!bodyweight) Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isEcho) {
                    SectionLabel("Echo level")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        EchoLevelChoice.entries.forEach { level ->
                            PresetBox(level.label, selected = setup.echoLevel == level, Modifier.weight(1f)) {
                                onUpdate { it.copy(echoLevel = level) }
                            }
                        }
                    }
                    SectionLabel("Eccentric load")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ECCENTRIC_OPTIONS.forEach { pct ->
                            PresetBox("$pct%", selected = setup.eccentricLoadPercent == pct, Modifier.weight(1f)) {
                                onUpdate { it.copy(eccentricLoadPercent = pct) }
                            }
                        }
                    }
                    Text(
                        "Echo adapts the load to how hard you push, so there's no % of 1RM to set.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        SectionLabel("Intensity · % of 1RM", Modifier.weight(1f))
                        Text("${setup.percent}%", style = MaterialTheme.typography.titleLarge)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        LoadCalculator.PERCENT_PRESETS.forEach { pct ->
                            PresetBox("$pct", selected = setup.percent == pct, Modifier.weight(1f)) { onPercent(pct) }
                        }
                    }
                    Slider(
                        value = setup.percent.toFloat(),
                        onValueChange = { onPercent((it / 5f).roundToInt() * 5) },
                        valueRange = 30f..100f,
                        steps = 13,
                        colors = SliderDefaults.colors(
                            thumbColor = CleanColors.Accent,
                            activeTrackColor = CleanColors.Accent,
                            inactiveTrackColor = CleanColors.Line,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            }

            if (!isEcho && !bodyweight) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.activeLifters, key = { it.id }) { l ->
                        val orm = state.oneRepMax(l.id, setup.exercise.id)
                        val load = loadFor(l.id)
                        val note = when {
                            setup.overridesKg.containsKey(l.id) -> "Set by hand"
                            orm == null && setup.lastUsedKg.containsKey(l.id) -> "Last used · no 1RM yet"
                            orm == null -> "No 1RM yet · start light"
                            orm.source == OneRepMaxSource.RELATED ->
                                "${setup.percent}% of ~${LoadCalculator.format(orm.kg, unit, withUnit = false)}"
                            else -> "${setup.percent}% of ${LoadCalculator.format(orm.kg, unit, withUnit = false)}"
                        }
                        LifterLoadCard(
                            lifter = l,
                            loadText = LoadCalculator.format(load, unit),
                            note = note,
                            // Offer the test to lifters without a real 1RM for this exercise.
                            onTest = if (orm == null || orm.isEstimate) ({ onTestOneRepMax(l.id) }) else null,
                            onMinus = { onNudge(l.id, -LoadCalculator.STEP_KG) },
                            onPlus = { onNudge(l.id, LoadCalculator.STEP_KG) }
                        )
                    }
                }
            }

            SetsSection(setup = setup, isEcho = isEcho || bodyweight, onUpdate = onUpdate)

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CleanColors.Surface)
                        .clickable { showMore = !showMore }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        buildString {
                            append(
                                when {
                                    bodyweight -> "No cables"
                                    setup.cableConfig == "SINGLE" -> "One cable"
                                    else -> "Both cables"
                                }
                            )
                            setup.durationSeconds?.let { append(" · Timed ${it}s") }
                            if (!isEcho && !bodyweight) {
                                append(" · Progression ")
                                append(if (setup.progressionKg == 0f) "off" else "%+.1f kg/rep".format(setup.progressionKg))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (showMore) "Done" else "More options", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
                }
                if (showMore && !bodyweight) {
                    SectionLabel("Cables")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PresetBox("Both cables", selected = setup.cableConfig != "SINGLE", Modifier.weight(1f)) {
                            onUpdate { it.copy(cableConfig = "DOUBLE") }
                        }
                        PresetBox("One cable", selected = setup.cableConfig == "SINGLE", Modifier.weight(1f)) {
                            onUpdate { it.copy(cableConfig = "SINGLE") }
                        }
                    }
                }
                if (showMore) {
                    if (!bodyweight) SectionLabel("Set type")
                    if (!bodyweight) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PresetBox("Count reps", selected = setup.durationSeconds == null, Modifier.weight(1f)) {
                            onUpdate { it.copy(durationSeconds = null) }
                        }
                        PresetBox("Timed", selected = setup.durationSeconds != null, Modifier.weight(1f)) {
                            onUpdate { it.copy(durationSeconds = it.durationSeconds ?: 30) }
                        }
                    }
                    if (!isEcho && !bodyweight) {
                        Stepper(
                            "Weight change per rep", "%+.1f kg".format(setup.progressionKg),
                            onMinus = { onUpdate { it.copy(progressionKg = (it.progressionKg - 0.5f).coerceAtLeast(-3f)) } },
                            onPlus = { onUpdate { it.copy(progressionKg = (it.progressionKg + 0.5f).coerceAtMost(3f)) } }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CleanColors.Background)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(CleanColors.Line))
            val who = state.activeLifters.firstOrNull()?.name
            Text(
                buildString {
                    append(summary(setup))
                    if (!isEcho && !bodyweight && setup.sets.all { it.percent == null }) append(" @ ${setup.percent}%")
                    if (who != null && state.activeLifters.size > 1) append(" · $who first")
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            setup.error?.let {
                Text(it, color = CleanColors.Danger, style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            PrimaryButton(
                "Start set",
                onClick = onStart,
                accent = true,
                enabled = state.activeLifters.isNotEmpty(),
                loading = setup.isStarting
            )
        }
    }
}

@Composable
private fun PresetBox(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) CleanColors.AccentSoft else Color.Transparent)
            .border(1.dp, if (selected) CleanColors.Accent else CleanColors.Line, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) CleanColors.AccentInk else CleanColors.Ink)
    }
}

@Composable
private fun LifterLoadCard(
    lifter: UiLifter,
    loadText: String,
    note: String,
    onTest: (() -> Unit)?,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(176.dp)
            .clip(shape)
            .border(1.5.dp, CleanColors.Line, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(lifter.color)
            Text(lifter.name, style = MaterialTheme.typography.titleMedium)
        }
        Text(loadText, style = MaterialTheme.typography.displayMedium.copy(color = lifter.color))
        Text(note, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (onTest != null) {
            Text(
                "Test 1RM",
                style = MaterialTheme.typography.labelLarge,
                color = CleanColors.AccentInk,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onTest).padding(vertical = 4.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            NudgeKey("−", onMinus, Modifier.weight(1f))
            NudgeKey("+", onPlus, Modifier.weight(1f))
        }
    }
}

@Composable
private fun NudgeKey(symbol: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CleanColors.Surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

private fun formatRest(seconds: Int): String =
    if (seconds < 60) "${seconds}s" else "%d:%02d".format(seconds / 60, seconds % 60)

/** "3 × 8", "4 sets · 10/8/6/AMRAP" or "3 × 30s". */
private fun summary(setup: SetupState): String {
    val n = setup.sets.size
    setup.durationSeconds?.let { return "$n × ${it}s" }
    val reps = setup.sets.map { if (it.kind == SetKind.AMRAP) "AMRAP" else it.reps.toString() }
    return if (reps.distinct().size == 1) "$n × ${reps.first()}" else "$n sets · ${reps.joinToString("/")}"
}

@Composable
private fun SetsSection(setup: SetupState, isEcho: Boolean, onUpdate: ((SetupState) -> SetupState) -> Unit) {
    var openIndex by rememberSaveable { mutableStateOf(-1) }
    val timed = setup.durationSeconds != null
    val firstReps = setup.sets.firstOrNull()?.reps ?: 8

    fun updateSets(transform: (List<SetSpec>) -> List<SetSpec>) =
        onUpdate { it.copy(sets = transform(it.sets).ifEmpty { listOf(SetSpec()) }) }

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper(
                "Sets", setup.sets.size.toString(),
                onMinus = { updateSets { if (it.size > 1) it.dropLast(1) else it } },
                onPlus = { updateSets { if (it.size < 10) it + it.last() else it } },
                modifier = Modifier.weight(1f)
            )
            if (timed) {
                Stepper(
                    "Seconds per set", "${setup.durationSeconds}s",
                    onMinus = { onUpdate { it.copy(durationSeconds = ((it.durationSeconds ?: 30) - 5).coerceAtLeast(5)) } },
                    onPlus = { onUpdate { it.copy(durationSeconds = ((it.durationSeconds ?: 30) + 5).coerceAtMost(300)) } },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Stepper(
                    "Reps · all sets", firstReps.toString(),
                    onMinus = { updateSets { sets -> sets.map { it.copy(reps = (firstReps - 1).coerceAtLeast(1)) } } },
                    onPlus = { updateSets { sets -> sets.map { it.copy(reps = (firstReps + 1).coerceAtMost(50)) } } },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CleanColors.Surface)
        ) {
            setup.sets.forEachIndexed { i, set ->
                val open = openIndex == i
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openIndex = if (open) -1 else i }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted, modifier = Modifier.width(18.dp))
                    Text(
                        when {
                            timed -> "${setup.durationSeconds}s"
                            set.kind == SetKind.AMRAP -> "AMRAP"
                            else -> "${set.reps} reps"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isEcho) {
                        Text(
                            "${setup.percentFor(i)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (set.percent != null) CleanColors.Accent else CleanColors.Muted
                        )
                    }
                    Text(if (i == setup.sets.lastIndex) "—" else formatRest(set.restSeconds),
                        style = MaterialTheme.typography.bodySmall, color = CleanColors.Muted)
                }
                if (open) {
                    SetEditor(
                        set = set,
                        index = i,
                        isLast = i == setup.sets.lastIndex,
                        timed = timed,
                        isEcho = isEcho,
                        percent = setup.percentFor(i),
                        canDelete = setup.sets.size > 1,
                        onChange = { changed -> updateSets { sets -> sets.mapIndexed { j, s -> if (j == i) changed(s) else s } } },
                        onCopy = { updateSets { sets -> if (sets.size < 10) sets.toMutableList().apply { add(i + 1, sets[i]) } else sets } },
                        onDelete = { openIndex = -1; updateSets { sets -> sets.filterIndexed { j, _ -> j != i } } }
                    )
                }
                if (i < setup.sets.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(CleanColors.Line))
            }
        }
        Text("Tap a set to change its reps, % or rest.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SetEditor(
    set: SetSpec,
    index: Int,
    isLast: Boolean,
    timed: Boolean,
    isEcho: Boolean,
    percent: Int,
    canDelete: Boolean,
    onChange: ((SetSpec) -> SetSpec) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!timed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PresetBox("Reps", selected = set.kind == SetKind.REPS, Modifier.weight(1f)) {
                    onChange { it.copy(kind = SetKind.REPS) }
                }
                PresetBox("AMRAP", selected = set.kind == SetKind.AMRAP, Modifier.weight(1f)) {
                    onChange { it.copy(kind = SetKind.AMRAP) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!timed && set.kind == SetKind.REPS) {
                Stepper(
                    "Reps", set.reps.toString(),
                    onMinus = { onChange { it.copy(reps = (it.reps - 1).coerceAtLeast(1)) } },
                    onPlus = { onChange { it.copy(reps = (it.reps + 1).coerceAtMost(50)) } },
                    modifier = Modifier.weight(1f)
                )
            }
            if (!isEcho) {
                Stepper(
                    "% of 1RM", "$percent%",
                    onMinus = { onChange { it.copy(percent = (percent - 5).coerceAtLeast(30)) } },
                    onPlus = { onChange { it.copy(percent = (percent + 5).coerceAtMost(100)) } },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (!isLast) {
            Stepper(
                "Rest after set ${index + 1}", formatRest(set.restSeconds),
                onMinus = { onChange { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } },
                onPlus = { onChange { it.copy(restSeconds = (it.restSeconds + 15).coerceAtMost(600)) } }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Copy set", onClick = onCopy, modifier = Modifier.weight(1f))
            if (canDelete) GhostButton("Delete set", onClick = onDelete, modifier = Modifier.weight(1f))
        }
    }
}
