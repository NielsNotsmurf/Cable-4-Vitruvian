package nl.nielsnotsmurf.cleanui.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.LifterLoad
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.theme.LifterColors

/** A lifter plus the colour the Clean UI draws them in. */
data class UiLifter(val lifter: Lifter, val color: Color) {
    val id: String get() = lifter.id
    val name: String get() = lifter.name
}

data class CleanUiState(
    val lifters: List<UiLifter> = emptyList(),
    val activeLifters: List<UiLifter> = emptyList(),
    val exercises: List<ExerciseCard> = emptyList(),
    val connection: TrainerConnection = TrainerConnection.Disconnected,
    val unit: WeightUnit = WeightUnit.KG,
    /** lifterId -> exerciseId -> resolved 1RM (stored, history or related). */
    val oneRepMaxes: Map<String, Map<String, OneRepMax>> = emptyMap()
) {
    fun oneRepMax(lifterId: String, exerciseId: String): OneRepMax? = oneRepMaxes[lifterId]?.get(exerciseId)

    val recentExercises: List<ExerciseCard>
        get() = exercises.filter { it.lastPerformedAt != null }
            .sortedByDescending { it.lastPerformedAt }
            .take(5)
}

data class SetupState(
    val exercise: ExerciseCard,
    val mode: TrainingMode = TrainingMode.OLD_SCHOOL,
    val percent: Int = 75,
    val sets: Int = 3,
    val reps: Int = 8,
    val restSeconds: Int = 90,
    val eccentricLoadPercent: Int = 100,
    val progressionKg: Float = 0f,
    /** Loads a lifter set by hand; they win over the %-of-1RM value. */
    val overridesKg: Map<String, Float> = emptyMap(),
    /** Last load used per lifter, for lifters without a 1RM. */
    val lastUsedKg: Map<String, Float> = emptyMap(),
    val isStarting: Boolean = false,
    val error: String? = null
)

