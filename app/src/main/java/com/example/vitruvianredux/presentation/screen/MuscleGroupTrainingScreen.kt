package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.domain.model.ExerciseBucket
import com.example.vitruvianredux.domain.usecase.GeneratedExercise
import com.example.vitruvianredux.domain.usecase.GeneratedWorkout
import com.example.vitruvianredux.domain.usecase.TrainingStyle
import com.example.vitruvianredux.presentation.navigation.NavigationRoutes
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import kotlin.math.roundToInt

private data class MuscleGroupOption(
    val bucket: ExerciseBucket,
    val displayName: String,
    val icon: ImageVector,
    val gradient: List<Color>
)

private val MUSCLE_GROUP_OPTIONS = listOf(
    MuscleGroupOption(ExerciseBucket.CHEST, "Chest", Icons.Default.FitnessCenter,
        listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))),
    MuscleGroupOption(ExerciseBucket.BACK, "Back", Icons.Default.AccessibilityNew,
        listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))),
    MuscleGroupOption(ExerciseBucket.SHOULDERS, "Shoulders", Icons.Default.Star,
        listOf(Color(0xFF8B5CF6), Color(0xFFA855F7))),
    MuscleGroupOption(ExerciseBucket.BICEPS, "Biceps", Icons.Default.FitnessCenter,
        listOf(Color(0xFF10B981), Color(0xFF059669))),
    MuscleGroupOption(ExerciseBucket.TRICEPS, "Triceps", Icons.Default.FitnessCenter,
        listOf(Color(0xFF06B6D4), Color(0xFF0891B2))),
    MuscleGroupOption(ExerciseBucket.QUADS, "Quads", Icons.Default.DirectionsRun,
        listOf(Color(0xFFF59E0B), Color(0xFFD97706))),
    MuscleGroupOption(ExerciseBucket.HAMSTRINGS_GLUTES, "Hamstrings & Glutes", Icons.Default.DirectionsRun,
        listOf(Color(0xFFEF4444), Color(0xFFDC2626))),
    MuscleGroupOption(ExerciseBucket.POSTERIOR_CHAIN, "Posterior Chain", Icons.Default.TrendingUp,
        listOf(Color(0xFFEC4899), Color(0xFFDB2777))),
    MuscleGroupOption(ExerciseBucket.CORE, "Core", Icons.Default.RadioButtonChecked,
        listOf(Color(0xFF84CC16), Color(0xFF65A30D)))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleGroupTrainingScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val generatedWorkout by viewModel.generatedWorkout.collectAsState()
    val isGenerating by viewModel.isGeneratingWorkout.collectAsState()
    val baselineResults by viewModel.baselineResults.collectAsState()

    var selectedBucket by remember { mutableStateOf<ExerciseBucket?>(null) }
    var selectedStyle by remember { mutableStateOf<TrainingStyle?>(null) }

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
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // Baseline prompt if not completed
            if (baselineResults == null || !baselineResults!!.isComplete) {
                item {
                    BaselinePromptBanner(
                        hasPartialBaseline = baselineResults?.testedBucketCount?.let { it > 0 } ?: false,
                        testedCount = baselineResults?.testedBucketCount ?: 0,
                        onStartAssessment = {
                            navController.navigate(NavigationRoutes.BaselineAssessment.route)
                        }
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                }
            }

            // Section: Choose Muscle Group
            item {
                Text(
                    text = "1. Choose a muscle group",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Muscle group grid (2 columns)
            item {
                MuscleGroupGrid(
                    options = MUSCLE_GROUP_OPTIONS,
                    selected = selectedBucket,
                    onSelect = { bucket ->
                        selectedBucket = bucket
                        // Reset generated workout when muscle group changes
                        if (bucket != selectedBucket) {
                            viewModel.clearGeneratedWorkout()
                        }
                    }
                )
            }

            // Section: Choose Training Style
            item {
                AnimatedVisibility(
                    visible = selectedBucket != null,
                    enter = fadeIn(tween(250)) + expandVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Spacing.medium))
                        Text(
                            text = "2. Choose a training style",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(Spacing.small))
                        TrainingStyleSelector(
                            selected = selectedStyle,
                            onSelect = { style ->
                                selectedStyle = style
                                val bucket = selectedBucket ?: return@TrainingStyleSelector
                                viewModel.generateWorkout(
                                    bucket = bucket,
                                    style = style,
                                    profileId = activeProfileId
                                )
                            }
                        )
                    }
                }
            }

            // Generated workout preview
            if (isGenerating) {
                item {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (generatedWorkout != null && generatedWorkout!!.exercises.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(300)) + expandVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(Spacing.medium))
                            Text(
                                text = "3. Review your workout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                        }
                    }
                }

                items(generatedWorkout!!.exercises) { exercise ->
                    GeneratedExerciseCard(exercise = exercise)
                }

                item {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Button(
                        onClick = {
                            navController.navigate(NavigationRoutes.SingleExercise.route)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Start First Exercise",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            val bucket = selectedBucket ?: return@TextButton
                            val style = selectedStyle ?: return@TextButton
                            viewModel.generateWorkout(bucket, style, activeProfileId)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(Spacing.extraLarge)) }
        }
    }
}

@Composable
private fun BaselinePromptBanner(
    hasPartialBaseline: Boolean,
    testedCount: Int,
    onStartAssessment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
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
                    text = if (hasPartialBaseline)
                        "Assessment ${testedCount}/9 complete"
                    else
                        "No strength baseline yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = "Take a 15-min assessment to get personalized weights",
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

@Composable
private fun MuscleGroupGrid(
    options: List<MuscleGroupOption>,
    selected: ExerciseBucket?,
    onSelect: (ExerciseBucket) -> Unit
) {
    val chunked = options.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        chunked.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { option ->
                    MuscleGroupCard(
                        option = option,
                        isSelected = selected == option.bucket,
                        onClick = { onSelect(option.bucket) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Pad last row if odd number
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MuscleGroupCard(
    option: MuscleGroupOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        if (isSelected) option.gradient
                        else listOf(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    option.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = option.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TrainingStyleSelector(
    selected: TrainingStyle?,
    onSelect: (TrainingStyle) -> Unit
) {
    val styles = TrainingStyle.entries.filter { it != TrainingStyle.CUSTOM }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        styles.chunked(3).forEach { row ->
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                row.forEach { style ->
                    FilterChip(
                        selected = selected == style,
                        onClick = { onSelect(style) },
                        label = { Text(style.displayName) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = if (selected == style) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratedExerciseCard(exercise: GeneratedExercise) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.exercise.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${exercise.sets} sets × ${exercise.reps} reps • ${exercise.restSeconds}s rest",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (exercise.isFromHistory || exercise.isFromBaseline) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (exercise.isFromHistory) Icons.Default.History else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = if (exercise.isFromHistory) "From your history" else "Estimated from baseline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(Spacing.small))

            // Weight badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${exercise.suggestedWeightKg.let {
                            if (it == it.roundToInt().toFloat()) it.roundToInt().toString()
                            else "%.1f".format(it)
                        }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "kg/cable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
