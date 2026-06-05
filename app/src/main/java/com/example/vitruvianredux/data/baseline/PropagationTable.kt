package com.example.vitruvianredux.data.baseline

import com.example.vitruvianredux.domain.model.ExerciseBucket
import kotlin.math.round

/**
 * Propagation coefficient table for Phase 2 baseline system.
 *
 * Given a bucket baseline weight (from anchor assessment) and an exerciseId,
 * returns the estimated starting weight for that exercise.
 *
 * Priority order for weight resolution:
 *   1. Actual exercise history (handled in ViewModel — never overwrite real data)
 *   2. Propagated estimate from this table
 *   3. Bucket conservative default (AnchorExerciseConfig.Anchor.conservativeDefaultKg)
 *   4. Global safe default (AnchorExerciseConfig.FIXED_SAFE_DEFAULT_KG)
 *
 * Coefficients are relative to the anchor (baseline) exercise for each bucket.
 * Values are approximations based on common strength ratios; they improve over time
 * as users build history (actual history always supersedes).
 */
object PropagationTable {

    /**
     * Per-bucket coefficient tables.
     * Keys are exercise IDs from exercise_dump.json.
     * Values are multipliers applied to the bucket baseline weight.
     */

    // ─── CHEST (baseline: Bench Press) ──────────────────────────────────────
    private val CHEST_COEFFICIENTS: Map<String, Float> = mapOf(
        "ZZ92N8QsBdp6HCh3" to 1.00f,  // Bench Press HANDLES
        "fc5b26IdtckFcJsq1" to 1.00f,  // Bench Press variant
        "incline-bench-handles" to 0.85f,
        "decline-bench-handles" to 0.95f,
        "close-grip-bench" to 0.90f,
        "neutral-grip-bench" to 0.95f,
        "floor-press-handles-0001" to 0.90f,
        "sa-bench-press" to 0.60f,
        "sa-floor-press" to 0.55f,
        "chest-press-gymball" to 0.75f,
        "incline-press-gymball" to 0.70f
    )

    // ─── BACK (baseline: Bent Over Row) ─────────────────────────────────────
    private val BACK_COEFFICIENTS: Map<String, Float> = mapOf(
        "31ea9d9e-1234-4d96-804b-bentoverrow01" to 1.00f,  // Bent Over Row HANDLES
        "bent-over-row-wide" to 0.95f,
        "bent-over-row-reverse" to 1.00f,
        "seated-row-handles" to 1.00f,
        "seated-row-shortbar" to 1.00f,
        "seated-row-rope" to 0.90f,
        "kneeling-row" to 0.95f,
        "chest-supported-row" to 1.05f,
        "upright-row-handles" to 0.65f,
        "crossover-row" to 0.90f,
        // Rear delt folded from Face Pull (Option A: no separate anchor)
        "face-pull-rope" to 0.30f,
        "rear-delt-fly" to 0.20f,
        "crossover-rear-delt" to 0.20f,
        "shrug-handles" to 0.35f
    )

    // ─── POSTERIOR CHAIN (baseline: Romanian Deadlift) ──────────────────────
    private val POSTERIOR_CHAIN_COEFFICIENTS: Map<String, Float> = mapOf(
        "49c4594c-rdl-handles-0001" to 1.00f,  // Romanian Deadlift HANDLES
        "rdl-bar" to 1.00f,
        "rdl-shortbar" to 1.00f,
        "conventional-deadlift" to 1.15f,
        "sumo-deadlift" to 1.05f,
        "stiff-leg-deadlift" to 0.90f,
        "suitcase-deadlift" to 0.95f,
        "snatch-grip-deadlift" to 0.85f,
        "good-morning" to 0.65f,
        "pull-through" to 0.70f,
        "crossover-rdl" to 0.85f,
        "sl-rdl-handles" to 0.55f,
        "sl-rdl-rope" to 0.55f,
        "sl-rdl-bar" to 0.55f,
        // Hamstring curl folded from RDL (Option A: no separate anchor)
        "lying-hamstring-curl" to 0.25f,
        "sl-hamstring-curl" to 0.18f,
        "standing-hamstring-curl" to 0.22f
    )

    // ─── QUADS (baseline: Squat) ─────────────────────────────────────────────
    private val QUADS_COEFFICIENTS: Map<String, Float> = mapOf(
        "lpnNPw86Vud67vWQ" to 1.00f,  // Squat BELT
        "squat-handles" to 1.00f,
        "squat-bar" to 1.00f,
        "suitcase-squat-handles" to 0.95f,
        "goblet-squat" to 0.85f,
        "front-squat" to 0.85f,
        "front-rack-squat" to 0.85f,
        "sumo-squat" to 1.00f,
        "sumo-belt-squat" to 1.00f,
        "low-bar-squat" to 1.10f,
        "high-bar-squat" to 1.00f,
        "sit-to-stand" to 0.70f,
        "squat-pulses" to 0.70f,
        "squat-to-calf-raise" to 0.90f
    )

