package com.example.vitruvianredux.data.baseline

import com.example.vitruvianredux.domain.model.ExerciseBucket
import kotlin.math.round

/**
 * Propagation coefficient table for Phase 2 baseline system.
 *
 * All exercise IDs verified against exercise_dump.json.
 *
 * Priority order for weight resolution (implemented in BaselineRepository):
 *   1. Actual exercise history (caller responsibility — never overwrite real data)
 *   2. PropagationTable estimate: bucket_baseline_kg × coefficient
 *   3. Anchor conservative default
 *   4. Global safe default (10 kg/cable)
 *
 * Coefficients are relative to the anchor exercise for each bucket.
 * Values based on standard strength ratios; improve as users build history.
 * Rounds to nearest 0.5 kg, minimum 1 kg.
 */
object PropagationTable {

    // ─── CHEST (anchor: Bench Press HANDLES — ZZ92N8QsBdp6HCh3) ────────────
    private val CHEST: Map<String, Float> = mapOf(
        "ZZ92N8QsBdp6HCh3" to 1.00f,   // Bench Press HANDLES (anchor)
        "b5d0f3d1-994b-4589-9d2b-b3f36f1412c7" to 1.00f,  // Bench Press BAR
        "aR4mXWcgsqNaxw4C" to 0.85f,   // Incline Bench Press BENCH+HANDLES
        "99dda2a6-96fa-4d82-970d-58b69425d4db" to 0.85f,  // Incline Bench Press variant
        "Hgy56KiC5KIkfnKa" to 0.95f,   // Decline Bench Press BENCH+HANDLES
        "86CrkMrBHgaM0F37" to 0.95f,   // Decline Bench Press BAR
        "63dc79af-81cd-4180-afea-e07204d2b0fd" to 0.90f,  // Close Grip Bench Press
        "NmuYV1NROjrd0NaU" to 0.95f,   // Neutral Grip Bench Press
        "-5NkaLCrlHvAt0Or" to 0.95f,   // Neutral Grip Bench Press variant
        "NGccEsSujysaQIed" to 0.90f,   // Floor Press HANDLES
        "mfBF5EZDbOgIzoqK" to 0.60f,   // SA Bench Press BENCH+HANDLES (per cable)
        // Chest fly folded from bench press (Option A: no dedicated fly anchor)
        "0zZA4M7uhQ9zi-qi" to 0.55f,   // Rear Delt Fly (used as generic fly proxy)
        "w7GvCyjz7FQ_18iw" to 0.55f    // Rear Delt Fly variant
    )

    // ─── BACK (anchor: Bent Over Row HANDLES — 31ea9d9e-669c-4752-9f44-80c3fa864021) ─
    private val BACK: Map<String, Float> = mapOf(
        "31ea9d9e-669c-4752-9f44-80c3fa864021" to 1.00f,  // Bent Over Row HANDLES (anchor)
        "cJt26IdtckFcJsq1" to 1.00f,   // Bent Over Row ROPE
        "rC0baJJTFuQdbwng" to 1.00f,   // Bent Over Row SHORT_BAR
        "67195cbd-7e2b-4d96-804b-0182b8bf2bab" to 1.00f,  // Bent Over Row BAR
        "lqezCotrgYPfpuDg" to 1.00f,   // Seated Row BENCH+HANDLES
        "tn8HJeXMdU_8IDT_" to 1.00f,   // Seated Row BENCH+SHORT_BAR
        "513b8b5b-5315-4510-87c6-04df95a51053" to 0.65f,  // Upright Row HANDLES
        "7d141cfb-fdde-4320-8553-1e03f6b4e9bd" to 0.65f,  // Upright Row BAR
        // Rear delt / face pull folded from row (Option A: ×0.30)
        "lKxWrGuEzVcxLYqG" to 0.30f,   // Face Pull BENCH+ROPE
        "20b9cd61-8720-4546-a3c0-8926e3b37559" to 0.25f,  // Crossover Rear Delt Fly HANDLES
        "0zZA4M7uhQ9zi-qi" to 0.25f,   // Rear Delt Fly HANDLES
        "w7GvCyjz7FQ_18iw" to 0.25f,   // Rear Delt Fly HANDLES variant
        "sr674W7IvMJKk_Ft" to 0.25f,   // Rear Delt Fly Chest Supported
        "GIQYOXGxJkgszu1e" to 0.25f,   // SA Rear Delt Fly Bench Supported
        "e1e6eda5-42e7-45ee-82e6-ce97746ae0ca" to 0.35f,  // Shrug HANDLES
        "1cAPp9FYOqgEFDfm" to 0.35f,   // Shrug SHORT_BAR
        "149484fe-3baa-4601-97c5-8d273a6b455a" to 0.35f   // Shrug BAR
    )

