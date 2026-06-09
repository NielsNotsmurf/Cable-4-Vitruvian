package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.*
import com.example.vitruvianredux.domain.model.ExerciseBucket
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.util.UUID

// ── Body regions shown in the picker ──────────────────────────────────────────

private data class BodyRegion(
    val name: String,
    val buckets: List<ExerciseBucket>,
    val icon: ImageVector,
    val gradient: List<Color>
)

private val BODY_REGIONS = listOf(
    BodyRegion(
        name = "Chest",
        buckets = listOf(ExerciseBucket.CHEST),
        icon = Icons.Default.FitnessCenter,
        gradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
    ),
    BodyRegion(
        name = "Back",
        buckets = listOf(ExerciseBucket.BACK, ExerciseBucket.POSTERIOR_CHAIN),
        icon = Icons.Default.AccessibilityNew,
        gradient = listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))
    ),
    BodyRegion(
        name = "Shoulders",
        buckets = listOf(ExerciseBucket.SHOULDERS),
        icon = Icons.Default.Star,
        gradient = listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))
    ),
    BodyRegion(
        name = "Arms",
        buckets = listOf(ExerciseBucket.BICEPS, ExerciseBucket.TRICEPS),
        icon = Icons.Default.FitnessCenter,
        gradient = listOf(Color(0xFF10B981), Color(0xFF059669))
    ),
    BodyRegion(
        name = "Legs",
        buckets = listOf(ExerciseBucket.QUADS, ExerciseBucket.HAMSTRINGS_GLUTES, ExerciseBucket.CALVES),
        icon = Icons.Default.DirectionsRun,
        gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
    ),
    BodyRegion(
        name = "Core",
        buckets = listOf(ExerciseBucket.CORE),
        icon = Icons.Default.RadioButtonChecked,
        gradient = listOf(Color(0xFF84CC16), Color(0xFF65A30D))
    )
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupTrainingScreen(
    navController: NavController,
    viewModel: MainViewModel,
    exerciseRepository: ExerciseRepository
) {
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val baselineResults by viewModel.baselineResults.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()
    val enableVideoPlayback by viewModel.enableVideoPlayback.collectAsState()

    var selectedRegion by remember { mutableStateOf<BodyRegion?>(null) }
    var selectedExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var exerciseToConfig by remember { mutableStateOf<RoutineExercise?>(null) }
    var isLoadingConfig by remember { mutableStateOf(false) }

    val allExercises by exerciseRepository.getAllExercises().collectAsState(initial = emptyList())

    val coroutineScope = rememberCoroutineScope()

    // Filter exercises to the selected region's buckets
    val filteredExercises = remember(allExercises, selectedRegion) {
        val region = selectedRegion ?: return@remember emptyList()
        allExercises
            .filter { entity ->
                if (entity.archived) return@filter false
                val muscles = entity.muscles.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val muscleGroups = entity.muscleGroups.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val bucket = ExerciseBucket.from(muscles, entity.movement, muscleGroups)
                bucket != null && bucket in region.buckets
            }
            .sortedByDescending { it.popularity }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Train by Muscle Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Baseline banner
            if (baselineResults == null || !baselineResults!!.isComplete) {
                item {
                    BaselinePromptBanner(
                        hasPartialBaseline = (baselineResults?.testedBucketCount ?: 0) > 0,
                        testedCount = baselineResults?.testedBucketCount ?: 0,
                        onStartAssessment = {
                            navController.navigate(NavigationRoutes.BaselineAssessment.route)
                        }
                    )
                }
            }

            // Section header
            item {
                Text(
                    text = "Choose a muscle group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Body region grid
            item {
                RegionGrid(
                    regions = BODY_REGIONS,
                    selected = selectedRegion,
                    onSelect = { region ->
                        selectedRegion = region
                        selectedExercise = null
                        exerciseToConfig = null
                    }
                )
            }

            // Exercise browser
            if (selectedRegion != null) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300)) + expandVertically()
                    ) {
                        Text(
                            text = "${selectedRegion!!.name} exercises — tap to configure",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (filteredExercises.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    // 2-column grid of exercise cards using chunked rows
                    val chunked = filteredExercises.chunked(2)
                    items(chunked) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { entity ->
                                ExerciseVisualCard(
                                    entity = entity,
                                    exerciseRepository = exerciseRepository,
                                    isSelected = selectedExercise?.id == entity.id,
                                    onClick = {
                                        if (isLoadingConfig) return@ExerciseVisualCard
                                        selectedExercise = entity
                                        isLoadingConfig = true
                                        coroutineScope.launch {
                                            try {
                                                val exercise = Exercise(
                                                    name = entity.name,
                                                    muscleGroup = entity.muscleGroups
                                                        .split(",").firstOrNull()?.trim() ?: "Full Body",
                                                    equipment = entity.equipment
                                                        .split(",").firstOrNull()?.trim() ?: "",
                                                    defaultCableConfig = CableConfiguration.DOUBLE,
                                                    id = entity.id
                                                )
                                                val cableConfig = exercise.resolveDefaultCableConfig()
                                                val muscles = entity.muscles.split(",")
                                                    .map { it.trim() }.filter { it.isNotBlank() }
                                                val muscleGroups = entity.muscleGroups.split(",")
                                                    .map { it.trim() }.filter { it.isNotBlank() }
                                                val bucket = ExerciseBucket.from(
                                                    muscles, entity.movement, muscleGroups
                                                )
                                                val suggestedKg = viewModel.suggestExerciseWeight(
                                                    entity.id, cableConfig, bucket
                                                )
                                                exerciseToConfig = RoutineExercise(
                                                    id = UUID.randomUUID().toString(),
                                                    exercise = exercise,
                                                    cableConfig = cableConfig,
                                                    orderIndex = 0,
                                                    setReps = listOf(10, 10, 10),
                                                    weightPerCableKg = suggestedKg,
                                                    setWeightsPerCableKg = emptyList(),
                                                    progressionKg = 0f,
                                                    setRestSeconds = listOf(90, 90, 90),
                                                    workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                                                    eccentricLoad = EccentricLoad.LOAD_100,
                                                    echoLevel = EchoLevel.HARDER
                                                )
                                            } finally {
                                                isLoadingConfig = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Pad odd last row
                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(Spacing.extraLarge)) }
        }
    }

    // Loading overlay
    if (isLoadingConfig) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }

    // Exercise config bottom sheet
    if (!isLoadingConfig) {
        exerciseToConfig?.let { routineExercise ->
            ExerciseEditBottomSheet(
                exercise = routineExercise,
                weightUnit = weightUnit,
                enableVideoPlayback = enableVideoPlayback,
                kgToDisplay = viewModel::kgToDisplay,
                displayToKg = viewModel::displayToKg,
                exerciseRepository = exerciseRepository,
                personalRecordRepository = viewModel.personalRecordRepository,
                formatWeight = viewModel::formatWeight,
                buttonText = "Start Exercise",
                onSave = { configuredExercise ->
                    val tempRoutine = Routine(
                        id = "${MainViewModel.TEMP_SINGLE_EXERCISE_PREFIX}${UUID.randomUUID()}",
                        name = "Single Exercise: ${configuredExercise.exercise.name}",
                        description = "Temporary routine for muscle group training",
                        exercises = listOf(configuredExercise)
                    )
                    viewModel.loadRoutine(tempRoutine)
                    viewModel.ensureConnection(
                        onConnected = {
                            viewModel.startWorkout()
                            navController.navigate(NavigationRoutes.ActiveWorkout.route) {
                                popUpTo(NavigationRoutes.Home.route)
                            }
                        },
                        onFailed = {}
                    )
                    exerciseToConfig = null
                },
                onDismiss = {
                    exerciseToConfig = null
                    selectedExercise = null
                }
            )
        }
    }
}

// ── Region picker grid ────────────────────────────────────────────────────────

@Composable
private fun RegionGrid(
    regions: List<BodyRegion>,
    selected: BodyRegion?,
    onSelect: (BodyRegion) -> Unit
) {
    val chunked = regions.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        chunked.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { region ->
                    RegionChip(
                        region = region,
                        isSelected = selected?.name == region.name,
                        onClick = { onSelect(region) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RegionChip(
    region: BodyRegion,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isSelected)
                        Brush.horizontalGradient(region.gradient)
                    else
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    region.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = region.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ── Visual exercise card ──────────────────────────────────────────────────────

@Composable
private fun ExerciseVisualCard(
    entity: ExerciseEntity,
    exerciseRepository: ExerciseRepository,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnailUrl by remember(entity.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(entity.id) {
        val videos = exerciseRepository.getVideos(entity.id)
        thumbnailUrl = (videos.firstOrNull { it.angle == "FRONT" } ?: videos.firstOrNull())?.thumbnailUrl
    }

    val equipmentDisplay = entity.equipment
        .split(",")
        .map { it.trim().lowercase().replaceFirstChar { c -> c.uppercase() } }
        .filter { it.isNotBlank() && it !in listOf("Black_cables", "Red_cables", "Grey_cables", "Cables", "Cable") }
        .take(2)
        .joinToString(", ")

    Card(
        modifier = modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Column {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailUrl != null) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = entity.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Info
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entity.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (equipmentDisplay.isNotBlank()) {
                    Text(
                        text = equipmentDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Baseline banner ───────────────────────────────────────────────────────────

@Composable
private fun BaselinePromptBanner(
    hasPartialBaseline: Boolean,
    testedCount: Int,
    onStartAssessment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasPartialBaseline) "Assessment ${testedCount}/9 complete"
                    else "No strength baseline yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Take a 15-min assessment for personalised weights",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            Spacer(modifier = Modifier.width(Spacing.small))
            TextButton(
                onClick = onStartAssessment,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text(if (hasPartialBaseline) "Continue" else "Start")
            }
        }
    }
}
