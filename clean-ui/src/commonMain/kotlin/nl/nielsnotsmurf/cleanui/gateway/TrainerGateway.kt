package nl.nielsnotsmurf.cleanui.gateway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.LedScheme
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.LastPlan
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.TestAnchor
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.WeightUnit

/**
 * The only door between the Clean UI and the rest of the app.
 *
 * :app implements this on top of the existing BLE, profile, history and baseline code.
 * When the upstream code changes, only that implementation needs to follow.
 */
interface TrainerGateway {

    /** All local profiles. */
    val lifters: Flow<List<Lifter>>

    /** Profiles training right now, in rotation order. Never empty once profiles exist. */
    val activeLifterIds: Flow<List<String>>

    /** Choose who's training. Sets rotate through [lifterIds] in order. */
    fun setActiveLifters(lifterIds: List<String>)

    /** Creates a profile. */
    fun addLifter(name: String, colorArgb: Int)

    /** Deletes a profile (never the last one). Their workout history stays. */
    fun deleteLifter(lifterId: String)

    val connection: Flow<TrainerConnection>

    /**
     * Live heart rate of whoever lifts next (the rotation's performer, else the active profile),
     * or null when that person has no sensor broadcasting.
     */
    val liveHeartRate: Flow<Int?> get() = flowOf(null)

    val weightUnit: Flow<WeightUnit>

    /** The exercise library. */
    val exercises: Flow<List<ExerciseCard>>

    /**
     * Each lifter's best completed sets, one per exercise, used to estimate a 1RM.
     * Keyed by lifter id.
     */
    val bestSetsByLifter: Flow<Map<String, List<LiftRecord>>>

    /**
     * A 1RM estimate for an exercise the lifter has never done, scaled from their
     * benchmark of a related exercise. Returns null when there is nothing to scale from.
     */
    suspend fun relatedOneRepMaxKg(lifterId: String, exercise: ExerciseCard): Float?

    /** Reps per set for an exercise the lifter has never configured (the app's Default reps setting). */
    val defaultReps: Int

    /** Mode, sets and rest this lifter used last time on [exercise], or null if never. */
    suspend fun lastPlan(lifterId: String, exercise: ExerciseCard): LastPlan?

    suspend fun toggleFavorite(exerciseId: String)

    /** The load this lifter used last time on [exercise], or null if they never did it. */
    suspend fun lastUsedLoadKg(lifterId: String, exercise: ExerciseCard): Float?

    /**
     * Configure and start the planned exercise. [onStarted] runs once the trainer is
     * connected and the first set is armed; the host then shows the lifting screen.
     */
    fun startExercise(plan: SetPlan, onStarted: () -> Unit, onFailed: (String) -> Unit)

    /**
     * Start several exercises back to back as one workout, in the order given. Loads per
     * lifter work as in [startExercise]. Hosts without routines start only the first.
     */
    fun startWorkout(name: String, plans: List<SetPlan>, onStarted: () -> Unit, onFailed: (String) -> Unit) =
        startExercise(plans.first(), onStarted, onFailed)

    /** Live state of the set on the trainer, or null when the host has none to share. */
    val liveWorkout: Flow<LiveWorkout?> get() = flowOf(null)

    /** A short line per personal record set during the workout, e.g. "Bench press · 40 kg × 8". */
    val personalRecords: Flow<String> get() = emptyFlow()

    /** Controls for the set on the trainer, used by the Clean lifting screen. */
    fun skipRest() {}
    /** Changes the load per cable while the set gets ready, counts down or does its warm-up reps. */
    fun adjustLiveWeight(perCableKg: Float) {}
    fun proceedFromSummary() {}
    fun stopWorkout() {}
    fun advanceToNextExercise() {}
    fun resetForNewWorkout() {}
    /** Ends the set being lifted now with the reps done so far and moves on as usual. */
    fun finishSetEarly() {}
    /** Starts the set being lifted over, so the warm-up reps set the range of motion again. */
    fun restartSet() {}
    /** During rest: the coming set moves [delta] sets forward (skip) or back (redo), across exercises. */
    fun moveSet(delta: Int) {}
    /** During rest: the coming set becomes the first set of the next (1) or previous (-1) exercise. */
    fun moveExercise(delta: Int) {}
    /** Loads per cable for the coming sets of [exerciseId], per lifter id, in set order. */
    fun setUpcomingLoads(exerciseId: String, loadsByLifter: Map<String, List<Float>>) {}

    /** Exercises of the 1RM test, in order: the main lift for each muscle group. */
    val oneRepMaxTestExercises: List<TestAnchor>

    /** How to do [exerciseId]: its description and demo video, or null if unknown. */
    suspend fun exerciseGuide(exerciseId: String): ExerciseGuide?

    /**
     * Load [kg] per cable for one strength-test attempt (3 reps) by [lifterId] alone. Unlike
     * [startExercise] this saves no per-exercise defaults. It does make [lifterId] the only
     * one training; the caller puts the previous lifters back with [setActiveLifters].
     */
    fun startSingleAttempt(
        lifterId: String,
        exercise: ExerciseCard,
        kg: Float,
        onStarted: () -> Unit,
        onFailed: (String) -> Unit
    )

    /** Finished workouts for every profile, newest first. */
    val history: Flow<List<HistoryEntry>>

    /** Deletes workouts by [HistoryEntry.id]. */
    fun deleteWorkouts(ids: List<String>)

    val settings: Flow<CleanSettings>

    /** Saves the settings that differ from the current ones. */
    fun updateSettings(transform: (CleanSettings) -> CleanSettings)

    val ledSchemes: List<LedScheme>

    /** Sends an LED colour scheme to the trainer. */
    fun setLedScheme(index: Int)

    /** Hand back to the classic UI (for screens the Clean UI doesn't have yet). */
    fun switchToClassicUi()
}