    // ─── POSTERIOR CHAIN (anchor: Romanian Deadlift HANDLES — 49c4594c-f997-4d70-8008-b293e247e2e4) ─
    private val POSTERIOR_CHAIN: Map<String, Float> = mapOf(
        "49c4594c-f997-4d70-8008-b293e247e2e4" to 1.00f,  // Romanian Deadlift HANDLES (anchor)
        "WAB_Z7EUGeUxF9ce" to 1.00f,   // Romanian Deadlift HANDLES variant
        "1c4f037a-1cab-4133-b72a-458be3e7018d" to 1.00f,  // Romanian Deadlift BAR
        "rMYzHKGP3u9agdih" to 1.00f,   // Romanian Deadlift SHORT_BAR
        "e64c7837-52e2-4b97-b771-cf08ab861af1" to 1.15f,  // Conventional Deadlift BAR
        "c555d400-cd2d-4439-af8d-d98140fef664" to 1.05f,  // Sumo Deadlift BAR
        "NmYpPDrf9XstKbEH" to 0.90f,   // Stiff Leg Deadlift BAR
        "466aeaab-97f1-4a25-804e-67a87a9e5e75" to 0.65f,  // Good Morning BAR
        "HFrc4ELZxjSKX8PW" to 0.65f,   // Good Morning BELT
        // Hamstring curl folded from RDL (Option A: ×0.25)
        "xh7phUUawthAuF41" to 0.25f,   // Lying Hamstring Curl BENCH+STRAPS
        "cJkTi7rZz0DmvcIF" to 0.25f,   // Lying Hamstring Curl STRAPS
        "qd08x4cngup1H1BO" to 0.22f,   // Standing Hamstring Curl STRAPS
        "2G-D8vn2pvW5wVMi" to 0.22f,   // Standing Hamstring Curl STRAPS variant
        "shp7LbPRawwkU2Ct" to 0.18f,   // SL Hamstring Curl STRAPS (per cable)
        "a6da0305-9a19-4314-85d7-0275cd5c99ce" to 0.18f   // SL Hamstring Curl BENCH+STRAPS
    )

    // ─── QUADS (anchor: Squat BELT — lpnNPw86Vud67vWQ) ─────────────────────
    private val QUADS: Map<String, Float> = mapOf(
        "lpnNPw86Vud67vWQ" to 1.00f,   // Squat BELT (anchor)
        "UjIGHxCav-lS9B2I" to 1.00f,   // Squat (no equipment)
        "8c291e21-6e26-49ff-9e24-048d6868f05d" to 0.85f,  // Goblet Squat HANDLES
        "wmNz4Hk9A8_ls07m" to 0.85f,   // Goblet Squat ROPE
        "b254f76e-3109-4db3-bae7-63ea400e63f1" to 0.85f,  // Front Squat BAR
        "rwTxzKiYAl8UENGp" to 0.85f,   // Front Squat SHORT_BAR
        "6f2a6d7e-b34d-4875-b57f-1ae3ad61e881" to 1.00f,  // Crossover Sumo Squat HANDLES
        "3bb4fb5f-7043-432c-b05c-4c74b25296bc" to 1.00f   // Crossover Sumo Squat to Upright Row
    )

