package com.example.vitruvianredux.presentation.screen

import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.data.local.UserProfileEntity
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.ui.theme.ThemeMode

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode
) {
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val workoutParticipants by viewModel.workoutParticipants.collectAsState()
    var showParticipantSheet by remember { mutableStateOf(false) }

    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val backgroundGradient = if (useDarkColors) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF172554))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(Color(0xFFE0E7FF), Color(0xFFFCE7F3), Color(0xFFDDD6FE))
        )
    }

    LaunchedEffect(Unit) { viewModel.updateTopBarTitle("") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) 3 else 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session participants — compact, full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                SessionParticipantsCard(
                    participants = workoutParticipants,
                    onClick = { showParticipantSheet = true }
                )
            }

            // Train by Muscle Group — featured, full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                TrainByMuscleGroupCard(
                    onClick = { navController.navigate(NavigationRoutes.MuscleGroupTraining.route) }
                )
            }

            // Just Lift
            item {
                WorkoutCard(
                    title = "Just Lift",
                    description = "Quick setup, start lifting immediately",
                    icon = Icons.Default.FitnessCenter,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE))
                    ),
                    onClick = { navController.navigate(NavigationRoutes.JustLift.route) }
                )
            }

            // Single Exercise
            item {
                WorkoutCard(
                    title = "Single Exercise",
                    description = "One exercise with full configuration",
                    icon = Icons.Default.PlayArrow,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA))
                    ),
                    onClick = { navController.navigate(NavigationRoutes.SingleExercise.route) }
                )
            }

            // Strength Assessment — full width if only 1 remaining in last row, else normal
            item(span = { GridItemSpan(if (gridColumns == 3) 1 else maxLineSpan) }) {
                StrengthAssessmentCard(
                    onClick = { navController.navigate(NavigationRoutes.BaselineAssessment.route) }
                )
            }
        }

        if (isAutoConnecting) {
            com.example.vitruvianredux.presentation.components.ConnectingOverlay(
                onCancel = { viewModel.cancelAutoConnecting() }
            )
        }

        connectionError?.let { error ->
            com.example.vitruvianredux.presentation.components.ConnectionErrorDialog(
                message = error,
                onDismiss = { viewModel.clearConnectionError() }
            )
        }
    }

    if (showParticipantSheet) {
        ParticipantPickerSheet(
            profiles = profiles,
            selected = workoutParticipants,
            onDismiss = { showParticipantSheet = false },
            onConfirm = { selection ->
                if (selection.isEmpty()) viewModel.clearWorkoutParticipants()
                else viewModel.setWorkoutParticipants(selection)
                showParticipantSheet = false
            }
        )
    }
}

// ── Cards ─────────────────────────────────────────────────────────────────────

@Composable
fun WorkoutCard(
    title: String,
    description: String,
    icon: ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        onClick = { isPressed = true; onClick() },
        modifier = Modifier.fillMaxWidth().scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPressed) 4.dp else 8.dp),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(6.dp, RoundedCornerShape(18.dp))
                    .background(gradient, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) { kotlinx.coroutines.delay(100); isPressed = false }
    }
}

@Composable
fun TrainByMuscleGroupCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED), Color(0xFFEC4899))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Train by Muscle Group",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Browse exercises — Cable suggests weights",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun StrengthAssessmentCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF06B6D4), Color(0xFF0891B2))
                        ),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Strength Assessment",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "15-min baseline — unlocks personalised weights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Participants card (unchanged) ─────────────────────────────────────────────

@Composable
private fun SessionParticipantsCard(
    participants: List<UserProfileEntity>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                if (participants.isEmpty()) {
                    Text(
                        "Solo session",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    participants.forEach { profile ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(profile.colorInt)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = profile.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Text(
                        text = participants.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Change participants",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ── Participant picker sheet ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantPickerSheet(
    profiles: List<UserProfileEntity>,
    selected: List<UserProfileEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<UserProfileEntity>) -> Unit
) {
    val selectedIds = remember(selected) { selected.map { it.id }.toMutableStateList() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Text(
                "Who's training?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = Spacing.small)
            )
            Text(
                "Select up to 4 participants. Sets rotate automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            profiles.forEach { profile ->
                val isChecked = selectedIds.contains(profile.id)
                val atMax = selectedIds.size >= 4 && !isChecked
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !atMax) {
                            if (isChecked) selectedIds.remove(profile.id)
                            else selectedIds.add(profile.id)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(profile.colorInt)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profile.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        color = if (atMax) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked && !atMax) selectedIds.add(profile.id)
                            else if (!checked) selectedIds.remove(profile.id)
                        },
                        enabled = !atMax || isChecked
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                OutlinedButton(
                    onClick = { onConfirm(emptyList()) },
                    modifier = Modifier.weight(1f)
                ) { Text("Train Alone") }
                Button(
                    onClick = {
                        val result = profiles.filter { it.id in selectedIds }
                        onConfirm(result)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedIds.isNotEmpty()
                ) { Text("Confirm") }
            }
        }
    }
}
