package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.data.local.UserProfileEntity
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import com.example.vitruvianredux.ui.theme.ThemeMode
import java.time.LocalDate
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Home screen showing workout type selection with modern gradient card design.
 * This is the main landing screen when user opens the app.
 */
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: MainViewModel,
    themeMode: ThemeMode
) {
    // Collect stats from ViewModel
    val workoutStreak by viewModel.workoutStreak.collectAsState()
    val completedWorkouts by viewModel.completedWorkouts.collectAsState()
    val progressPercentage by viewModel.progressPercentage.collectAsState()

    // Collect connection state
    val isAutoConnecting by viewModel.isAutoConnecting.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    // Collect active program and routines for Active Program Widget
    val activeProgram by viewModel.activeProgram.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    // Participant session state
    val profiles by viewModel.profiles.collectAsState()
    val workoutParticipants by viewModel.workoutParticipants.collectAsState()
    var showParticipantSheet by remember { mutableStateOf(false) }

    // Determine actual theme (matching Theme.kt logic)
    val useDarkColors = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val backgroundGradient = if (useDarkColors) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A), // slate-900
                Color(0xFF1E1B4B), // indigo-950
                Color(0xFF172554)  // blue-950
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFE0E7FF), // indigo-200 - soft lavender
                Color(0xFFFCE7F3), // pink-100 - soft pink
                Color(0xFFDDD6FE)  // violet-200 - soft violet
            )
        )
    }

    // Clear title to allow global branding to show
    LaunchedEffect(Unit) {
        viewModel.updateTopBarTitle("")
    }

    // Detect orientation for grid layout
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridColumns = if (isLandscape) 4 else 2

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Active Program Widget - Full Width
            if (activeProgram != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeActiveProgramCard(
                        program = activeProgram!!,
                        routines = routines,
                        weightUnit = weightUnit,
                        formatWeight = viewModel::formatWeight,
                        kgToDisplay = viewModel::kgToDisplay,
                        onStartRoutine = { routineId ->
                            viewModel.ensureConnection(
                                onConnected = {
                                    viewModel.loadRoutineById(routineId)
                                    viewModel.startWorkout()
                                    navController.navigate(NavigationRoutes.DailyRoutines.route)
                                },
                                onFailed = { /* Error shown via StateFlow */ }
                            )
                        }
                    )
                }
            }

            // Session participants card — full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                SessionParticipantsCard(
                    participants = workoutParticipants,
                    onClick = { showParticipantSheet = true }
                )
            }

            // Phase 2: Train by Muscle Group — full width featured card
            item(span = { GridItemSpan(maxLineSpan) }) {
                TrainByMuscleGroupCard(
                    onClick = { navController.navigate(NavigationRoutes.MuscleGroupTraining.route) }
                )
            }

            // Cards Grid
            item {
                WorkoutCard(
                    title = "Just Lift",
                    description = "Quick setup, start lifting immediately",
                    icon = Icons.Default.FitnessCenter,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF9333EA), Color(0xFF7E22CE)) // purple-500 to purple-700
                    ),
                    onClick = { navController.navigate(NavigationRoutes.JustLift.route) }
                )
            }

            item {
                WorkoutCard(
                    title = "Single Exercise",
                    description = "Perform a single customized exercise",
                    icon = Icons.Default.PlayArrow,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF8B5CF6), Color(0xFF9333EA)) // violet-500 to purple-600
                    ),
                    onClick = { navController.navigate(NavigationRoutes.SingleExercise.route) }
                )
            }

            item {
                WorkoutCard(
                    title = "Daily Routines",
                    description = "Build multi-exercise workouts",
                    icon = Icons.Default.CalendarToday,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)) // indigo-500 to violet-600
                    ),
                    onClick = { navController.navigate(NavigationRoutes.DailyRoutines.route) }
                )
            }

            item {
                WorkoutCard(
                    title = "Weekly Programs",
                    description = "Build a structured schedule of routines",
                    icon = Icons.Default.DateRange,
                    gradient = Brush.linearGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1)) // blue-500 to indigo-600
                    ),
                    onClick = { navController.navigate(NavigationRoutes.WeeklyPrograms.route) }
                )
            }
        }

        // Auto-connect UI overlays (same as exercise start screens)
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

