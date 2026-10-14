package nl.nielsnotsmurf.cleanui.logic

/**
 * Rules for the Garmin watch app: which profile's sets go to which watch, and which Garmin
 * exercise category a Vitruvian exercise belongs to. Plain logic, so :app and tests share it.
 */
object GarminWatches {

    /** Stored as a profile's link when it should never send to a watch. */
    const val NO_WATCH = -1L

    /** Garmin's FIT exercise_category for an exercise it has no category for. */
    const val UNKNOWN_CATEGORY = 65534

    data class Watch(val id: Long, val name: String)

    /**
     * The watch that records [profileId]'s sets, or null when that profile has none.
     *
     * A choice made for the profile wins ([NO_WATCH] opts out). Without one, a watch belongs to
     * the profile whose paired heart rate sensor has the watch's name (phase 1 pairs the same
     * Venu 4 for heart rate). With a single profile and a single watch, they belong together.
     * A watch someone chose for themselves is never picked automatically for anyone else.
     * [heartRateSensorNames] holds every profile, with the name of its paired sensor or null.
     */
    fun watchFor(
        profileId: String,
        watches: List<Watch>,
        chosen: Map<String, Long>,
        heartRateSensorNames: Map<String, String?>
    ): Watch? {
        chosen[profileId]?.let { id -> return watches.firstOrNull { it.id == id } }
        val claimed = chosen.filterKeys { it != profileId }.values.toSet()
        val free = watches.filter { it.id !in claimed }
        heartRateSensorNames[profileId]?.let { sensor ->
            free.firstOrNull { sameDevice(it.name, sensor) }?.let { return it }
        }
        val anyoneElseMatches = heartRateSensorNames.any { (id, sensor) ->
            id != profileId && sensor != null && free.any { sameDevice(it.name, sensor) }
        }
        return if (heartRateSensorNames.size <= 1 && free.size == 1 && !anyoneElseMatches) free.single() else null
    }

    /** "Venu 4" from Garmin Connect matches a heart rate broadcast called "Venu4" or "Venu 4 HR". */
    fun sameDevice(watchName: String, sensorName: String): Boolean {
        val a = normalize(watchName)
        val b = normalize(sensorName)
        return a.isNotEmpty() && b.isNotEmpty() && (a.contains(b) || b.contains(a))
    }

    private fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Garmin FIT exercise_category for a Vitruvian exercise name, or [UNKNOWN_CATEGORY].
     * Earlier rules win, so "leg curl" is checked before "curl".
     */
    fun categoryFor(exerciseName: String): Int {
        val n = " " + exerciseName.lowercase().replace('-', ' ') + " "
        return CATEGORY_RULES.firstOrNull { (words, _) -> words.any { it in n } }?.second ?: UNKNOWN_CATEGORY
    }

    // Values from the FIT profile's exercise_category type.
    private val CATEGORY_RULES: List<Pair<List<String>, Int>> = listOf(
        listOf("leg curl", "hamstring curl", "nordic") to 15,          // leg_curl
        listOf("leg press", "squat", "leg extension") to 28,            // squat
        listOf("face pull", " row") to 23,                               // row
        listOf("pull up", "pullup", "chin up", "pulldown", "pull down", "lat pull") to 21, // pull_up
        listOf("push up", "pushup") to 22,                              // push_up
        listOf("lateral raise", "side raise", "front raise", "rear delt", "reverse fly") to 14, // lateral_raise
        listOf(" fly", " flye", " flies", "pec deck") to 9,            // flye
        listOf("bench", "chest press", "incline press", "decline press", "floor press") to 0, // bench_press
        listOf("tricep", "pushdown", "push down", "skull", "kickback") to 30, // triceps_extension
        listOf("curl") to 7,                                            // curl
        listOf("calf") to 1,                                            // calf_raise
        listOf("deadlift", "rdl", "good morning") to 8,                 // deadlift
        listOf("hip thrust", "glute bridge", "bridge") to 10,           // hip_raise
        listOf("lunge", "split squat", "step up") to 17,                // lunge
        listOf("clean", "snatch", "jerk") to 18,                        // olympic_lift
        listOf("shrug") to 26,                                          // shrug
        listOf("shoulder press", "overhead press", "military", "arnold", "push press") to 24, // shoulder_press
        listOf("crunch") to 6,                                          // crunch
        listOf("chop") to 4,                                            // chop
        listOf("plank") to 19,                                          // plank
        listOf("carry") to 3,                                           // carry
        listOf("back extension", "hyperextension") to 13,               // hyperextension
        listOf("twist", "rotation", "pallof", "anti rotation") to 5,    // core
        listOf("abduction", "adduction", "hip abduct", "hip adduct") to 11 // hip_stability
    )
}
