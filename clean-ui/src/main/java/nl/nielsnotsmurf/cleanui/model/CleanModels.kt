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
    val popularity: Float = 0f,
    /** Body region for the muscle-group picker ("Chest", "Back", "Shoulders", "Arms", "Legs", "Core"). */
    val region: String = ""
) {
    /** No cable load: the classic app runs these as timed sets only. */
    val isBodyweight: Boolean get() = equipment.isBlank() || equipment.equals("bodyweight", ignoreCase = true)
}

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

/** Echo difficulty, matching the trainer's four levels. */
enum class EchoLevelChoice(val label: String) { HARD("Hard"), HARDER("Harder"), HARDEST("Hardest"), EPIC("Epic") }

enum class SetKind { REPS, AMRAP }

/**
 * One set of a planned exercise. [percent] overrides the exercise's % of 1RM for this set;
 * null means "same as the rest".
 */
data class SetSpec(
    val kind: SetKind = SetKind.REPS,
    val reps: Int = 8,
    val percent: Int? = null,
    val restSeconds: Int = 90
)

/** One lifter's part of a planned exercise: their load for each set, in set order. */
data class LifterLoad(
    val lifterId: String,
    val setWeightsKg: List<Float>
) {
    val weightPerCableKg: Float get() = setWeightsKg.firstOrNull() ?: 0f
}

/**
 * Everything needed to start an exercise. Lifters rotate set by set in the order of [loads].
 */
data class SetPlan(
    val exercise: ExerciseCard,
    val mode: TrainingMode,
    val percentOfOneRepMax: Int,
    val sets: List<SetSpec>,
    val loads: List<LifterLoad>,
    /** "SINGLE" or "DOUBLE". */
    val cableConfig: String = "DOUBLE",
    /** Timed sets: every set lasts this many seconds instead of counting reps. */
    val durationSeconds: Int? = null,
    /** Echo / eccentric option, 0–150. Only used by Echo. */
    val eccentricLoadPercent: Int = 100,
    val echoLevel: EchoLevelChoice = EchoLevelChoice.HARDER,
    /** Kilograms added (or removed when negative) per rep. */
    val progressionKg: Float = 0f
)

/** One exercise of the 1RM test, with a safe first attempt for lifters who have no 1RM yet. */
data class TestAnchor(
    val exerciseId: String,
    val defaultStartKg: Float
)

/** How to do an exercise, shown before the first attempt. */
data class ExerciseGuide(
    val description: String,
    val videoUrl: String? = null,
    val thumbnailUrl: String? = null
)

/** Result of one single-rep attempt in the 1RM test. */
enum class AttemptOutcome { MADE_EASY, MADE_TOUGH, MISSED }

data class Attempt(val kg: Float, val outcome: AttemptOutcome) {
    val made: Boolean get() = outcome != AttemptOutcome.MISSED
}

/** How a lifter did an exercise last time, used to prefill set setup. */
data class LastPlan(
    val mode: TrainingMode,
    val sets: List<SetSpec>,
    val durationSeconds: Int?,
    val cableConfig: String,
    val eccentricLoadPercent: Int,
    val echoLevel: EchoLevelChoice,
    val progressionKg: Float
)

/** One finished exercise for one lifter, as the History tab lists it. */
data class HistoryEntry(
    val id: String,
    val lifterId: String,
    val exerciseName: String,
    val timestamp: Long,
    val mode: String,
    val sets: Int,
    val reps: Int,
    /** Heaviest load per cable; null for Echo, which adapts the load. */
    val topKgPerCable: Float?,
    val durationMs: Long,
    /** Sets done back to back in one workout share this (rotations, routines). */
    val groupId: String
)
