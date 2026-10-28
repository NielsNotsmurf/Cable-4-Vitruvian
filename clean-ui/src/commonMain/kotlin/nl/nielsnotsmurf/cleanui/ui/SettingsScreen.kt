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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import nl.nielsnotsmurf.cleanui.ui.components.rememberFeedbackUrl
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.LedScheme
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.CleanFilterChip
import nl.nielsnotsmurf.cleanui.ui.components.ConnectionPill
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel
import nl.nielsnotsmurf.cleanui.ui.components.Stepper
import nl.nielsnotsmurf.cleanui.ui.tour.TourTargets
import nl.nielsnotsmurf.cleanui.ui.tour.tourTarget
import nl.nielsnotsmurf.cleanui.platform.DateFormatter

/**
 * Settings, from personal to technical: account, people, training, sound and video,
 * trainer, help, data, and the developer tools folded away under Advanced.
 */
@Composable
fun SettingsScreen(
    state: CleanUiState,
    settings: CleanSettings,
    account: AccountState,
    ledSchemes: List<LedScheme>,
    /** The scheme picked last, or -1. */
    ledIndex: Int,
    onUpdate: ((CleanSettings) -> CleanSettings) -> Unit,
    onLedScheme: (Int) -> Unit,
    uiSounds: Boolean,
    onUiSounds: (Boolean) -> Unit,
    onProfiles: () -> Unit,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onRemovePin: () -> Unit,
    onSwitchAccount: () -> Unit,
    onStartTour: () -> Unit,
    onPairHeartRate: ((lifterId: String) -> Unit)?,
    onBackup: () -> Unit,
    onConnectionLogs: () -> Unit,
    onProtocolTester: () -> Unit,
    onSwitchToClassic: () -> Unit
) {
    val me = state.lifters.firstOrNull { it.id == account.signedInId }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showHeartRateGuide by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { DateFormatter("d MMM yyyy") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            ConnectionPill(state.connection)
        }

        if (me != null) {
            SectionLabel("Account", Modifier.padding(top = 8.dp))
            Group(Modifier.tourTarget(TourTargets.ACCOUNT)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(me.color), contentAlignment = Alignment.Center) {
                        Text(me.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(me.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (account.hasPin(me.id)) "PIN on. Asked when the app opens as you." else "No PIN. Anyone with this phone can open your account.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (account.hasPin(me.id)) {
                    LinkRow("Change PIN", "Type the current one, then a new one", onChangePin)
                    LinkRow("Remove PIN", "Your history becomes visible to everyone on this phone", onRemovePin)
                } else {
                    LinkRow("Set a PIN", "Keep your history, 1RMs and heart rate private", onSetPin)
                }
                LinkRow("Switch account", "Locks the app and asks who's training", onSwitchAccount)
            }
        }

        SectionLabel("People", Modifier.padding(top = 8.dp))
        Group(Modifier.tourTarget(TourTargets.SETTINGS_PROFILES)) {
            val sensors = state.lifters.count { it.lifter.heartRateSensor != null }
            LinkRow(
                "Profiles and heart rate",
                "${state.lifters.size} profiles · " + if (sensors == 0) "no heart rate sensors" else "$sensors heart rate sensor" + if (sensors > 1) "s" else "",
                onProfiles
            )
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
                "Sets per exercise", settings.defaultSets.toString(),
                onMinus = { onUpdate { it.copy(defaultSets = (it.defaultSets - 1).coerceAtLeast(1)) } },
                onPlus = { onUpdate { it.copy(defaultSets = (it.defaultSets + 1).coerceAtMost(10)) } },
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Stepper(
                "Reps per set", settings.defaultReps.toString(),
                onMinus = { onUpdate { it.copy(defaultReps = (it.defaultReps - 1).coerceAtLeast(1)) } },
                onPlus = { onUpdate { it.copy(defaultReps = (it.defaultReps + 1).coerceAtMost(50)) } },
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Stepper(
                "Warm-up reps (set the range of motion)", settings.warmupReps.toString(),
                onMinus = { onUpdate { it.copy(warmupReps = (it.warmupReps - 1).coerceAtLeast(1)) } },
                onPlus = { onUpdate { it.copy(warmupReps = (it.warmupReps + 1).coerceAtMost(3)) } },
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                Text("Training load · % of 1RM", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("${settings.defaultPercent}%", style = MaterialTheme.typography.titleLarge)
            }
            Slider(
                value = settings.defaultPercent.toFloat(),
                onValueChange = { v -> onUpdate { it.copy(defaultPercent = (v / 5f).roundToInt() * 5) } },
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
            Toggle("Autoplay", "Start the next set by itself after rest", settings.autoplay) { v -> onUpdate { it.copy(autoplay = v) } }
            Toggle("Stop at top", "End sets at the top of the last rep instead of the bottom", settings.stopAtTop) { v -> onUpdate { it.copy(stopAtTop = v) } }
        }

        SectionLabel("Sound and video", Modifier.padding(top = 8.dp))
        Group {
            Toggle("Menu sounds", "Bops and boops for taps and screens", uiSounds, onUiSounds)
            Toggle("Workout beeps", "Countdown, reps, set done and rest", settings.beeps) { v -> onUpdate { it.copy(beeps = v) } }
            Toggle("Exercise videos", "Demo clips in Set setup and the 1RM test", settings.videos) { v -> onUpdate { it.copy(videos = v) } }
        }

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
                                .clickable(role = Role.RadioButton) { onLedScheme(i) }
                        )
                    }
                }
                Text(
                    if (ledIndex >= 0) "${ledSchemes[ledIndex].name} is sent to the trainer and colours the app." else "Tap a colour to send it to the trainer. The app takes on the same colour.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }
        }

        SectionLabel("Help", Modifier.padding(top = 8.dp))
        Group(Modifier.tourTarget(TourTargets.SETTINGS_HELP)) {
            val tour = me?.let { account.tour(it.id) } ?: TourStatus.NotSeen
            LinkRow(
                "App tour",
                when (tour) {
                    is TourStatus.Done -> "Watched on ${dateFormat.format(tour.at)}. Watch it again"
                    is TourStatus.Skipped -> "Skipped. Arrows walk you through every screen"
                    TourStatus.NotSeen -> "Arrows walk you through every screen"
                },
                onStartTour
            )
            LinkRow("Heart rate setup", "Broadcast from a Garmin watch, or use a chest strap", { showHeartRateGuide = true })
            val feedbackUrl = rememberFeedbackUrl()
            val uriHandler = LocalUriHandler.current
            LinkRow("Send feedback", "Report a problem or share an idea on GitHub", {
                uriHandler.openUri(feedbackUrl)
            })
        }

        SectionLabel("Data", Modifier.padding(top = 8.dp))
        Group {
            LinkRow("Backup and restore", "Save everything to Download/Cable, restore it, or delete all workouts", onBackup)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { showAdvanced = !showAdvanced }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel("Advanced", Modifier.weight(1f))
            Text(if (showAdvanced) "Hide" else "Show", style = MaterialTheme.typography.bodySmall)
        }
        if (showAdvanced) {
            Group {
                LinkRow("Connection logs", "Bluetooth events, search and export", onConnectionLogs)
                LinkRow("Protocol tester", "For debugging the trainer connection", onProtocolTester)
                LinkRow("Switch to the classic layout", "You can switch back from its Settings", onSwitchToClassic)
            }
        }
    }

    if (showHeartRateGuide) {
        AlertDialog(
            onDismissRequest = { showHeartRateGuide = false },
            modifier = Modifier.clickSounds(),
            containerColor = CleanColors.Background,
            title = { Text("Heart rate setup") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { HeartRateSteps() } },
            confirmButton = {
                if (me != null && onPairHeartRate != null) {
                    TextButton(onClick = { showHeartRateGuide = false; onPairHeartRate(me.id) }) { Text("Pair for ${me.name}") }
                } else {
                    TextButton(onClick = { showHeartRateGuide = false }) { Text("OK") }
                }
            },
            dismissButton = {
                if (me != null && onPairHeartRate != null) {
                    TextButton(onClick = { showHeartRateGuide = false }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, CleanColors.Line, RoundedCornerShape(18.dp))
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