    // ─── HAMSTRINGS_GLUTES — unilateral cluster (anchor: Bulgarian Split Squats BENCH+HANDLES — a255fcc3) ─
    // Also covers Hip Thrust cluster (anchor: Hip Thrust BELT+BENCH — T6Vf4-OIurKVfECK)
    private val HAMSTRINGS_GLUTES: Map<String, Float> = mapOf(
        // Bulgarian Split Squat cluster (lunge/split patterns)
        "a255fcc3-af4d-44b1-8f88-8de014b39ed3" to 1.00f,  // Bulgarian Split Squats BENCH+HANDLES (anchor)
        "-YjRuMgOttzv0yZW" to 1.00f,   // Bulgarian Split Squat BELT+BENCH
        "2R1uXL45E-ZnUYYx" to 1.00f,   // Bulgarian Split Squat BENCH
        "ePpAzISZjVFNQfSM" to 1.00f,   // Bulgarian Split Squat BENCH
        "7tufZzs2Sq8JFCdw" to 1.00f,   // Bulgarian Split Squat BAR+BENCH
        "YSIasv929EVKowG5" to 1.00f,   // Bulgarian Split Squat SC BENCH+HANDLES
        "iIh4XO5YfBOm1yL-" to 1.05f,   // Reverse Lunge
        // Hip Thrust / Glute Bridge cluster
        "T6Vf4-OIurKVfECK" to 0.90f,   // Hip Thrust BELT+BENCH
        "o6m-8Vt7VBtb0zNj" to 0.90f,   // Hip Thrust BENCH+SHORT_BAR
        "39689096-7fb9-46aa-a411-59c5c6886c5d" to 0.90f,  // Hip Thrust BAR+BENCH
        "ao5pj3MoMoeNZX4i" to 0.90f,   // Hip Thrust SC BENCH+SHORT_BAR
        "ntLTaE7RsedsHKZV" to 0.65f,   // Hip Thrust Pulses
        "l-XYYcr1wOlMnbr3" to 0.55f,   // SL Hip Thrust BELT+BENCH (per cable)
        "I6HAxWzI9ZrdIS2v" to 0.55f,   // SL Hip Thrust BENCH+SHORT_BAR
        "WK8z78u6e_ebgXco" to 0.55f,   // SL Hip Thrust BAR+BENCH
        "bFiC9iuNTVSZp0v9" to 0.55f,   // SL Hip Thrust (no equipment)
        "eh2ElKDA1-0NYgK1" to 0.75f,   // Glute Bridge BELT
        "6fQ73rEu3CXnMdY6" to 0.75f,   // Glute Bridge SHORT_BAR
        "Nt9h8E_VjwMXl0CP" to 0.75f,   // Glute Bridge BAR
        "RZtNggNHiRbBJyO2" to 0.75f,   // Glute Bridge BELT variant
        "buqjllm5RHJPZySA" to 0.75f,   // Glute Bridge (no equipment)
        // Glute isolation accessories
        "f81ee440-c855-4f3a-b1c8-f9ecf7d094cb" to 0.20f,  // Donkey Kicks STRAPS
        "4OXK82acFzVXTrZe" to 0.20f,   // Donkey Kicks BENCH+STRAPS
        "PfDaDszTwYX2Kh0O" to 0.20f,   // Standing Glute Kickback STRAPS
        "e280829c-aa17-4812-b8fa-bcd0d89ad815" to 0.20f   // Glute Kickbacks STRAPS
    )

    // ─── SHOULDERS — press (anchor: Shoulder Press HANDLES — 0040d53f-85c7-4564-b14e-9b38c979b461) ─
    private val SHOULDERS_PRESS: Map<String, Float> = mapOf(
        "0040d53f-85c7-4564-b14e-9b38c979b461" to 1.00f,  // Shoulder Press HANDLES (anchor)
        "68c3b06f-1767-44fd-9676-e1a4d1c7699b" to 1.00f,  // Shoulder Press BAR
        "3cd0a0cb-8e56-4b0e-83a0-d88ff369749f" to 0.90f   // Arnold Press HANDLES
    )

    // ─── SHOULDERS — isolation (anchor: Lateral Raise HANDLES — rIjnJFQUK3mDbwBW) ─
    private val SHOULDERS_ISOLATION: Map<String, Float> = mapOf(
        "rIjnJFQUK3mDbwBW" to 1.00f,   // Lateral Raise HANDLES (anchor)
        "Y030qNm3LisddglM" to 1.00f,   // Lateral Raise HANDLES variant
        "qfeYOQWqoOIAw_0H" to 1.00f,   // Lateral Raise HANDLES variant 2
        "8WHxwWifeoVP8vLq" to 1.00f,   // Lateral Raise STRAPS
        "d38c603f-42a4-4317-8ab9-9a226f41dac7" to 0.90f,  // Front Raise HANDLES
        "YZ82MoaAeOm4rVTL" to 0.90f    // Front Raise SHORT_BAR
    )

    // ─── BICEPS (anchor: Bicep Curl HANDLES — fc5ca114-3cb6-462a-ab54-7ef1233b5fc2) ─
    private val BICEPS: Map<String, Float> = mapOf(
        "fc5ca114-3cb6-462a-ab54-7ef1233b5fc2" to 1.00f,  // Bicep Curl HANDLES (anchor)
        "k-PGXPztgc5uS42S" to 1.00f,   // Bicep Curl SHORT_BAR
        "b2fb6fcd-3f47-403d-bad1-0f5e3f62d048" to 1.00f,  // Bicep Curl BAR
        "77f8d4e5-d97c-43ac-b4fc-d5ff35f67f8d" to 0.95f,  // Hammer Curl HANDLES
        "05wiA4Eqtrj388Ui" to 0.95f,   // Hammer Curl ROPE
        "XfkITKH5emJFlGv7" to 0.90f,   // Alternating Hammer Curl HANDLES
        "wgiwmR1yt3QJtiWs" to 0.80f,   // Concentration Curl HANDLES
        "4ksnpJ1IfVp7QH5n" to 0.85f,   // Preacher Curl BENCH+HANDLES
        "1vS7ZNfrz2qF6KId" to 0.65f,   // Bayesian Curl HANDLES
        "QzWldJ1xdgra87Z1" to 0.65f,   // Bayesian Curl HANDLES variant
        "xfANU256X8tv1hUU" to 1.00f,   // Seated Bicep Curl BENCH+HANDLES
        "I4ZzPJg0qt7dw8sV" to 1.00f,   // Seated Bicep Curl BENCH+HANDLES variant
        "nFR6tLChZbdWvLhP" to 0.80f    // Seated Concentration Curl BENCH+HANDLES
    )

