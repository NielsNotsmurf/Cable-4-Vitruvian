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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    val picked by files.picked.collectAsState()

    LaunchedEffect(picked) {
        val file = picked ?: return@LaunchedEffect
        files.clearPicked()
        val text = file.text
        val parsed = text?.let { PersonalData.decode(it) }
        when {
            text == null -> message = "That file couldn't be opened. Save it to your phone's Downloads and try again."
            parsed == null -> message = "That file isn't a Cable data export."
            else -> { pending = parsed; message = null }
        }
    }

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
            PrimaryButton("Import data", enabled = !busy, onClick = { message = null; files.pick() })
        } else {
            val samePlatform = toImport.host != null && toImport.platform == gateway.dataPlatform
            Text(
                if (samePlatform) "Import this file? Profiles, workouts and routines are added; settings and 1RMs from the file replace the ones here."
                else "This file comes from another platform: its profiles, 1RMs, PINs, routines and settings are imported, workouts stay where they were made.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            PrimaryButton("Yes, import it", accent = true, loading = busy, onClick = {
                scope.launch {
                    busy = true
                    val hostOk = if (samePlatform) runCatching { gateway.importData(toImport.host!!) }.getOrDefault(false) else true
                    // From another platform: recreate its profiles by name, so their 1RMs and routine loads land on them.
                    val ids = if (samePlatform) emptyMap() else runCatching { matchLifters(gateway, toImport.lifters) }.getOrDefault(emptyMap())
                    val cleanOk = runCatching { store.restore(PersonalData.remap(toImport.clean, ids)) }.isSuccess
                    message = when {
                        !cleanOk -> "The file was read, but its routines, 1RMs and settings couldn't be saved."
                        hostOk -> "Data imported."
                        else -> "Routines, settings and 1RMs imported, but the workouts couldn't be read."
                    }
                    pending = null
                    busy = false
                }
            })
            PrimaryButton("Cancel", onClick = { pending = null })
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold) }

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
        extras?.invoke()
    }
}

/** File profile id -> the profile here with the same name, which is added first when there's none. */
private suspend fun matchLifters(gateway: TrainerGateway, fileLifters: List<PersonalData.FileLifter>): Map<String, String> {
    fun key(name: String) = name.trim().lowercase()
    val ids = mutableMapOf<String, String>()
    for (lifter in fileLifters) {
        val here = gateway.lifters.first().firstOrNull { key(it.name) == key(lifter.name) && it.id !in ids.values }
        if (here != null) { ids[lifter.id] = here.id; continue }
        val before = gateway.lifters.first().map { it.id }.toSet()
        gateway.addLifter(lifter.name.trim(), lifter.color)
        val added = withTimeoutOrNull(5_000) {
            gateway.lifters.first { list -> list.any { it.id !in before && key(it.name) == key(lifter.name) } }
        }?.firstOrNull { it.id !in before && key(it.name) == key(lifter.name) }
        if (added != null) ids[lifter.id] = added.id
    }
    return ids
}
