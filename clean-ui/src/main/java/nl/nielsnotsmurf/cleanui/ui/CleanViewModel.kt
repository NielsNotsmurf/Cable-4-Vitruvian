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
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxTest
import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.SetSpec
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
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
    val sets: List<SetSpec> = List(3) { SetSpec() },
    /** Timed sets: seconds per set, or null for rep-based sets. */
    val durationSeconds: Int? = null,
    /** "SINGLE" or "DOUBLE". */
    val cableConfig: String = if (exercise.cableConfig == "SINGLE") "SINGLE" else "DOUBLE",
    val eccentricLoadPercent: Int = 100,
    val echoLevel: EchoLevelChoice = EchoLevelChoice.HARDER,
    val progressionKg: Float = 0f,
    /** Loads a lifter set by hand; they win over the %-of-1RM value. */
    val overridesKg: Map<String, Float> = emptyMap(),
    /** Last load used per lifter, for lifters without a 1RM. */
    val lastUsedKg: Map<String, Float> = emptyMap(),
    /** Demo video and description, when the exercise has one. */
    val guide: ExerciseGuide? = null,
    val isStarting: Boolean = false,
    val error: String? = null
) {
    /** The % of 1RM a set uses: its own, or the exercise's. */
    fun percentFor(setIndex: Int): Int = sets.getOrNull(setIndex)?.percent ?: percent
}

enum class TestPhase { INTRO, ATTEMPT, RESULT }

