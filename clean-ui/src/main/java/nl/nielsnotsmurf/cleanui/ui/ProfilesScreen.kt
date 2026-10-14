package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.tour.TourTargets
import nl.nielsnotsmurf.cleanui.ui.tour.tourTarget

/** The classic app's profile colours, so profiles look the same in both layouts. */
internal val PROFILE_COLORS = listOf(
    Color(0xFF6200EE), Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFFF44336), Color(0xFF009688), Color(0xFFE91E63), Color(0xFFFF5722)
)

/** Add and delete profiles, and pick who's training. */
@Composable
fun ProfilesScreen(
    state: CleanUiState,
    account: AccountState,
    onBack: () -> Unit,
    onToggleLifter: (String) -> Unit,
    onAdd: (name: String, colorArgb: Int) -> Unit,
    onDelete: (String) -> Unit,
    /** Opens pairing for a heart rate sensor (Garmin watch or strap); null hides it. */
    onHeartRate: ((String) -> Unit)? = null
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val active = state.activeLifters.map { it.id }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text("Profiles", style = MaterialTheme.typography.headlineLarge)
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("Tap to add or remove from today's training")
                state.lifters.forEach { l ->
                    val training = l.id in active
                    val position = active.indexOf(l.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.5.dp, if (training) l.color else CleanColors.Line, RoundedCornerShape(16.dp))
                            .clickable(role = Role.Checkbox) { onToggleLifter(l.id) }
                            .heightIn(min = 64.dp)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(l.color), contentAlignment = Alignment.Center) {
                            Text(l.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(l.name, style = MaterialTheme.typography.titleMedium)
                                if (account.hasPin(l.id)) {
                                    Icon(Icons.Filled.Lock, contentDescription = "Has a PIN", tint = CleanColors.Muted, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text(
                                when {
                                    !training -> "Not training"
                                    active.size == 1 -> "Training"
                                    else -> "Training · lifts ${ordinal(position + 1)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (training) l.color else CleanColors.Muted
                            )
                            l.lifter.heartRateSensor?.let { sensor ->
                                Text(
                                    "♥ $sensor · " + if (l.lifter.heartRateConnected) "connected" else "not broadcasting",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            l.lifter.garminWatch?.let { watch ->
                                Text("⌚ $watch · records this lifter's sets", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (onHeartRate != null) {
                            // The tour points at the first profile's heart button.
                            val target = if (l.id == state.lifters.first().id) Modifier.tourTarget(TourTargets.HEART_RATE) else Modifier
                            IconButton(onClick = { onHeartRate(l.id) }, modifier = target) {
                                Icon(
                                    if (l.lifter.heartRateSensor != null) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Heart rate sensor for ${l.name}",
                                    tint = if (l.lifter.heartRateConnected) CleanColors.Danger else CleanColors.Muted
                                )
                            }
                        }
                        if (state.lifters.size > 1) {
                            IconButton(onClick = { deleteId = l.id }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete ${l.name}", tint = CleanColors.Muted)
                            }
                        }
                    }
                }
                Text(
                    "Up to ${CleanViewModel.MAX_LIFTERS} people can train together. Sets rotate in this order.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton("Add profile", onClick = { adding = true })
        }
    }

    if (adding) {
        AddProfileDialog(
            onDismiss = { adding = false },
            onAdd = { name, color -> onAdd(name, color); adding = false }
        )
    }
    deleteId?.let { id ->
        val name = state.lifters.firstOrNull { it.id == id }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete $name?") },
            text = { Text("Their workout history stays in the app, and their PIN is removed.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deleteId = null }) { Text("Delete", color = CleanColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddProfileDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var colorIndex by rememberSaveable { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CleanColors.Background,
        title = { Text("New profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CleanColors.Ink)
                )
                SectionLabel("Colour")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PROFILE_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (i == colorIndex) Modifier.border(3.dp, CleanColors.Ink, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { colorIndex = i }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, PROFILE_COLORS[colorIndex].toArgb()) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun ordinal(n: Int): String = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }
