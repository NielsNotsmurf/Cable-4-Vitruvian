package com.example.vitruvianredux.presentation.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.vitruvianredux.data.baseline.AnchorExerciseConfig
import com.example.vitruvianredux.domain.model.ExerciseBucket
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import com.example.vitruvianredux.ui.theme.Spacing
import kotlin.math.roundToInt


private data class AssessmentStep(
    val bucket: ExerciseBucket,
    val anchorName: String,
    val equipment: String,
    val instructions: String
)

private val ASSESSMENT_STEPS = listOf(
    AssessmentStep(
        bucket = ExerciseBucket.CHEST,
        anchorName = "Bench Press",
        equipment = "Handles",
        instructions = "Lie on bench, cables from floor. Press both handles up. Find weight where 10 reps feels like RPE 7–8 (hard but could do 2–3 more)."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.BACK,
        anchorName = "Bent Over Row",
        equipment = "Handles",
        instructions = "Stand, hinge at hips ~45°. Pull both handles to lower ribs. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.POSTERIOR_CHAIN,
        anchorName = "Romanian Deadlift",
        equipment = "Handles",
        instructions = "Stand tall, hold handles. Hinge at hips with soft knees, lower handles along legs. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.QUADS,
        anchorName = "Squat",
        equipment = "Belt or Handles",
        instructions = "Stand on platform, feet shoulder-width. Squat to parallel or below. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.HAMSTRINGS_GLUTES,
        anchorName = "Hip Thrust",
        equipment = "Belt + Bench",
        instructions = "Upper back on bench, belt around hips, cables attached. Drive hips up until body is straight. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.SHOULDERS,
        anchorName = "Shoulder Press",
        equipment = "Handles",
        instructions = "Stand tall. Press both handles from shoulder height to overhead. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.SHOULDERS,
        anchorName = "Lateral Raise",
        equipment = "Handles",
        instructions = "Stand with handles at sides. Raise arms out to shoulder height, slight bend in elbows. Find 12-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.BICEPS,
        anchorName = "Bicep Curl",
        equipment = "Handles",
        instructions = "Stand, arms at sides. Curl both handles to shoulder height, elbows fixed. Find 10-rep RPE 7–8 weight."
    ),
    AssessmentStep(
        bucket = ExerciseBucket.TRICEPS,
        anchorName = "Overhead Tricep Extension",
        equipment = "Handles",
        instructions = "Stand, raise handles overhead. Lower behind head by bending elbows, then extend. Find 10-rep RPE 7–8 weight."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaselineAssessmentScreen(
    navController: NavController,
    viewModel: MainViewModel
) {
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val baselineResults by viewModel.baselineResults.collectAsState()

    var currentStepIndex by remember { mutableIntStateOf(0) }
    var weightInput by remember { mutableStateOf("10") }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var skippedSteps by remember { mutableStateOf(setOf<Int>()) }

    val currentStep = ASSESSMENT_STEPS.getOrNull(currentStepIndex)
    val isLastStep = currentStepIndex == ASSESSMENT_STEPS.lastIndex

    LaunchedEffect(currentStepIndex) {
        val step = ASSESSMENT_STEPS.getOrNull(currentStepIndex)
        if (step != null) {
            val anchor = AnchorExerciseConfig.anchorForBucket(step.bucket)
            weightInput = (anchor?.conservativeDefaultKg?.roundToInt() ?: 10).toString()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Strength Assessment") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Progress header
            AssessmentProgressHeader(
                currentStep = currentStepIndex + 1,
                totalSteps = ASSESSMENT_STEPS.size,
                skippedSteps = skippedSteps
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Animated step content
            AnimatedContent(
                targetState = currentStepIndex,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith
                        fadeOut(animationSpec = tween(150))
                },
                label = "assessment_step"
            ) { stepIndex ->
                val step = ASSESSMENT_STEPS.getOrNull(stepIndex) ?: return@AnimatedContent

                AssessmentStepCard(
                    step = step,
                    stepNumber = stepIndex + 1,
                    weightInput = weightInput,
                    onWeightChange = { weightInput = it }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Skip button
                OutlinedButton(
                    onClick = {
                        skippedSteps = skippedSteps + currentStepIndex
                        if (isLastStep) {
                            showCompletionDialog = true
                        } else {
                            currentStepIndex++
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }

                // Save & continue button
                Button(
                    onClick = {
                        val kg = weightInput.toFloatOrNull()
                        if (kg != null && kg > 0f) {
                            currentStep?.let { step ->
                                viewModel.saveBaselineResult(
                                    profileId = activeProfileId,
                                    bucket = step.bucket,
                                    kg = kg
                                )
                            }
                        }
                        if (isLastStep) {
                            viewModel.completeBaseline(activeProfileId)
                            showCompletionDialog = true
                        } else {
                            currentStepIndex++
                        }
                    },
                    modifier = Modifier.weight(2f),
                    enabled = weightInput.toFloatOrNull()?.let { it > 0f } ?: false
                ) {
                    Text(if (isLastStep) "Finish" else "Save & Continue")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (isLastStep) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tested count so far
            val testedCount = (baselineResults?.testedBucketCount ?: 0)
            if (testedCount > 0) {
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(
                    text = "$testedCount of 9 buckets tested",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }

    if (showCompletionDialog) {
        AssessmentCompleteDialog(
            skippedCount = skippedSteps.size,
            onDone = { navController.popBackStack() }
        )
    }
}

@Composable
private fun AssessmentProgressHeader(
    currentStep: Int,
    totalSteps: Int,
    skippedSteps: Set<Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            )
            .padding(Spacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Step $currentStep of $totalSteps",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "~${(totalSteps - currentStep + 1) * 2} min left",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Step dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(totalSteps) { index ->
                val isCompleted = index < currentStep - 1
                val isCurrent = index == currentStep - 1
                val isSkipped = index in skippedSteps

                Box(
                    modifier = Modifier
                        .size(if (isCurrent) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSkipped -> MaterialTheme.colorScheme.outline
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isCurrent -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                            }
                        )
                )
            }
        }
    }
}

@Composable
private fun AssessmentStepCard(
    step: AssessmentStep,
    stepNumber: Int,
    weightInput: String,
    onWeightChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.large)
        ) {
            // Bucket label
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = step.bucket.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(Spacing.small))

            Text(
                text = step.anchorName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = step.equipment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.medium))

            // Instructions
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = step.instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            // RPE guide
            RpeGuideChips()

            Spacer(modifier = Modifier.height(Spacing.large))

            // Weight input
            Text(
                text = "Weight at RPE 7–8 (kg per cable)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(Spacing.extraSmall))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                IconButton(
                    onClick = {
                        val current = weightInput.toFloatOrNull() ?: 10f
                        val new = (current - 2.5f).coerceAtLeast(1f)
                        onWeightChange(if (new == new.toInt().toFloat()) new.toInt().toString() else "%.1f".format(new))
                    }
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }

                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { value ->
                        if (value.isEmpty() || value.matches(Regex("^\\d{0,3}(\\.\\d{0,1})?\$"))) {
                            onWeightChange(value)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    suffix = { Text("kg") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )

                IconButton(
                    onClick = {
                        val current = weightInput.toFloatOrNull() ?: 10f
                        val new = current + 2.5f
                        onWeightChange(if (new == new.toInt().toFloat()) new.toInt().toString() else "%.1f".format(new))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        }
    }
}

@Composable
private fun RpeGuideChips() {
    Text(
        text = "How did it feel?",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "Too easy" to MaterialTheme.colorScheme.errorContainer,
            "About right (RPE 7–8)" to MaterialTheme.colorScheme.primaryContainer,
            "Too hard" to MaterialTheme.colorScheme.tertiaryContainer
        ).forEach { (label, color) ->
            Surface(
                color = color,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AssessmentCompleteDialog(
    skippedCount: Int,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        icon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = { Text("Assessment Complete") },
        text = {
            Column {
                Text("Your baseline strength data has been saved.")
                if (skippedCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$skippedCount exercise${if (skippedCount > 1) "s" else ""} skipped — conservative defaults will be used for those.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "vitruvian- will now suggest weights for all exercises based on your results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDone) { Text("Start Training") }
        }
    )
}
