package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.logic.Routine
import nl.nielsnotsmurf.cleanui.logic.Routines
import nl.nielsnotsmurf.cleanui.platform.formatDate
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton

/**
 * Saved routines. Opening one shows it on the plan screen, where exercises, sets, reps,
 * the % of 1RM and progression are changed and saved straight away.
 */
@Composable
fun RoutinesScreen(
    routines: List<Routine>,
    onBack: () -> Unit,
    onOpen: (Routine) -> Unit,
    onNew: () -> Unit,
    onDelete: (String) -> Unit
) {
    var deleting by remember { mutableStateOf<Routine?>(null) }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            modifier = Modifier.clickSounds(),
            title = { Text("Delete ${r.name}?") },
            text = { Text("Workouts you did with it stay in your history.") },
            confirmButton = {
                TextButton(onClick = { onDelete(r.id); deleting = null }) { Text("Delete", color = CleanColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Your routines", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Your own workouts, with loads as % of each lifter's 1RM. Build one from scratch, or save a built workout as a routine.",
                style = MaterialTheme.typography.bodyMedium,
                color = CleanColors.Muted
            )
            PrimaryButton("New routine", onClick = onNew, accent = true)
            if (routines.isEmpty()) {
                Text("No routines yet.", style = MaterialTheme.typography.bodyMedium)
            }
            routines.forEach { r -> RoutineRow(r, onOpen = { onOpen(r) }, onDelete = { deleting = r }) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoutineRow(routine: Routine, onOpen: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.5.dp, CleanColors.Line, shape)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 4.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val count = routine.exerciseIds.size
                Text(
                    "$count exercise${if (count == 1) "" else "s"} · ${routine.sets}×${routine.reps} · ${routine.mode.label} · ${routine.percent}% of 1RM",
                    style = MaterialTheme.typography.bodySmall,
                    color = CleanColors.Muted
                )
                Text(
                    Routines.describe(routine) + (routine.lastDoneAt?.let { " · last done ${formatDate(it, "d MMM")}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = CleanColors.Muted
                )
            }
            TextButton(onClick = onDelete) { Text("Delete", color = CleanColors.Muted) }
        }
        routine.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = CleanColors.Accent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(Modifier.height(4.dp).background(CleanColors.Background))
    }
}
