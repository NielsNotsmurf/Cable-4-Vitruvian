package nl.nielsnotsmurf.cleanui.gateway

import kotlinx.coroutines.flow.Flow
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.WeightUnit

/**
 * The only door between the Clean UI and the rest of the app.
 *
 * :app implements this on top of the existing BLE, profile, history and baseline code.
 * When upstream Phoenix changes, only that implementation needs to follow.
 */
interface TrainerGateway {

    /** All local profiles. */
    val lifters: Flow<List<Lifter>>

    /** Profiles training right now, in rotation order. Never empty once profiles exist. */
    val activeLifterIds: Flow<List<String>>

    /** Choose who's training. Sets rotate through [lifterIds] in order. */
    fun setActiveLifters(lifterIds: List<String>)

    val connection: Flow<TrainerConnection>

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

    /** The load this lifter used last time on [exercise], or null if they never did it. */
    suspend fun lastUsedLoadKg(lifterId: String, exercise: ExerciseCard): Float?

    /**
     * Configure and start the planned exercise. [onStarted] runs once the trainer is
     * connected and the first set is armed; the host then shows the lifting screen.
     */
    fun startExercise(plan: SetPlan, onStarted: () -> Unit, onFailed: (String) -> Unit)

    /** Hand back to the classic UI (for screens the Clean UI doesn't have yet). */
    fun switchToClassicUi()
}
