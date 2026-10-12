package nl.nielsnotsmurf.cleanui.model

/**
 * Plain models the Clean UI works with. They deliberately mirror nothing from :app,
 * so upstream renames or schema changes don't ripple into the screens.
 * All loads are kilograms per cable, matching the rest of the app.
 */

data class Lifter(
    val id: String,
    val name: String,
    /** ARGB colour chosen for the profile; used for chips, bands and 1RM text. */
    val colorArgb: Int
)

data class ExerciseCard(
    val id: String,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    /** Cable setup the trainer should use by default ("SINGLE", "DOUBLE", "EITHER"). */
    val cableConfig: String = "DOUBLE",
    val isFavorite: Boolean = false,
    val lastPerformedAt: Long? = null,
    val popularity: Float = 0f
)

/** A completed set from history, used to estimate a 1RM. */
data class LiftRecord(
    val exerciseId: String,
    val weightPerCableKg: Float,
    val reps: Int,
    val timestamp: Long
)

enum class OneRepMaxSource {
    /** Entered in the benchmark flow from a rep-max set. */
    BENCHMARK,
    /** Typed in by the lifter as a known 1RM. */
    MANUAL,
    /** Estimated (Epley) from the lifter's own history for this exercise. */
    HISTORY,
    /** Scaled from a related exercise's baseline; shown with "~". */
    RELATED
}

data class OneRepMax(
    val kg: Float,
    val source: OneRepMaxSource,
    val updatedAt: Long = 0L
) {
    val isEstimate: Boolean get() = source == OneRepMaxSource.RELATED
}

enum class TrainingMode(val label: String) {
    OLD_SCHOOL("Old School"),
    PUMP("Pump"),
    TUT("TUT"),
    TUT_BEAST("TUT Beast"),
    ECCENTRIC_ONLY("Eccentric"),
    ECHO("Echo")
}

enum class WeightUnit { KG, LB }

sealed interface TrainerConnection {
    data object Disconnected : TrainerConnection
    data object Connecting : TrainerConnection
    data class Connected(val deviceName: String) : TrainerConnection
    data class Failed(val message: String) : TrainerConnection
}

/** One lifter's part of a planned exercise. */
data class LifterLoad(
    val lifterId: String,
    val weightPerCableKg: Float
)

/**
 * Everything needed to start an exercise. Lifters rotate set by set in the order of [loads].
 */
data class SetPlan(
    val exercise: ExerciseCard,
    val mode: TrainingMode,
    val percentOfOneRepMax: Int,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val loads: List<LifterLoad>,
    /** Echo / eccentric option, 0–150. Only used by Echo. */
    val eccentricLoadPercent: Int = 100,
    /** Kilograms added (or removed when negative) per rep. */
    val progressionKg: Float = 0f
)
