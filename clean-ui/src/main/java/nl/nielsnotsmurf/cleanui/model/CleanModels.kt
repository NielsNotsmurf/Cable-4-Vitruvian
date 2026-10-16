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
    val colorArgb: Int,
    /** Name of the paired heart rate sensor (Garmin watch or strap), if any. */
    val heartRateSensor: String? = null,
    val heartRateConnected: Boolean = false
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
    /** Scaled from a related exercise (one_rep_max_map.json or the baseline); shown with "~" as estimated. */
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

/** One test attempt: the load set on the trainer and, once the set is saved, what it measured. */
data class Attempt(val kg: Float, val outcome: AttemptOutcome, val measuredKg: Float? = null) {
    val made: Boolean get() = outcome != AttemptOutcome.MISSED

    /** What counts for the 3RM: the measured peak when there is one, like every PR. */
    val resultKg: Float get() = measuredKg ?: kg
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
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Sets done back to back in one workout share this (rotations, routines). */
    val groupId: String,
    /** Exercise library id; empty for Just Lift. */
    val exerciseId: String = ""
)

/** App settings the Clean Settings tab edits. They are the classic app's own preferences. */
data class CleanSettings(
    val unit: WeightUnit = WeightUnit.KG,
    val defaultReps: Int = 10,
    /** Sets per exercise. Every set gets [defaultReps]; only the pre-workout screen changes them. */
    val defaultSets: Int = 3,
    val autoplay: Boolean = true,
    val stopAtTop: Boolean = false,
    val beeps: Boolean = true,
    val videos: Boolean = true
)

/** A trainer LED colour scheme: its name and the colours it cycles through. */
data class LedScheme(val name: String, val colorsArgb: List<Int>)

/** Where the set on the trainer is, as the Clean lifting screen shows it. */
enum class LivePhase { STARTING, COUNTDOWN, LIFTING, SET_DONE, RESTING, FINISHED, OTHER }

/** Live state of the set on the trainer. Loads are per cable in kg. */
data class LiveWorkout(
    val phase: LivePhase,
    val exerciseName: String = "",
    val countdown: Int = 0,
    val reps: Int = 0,
    val targetReps: Int? = null,
    val warmupReps: Int = 0,
    val warmupTarget: Int = 0,
    val warmupDone: Boolean = true,
    val loadKg: Float = 0f,
    val liveLoadKg: Float = 0f,
    /** Handle height in the learned rep range, 0 (bottom) to 1 (top); null until learned. */
    val position: Float? = null,
    val setNumber: Int = 0,
    val totalSets: Int = 0,
    val restSeconds: Int = 0,
    val nextName: String = "",
    val isLastExercise: Boolean = false,
    val lifterId: String? = null,
    val lifterName: String? = null,
    val lifterColorArgb: Int? = null,
    val doneReps: Int = 0,
    val peakPower: Float = 0f,
    val avgPower: Float = 0f,
    /** True for a bodyweight exercise, which only the classic screen times. */
    val bodyweight: Boolean = false
)