    // ─── TRICEPS (anchor: Overhead Tricep Extension HANDLES — bf1437e9-f8ca-4bfe-b766-af5fee1834a5) ─
    private val TRICEPS: Map<String, Float> = mapOf(
        "bf1437e9-f8ca-4bfe-b766-af5fee1834a5" to 1.00f,  // Overhead Tricep Extension HANDLES (anchor)
        "e3c59976-efa7-4e7d-8848-77e864ba1d0f" to 1.00f,  // Overhead Tricep Extension HANDLES variant
        "RASpI84AvAi3ucvr" to 1.00f,   // Overhead Tricep Extension ROPE
        "_i1E704BS8bngWrv" to 1.00f,   // Overhead Tricep Extension BAR+GREY
        "L3m5K_4X7ztA2yXV" to 1.00f,   // Overhead Tricep Extension BAR+BLACK
        "euBpwFXDmK3qak2_" to 0.95f,   // Kneeling Overhead Tricep Extension SHORT_BAR
        "y1ydZQjxjqNn-Fl7" to 0.95f,   // Kneeling Overhead Tricep Extension HANDLES
        "cOaTQ1ljsuUom_cn" to 1.10f,   // Skull Crusher BENCH+ROPE
        "NTtSMyHmJmTE7iQ5" to 1.10f,   // Skull Crusher BENCH+HANDLES
        "vI7FQn2KAPJxBS-n" to 1.05f,   // Skull Crusher SHORT_BAR
        "a1f4e3d7-8ab4-442d-ba06-8dfd9c11e248" to 1.10f,  // Skull Crusher HANDLES
        "d99a9738-3828-42a6-815e-459c1f825ff9" to 1.05f,  // Skull Crusher BENCH+SHORT_BAR
        "6hCTlg47HzDa8KMH" to 1.05f,   // Skull Crusher ROPE
        "IrPf7PCbwEcuJEG5" to 0.35f,   // Tricep Kick Back HANDLES
        "fs5XmqhpN9Mk9mnF" to 0.35f,   // Tricep Kick Back HANDLES variant
        "85c0aa8d-d0e4-4ba0-a802-a557affd1116" to 0.35f   // Tricep Kick Back Supported BENCH+HANDLES
    )

    // ─── CALVES — fixed default only, no propagation ──────────────────────
    // See AnchorExerciseConfig.CALF_DEFAULT_KG
    // IDs listed for reference only (not used in coefficient lookup):
    // 287a9e39 HANDLES, j3Y1MpvaeGPy0o99 SHORT_BAR, SfPmFe9Tm9CVQ9hC BELT
    // 0Oh0_lqI38McSWPi SL_SHORT_BAR, e74RvoLRZs5arglE BAR_GREY

    /**
     * Returns estimated weight per cable (kg) given bucket baseline weight.
     * Returns null if no coefficient entry exists for this exerciseId+bucket.
     */
    fun estimateDefault(
        exerciseId: String,
        bucket: ExerciseBucket,
        bucketBaselineKg: Float
    ): Float? {
        val coefficient = findCoefficient(exerciseId, bucket) ?: return null
        return roundToHalf(bucketBaselineKg * coefficient)
    }

    fun findCoefficient(exerciseId: String, bucket: ExerciseBucket): Float? =
        tableForBucket(bucket)?.get(exerciseId)

    private fun tableForBucket(bucket: ExerciseBucket): Map<String, Float>? = when (bucket) {
        ExerciseBucket.CHEST -> CHEST
        ExerciseBucket.BACK -> BACK
        ExerciseBucket.POSTERIOR_CHAIN -> POSTERIOR_CHAIN
        ExerciseBucket.QUADS -> QUADS
        ExerciseBucket.HAMSTRINGS_GLUTES -> HAMSTRINGS_GLUTES
        ExerciseBucket.SHOULDERS -> SHOULDERS_PRESS + SHOULDERS_ISOLATION
        ExerciseBucket.BICEPS -> BICEPS
        ExerciseBucket.TRICEPS -> TRICEPS
        ExerciseBucket.CALVES -> null
        ExerciseBucket.CORE -> null
    }

    private fun roundToHalf(value: Float): Float =
        (round(value * 2f) / 2f).coerceAtLeast(1f)
}