    // ─── UNILATERAL / HAMSTRINGS-GLUTES (baseline: Bulgarian Split Squat) ───
    private val HAMSTRINGS_GLUTES_COEFFICIENTS: Map<String, Float> = mapOf(
        "a255fcc3-bulgsplit-0001" to 1.00f,  // Bulgarian Split Squat
        "bulgarian-split-squat-bar" to 1.00f,
        "reverse-lunge-handles-001" to 1.05f,
        "reverse-lunge-bar" to 1.05f,
        "front-rack-lunge" to 0.95f,
        "alternating-lunge" to 0.90f,
        "alternating-curtsy-lunge" to 0.80f,
        "deficit-lunge" to 0.90f,
        "side-lunge" to 0.75f,
        "cossack-squat" to 0.70f,
        "step-up-handles" to 0.85f,
        "sl-step-down" to 0.80f,
        "alternating-side-lunge" to 0.75f,
        "hip-thrust-handles" to 0.90f,         // Hip Thrust overlaps here
        "T6Vf4-OIurKVfECK" to 0.90f,           // Hip Thrust BELT+BENCH
        "glute-bridge-belt-00001" to 0.75f,
        "glute-bridge-bar" to 0.75f,
        "sl-hip-thrust" to 0.55f,
        "sl-glute-bridge" to 0.50f,
        "feet-raised-glute-bridge" to 0.80f,
        "hip-thrust-pulses" to 0.65f,
        "donkey-kick" to 0.20f,
        "standing-glute-kickback" to 0.20f,
        "glute-bridge-belt-00001" to 0.75f
    )

    // ─── SHOULDERS (baseline: Shoulder Press for press, Lateral Raise for isolation) ──
    private val SHOULDERS_PRESS_COEFFICIENTS: Map<String, Float> = mapOf(
        "0040d53f-shoulderpress-01" to 1.00f,  // Shoulder Press HANDLES
        "shoulder-press-seated" to 1.05f,
        "arnold-press" to 0.90f,
        "standing-shoulder-press" to 0.95f,
        "sa-shoulder-press" to 0.60f,
        "overhead-press-bar" to 1.00f
    )

    private val SHOULDERS_ISOLATION_COEFFICIENTS: Map<String, Float> = mapOf(
        "rIjnJFQUK3mDbwBW" to 1.00f,  // Lateral Raise HANDLES
        "lateral-raise-crossover" to 1.05f,
        "lateral-raise-straps" to 1.00f,
        "lateral-raise-seated" to 0.95f,
        "front-raise-handles" to 0.90f,
        "double-arm-front-raise" to 0.90f,
        "bent-over-sa-lateral" to 0.75f,
        "bent-over-shrug" to 1.10f
    )

    // ─── BICEPS (baseline: Bicep Curl HANDLES) ──────────────────────────────
    private val BICEPS_COEFFICIENTS: Map<String, Float> = mapOf(
        "fc5ca114-3cb6-462a-ab54-7ef1233b5fc2" to 1.00f,  // Bicep Curl HANDLES
        "bicep-curl-pronated" to 0.85f,
        "hammer-curl" to 0.95f,
        "concentration-curl" to 0.80f,
        "sa-bicep-curl" to 0.75f,
        "preacher-curl" to 0.85f,
        "bayesian-curl" to 0.65f,
        "wide-grip-curl" to 0.90f,
        "seated-bicep-curl" to 1.00f,
        "crossover-bicep-curl" to 0.90f
    )

    // ─── TRICEPS (baseline: Overhead Tricep Extension HANDLES) ──────────────
    private val TRICEPS_COEFFICIENTS: Map<String, Float> = mapOf(
        "bf1437e9-overtricep-0001" to 1.00f,  // Overhead Tricep Extension HANDLES
        "overhead-tricep-rope" to 1.00f,
        "overhead-tricep-bar" to 1.00f,
        "kneeling-overhead-tricep" to 0.95f,
        "seated-overhead-tricep" to 0.95f,
        "skull-crusher-bench-rope" to 1.10f,
        "skull-crusher-bench-handles" to 1.10f,
        "skull-crusher-shortbar" to 1.05f,
        "skull-crusher-handles" to 1.10f,
        "tricep-kickback" to 0.35f,
        "bent-over-tricep-ext" to 0.70f,
        "sa-overhead-tricep" to 0.60f
    )

    // ─── CALVES — fixed defaults only (no compound anchor) ──────────────────
    // Never propagate from Squat. Use AnchorExerciseConfig.CALF_DEFAULT_KG.

    /**
     * Returns estimated weight per cable (kg) for the given exercise,
     * using the provided bucket baseline weight.
     *
     * Returns null if the exerciseId has no coefficient entry for the bucket —
     * callers should fall back to the anchor's conservativeDefaultKg.
     */
    fun estimateDefault(
        exerciseId: String,
        bucket: ExerciseBucket,
        bucketBaselineKg: Float
    ): Float? {
        val coefficient = findCoefficient(exerciseId, bucket) ?: return null
        val raw = bucketBaselineKg * coefficient
        return roundToHalf(raw)
    }

    /**
     * Looks up the coefficient for an exercise in its bucket's table.
     */
    fun findCoefficient(exerciseId: String, bucket: ExerciseBucket): Float? {
        val table = tableForBucket(bucket) ?: return null
        return table[exerciseId]
    }

    private fun tableForBucket(bucket: ExerciseBucket): Map<String, Float>? = when (bucket) {
        ExerciseBucket.CHEST -> CHEST_COEFFICIENTS
        ExerciseBucket.BACK -> BACK_COEFFICIENTS
        ExerciseBucket.POSTERIOR_CHAIN -> POSTERIOR_CHAIN_COEFFICIENTS
        ExerciseBucket.QUADS -> QUADS_COEFFICIENTS
        ExerciseBucket.HAMSTRINGS_GLUTES -> HAMSTRINGS_GLUTES_COEFFICIENTS
        ExerciseBucket.SHOULDERS -> SHOULDERS_PRESS_COEFFICIENTS + SHOULDERS_ISOLATION_COEFFICIENTS
        ExerciseBucket.BICEPS -> BICEPS_COEFFICIENTS
        ExerciseBucket.TRICEPS -> TRICEPS_COEFFICIENTS
        ExerciseBucket.CALVES -> null
        ExerciseBucket.CORE -> null
    }

    /** Rounds to nearest 0.5 kg. */
    private fun roundToHalf(value: Float): Float =
        (round(value * 2f) / 2f).coerceAtLeast(1f)
}
