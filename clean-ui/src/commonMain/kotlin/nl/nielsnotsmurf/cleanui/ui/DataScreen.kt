package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.data.PersonalData
import nl.nielsnotsmurf.cleanui.data.rememberDataFiles
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.platform.formatDate
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.ui.components.PrimaryButton
import nl.nielsnotsmurf.cleanui.ui.components.SectionLabel

/**
 * Settings, Your data: export everything to one file, import it again (on this or another
 * device), and delete all workouts. The same screen on Android and the web.
 */
@Composable
fun DataScreen(
    gateway: TrainerGateway,
    store: CleanUiStore,
    /** Platform extras below the data options (the web's usage counts and install guide). */
    extras: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val files = rememberDataFiles()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<PersonalData.Parsed?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionLabel("Export and import", Modifier.padding(top = 8.dp))
        Text(
            "One file with every profile, PIN, 1RM, routine, workout and setting. Keep it somewhere safe, " +
                "or import it on a new phone or in another browser.",
            style = MaterialTheme.typography.bodyMedium
        )
        PrimaryButton("Export my data", loading = busy, onClick = {
            scope.launch {
                busy = true
                message = runCatching {
                    val text = PersonalData.encode(store.snapshot(), gateway.dataPlatform, gateway.exportData(), nowMillis())
                    val saved = files.save("cable-data-${formatDate(nowMillis(), "yyyy-MM-dd")}.json", text)
                    if (saved) "Data exported." else null
                }.getOrElse { "Export failed." }
                busy = false
            }
        })
        val toImport = pending
        if (toImport == null) {
            PrimaryButton("Import data", enabled = !busy, onClick = {
                scope.launch {
                    val text = files.open() ?: return@launch
                    val parsed = PersonalData.decode(text)
                    if (parsed == null) message = "That file isn't a Cable data export." else { pending = parsed; message = null }
                }
            })
        } else {
            val samePlatform = toImport.host != null && toImport.platform == gateway.dataPlatform
            Text(
                if (samePlatform) "Import this file? Profiles, workouts and routines are added; settings and 1RMs from the file replace the ones here."
                else "This file comes from another platform: its 1RMs, PINs, routines and settings are imported, workouts and profiles stay where they were made.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            PrimaryButton("Yes, import it", accent = true, loading = busy, onClick = {
                scope.launch {
                    busy = true
                    val hostOk = if (samePlatform) runCatching { gateway.importData(toImport.host!!) }.getOrDefault(false) else true
                    runCatching { store.restore(toImport.clean) }
                    message = if (hostOk) "Data imported." else "Settings and 1RMs imported, but the workouts couldn't be read."
                    pending = null
                    busy = false
                }
            })
            PrimaryButton("Cancel", onClick = { pending = null })
        }

        SectionLabel("Workouts", Modifier.padding(top = 8.dp))
        if (!confirmDelete) {
            PrimaryButton("Delete all workouts", onClick = { confirmDelete = true; message = null })
        } else {
            Text("Delete every workout? Profiles, 1RMs and routines stay.", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            PrimaryButton("Yes, delete them", accent = true, onClick = {
                gateway.deleteAllWorkouts()
                confirmDelete = false
                message = "All workouts deleted."
            })
            PrimaryButton("Cancel", onClick = { confirmDelete = false })
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CleanColors.Muted) }
        extras?.invoke()
    }
}
