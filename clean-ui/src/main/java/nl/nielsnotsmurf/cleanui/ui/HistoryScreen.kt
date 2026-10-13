package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.Dot
import nl.nielsnotsmurf.cleanui.ui.components.GhostButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One workout as History shows it: an exercise everyone did back to back. */
private data class Workout(val key: String, val name: String, val timestamp: Long, val mode: String, val entries: List<HistoryEntry>)

/** Entries the classic app saved separately (one per lifter in a rotation) belong together. */
private const val SAME_WORKOUT_GAP_MS = 30 * 60 * 1000L

private fun group(entries: List<HistoryEntry>): List<Workout> {
    val out = mutableListOf<MutableList<HistoryEntry>>()
    entries.sortedByDescending { it.timestamp }.forEach { e ->
        val last = out.lastOrNull()?.last()
        val sameGroup = last != null && (
            (e.groupId != e.id && e.groupId == last.groupId) ||
                (e.groupId == e.id && last.groupId == last.id && e.exerciseName == last.exerciseName &&
                    last.timestamp - e.timestamp <= SAME_WORKOUT_GAP_MS)
            )
        if (sameGroup) out.last().add(e) else out.add(mutableListOf(e))
    }
    return out.map { list ->
        val names = list.map { it.exerciseName }.distinct()
        Workout(
            key = list.first().id,
            name = if (names.size == 1) names.first() else "${names.first()} + ${names.size - 1} more",
            timestamp = list.first().timestamp,
            mode = list.first().mode,
            entries = list
        )
    }
}

@Composable
fun HistoryScreen(
    state: CleanUiState,
    history: List<HistoryEntry>,
    onDelete: (List<String>) -> Unit
) {
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Workout?>(null) }
    val byId = state.lifters.associateBy { it.id }
    val shown = remember(history, filter) { history.filter { filter == null || it.lifterId == filter } }
    val workouts = remember(shown) { group(shown) }
    val dayFormat = remember { SimpleDateFormat("EEEE d MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "History",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
            )
        }
        if (state.lifters.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CleanFilterChip("Everyone", selected = filter == null, onClick = { filter = null }) }
                items(state.lifters, key = { it.id }) { l ->
                    CleanFilterChip(l.name, selected = filter == l.id, onClick = { filter = l.id })
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp)).padding(vertical = 14.dp)
            ) {
                Stat(workouts.size.toString(), "workouts", Modifier.weight(1f))
                Stat(shown.sumOf { it.sets }.toString(), "sets", Modifier.weight(1f))
                Stat(shown.sumOf { it.reps }.toString(), "reps", Modifier.weight(1f))
            }
        }
        if (workouts.isEmpty()) item {
            Text(
                "No workouts yet. Your sets show up here after you train.",
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
        var lastDay = ""
        workouts.forEach { w ->
            val day = dayFormat.format(Date(w.timestamp))
            if (day != lastDay) {
                lastDay = day
                item(key = "day-$day") { SectionLabel(day, Modifier.padding(start = 20.dp, top = 6.dp)) }
            }
            item(key = w.key) {
                val open = openKey == w.key
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, if (open) CleanColors.Ink else CleanColors.Line, RoundedCornerShape(18.dp))
                        .clickable { openKey = if (open) null else w.key }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${timeFormat.format(Date(w.timestamp))} · ${w.mode}", style = MaterialTheme.typography.bodySmall)
                    }
                    w.entries.groupBy { it.lifterId }.forEach { (lifterId, list) ->
                        val l = byId[lifterId]
                        val top = list.mapNotNull { it.topKgPerCable }.maxOrNull()
                        val maxHr = list.mapNotNull { it.maxHeartRate }.maxOrNull()
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            l?.let { Dot(it.color) }
                            Text(
                                buildString {
                                    append(l?.name ?: "Removed profile")
                                    append(" · ${list.sumOf { it.sets }} sets · ${list.sumOf { it.reps }} reps")
                                    append(if (top != null) " · top ${LoadCalculator.format(top, state.unit)}" else " · adaptive load")
                                    if (maxHr != null) append(" · ♥ max $maxHr")
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (open) {
                        val secs = w.entries.sumOf { it.durationMs } / 1000
                        w.entries.filter { it.maxHeartRate != null }.groupBy { it.lifterId }.forEach { (id, list) ->
                            val avg = list.mapNotNull { it.avgHeartRate }.takeIf { it.isNotEmpty() }?.average()?.toInt()
                            Text(
                                "${byId[id]?.name ?: "Removed profile"}: heart rate " +
                                    (if (avg != null) "avg $avg · " else "") + "max ${list.mapNotNull { it.maxHeartRate }.max()} bpm",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (secs > 0) {
                            Text("Duration: ${secs / 60} min ${secs % 60} s", style = MaterialTheme.typography.bodySmall)
                        }
                        if (w.entries.map { it.exerciseName }.distinct().size > 1) {
                            w.entries.forEach { e ->
                                Text("${e.exerciseName} · ${e.sets} × ${e.reps / e.sets.coerceAtLeast(1)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        GhostButton("Delete workout", onClick = { confirmDelete = w }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    confirmDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this workout?") },
            text = { Text("${w.name} for ${w.entries.map { it.lifterId }.distinct().size} lifter(s). This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(w.entries.map { it.id }); confirmDelete = null; openKey = null }) {
                    Text("Delete", color = CleanColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
