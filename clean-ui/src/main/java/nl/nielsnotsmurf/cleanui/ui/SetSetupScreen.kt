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
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.Dot
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
    onStart: () -> Unit
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    val isEcho = setup.mode == TrainingMode.ECHO
    val unit = state.unit

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
                    if (setup.exercise.equipment.isNotBlank()) {
                        Text(setup.exercise.equipment, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(TrainingMode.entries) { m ->
                    CleanFilterChip(m.label, selected = setup.mode == m, onClick = { onUpdate { it.copy(mode = m) } })
                }
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isEcho) {
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

            if (!isEcho) {
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
                            onMinus = { onNudge(l.id, -LoadCalculator.STEP_KG) },
                            onPlus = { onNudge(l.id, LoadCalculator.STEP_KG) }
                        )
                    }
                }
            }

            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper(
                    "Sets", setup.sets.toString(),
                    onMinus = { onUpdate { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } },
                    onPlus = { onUpdate { it.copy(sets = (it.sets + 1).coerceAtMost(10)) } },
                    modifier = Modifier.weight(1f)
                )
                Stepper(
                    "Reps", setup.reps.toString(),
                    onMinus = { onUpdate { it.copy(reps = (it.reps - 1).coerceAtLeast(1)) } },
                    onPlus = { onUpdate { it.copy(reps = (it.reps + 1).coerceAtMost(50)) } },
                    modifier = Modifier.weight(1f)
                )
            }

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
                        "Rest ${formatRest(setup.restSeconds)} · Progression " +
                            if (setup.progressionKg == 0f) "off" else "%+.1f kg/rep".format(setup.progressionKg),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(if (showMore) "Done" else "Edit", style = MaterialTheme.typography.labelMedium, color = CleanColors.Muted)
                }
                if (showMore) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stepper(
                            "Rest", formatRest(setup.restSeconds),
                            onMinus = { onUpdate { it.copy(restSeconds = (it.restSeconds - 15).coerceAtLeast(0)) } },
                            onPlus = { onUpdate { it.copy(restSeconds = (it.restSeconds + 15).coerceAtMost(600)) } },
                            modifier = Modifier.weight(1f)
                        )
                        if (!isEcho) {
                            Stepper(
                                "Kg per rep", "%+.1f".format(setup.progressionKg),
                                onMinus = { onUpdate { it.copy(progressionKg = (it.progressionKg - 0.5f).coerceAtLeast(-3f)) } },
                                onPlus = { onUpdate { it.copy(progressionKg = (it.progressionKg + 0.5f).coerceAtMost(3f)) } },
                                modifier = Modifier.weight(1f)
                            )
                        }
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
                    append("${setup.sets} × ${setup.reps}")
                    if (!isEcho) append(" @ ${setup.percent}%")
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
