package nl.nielsnotsmurf.cleanui.logic

/** The basic movement an exercise follows; each one has its own animated figure in the demo. */
enum class Move {
    SQUAT, HINGE, LUNGE, OVERHEAD_PRESS, BENCH_PRESS, ROW, HIGH_PULL, UPRIGHT_ROW, CURL,
    OVERHEAD_EXTENSION, ARM_KICKBACK, LATERAL_RAISE, FRONT_RAISE, FLY, REAR_FLY, SHRUG, CALF_RAISE,
    CRUNCH, ROTATION, HIP_THRUST, GLUTE_KICKBACK, LEG_CURL, ABDUCTION, PULLOVER
}

/** The movement plus, when the free-exercise-db has the same exercise, its start/end photos. */
data class ExerciseMove(val move: Move, val photoId: String?) {
    /** Start and end photo (public domain, free-exercise-db), or empty. */
    val photos: List<String>
        get() = photoId?.let { id -> listOf(0, 1).map { "$PHOTO_BASE$id/$it.jpg" } }.orEmpty()

    companion object {
        const val PHOTO_BASE = "https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/"
        const val PHOTO_CREDIT = "Photos: free-exercise-db (public domain)"
    }
}

/**
 * Finds the movement from the exercise name. Rules are checked in order, so the more specific
 * ones (incline bench press) come before the general ones (press). Unknown names return null,
 * and the demo falls back to the body map.
 */
object ExerciseMoves {
    private class Rule(val words: List<String>, val move: Move, val photo: String?)

    private fun r(words: String, move: Move, photo: String? = null) = Rule(words.split('|'), move, photo)

    private val rules = listOf(
        r("face pull", Move.HIGH_PULL, "Face_Pull"),
        r("upright row|high pull", Move.UPRIGHT_ROW, "Upright_Cable_Row"),
        r("rear delt fly|reverse fly|rear delt raise", Move.REAR_FLY, "Cable_Rear_Delt_Fly"),
        r("rear delt row", Move.ROW, "Barbell_Rear_Delt_Row"),
        r("renegade row", Move.ROW, "Alternating_Renegade_Row"),
        r("row", Move.ROW, "Bent_Over_Barbell_Row"),
        r("romanian deadlift|rdl|stiff leg", Move.HINGE, "Romanian_Deadlift"),
        r("sumo deadlift", Move.HINGE, "Sumo_Deadlift"),
        r("good morning", Move.HINGE, "Good_Morning"),
        r("deadlift|rack pull|pull through|swing", Move.HINGE, "Barbell_Deadlift"),
        r("split squat|bulgarian", Move.LUNGE, "Split_Squats"),
        r("step up", Move.LUNGE, "Dumbbell_Step_Ups"),
        r("lunge", Move.LUNGE, "Dumbbell_Lunges"),
        r("sit to stand", Move.SQUAT, "Bodyweight_Squat"),
        r("goblet squat", Move.SQUAT, "Goblet_Squat"),
        r("front squat", Move.SQUAT, "Front_Squat_Clean_Grip"),
        r("squat", Move.SQUAT, "Barbell_Squat"),
        r("hip thrust", Move.HIP_THRUST, "Barbell_Hip_Thrust"),
        r("glute bridge|bridge", Move.HIP_THRUST, "Barbell_Glute_Bridge"),
        r("donkey kick|glute kickback|hip extension|45 degree kickback|kneeling kickback", Move.GLUTE_KICKBACK, "Glute_Kickback"),
        r("hamstring curl|leg curl", Move.LEG_CURL, "Standing_Leg_Curl"),
        r("abduct", Move.ABDUCTION, "Thigh_Abductor"),
        r("calf", Move.CALF_RAISE, "Standing_Calf_Raises"),
        r("shrug", Move.SHRUG, "Cable_Shrugs"),
        r("incline bench|incline press|incline chest", Move.BENCH_PRESS, "Barbell_Incline_Bench_Press_-_Medium_Grip"),
        r("close grip bench", Move.BENCH_PRESS, "Close-Grip_Barbell_Bench_Press"),
        r("floor press", Move.BENCH_PRESS, "Floor_Press"),
        r("bench press|chest press", Move.BENCH_PRESS, "Barbell_Bench_Press_-_Medium_Grip"),
        r("arnold", Move.OVERHEAD_PRESS, "Arnold_Dumbbell_Press"),
        r("shoulder press|overhead press|military|push press|thruster", Move.OVERHEAD_PRESS, "Cable_Shoulder_Press"),
        r("skull crusher|skullcrusher", Move.OVERHEAD_EXTENSION, "EZ-Bar_Skullcrusher"),
        r("tricep kickback|triceps kickback|tricep extension bent|bent over tricep", Move.ARM_KICKBACK, "Tricep_Dumbbell_Kickback"),
        r("tricep|triceps", Move.OVERHEAD_EXTENSION, "Cable_Rope_Overhead_Triceps_Extension"),
        r("hammer curl", Move.CURL, "Hammer_Curls"),
        r("concentration curl", Move.CURL, "Concentration_Curls"),
        r("preacher", Move.CURL, "Cable_Preacher_Curl"),
        r("curl", Move.CURL, "Barbell_Curl"),
        r("pullover", Move.PULLOVER, "Straight-Arm_Dumbbell_Pullover"),
        r("lateral raise|side raise|y raise", Move.LATERAL_RAISE, "Side_Lateral_Raise"),
        r("front raise", Move.FRONT_RAISE, "Front_Plate_Raise"),
        r("crossover|fly|flye|pec", Move.FLY, "Cable_Crossover"),
        r("crunch|sit up", Move.CRUNCH, "Cable_Crunch"),
        r("pallof", Move.ROTATION, "Pallof_Press"),
        r("russian twist|twist", Move.ROTATION, "Cable_Russian_Twists"),
        r("woodchop|wood chop|chop|lift|rotation|oblique", Move.ROTATION, null),
        r("press", Move.BENCH_PRESS, null),
        r("raise", Move.FRONT_RAISE, null),
    )

    fun of(name: String): ExerciseMove? {
        val n = " " + name.lowercase().replace('-', ' ').replace(Regex("[^a-z0-9 ]"), " ").replace(Regex(" +"), " ").trim() + " "
        val rule = rules.firstOrNull { rule -> rule.words.any { n.contains(" $it") } } ?: return null
        return ExerciseMove(rule.move, rule.photo)
    }
}