/**
 * Compact workout card matching reference design.
 * Features: 64dp icon, title, description, smooth animations.
 * No dummy stats displayed.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        targetValue = if (isPressed) 0.95f else 1f, // Material 3 Expressive: More scale (was 0.97f)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, // Material 3 Expressive: More bouncy (was MediumBouncy)
            stiffness = Spring.StiffnessLow // Material 3 Expressive: Lower stiffness for springy feel (was 400f)
        ),
        label = "scale"
    )

    Card(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(20.dp), // Material 3 Expressive: More rounded (was 16dp)
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest // Expressive: Higher contrast
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 4.dp else 8.dp // Material 3 Expressive: Higher elevation (was 2/4dp)
        ),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF)) // Material 3 Expressive: Thicker border (was 1dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp), // Material 3 Expressive: More padding (was 16dp)
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Material 3 Expressive: Larger Gradient Icon Container (72dp)
            Box(
                modifier = Modifier
                    .size(72.dp) // Material 3 Expressive: Larger (was 64dp)
                    .shadow(8.dp, RoundedCornerShape(20.dp)) // Material 3 Expressive: More shadow, more rounded (was 16dp)
                    .background(gradient, RoundedCornerShape(20.dp)), // Material 3 Expressive: More rounded (was 16dp)
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Select $title workout",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp) // Material 3 Expressive: Larger icon (was 32dp)
                )
            }

            // Content Column - Only title and description
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

/**
 * Featured card for Phase 2 muscle-group-first training.
 * Full-width, prominent placement at top of home screen grid.
 */
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
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Train by Muscle Group",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Phoenix picks exercises & weights for you",
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

/**
 * Compact Active Program Card for HomeScreen.
 * Shows today's workout exercises with expandable/collapsible format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeActiveProgramCard(
    program: com.example.vitruvianredux.data.local.WeeklyProgramWithDays,
    routines: List<com.example.vitruvianredux.domain.model.Routine>,
    weightUnit: WeightUnit,
    formatWeight: (Float, WeightUnit) -> String,
    kgToDisplay: (Float, WeightUnit) -> Float,
    onStartRoutine: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val today = LocalDate.now().dayOfWeek
    // Use Java DayOfWeek.value directly (MONDAY=1, TUESDAY=2, ..., SUNDAY=7)
    // This matches what ProgramBuilder saves: day.value
    val todayDayValue = today.value

    // Find today's routine ID from program days
    val todayRoutineId = program.days.find { it.dayOfWeek == todayDayValue }?.routineId
    val todayRoutine = todayRoutineId?.let { routineId ->
        routines.find { it.id == routineId }
    }
    val hasWorkoutToday = todayRoutineId != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            if (hasWorkoutToday) {
                // Show exercises if routine is loaded
                todayRoutine?.let { routine ->
                    // Expand/Collapse button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExpanded) "Today's Routine" else "Today's Routine",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(Spacing.small))
                    
                    if (isExpanded) {
                        // Expanded view: Show all exercises with detailed format
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                        ) {
                            routine.exercises.forEach { exercise ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                                ) {
                                    // Exercise name
                                    Text(
                                        text = exercise.exercise.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    // Mode
                                    Text(
                                        text = "(${exercise.workoutType.displayName} Mode)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    // Sets with weights
                                    val weightSuffix = if (weightUnit == WeightUnit.LB) "lbs" else "kg"
                                    exercise.setReps.forEachIndexed { index, reps ->
                                        val weight = if (exercise.setWeightsPerCableKg.isNotEmpty() && index < exercise.setWeightsPerCableKg.size) {
                                            kgToDisplay(exercise.setWeightsPerCableKg[index], weightUnit)
                                        } else {
                                            kgToDisplay(exercise.weightPerCableKg, weightUnit)
                                        }
                                        Text(
                                            text = "${reps ?: "AMRAP"} x ${"%.1f".format(weight)} $weightSuffix",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Collapsed view: Show only exercise names
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                        ) {
                            routine.exercises.forEach { exercise ->
                                Text(
                                    text = exercise.exercise.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Start Routine button
                    Button(
                        onClick = { onStartRoutine(todayRoutineId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 4.dp,
                            pressedElevation = 2.dp
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start routine")
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            "Start Routine",
                            style = MaterialTheme.typography.titleMedium, // Reduced from titleLarge
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Rest day
                Text(
                    text = "Rest day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionParticipantsCard(
    participants: List<UserProfileEntity>,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(2.dp, Color(0xFFF5F3FF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.medium, vertical = 12.dp),
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
                    modifier = Modifier.size(20.dp)
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
                                .size(28.dp)
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
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

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