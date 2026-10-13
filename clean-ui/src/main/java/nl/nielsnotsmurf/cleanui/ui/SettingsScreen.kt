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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.LedScheme
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.ConnectionPill
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Stepper

@Composable
fun SettingsScreen(
    state: CleanUiState,
    settings: CleanSettings,
    ledSchemes: List<LedScheme>,
    onUpdate: ((CleanSettings) -> CleanSettings) -> Unit,
    onLedScheme: (Int) -> Unit,
    onProfiles: () -> Unit,
    onMoreSettings: () -> Unit,
    onConnectionLogs: () -> Unit,
    onProtocolTester: () -> Unit,
    onSwitchToClassic: () -> Unit
) {
    // The trainer doesn't report its LED scheme, so this only marks what was sent this session.
    var ledIndex by rememberSaveable { mutableStateOf(-1) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            ConnectionPill(state.connection)
        }

        SectionLabel("Training", Modifier.padding(top = 8.dp))
        Group {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Weight unit", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                CleanFilterChip("kg", selected = settings.unit == WeightUnit.KG, onClick = { onUpdate { it.copy(unit = WeightUnit.KG) } })
                CleanFilterChip("lb", selected = settings.unit == WeightUnit.LB, onClick = { onUpdate { it.copy(unit = WeightUnit.LB) } })
            }
            Stepper(
                "Default reps for a new exercise", settings.defaultReps.toString(),
                onMinus = { onUpdate { it.copy(defaultReps = (it.defaultReps - 1).coerceAtLeast(1)) } },
                onPlus = { onUpdate { it.copy(defaultReps = (it.defaultReps + 1).coerceAtMost(50)) } },
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Toggle("Autoplay", "Start the next set by itself after rest", settings.autoplay) { v -> onUpdate { it.copy(autoplay = v) } }
            Toggle("Stop at top", "End sets at the top of the last rep instead of the bottom", settings.stopAtTop) { v -> onUpdate { it.copy(stopAtTop = v) } }
            Toggle("Workout beeps", "Sounds for countdown and rest", settings.beeps) { v -> onUpdate { it.copy(beeps = v) } }
            Toggle("Show exercise videos", "Demo videos in set setup and the 1RM test", settings.videos) { v -> onUpdate { it.copy(videos = v) } }
        }

        SectionLabel("People", Modifier.padding(top = 8.dp))
        Group { LinkRow("Profiles", "${state.lifters.size} profiles", onProfiles) }

        if (ledSchemes.isNotEmpty()) {
            SectionLabel("Trainer LED colour", Modifier.padding(top = 8.dp))
            Group {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ledSchemes.forEachIndexed { i, scheme ->
                        val c = Color(scheme.colorsArgb.firstOrNull() ?: 0xFF888888.toInt())
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .then(if (i == ledIndex) Modifier.border(3.dp, CleanColors.Ink, CircleShape) else Modifier)
                                .clickable(role = Role.RadioButton) { ledIndex = i; onLedScheme(i) }
                        )
                    }
                }
                Text(
                    if (ledIndex >= 0) "${ledSchemes[ledIndex].name} sent to the trainer." else "Tap a colour to send it to the trainer.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }

        SectionLabel("Data and app", Modifier.padding(top = 8.dp))
        Group {
            LinkRow("Backup, restore and delete all", "Everything else from the classic settings", onMoreSettings)
            LinkRow("Connection logs", "Bluetooth events, search and export", onConnectionLogs)
            LinkRow("Protocol tester", "For debugging the trainer connection", onProtocolTester)
            LinkRow("Switch to the classic layout", "You can switch back from its Settings", onSwitchToClassic)
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) { content() }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Switch) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = CleanColors.Accent, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CleanColors.Muted)
    }
}