/** The 1RM test in progress: one lifter working through [exerciseIds]. */
data class TestState(
    val lifterId: String,
    val exerciseIds: List<String>,
    val index: Int,
    val phase: TestPhase,
    val kg: Float,
    val startReason: OneRepMaxTest.StartReason,
    val attempts: List<Attempt> = emptyList(),
    /** When the rest before the next attempt ends (epoch ms); 0 means no rest running. */
    val restEndsAt: Long = 0L,
    val guide: ExerciseGuide? = null,
    val isStarting: Boolean = false,
    val error: String? = null
) {
    val exerciseId: String get() = exerciseIds[index]
    val isLast: Boolean get() = index >= exerciseIds.lastIndex
    val best: Float? get() = OneRepMaxTest.best(attempts)
}

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

    private val defaultReps: Int get() = gateway.defaultReps.coerceIn(1, 50)

    private val _setup = MutableStateFlow<SetupState?>(null)
    val setup: StateFlow<SetupState?> = _setup.asStateFlow()

    fun addLifter(name: String, colorArgb: Int) {
        if (name.isNotBlank()) gateway.addLifter(name.trim(), colorArgb)
    }

    fun deleteLifter(lifterId: String) {
        if (state.value.lifters.size > 1) gateway.deleteLifter(lifterId)
    }

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
        _setup.value = SetupState(
            exercise = exercise,
            sets = List(3) { SetSpec(reps = defaultReps) },
            durationSeconds = if (exercise.isBodyweight) 30 else null
        )
        viewModelScope.launch {
            // Start from what the first lifter did last time: mode, sets, reps, rest.
            val lifter = state.value.activeLifters.firstOrNull()?.id ?: return@launch
            val last = runCatching { gateway.lastPlan(lifter, exercise) }.getOrNull() ?: return@launch
            _setup.update {
                it?.copy(
                    mode = last.mode,
                    sets = last.sets.ifEmpty { it.sets },
                    durationSeconds = last.durationSeconds ?: if (exercise.isBodyweight) 30 else null,
                    cableConfig = last.cableConfig,
                    eccentricLoadPercent = last.eccentricLoadPercent,
                    echoLevel = last.echoLevel,
                    progressionKg = last.progressionKg
                )
            }
        }
        viewModelScope.launch {
            val guide = runCatching { gateway.exerciseGuide(exercise.id) }.getOrNull() ?: return@launch
            _setup.update { if (it?.exercise?.id == exercise.id) it.copy(guide = guide) else it }
        }
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

    /** The load each active lifter would start with (their first set). */
    fun loadFor(lifterId: String, setup: SetupState): Float = loadFor(lifterId, setup, 0)

    /**
     * A lifter's load for one set. A set with its own % scales from the lifter's base load,
     * so hand-set loads and lifters without a 1RM follow the same ratio.
     */
    fun loadFor(lifterId: String, setup: SetupState, setIndex: Int): Float {
        val pct = setup.percentFor(setIndex)
        val orm = state.value.oneRepMax(lifterId, setup.exercise.id)
        val base = setup.overridesKg[lifterId]
            ?: if (orm != null) return LoadCalculator.loadForPercent(orm.kg, pct)
            else setup.lastUsedKg[lifterId] ?: DEFAULT_LOAD_KG
        if (pct == setup.percent) return base
        return LoadCalculator.roundToStep(base * pct / setup.percent).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
    }

    fun toggleFavorite(exercise: ExerciseCard) {
        viewModelScope.launch { runCatching { gateway.toggleFavorite(exercise.id) } }
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
            loads = lifters.map { l -> LifterLoad(l.id, s.sets.indices.map { i -> loadFor(l.id, s, i) }) },
            cableConfig = s.cableConfig,
            durationSeconds = s.durationSeconds,
            eccentricLoadPercent = s.eccentricLoadPercent,
            echoLevel = s.echoLevel,
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

    // ---- 1RM test ----

    private val _test = MutableStateFlow<TestState?>(null)
    val test: StateFlow<TestState?> = _test.asStateFlow()

    /** Who was training before the test, restored when it closes (attempts are taken solo). */
    private var liftersBeforeTest: List<String>? = null

    /**
     * Opens the test for [lifterId] (default: first active lifter) on [exerciseIds]
     * (default: the anchor lifts, starting at the first one without a tested 1RM).
     */
    fun openTest(lifterId: String? = null, exerciseIds: List<String>? = null) {
        val s = state.value
        val lifter = lifterId ?: s.activeLifters.firstOrNull()?.id ?: s.lifters.firstOrNull()?.id ?: return
        val known = s.exercises.map { it.id }.toSet()
        val ids = exerciseIds ?: gateway.oneRepMaxTestExercises.map { it.exerciseId }.filter { it in known }
        if (ids.isEmpty()) return
        val index = if (exerciseIds == null) {
            ids.indexOfFirst { s.oneRepMax(lifter, it)?.source != OneRepMaxSource.BENCHMARK }.coerceAtLeast(0)
        } else 0
        liftersBeforeTest = s.activeLifters.map { it.id }
        _test.value = prepared(lifter, ids, index)
    }

    fun testSelectLifter(lifterId: String) {
        val t = _test.value ?: return
        _test.value = prepared(lifterId, t.exerciseIds, t.index)
    }

    fun testBegin() = updateTest { it.copy(phase = TestPhase.ATTEMPT) }

    fun testNudge(deltaKg: Float) = updateTest {
        it.copy(kg = LoadCalculator.roundToStep(it.kg + deltaKg).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG))
    }

    fun testRecord(outcome: AttemptOutcome) = updateTest {
        val attempts = it.attempts + Attempt(it.kg, outcome)
        val fromKnown = it.startReason == OneRepMaxTest.StartReason.KNOWN_ONE_REP_MAX
        if (outcome == AttemptOutcome.MISSED) {
            // After a miss with nothing made, suggest a lighter retry.
            val nextKg = if (OneRepMaxTest.best(attempts) == null) OneRepMaxTest.next(it.kg, outcome, fromKnown) else it.kg
            it.copy(attempts = attempts, kg = nextKg, phase = TestPhase.RESULT, restEndsAt = 0L)
        } else {
            it.copy(attempts = attempts, kg = OneRepMaxTest.next(it.kg, outcome, fromKnown), restEndsAt = restEnd())
        }
    }

    /** One more attempt, at [kg] (the midpoint) or at the suggested lighter weight. */
    fun testTryAgain(kg: Float? = null) = updateTest {
        it.copy(kg = kg ?: it.kg, phase = TestPhase.ATTEMPT, restEndsAt = restEnd())
    }

    /** Loads the current attempt on the trainer; [onStarted] shows the lifting screen. */
    fun testLiftOnTrainer(onStarted: () -> Unit) {
        val t = _test.value ?: return
        val exercise = state.value.exercises.firstOrNull { it.id == t.exerciseId } ?: return
        updateTest { it.copy(isStarting = true) }
        gateway.startSingleAttempt(
            t.lifterId, exercise, t.kg,
            onStarted = {
                updateTest { it.copy(isStarting = false) }
                onStarted()
            },
            onFailed = { msg -> updateTest { it.copy(isStarting = false, error = msg) } }
        )
    }

    /** Saves the best made rep as the lifter's 1RM. Returns true when the test is finished. */
    fun testSave(): Boolean {
        val t = _test.value ?: return true
        val best = t.best ?: return testSkip()
        viewModelScope.launch {
            store.saveOneRepMax(t.lifterId, t.exerciseId, OneRepMax(best, OneRepMaxSource.BENCHMARK, System.currentTimeMillis()))
        }
        return advanceTest()
    }

    /** Moves on without saving. Returns true when the test is finished. */
    fun testSkip(): Boolean = advanceTest()

    fun closeTest() {
        _test.value = null
        liftersBeforeTest?.let { if (it.isNotEmpty()) gateway.setActiveLifters(it) }
        liftersBeforeTest = null
    }

    private fun advanceTest(): Boolean {
        val t = _test.value ?: return true
        if (t.isLast) return true
        _test.value = prepared(t.lifterId, t.exerciseIds, t.index + 1)
        return false
    }

    private fun prepared(lifterId: String, ids: List<String>, index: Int): TestState {
        val exId = ids[index]
        val default = gateway.oneRepMaxTestExercises.firstOrNull { it.exerciseId == exId }?.defaultStartKg ?: DEFAULT_LOAD_KG
        val start = OneRepMaxTest.start(state.value.oneRepMax(lifterId, exId), default)
        viewModelScope.launch {
            val guide = runCatching { gateway.exerciseGuide(exId) }.getOrNull()
            _test.update { if (it?.exerciseId == exId) it.copy(guide = guide) else it }
        }
        return TestState(lifterId, ids, index, TestPhase.INTRO, start.kg, start.reason)
    }

    private fun updateTest(transform: (TestState) -> TestState) {
        _test.update { it?.copy(error = null)?.let(transform) }
    }

    private fun restEnd() = System.currentTimeMillis() + OneRepMaxTest.REST_SECONDS * 1000L

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
