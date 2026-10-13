package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.TrainingStats
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.LifterChip
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L

/** One lifter at a time: this week, streak, 1RMs, progress per exercise and recent records. */
@Composable
fun AnalyticsScreen(
    state: CleanUiState,
    history: List<HistoryEntry>,
    onMoreAnalytics: () -> Unit
) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val lifter = state.lifters.firstOrNull { it.id == picked }
        ?: state.activeLifters.firstOrNull() ?: state.lifters.firstOrNull()
    val now = remember(history) { System.currentTimeMillis() }
    val mine = remember(history, lifter?.id) { history.filter { it.lifterId == lifter?.id } }
    val week = remember(mine) { TrainingStats.totals(mine, now - WEEK_MS) }
    val streak = remember(mine) { TrainingStats.weekStreak(mine, now) }
    val progress = remember(mine) { TrainingStats.progress(mine) }
    val records = remember(mine) { TrainingStats.records(mine) }
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val names = state.exercises.associate { it.id to it.name }
    val maxes = lifter?.let { state.oneRepMaxes[it.id] }.orEmpty()
        .filterValues { !it.isEstimate }
        .mapNotNull { (id, orm) -> names[id]?.let { it to orm } }
        .sortedByDescending { it.second.kg }
    val accent = lifter?.color ?: CleanColors.Accent

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Analytics", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 16.dp))
        if (state.lifters.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.lifters, key = { it.id }) { l -> LifterChip(l, selected = l.id == lifter?.id, onClick = { picked = l.id }) }
            }
        }

        Card {
            SectionLabel("Last 7 days")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stat(week.workouts.toString(), "workouts", Modifier.weight(1f))
                Stat(week.sets.toString(), "sets", Modifier.weight(1f))
                Stat(week.reps.toString(), "reps", Modifier.weight(1f))
                Stat(streak.toString(), if (streak == 1) "week streak" else "weeks streak", Modifier.weight(1f))
            }
        }

        Card {
            SectionLabel("1RMs")
            if (maxes.isEmpty()) {
                Text("No 1RMs yet. Run the 1RM test from Train, or train an exercise and one is worked out from your sets.",
                    style = MaterialTheme.typography.bodySmall)
            }
            maxes.take(8).forEach { (name, orm) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(
                            when (orm.source) {
                                OneRepMaxSource.BENCHMARK -> "Tested"
                                OneRepMaxSource.MANUAL -> "Entered by hand"
                                else -> "Worked out from your sets"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(LoadCalculator.format(orm.kg, state.unit), style = MaterialTheme.typography.titleMedium, color = accent)
                }
            }
            if (maxes.size > 8) Text("and ${maxes.size - 8} more", style = MaterialTheme.typography.bodySmall)
        }

        Card {
            SectionLabel("Progress · heaviest load per session")
            if (progress.isEmpty()) {
                Text("Train an exercise a few times to see it here.", style = MaterialTheme.typography.bodySmall)
            }
            progress.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.exerciseName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        val change = p.last - p.first
                        Text(
                            "Best ${LoadCalculator.format(p.best, state.unit)}" +
                                if (p.points.size > 1 && change != 0f) " · ${if (change > 0) "+" else "−"}${LoadCalculator.format(kotlin.math.abs(change), state.unit)} since ${dateFormat.format(Date(p.points.first().first))}" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Sparkline(p.points.map { it.second }, accent, Modifier.width(96.dp).height(36.dp))
                }
            }
        }

        if (records.isNotEmpty()) {
            Card {
                SectionLabel("Recent records")
                records.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(r.exerciseName, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${LoadCalculator.format(r.kg, state.unit)} · ${dateFormat.format(Date(r.timestamp))}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        Row(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
                .clickable(role = Role.Button, onClick = onMoreAnalytics)
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Charts, insights and CSV export", style = MaterialTheme.typography.bodyLarge)
                Text("PR charts, muscle balance, consistency and safety events", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CleanColors.Muted)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { content() }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Sparkline(values: List<Float>, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (values.size < 2) {
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(size.width - 4.dp.toPx(), size.height / 2))
            return@Canvas
        }
        val min = values.min()
        val range = (values.max() - min).takeIf { it > 0f } ?: 1f
        val pad = 4.dp.toPx()
        fun point(i: Int) = Offset(
            pad + i * (size.width - 2 * pad) / (values.size - 1),
            size.height - pad - (values[i] - min) / range * (size.height - 2 * pad)
        )
        val path = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, radius = 3.5.dp.toPx(), center = point(values.lastIndex))
    }
}
