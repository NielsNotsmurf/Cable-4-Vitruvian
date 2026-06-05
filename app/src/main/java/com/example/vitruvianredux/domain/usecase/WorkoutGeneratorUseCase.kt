package com.example.vitruvianredux.domain.usecase

import com.example.vitruvianredux.data.baseline.AnchorExerciseConfig
import com.example.vitruvianredux.data.baseline.BaselineRepository
import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.repository.ExerciseRepository
import com.example.vitruvianredux.domain.model.ExerciseBucket
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Training style that controls exercise selection and set/rep scheme.
 */
enum class TrainingStyle(
    val displayName: String,
    val sets: Int,
    val repsMin: Int,
    val repsMax: Int,
    val restSeconds: Int,
    val exerciseCount: Int
) {
    STRENGTH("Strength", sets = 4, repsMin = 4, repsMax = 6, restSeconds = 180, exerciseCount = 3),
    HYPERTROPHY("Hypertrophy", sets = 3, repsMin = 8, repsMax = 12, restSeconds = 90, exerciseCount = 4),
    PUMP("Pump", sets = 3, repsMin = 15, repsMax = 20, restSeconds = 45, exerciseCount = 5),
    TUT("TUT", sets = 3, repsMin = 8, repsMax = 10, restSeconds = 90, exerciseCount = 3),
    ECHO("Echo", sets = 3, repsMin = 8, repsMax = 10, restSeconds = 90, exerciseCount = 3),
    RECOVERY("Recovery", sets = 2, repsMin = 15, repsMax = 20, restSeconds = 60, exerciseCount = 4),
    CUSTOM("Custom", sets = 3, repsMin = 10, repsMax = 10, restSeconds = 60, exerciseCount = 4)
}

/**
 * A generated workout item — one exercise with pre-filled parameters.
 */
data class GeneratedExercise(
    val exercise: ExerciseEntity,
    val bucket: ExerciseBucket,
    val suggestedWeightKg: Float,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val isFromHistory: Boolean,
    val isFromBaseline: Boolean
)

/**
 * A generated workout session — a list of exercises for one muscle group + training style.
 */
data class GeneratedWorkout(
    val muscleGroup: ExerciseBucket,
    val trainingStyle: TrainingStyle,
    val exercises: List<GeneratedExercise>,
    val profileId: String,
    val generatedAt: Long = System.currentTimeMillis()
)

/**
 * Generates a workout for a given muscle group, training style, and profile.
 *
 * Selection criteria (in order):
 *   1. Exercises with actual recent history (last 30 days) — sorted by recency
 *   2. Popular exercises in the bucket (sorted by popularity desc)
 *   3. Fill to target count using anchor exercise as guaranteed slot
 *
 * Weights:
 *   1. Most recent actual weight from workout history
 *   2. Propagated baseline estimate from BaselineRepository
 *   3. Anchor conservative default
 */
@Singleton
class WorkoutGeneratorUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val baselineRepository: BaselineRepository
) {

    suspend fun generate(
        bucket: ExerciseBucket,
        style: TrainingStyle,
        profileId: String,
        excludeBenchExercises: Boolean = false
    ): GeneratedWorkout {
        val targetCount = style.exerciseCount

        // Get all exercises in this bucket
        val allExercises = exerciseRepository.getAllExercises().firstOrNull() ?: emptyList()
        val bucketExercises = allExercises.filter { exercise ->
            val muscles = exercise.muscles.split(",").map { it.trim() }
            val movement = exercise.movement
            val muscleGroups = exercise.muscleGroups.split(",").map { it.trim() }
            val mapped = ExerciseBucket.from(muscles, movement, muscleGroups)
            mapped == bucket && !isExcluded(exercise)
        }

        if (bucketExercises.isEmpty()) {
            Timber.w("No exercises found for bucket=$bucket")
            return GeneratedWorkout(bucket, style, emptyList(), profileId)
        }

        // Filter bench exercises if user has no bench
        val eligible = if (excludeBenchExercises) {
            bucketExercises.filter { "BENCH" !in it.equipment.uppercase() }
                .ifEmpty { bucketExercises }
        } else {
            bucketExercises
        }

        // Sort: recently performed first, then by popularity
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        val (recent, notRecent) = eligible.partition {
            it.lastPerformed != null && it.lastPerformed > thirtyDaysAgo
        }
        val sortedRecent = recent.sortedByDescending { it.lastPerformed }
        val sortedRest = notRecent.sortedByDescending { it.popularity }

        // Always include the anchor exercise as the first slot
        val anchorId = AnchorExerciseConfig.anchorForBucket(bucket)?.exerciseId
        val anchorExercise = eligible.firstOrNull { it.id == anchorId }

        val selected = mutableListOf<ExerciseEntity>()
        if (anchorExercise != null && anchorExercise !in sortedRecent.take(targetCount)) {
            selected.add(anchorExercise)
        }

        // Fill from recent history first, then popular
        val pool = (sortedRecent + sortedRest)
            .filter { it !in selected }
            .distinctBy { it.id }
        selected.addAll(pool.take(targetCount - selected.size))

        // Build GeneratedExercise objects with weight estimates
        val generated = selected.take(targetCount).map { exercise ->
            val muscles = exercise.muscles.split(",").map { it.trim() }
            val exerciseBucket = ExerciseBucket.from(muscles, exercise.movement, exercise.muscleGroups.split(",").map { it.trim() })
                ?: bucket

            val suggestedWeight = baselineRepository.propagateDefault(
                profileId = profileId,
                exerciseId = exercise.id,
                bucket = exerciseBucket
            )

            val isFromHistory = exercise.lastPerformed != null
            val isFromBaseline = !isFromHistory && suggestedWeight != AnchorExerciseConfig.FIXED_SAFE_DEFAULT_KG

            GeneratedExercise(
                exercise = exercise,
                bucket = exerciseBucket,
                suggestedWeightKg = suggestedWeight,
                sets = style.sets,
                reps = style.repsMin,
                restSeconds = style.restSeconds,
                isFromHistory = isFromHistory,
                isFromBaseline = isFromBaseline
            )
        }

        return GeneratedWorkout(
            muscleGroup = bucket,
            trainingStyle = style,
            exercises = generated,
            profileId = profileId
        )
    }

    /**
     * Exercises excluded from automatic workout generation:
     * - No equipment (bodyweight only)
     * - Cardio / warmup patterns
     * - Mobility / stretching
     */
    private fun isExcluded(exercise: ExerciseEntity): Boolean {
        val name = exercise.name.lowercase()
        val equipment = exercise.equipment.lowercase()
        val muscles = exercise.muscles.lowercase()

        // No cable resistance possible
        if (equipment.isBlank() || equipment == "none") return true

        // Explicit bodyweight exclusion patterns
        val bodyweightKeywords = listOf(
            "push up", "push-up", "burpee", "plank", "crunch", "bird dog",
            "superman", "bear crawl", "fire hydrant", "clam shell", "dead bug",
            "inchworm", "mountain climber", "pigeon", "down dog", "stretch"
        )
        if (bodyweightKeywords.any { name.contains(it) }) return true

        // Cardio patterns
        if (name.contains("high knee") || name.contains("butt kick") ||
            name.contains("jumping") || name.contains("jump over")) return true

        // Rotation / speed patterns without load
        if (name.contains("punch") || name.contains("rotation") && muscles.contains("core") &&
            !muscles.contains("shoulders")) return true

        return false
    }
}