class CleanViewModel(
    private val gateway: TrainerGateway,
    private val store: CleanUiStore
) : ViewModel() {

    private val relatedCache = MutableStateFlow<Map<String, Float?>>(emptyMap())

    private val uiLifters = combine(gateway.lifters, gateway.activeLifterIds) { all, activeIds ->
        val decorated = all.mapIndexed { i, l -> UiLifter(l, LifterColors.forLifter(l.colorArgb, i)) }
        val byId = decorated.associateBy { it.id }
        decorated to activeIds.mapNotNull { byId[it] }
    }

    private val resolvedOneRepMaxes = combine(
        gateway.lifters,
        store.storedOneRepMaxes,
        gateway.bestSetsByLifter,
        relatedCache
    ) { lifters, stored, bestSets, related ->
        lifters.associate { lifter ->
            val storedForLifter = stored[lifter.id].orEmpty()
            val historyByExercise: Map<String, List<LiftRecord>> =
                bestSets[lifter.id].orEmpty().groupBy { it.exerciseId }
            val exerciseIds = storedForLifter.keys + historyByExercise.keys +
                related.keys.filter { it.startsWith(lifter.id + KEY_SEP) }.map { it.substringAfter(KEY_SEP) }
            lifter.id to exerciseIds.mapNotNull { exId ->
                LoadCalculator.resolveOneRepMax(
                    stored = storedForLifter[exId],
                    history = historyByExercise[exId].orEmpty(),
                    relatedKg = related[relatedKey(lifter.id, exId)]
                )?.let { exId to it }
            }.toMap()
        }
    }

    val state: StateFlow<CleanUiState> = combine(
        uiLifters,
        gateway.exercises,
        gateway.connection,
        gateway.weightUnit,
        resolvedOneRepMaxes
    ) { (all, active), exercises, connection, unit, orms ->
        CleanUiState(all, active, exercises, connection, unit, orms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanUiState())

    private val _setup = MutableStateFlow<SetupState?>(null)
    val setup: StateFlow<SetupState?> = _setup.asStateFlow()

    fun toggleLifter(lifterId: String) {
        val current = state.value.activeLifters.map { it.id }
        val next = if (lifterId in current) current - lifterId else current + lifterId
        // Someone always has to be training.
        if (next.isNotEmpty() && next.size <= MAX_LIFTERS) gateway.setActiveLifters(next)
    }

    /**
     * Looks up related-exercise estimates for lifters with no 1RM for [exercise].
     * Called as rows scroll into view; each pair is only asked once.
     */
    fun requestEstimates(exercise: ExerciseCard) {
        val s = state.value
        s.activeLifters.forEach { l ->
            val key = relatedKey(l.id, exercise.id)
            if (s.oneRepMax(l.id, exercise.id) != null || relatedCache.value.containsKey(key)) return@forEach
            relatedCache.update { it + (key to null) }
            viewModelScope.launch {
                val kg = runCatching { gateway.relatedOneRepMaxKg(l.id, exercise) }.getOrNull()
                if (kg != null) relatedCache.update { it + (key to kg) }
            }
        }
    }

    fun openSetup(exercise: ExerciseCard) {
        requestEstimates(exercise)
        _setup.value = SetupState(exercise = exercise)
        viewModelScope.launch {
            val pct = store.lastPercent(exercise.id).first()
            if (pct != null) _setup.update { it?.copy(percent = pct) }
        }
        viewModelScope.launch {
            val last = state.value.activeLifters.mapNotNull { l ->
                gateway.lastUsedLoadKg(l.id, exercise)?.let { l.id to it }
            }.toMap()
            _setup.update { it?.copy(lastUsedKg = last) }
        }
    }

    fun updateSetup(transform: (SetupState) -> SetupState) {
        _setup.update { it?.let(transform)?.copy(error = null) }
    }

    fun setPercent(percent: Int) = updateSetup {
        // A new % replaces hand-set loads for lifters who have a 1RM.
        val keep = it.overridesKg.filterKeys { id -> state.value.oneRepMax(id, it.exercise.id) == null }
        it.copy(percent = percent.coerceIn(30, 100), overridesKg = keep)
    }

    /** The load each active lifter would start with. */
    fun loadFor(lifterId: String, setup: SetupState): Float {
        setup.overridesKg[lifterId]?.let { return it }
        val orm = state.value.oneRepMax(lifterId, setup.exercise.id)
        if (orm != null) return LoadCalculator.loadForPercent(orm.kg, setup.percent)
        return setup.lastUsedKg[lifterId] ?: DEFAULT_LOAD_KG
    }

    fun nudgeLoad(lifterId: String, deltaKg: Float) = updateSetup {
        val now = loadFor(lifterId, it)
        val next = LoadCalculator.roundToStep(now + deltaKg).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
        it.copy(overridesKg = it.overridesKg + (lifterId to next))
    }

    fun start(onStarted: () -> Unit) {
        val s = _setup.value ?: return
        val lifters = state.value.activeLifters
        if (lifters.isEmpty()) {
            updateSetup { it.copy(error = "Choose who's training first.") }
            return
        }
        val plan = SetPlan(
            exercise = s.exercise,
            mode = s.mode,
            percentOfOneRepMax = s.percent,
            sets = s.sets,
            reps = s.reps,
            restSeconds = s.restSeconds,
            loads = lifters.map { LifterLoad(it.id, loadFor(it.id, s)) },
            eccentricLoadPercent = s.eccentricLoadPercent,
            progressionKg = s.progressionKg
        )
        _setup.update { it?.copy(isStarting = true) }
        viewModelScope.launch { store.setLastPercent(s.exercise.id, s.percent) }
        gateway.startExercise(
            plan,
            onStarted = {
                _setup.update { it?.copy(isStarting = false) }
                onStarted()
            },
            onFailed = { msg -> _setup.update { it?.copy(isStarting = false, error = msg) } }
        )
    }

    fun switchToClassic() = gateway.switchToClassicUi()

    private fun relatedKey(lifterId: String, exerciseId: String) = "$lifterId$KEY_SEP$exerciseId"

    companion object {
        const val MAX_LIFTERS = 4
        private const val DEFAULT_LOAD_KG = 10f
        private const val KEY_SEP = "::"

        fun factory(gateway: TrainerGateway, store: CleanUiStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { CleanViewModel(gateway, store) } }
    }
}
