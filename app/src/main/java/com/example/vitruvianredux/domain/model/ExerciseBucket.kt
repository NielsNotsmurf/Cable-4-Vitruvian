package com.example.vitruvianredux.domain.model

/**
 * Canonical exercise buckets for workout generation and baseline propagation.
 * Derived from the muscles field in the exercise library, not the coarse muscleGroups field.
 *
 * 10 buckets cover ~92% of cable-loaded exercises in the library.
 */
enum class ExerciseBucket(val displayName: String, val muscleGroupLabel: String) {
    CHEST("Chest", "Chest"),
    BACK("Back", "Back"),
    POSTERIOR_CHAIN("Posterior Chain", "Legs"),
    SHOULDERS("Shoulders", "Shoulders"),
    BICEPS("Biceps", "Arms"),
    TRICEPS("Triceps", "Arms"),
    QUADS("Quads", "Legs"),
    HAMSTRINGS_GLUTES("Hamstrings & Glutes", "Legs"),
    CALVES("Calves", "Legs"),
    CORE("Core", "Core");

    companion object {
        /**
         * Classify an exercise into a bucket from its muscles and movement fields.
         * Falls back to muscleGroups if muscles is empty.
         * Returns null for excluded exercises (bodyweight, mobility, cardio).
         */
        fun from(
            muscles: List<String>,
            movement: String?,
            muscleGroups: List<String> = emptyList()
        ): ExerciseBucket? {
            val m = muscles.map { it.lowercase() }
            val mv = movement?.lowercase()

            // Movement-tag fast paths (most reliable)
            when (mv) {
                "chest_press", "fly" -> return CHEST
                "row" -> return BACK
                "deadlift" -> return POSTERIOR_CHAIN
                "shoulder_press" -> return SHOULDERS
                "shoulder_isolation" -> return SHOULDERS
                "bicep_curl" -> return BICEPS
                "tricep_extension" -> return TRICEPS
                "squat", "leg_extension" -> return QUADS
                "glute_accessory", "hamstring_curl", "unilateral_leg" -> return HAMSTRINGS_GLUTES
            }

            // Muscle-based fallback (for the 37% without movement tags)
            return when {
                "chest" in m -> CHEST
                "upper_back" in m && "lats" in m -> BACK
                "lats" in m && "lower_back" in m -> POSTERIOR_CHAIN
                "upper_back" in m -> BACK
                "lats" in m -> BACK
                "lower_back" in m && "hamstrings" in m -> POSTERIOR_CHAIN
                "lower_back" in m -> POSTERIOR_CHAIN
                "shoulders" in m && m.none { it in listOf("chest", "biceps", "triceps") } -> SHOULDERS
                "biceps" in m -> BICEPS
                "triceps" in m -> TRICEPS
                "calves" in m -> CALVES
                "quads" in m && "glutes" !in m -> QUADS
                "glutes" in m && "quads" in m -> HAMSTRINGS_GLUTES
                "glutes" in m -> HAMSTRINGS_GLUTES
                "hamstrings" in m -> HAMSTRINGS_GLUTES
                "quads" in m -> QUADS
                "core" in m || "obliques" in m -> CORE
                "abductors" in m -> HAMSTRINGS_GLUTES

                // muscleGroups fallback for exercises with no muscle data
                else -> {
                    val mg = muscleGroups.map { it.uppercase() }
                    when {
                        "CHEST" in mg -> CHEST
                        "BACK" in mg -> BACK
                        "SHOULDERS" in mg -> SHOULDERS
                        "ARMS" in mg -> BICEPS
                        "CORE" in mg -> CORE
                        "LEGS" in mg -> QUADS
                        else -> null
                    }
                }
            }
        }
    }
}
