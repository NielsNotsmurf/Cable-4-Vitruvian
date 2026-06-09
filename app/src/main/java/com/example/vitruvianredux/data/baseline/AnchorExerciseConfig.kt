package com.example.vitruvianredux.data.baseline

import com.example.vitruvianredux.domain.model.ExerciseBucket

/**
 * Canonical anchor exercise configuration for Phase 2 baseline system.
 *
 * 10 anchors cover ~88% of cable-loaded exercises (415 baseline-eligible after exclusions).
 * IDs are pinned to specific variants — always HANDLES unless noted.
 *
 * Anchor set: Option A (10 anchors, minimum practical set).
 * Upgrade to 12 anchors by enabling CHEST_FLY and REAR_DELT entries.
 *
 * Equipment note:
 *   3 anchors require a bench (Bench Press, Bulgarian Split Squat, Hip Thrust).
 *   No-bench fallbacks defined in NO_BENCH_SUBSTITUTES.
 */
object AnchorExerciseConfig {

    data class Anchor(
        val bucket: ExerciseBucket,
        val exerciseId: String,
        val exerciseName: String,
        val equipment: String,
        val conservativeDefaultKg: Float
    )

    val ANCHORS: List<Anchor> = listOf(
        Anchor(
            bucket = ExerciseBucket.CHEST,
            exerciseId = "ZZ92N8QsBdp6HCh3",
            exerciseName = "Bench Press",
            equipment = "HANDLES",
            conservativeDefaultKg = 15f
        ),
        Anchor(
            bucket = ExerciseBucket.BACK,
            exerciseId = "31ea9d9e-669c-4752-9f44-80c3fa864021",
            exerciseName = "Bent Over Row",
            equipment = "HANDLES",
            conservativeDefaultKg = 15f
        ),
        Anchor(
            bucket = ExerciseBucket.POSTERIOR_CHAIN,
            exerciseId = "49c4594c-f997-4d70-8008-b293e247e2e4",
            exerciseName = "Romanian Deadlift",
            equipment = "HANDLES",
            conservativeDefaultKg = 20f
        ),
        Anchor(
            bucket = ExerciseBucket.QUADS,
            exerciseId = "lpnNPw86Vud67vWQ",
            exerciseName = "Squat",
            equipment = "BELT",
            conservativeDefaultKg = 25f
        ),
        Anchor(
            bucket = ExerciseBucket.HAMSTRINGS_GLUTES,
            exerciseId = "a255fcc3-af4d-44b1-8f88-8de014b39ed3",
            exerciseName = "Bulgarian Split Squats",
            equipment = "BENCH+HANDLES",
            conservativeDefaultKg = 12f
        ),
        Anchor(
            bucket = ExerciseBucket.SHOULDERS,
            exerciseId = "0040d53f-85c7-4564-b14e-9b38c979b461",
            exerciseName = "Shoulder Press",
            equipment = "HANDLES",
            conservativeDefaultKg = 12f
        ),
        Anchor(
            bucket = ExerciseBucket.SHOULDERS,
            exerciseId = "rIjnJFQUK3mDbwBW",
            exerciseName = "Lateral Raise",
            equipment = "HANDLES",
            conservativeDefaultKg = 5f
        ),
        Anchor(
            bucket = ExerciseBucket.BICEPS,
            exerciseId = "fc5ca114-3cb6-462a-ab54-7ef1233b5fc2",
            exerciseName = "Bicep Curl",
            equipment = "HANDLES",
            conservativeDefaultKg = 10f
        ),
        Anchor(
            bucket = ExerciseBucket.TRICEPS,
            exerciseId = "bf1437e9-f8ca-4bfe-b766-af5fee1834a5",
            exerciseName = "Overhead Tricep Extension",
            equipment = "HANDLES",
            conservativeDefaultKg = 8f
        ),
        Anchor(
            bucket = ExerciseBucket.HAMSTRINGS_GLUTES,
            exerciseId = "T6Vf4-OIurKVfECK",
            exerciseName = "Hip Thrust",
            equipment = "BELT+BENCH",
            conservativeDefaultKg = 20f
        )
    )

    /**
     * Substitutes for users without a bench.
     * Same coefficients apply; only the anchor exercise changes.
     */
    val NO_BENCH_SUBSTITUTES: Map<String, Anchor> = mapOf(
        // Bench Press → Floor Press
        "ZZ92N8QsBdp6HCh3" to Anchor(
            bucket = ExerciseBucket.CHEST,
            exerciseId = "NGccEsSujysaQIed",
            exerciseName = "Floor Press",
            equipment = "HANDLES",
            conservativeDefaultKg = 13f
        ),
        // Bulgarian Split Squat → Reverse Lunge
        "a255fcc3-af4d-44b1-8f88-8de014b39ed3" to Anchor(
            bucket = ExerciseBucket.HAMSTRINGS_GLUTES,
            exerciseId = "iIh4XO5YfBOm1yL-",
            exerciseName = "Reverse Lunge",
            equipment = "HANDLES",
            conservativeDefaultKg = 10f
        ),
        // Hip Thrust → Glute Bridge (BELT)
        "T6Vf4-OIurKVfECK" to Anchor(
            bucket = ExerciseBucket.HAMSTRINGS_GLUTES,
            exerciseId = "eh2ElKDA1-0NYgK1",
            exerciseName = "Glute Bridge",
            equipment = "BELT",
            conservativeDefaultKg = 15f
        )
    )

    /**
     * Safe fixed default for exercises not covered by any anchor (calves, compound hybrids, etc.)
     */
    const val FIXED_SAFE_DEFAULT_KG = 10f

    /**
     * Calf raise conservative default (never derived from Squat).
     */
    const val CALF_DEFAULT_KG = 8f

    fun anchorForBucket(bucket: ExerciseBucket): Anchor? =
        ANCHORS.firstOrNull { it.bucket == bucket }

    fun anchorById(exerciseId: String): Anchor? =
        ANCHORS.firstOrNull { it.exerciseId == exerciseId }
}
