package com.example.vitruvianredux.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vitruvianredux.data.local.UserProfileEntity
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.*

val PROFILE_PRESET_COLORS = listOf(
    Color(-10354962),  // 0xFF6200EE — Purple
    Color(-13388315),  // 0xFF2196F3 — Blue
    Color(-11751600),  // 0xFF4CAF50 — Green
    Color(-26624),     // 0xFFFF9800 — Orange
    Color(-769226),    // 0xFFF44336 — Red
    Color(-16738680),  // 0xFF009688 — Teal
    Color(-1596399),   // 0xFFE91E63 — Pink
    Color(-683712),    // 0xFFFF5722 — Deep Orange
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val connectedHeartRateSensors by viewModel.connectedHeartRateSensors.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<UserProfileEntity?>(null) }
    var heartRateDialogFor by remember { mutableStateOf<UserProfileEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("Profiles")
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add profile") },
                text = { Text("Add Profile") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            item { Spacer(modifier = Modifier.height(Spacing.small)) }

            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    isActive = profile.id == activeProfileId,
                    canDelete = profiles.size > 1,
                    onSelect = { viewModel.setActiveProfile(profile.id) },
                    onDelete = { showDeleteConfirm = profile },
                    heartRateConnected = profile.hrSensorAddress != null &&
                        profile.hrSensorAddress in connectedHeartRateSensors,
                    onHeartRate = { heartRateDialogFor = profile }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddProfileDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, colorInt ->
                viewModel.createProfile(name, colorInt)
                showAddDialog = false
            }
        )
    }

    heartRateDialogFor?.let { dialogProfile ->
        // Re-read so the dialog reflects a pairing made while it is open
        val profile = profiles.firstOrNull { it.id == dialogProfile.id } ?: dialogProfile
        HeartRateSensorDialog(
            viewModel = viewModel,
            profile = profile,
            otherProfiles = profiles.filter { it.id != profile.id },
            onDismiss = {
                viewModel.stopHeartRateScan()
                heartRateDialogFor = null
            }
        )
    }

    showDeleteConfirm?.let { profile ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Profile") },
            text = { Text("Delete \"${profile.name}\"? Their workout history will remain in the database.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfileEntity,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    heartRateConnected: Boolean,
    onHeartRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isActive) 2.dp else 0.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(if (isActive) 8.dp else 2.dp, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(profile.colorInt)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isActive) {
                    Text(
                        text = "Active profile",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                profile.hrSensorName?.let { sensorName ->
                    Text(
                        text = if (heartRateConnected) "♥ $sensorName • connected" else "♥ $sensorName • not broadcasting",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onHeartRate) {
                Icon(
                    if (profile.hrSensorAddress != null) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Heart rate sensor",
                    tint = if (heartRateConnected) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete profile",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProfileDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorInt: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColorIndex by remember { mutableIntStateOf(0) }
    val nameError = name.isBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = nameError && name.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILE_PRESET_COLORS.forEachIndexed { index, color ->
                        val isSelected = index == selectedColorIndex
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), PROFILE_PRESET_COLORS[selectedColorIndex].value.toInt())
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Pair a Bluetooth heart rate sensor (a Garmin watch broadcasting HR, or a chest strap) to one
 * profile. Optional: the trainer works the same without one.
 */
@Composable
internal fun HeartRateSensorDialog(
    viewModel: MainViewModel,
    profile: UserProfileEntity,
    otherProfiles: List<UserProfileEntity>,
    onDismiss: () -> Unit
) {
    val scanResults by viewModel.heartRateScanResults.collectAsState()
    val isScanning by viewModel.isScanningHeartRate.collectAsState()

    LaunchedEffect(Unit) { viewModel.startHeartRateScan() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Heart rate for ${profile.name}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    "Heart rate is only saved for ${profile.name}'s sets. " +
                        "On a Garmin watch, turn on Broadcast Heart Rate: hold the top button and pick it " +
                        "from the controls, or go to Settings > Health & Wellness > Wrist Heart Rate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profile.hrSensorName?.let {
                    Text("Paired: $it", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Searching…", style = MaterialTheme.typography.bodySmall)
                    } else {
                        TextButton(onClick = { viewModel.startHeartRateScan() }) { Text("Search again") }
                    }
                }
                scanResults.forEach { sensor ->
                    val owner = otherProfiles.firstOrNull { it.hrSensorAddress == sensor.address }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.pairHeartRateSensor(profile.id, sensor.address, sensor.name)
                                onDismiss()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(sensor.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    sensor.address == profile.hrSensorAddress -> "Paired to ${profile.name}"
                                    owner != null -> "Paired to ${owner.name}, tap to move to ${profile.name}"
                                    else -> sensor.address
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            if (profile.hrSensorAddress != null) {
                TextButton(
                    onClick = {
                        viewModel.unpairHeartRateSensor(profile.id)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Unpair") }
            }
        }
    )
}
