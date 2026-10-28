package nl.nielsnotsmurf.cleanui.ui

import nl.nielsnotsmurf.cleanui.sound.CleanSounds
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import nl.nielsnotsmurf.cleanui.logic.PinLock
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxTest
import nl.nielsnotsmurf.cleanui.logic.WorkoutBuilder
import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
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
import nl.nielsnotsmurf.cleanui.theme.CleanColors
import nl.nielsnotsmurf.cleanui.theme.LifterColors
import nl.nielsnotsmurf.cleanui.platform.nowMillis

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

/** A workout built from muscle groups and a time budget, in equipment order. */
data class BuiltWorkout(
    val groups: List<String>,
    val minutes: Int,
    val exercises: List<ExerciseCard>,
    val percent: Int = 60,
    val sets: Int = 3,
    val reps: Int = 10,
    /** "lifterId::exerciseId" -> load set by hand on the plan screen. */
    val overridesKg: Map<String, Float> = emptyMap(),
    /** "lifterId::exerciseId" -> load used last time, for exercises without a 1RM. */
    val lastUsedKg: Map<String, Float> = emptyMap(),
    val isStarting: Boolean = false,
    val error: String? = null
)

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
    /** When the current attempt was sent to the trainer; 0 when none is waiting to be recorded. */
    val liftStartedAt: Long = 0L,
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
        relatedCache,
        gateway.exercises
    ) { lifters, stored, related, exercises ->
        lifters.associate { lifter ->
            val storedForLifter = stored[lifter.id].orEmpty()
            // A 1RM is only ever set by the 1RM test or by hand: normal sets never make or
            // overwrite one (one cable on a two-cable lift would look like a huge record).
            val exerciseIds = storedForLifter.keys +
                related.keys.filter { it.startsWith(lifter.id + KEY_SEP) }.map { it.substringAfter(KEY_SEP) }
            val resolved = exerciseIds.mapNotNull { exId ->
                LoadCalculator.resolveOneRepMax(
                    stored = storedForLifter[exId],
                    history = emptyList(),
                    relatedKg = related[relatedKey(lifter.id, exId)]
                )?.let { exId to it }
            }.toMap()
            // Every exercise without a real 1RM gets one scaled from the lifter's tested lifts.
            val real = resolved.filterValues { !it.isEstimate }
            val mapped = exercises.mapNotNull { ex ->
                if (ex.id in real) return@mapNotNull null
                store.oneRepMaxMap.estimate(ex.id, real)?.let { ex.id to OneRepMax(it, OneRepMaxSource.RELATED) }
            }
            lifter.id to resolved + mapped
        }
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<CleanUiState> = combine(
        uiLifters,
        gateway.exercises,
        gateway.connection,
        gateway.weightUnit,
        resolvedOneRepMaxes
    ) { (all, active), exercises, connection, unit, orms ->
        CleanUiState(all, active, exercises, connection, unit, orms)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanUiState())

    /** Finished workouts for the History tab, newest first. */
    val history: StateFlow<List<HistoryEntry>> =
        gateway.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteWorkouts(ids: List<String>) = gateway.deleteWorkouts(ids)

    val settings: StateFlow<CleanSettings> =
        gateway.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CleanSettings())

    fun updateSettings(transform: (CleanSettings) -> CleanSettings) = gateway.updateSettings(transform)

    /** Live heart rate of whoever lifts next, or null without a broadcasting sensor. */
    val heartRate: StateFlow<Int?> =
        gateway.liveHeartRate.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The LED scheme picked last, or -1 when none was picked yet. */
    val ledScheme: StateFlow<Int> = store.ledScheme
        .map { i -> i?.takeIf { it in gateway.ledSchemes.indices } ?: -1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    /** Menu and button sounds (Settings). */
    val uiSounds: StateFlow<Boolean> = store.uiSounds.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setUiSounds(on: Boolean) {
        viewModelScope.launch { store.setUiSounds(on) }
    }

    init {
        viewModelScope.launch { uiSounds.collect { CleanSounds.uiEnabled = it } }
        viewModelScope.launch { gateway.settings.collect { CleanSounds.workoutEnabled = it.beeps } }
        // The app's accent follows the LED colour.
        viewModelScope.launch {
            ledScheme.collect { i ->
                val argb = gateway.ledSchemes.getOrNull(i)?.colorsArgb?.firstOrNull()
                CleanColors.setAccent(argb?.let { Color(it) } ?: CleanColors.DefaultAccent)
            }
        }
    }

    fun setLedScheme(index: Int) {
        gateway.setLedScheme(index)
        viewModelScope.launch { store.setLedScheme(index) }
    }

    /** Hides demo videos when "Show exercise videos" is off, as the classic screens do. */
    private fun ExerciseGuide.respectVideoSetting(): ExerciseGuide =
        if (settings.value.videos) this else copy(videoUrl = null, thumbnailUrl = null)

    private val defaultReps: Int get() = gateway.defaultReps.coerceIn(1, 50)
    private val defaultSets: Int get() = settings.value.defaultSets.coerceIn(1, 10)
    private val defaultPercent: Int get() = settings.value.defaultPercent.coerceIn(30, 100)

    /** Sets and reps always come from Settings, never from what was done last time. */
    private fun defaultSetList(rest: Int = 90) = List(defaultSets) { SetSpec(reps = defaultReps, restSeconds = rest) }

    private val _setup = MutableStateFlow<SetupState?>(null)
    val setup: StateFlow<SetupState?> = _setup.asStateFlow()

    fun addLifter(name: String, colorArgb: Int) {
        if (name.isNotBlank()) gateway.addLifter(name.trim(), colorArgb)
    }

    fun deleteLifter(lifterId: String) {
        if (state.value.lifters.size <= 1) return
        gateway.deleteLifter(lifterId)
        if (session.value.signedIn == lifterId) signOut()
        viewModelScope.launch {
            store.setPinHash(lifterId, null)
            store.setTourStatus(lifterId, null)
        }
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
            sets = defaultSetList(),
            percent = defaultPercent,
            durationSeconds = if (exercise.isBodyweight) 30 else null
        )
        viewModelScope.launch {
            // Mode and cables follow what the first lifter did last time. Sets and reps don't:
            // they always come from Settings and only change on this screen.
            val lifter = state.value.activeLifters.firstOrNull()?.id ?: return@launch
            val last = runCatching { gateway.lastPlan(lifter, exercise) }.getOrNull() ?: return@launch
            _setup.update {
                it?.copy(
                    mode = last.mode,
                    durationSeconds = last.durationSeconds ?: if (exercise.isBodyweight) 30 else null,
                    cableConfig = last.cableConfig,
                    eccentricLoadPercent = last.eccentricLoadPercent,
                    echoLevel = last.echoLevel,
                    progressionKg = last.progressionKg
                )
            }
        }
        viewModelScope.launch {
            val guide = runCatching { gateway.exerciseGuide(exercise.id) }.getOrNull()?.respectVideoSetting() ?: return@launch
            _setup.update { if (it?.exercise?.id == exercise.id) it.copy(guide = guide) else it }
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
                startedLive(listOf(plan))
                onStarted()
            },
            onFailed = { msg -> _setup.update { it?.copy(isStarting = false, error = msg) } }
        )
    }

    // ---- Muscle-group workout ----

    private val _workout = MutableStateFlow<BuiltWorkout?>(null)
    val workout: StateFlow<BuiltWorkout?> = _workout.asStateFlow()

    /** Ranks exercises for the builder: real 1RMs first, then estimates, favourites and popular ones. */
    private fun builderScore(ex: ExerciseCard): Float {
        val s = state.value
        val orms = s.activeLifters.mapNotNull { s.oneRepMax(it.id, ex.id) }
        return orms.count { !it.isEstimate } * 10f + orms.count { it.isEstimate } * 3f +
            (if (ex.isFavorite) 4f else 0f) + (if (ex.lastPerformedAt != null) 1f else 0f) + ex.popularity
    }

    fun buildWorkout(groups: List<String>, minutes: Int) {
        val s = state.value
        val count = WorkoutBuilder.exerciseCount(minutes, s.activeLifters.size, groups.size, defaultSets)
        val exercises = WorkoutBuilder.build(s.exercises, groups, count, ::builderScore)
        _workout.value = BuiltWorkout(groups, minutes, exercises, percent = defaultPercent, sets = defaultSets, reps = defaultReps)
        exercises.forEach(::prepareWorkoutExercise)
    }

    /** Swaps [exercise] for one in the same group on different equipment. */
    fun substituteInWorkout(exercise: ExerciseCard) {
        val w = _workout.value ?: return
        val sub = WorkoutBuilder.substitute(exercise, w.exercises, state.value.exercises, ::builderScore)
        if (sub == null) {
            _workout.update { it?.copy(error = "No other ${exercise.region.lowercase()} exercise to swap in.") }
            return
        }
        _workout.update { it?.copy(exercises = WorkoutBuilder.swap(it.exercises, exercise, sub), error = null) }
        prepareWorkoutExercise(sub)
    }

    fun removeFromWorkout(exercise: ExerciseCard) =
        _workout.update { w -> w?.copy(exercises = w.exercises.filterNot { it.id == exercise.id }, error = null) }

    /** A new % replaces loads set by hand, as on the single-exercise screen. */
    fun setWorkoutPercent(percent: Int) = _workout.update { it?.copy(percent = percent.coerceIn(30, 100), overridesKg = emptyMap()) }

    fun setWorkoutSets(sets: Int) = _workout.update { it?.copy(sets = sets.coerceIn(1, 10)) }

    fun setWorkoutReps(reps: Int) = _workout.update { it?.copy(reps = reps.coerceIn(1, 50)) }

    /** Moves one lifter's load for one exercise by [steps] of [LoadCalculator.STEP_KG]. */
    fun nudgeWorkoutLoad(lifterId: String, exercise: ExerciseCard, steps: Int) = _workout.update { w ->
        w ?: return@update null
        val kg = (workoutLoad(lifterId, exercise, w) + steps * LoadCalculator.STEP_KG)
            .coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
        w.copy(overridesKg = w.overridesKg + (relatedKey(lifterId, exercise.id) to kg))
    }

    private fun prepareWorkoutExercise(exercise: ExerciseCard) {
        requestEstimates(exercise)
        viewModelScope.launch {
            val last = state.value.activeLifters.mapNotNull { l ->
                runCatching { gateway.lastUsedLoadKg(l.id, exercise) }.getOrNull()?.let { relatedKey(l.id, exercise.id) to it }
            }.toMap()
            _workout.update { it?.copy(lastUsedKg = it.lastUsedKg + last) }
        }
    }

    /** A lifter's load in the workout: % of their 1RM, else what they used last time. */
    fun workoutLoad(lifterId: String, exercise: ExerciseCard, w: BuiltWorkout): Float {
        w.overridesKg[relatedKey(lifterId, exercise.id)]?.let { return it }
        val orm = state.value.oneRepMax(lifterId, exercise.id)
        return if (orm != null) LoadCalculator.loadForPercent(orm.kg, w.percent)
        else w.lastUsedKg[relatedKey(lifterId, exercise.id)] ?: DEFAULT_LOAD_KG
    }

    fun startBuiltWorkout(onStarted: () -> Unit) {
        val w = _workout.value ?: return
        val lifters = state.value.activeLifters
        if (lifters.isEmpty() || w.exercises.isEmpty()) {
            _workout.update { it?.copy(error = if (lifters.isEmpty()) "Choose who's training first." else "Add an exercise first.") }
            return
        }
        val sets = List(w.sets) { SetSpec(reps = w.reps, restSeconds = WorkoutBuilder.REST_SECONDS) }
        val plans = w.exercises.map { ex ->
            SetPlan(
                exercise = ex,
                mode = TrainingMode.OLD_SCHOOL,
                percentOfOneRepMax = w.percent,
                sets = sets,
                loads = lifters.map { l -> LifterLoad(l.id, List(sets.size) { workoutLoad(l.id, ex, w) }) },
                cableConfig = if (ex.cableConfig == "SINGLE") "SINGLE" else "DOUBLE"
            )
        }
        _workout.update { it?.copy(isStarting = true, error = null) }
        gateway.startWorkout(
            name = w.groups.joinToString(" & "),
            plans = plans,
            onStarted = {
                _workout.update { it?.copy(isStarting = false) }
                startedLive(plans)
                onStarted()
            },
            onFailed = { msg -> _workout.update { it?.copy(isStarting = false, error = msg) } }
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

    fun testRecord(outcome: AttemptOutcome) {
        val t = _test.value ?: return
        viewModelScope.launch {
            // The measured peak of the set just lifted on the trainer, if it was lifted there.
            val measured = if (t.liftStartedAt == 0L) null else gateway.history.first()
                .filter { it.lifterId == t.lifterId && it.exerciseId == t.exerciseId && it.timestamp >= t.liftStartedAt }
                .mapNotNull { it.topKgPerCable }
                .maxOrNull()
            recordAttempt(outcome, measured)
        }
    }

    private fun recordAttempt(outcome: AttemptOutcome, measuredKg: Float?) = updateTest {
        val attempts = it.attempts + Attempt(it.kg, outcome, measuredKg)
        val fromKnown = it.startReason == OneRepMaxTest.StartReason.KNOWN_ONE_REP_MAX
        if (outcome == AttemptOutcome.MISSED) {
            // After a miss with nothing made, suggest a lighter retry.
            val nextKg = if (OneRepMaxTest.best(attempts) == null) OneRepMaxTest.next(it.kg, outcome, fromKnown) else it.kg
            it.copy(attempts = attempts, kg = nextKg, phase = TestPhase.RESULT, restEndsAt = 0L, liftStartedAt = 0L)
        } else {
            it.copy(attempts = attempts, kg = OneRepMaxTest.next(it.kg, outcome, fromKnown), restEndsAt = restEnd(), liftStartedAt = 0L)
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
        updateTest { it.copy(isStarting = true, liftStartedAt = nowMillis()) }
        gateway.startSingleAttempt(
            t.lifterId, exercise, t.kg,
            onStarted = {
                updateTest { it.copy(isStarting = false) }
                startedLive(emptyList())
                onStarted()
            },
            onFailed = { msg -> updateTest { it.copy(isStarting = false, error = msg) } }
        )
    }

    /** Saves the 1RM estimated from the best made 3-rep set (its measured peak). Returns true when the test is finished. */
    fun testSave(): Boolean {
        val t = _test.value ?: return true
        val best = t.best ?: return testSkip()
        viewModelScope.launch {
            store.saveOneRepMax(t.lifterId, t.exerciseId, OneRepMax(OneRepMaxTest.oneRepMaxFromTest(best), OneRepMaxSource.BENCHMARK, nowMillis()))
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
            val guide = runCatching { gateway.exerciseGuide(exId) }.getOrNull()?.respectVideoSetting()
            _test.update { if (it?.exerciseId == exId) it.copy(guide = guide) else it }
        }
        return TestState(lifterId, ids, index, TestPhase.INTRO, start.kg, start.reason)
    }

    private fun updateTest(transform: (TestState) -> TestState) {
        _test.update { it?.copy(error = null)?.let(transform) }
    }

    private fun restEnd() = nowMillis() + OneRepMaxTest.REST_SECONDS * 1000L

    fun switchToClassic() = gateway.switchToClassicUi()

    /** Sets [lifterId]'s 1RM for [exerciseId] by hand (kg per cable); null or 0 clears it. */
    fun setManualOneRepMax(lifterId: String, exerciseId: String, kg: Float?) {
        viewModelScope.launch {
            if (kg == null || kg <= 0f) store.clearOneRepMax(lifterId, exerciseId)
            else store.saveOneRepMax(
                lifterId, exerciseId,
                OneRepMax(LoadCalculator.roundToStep(kg).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG), OneRepMaxSource.MANUAL, nowMillis())
            )
        }
    }

    // ---- Training % during the workout ----

    private var livePlans: List<SetPlan> = emptyList()
    private val _livePercent = MutableStateFlow<Int?>(null)
    /** % of 1RM of the running workout; null when none was started from here (or for a 1RM attempt). */
    val livePercent: StateFlow<Int?> = _livePercent.asStateFlow()

    private fun startedLive(plans: List<SetPlan>) {
        livePlans = plans
        _livePercent.value = plans.firstOrNull()?.percentOfOneRepMax
    }

    /**
     * Changes the training % for the rest of the workout: every lifter's coming sets get that
     * % of their 1RM. Lifters without a 1RM keep their load scaled by the same ratio.
     */
    fun setLivePercent(percent: Int) {
        val p = percent.coerceIn(30, 100)
        if (livePlans.isEmpty()) return
        _livePercent.value = p
        livePlans.forEach { plan ->
            val loads = plan.loads.associate { load ->
                val orm = state.value.oneRepMax(load.lifterId, plan.exercise.id)
                val kg = if (orm != null) LoadCalculator.loadForPercent(orm.kg, p)
                else LoadCalculator.roundToStep(load.weightPerCableKg * p / plan.percentOfOneRepMax.coerceAtLeast(1))
                    .coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
                load.lifterId to List(plan.sets.size) { kg }
            }
            gateway.setUpcomingLoads(plan.exercise.id, loads)
        }
    }

    // ---- Accounts ----

    private data class Session(
        val signedIn: String? = null,
        val unlocked: Set<String> = emptySet(),
        val wrongTries: Int = 0,
        val lockedUntil: Long = 0L
    )

    private val session = MutableStateFlow(Session())
    private val sessionRestored = MutableStateFlow(false)

    /** Accounts, PINs and tours; null until storage has been read. */
    val account: StateFlow<AccountState?> = combine(
        gateway.lifters,
        store.onboarded,
        store.pinHashes,
        store.tourStatus,
        combine(session, sessionRestored, store.lastAccount) { s, restored, last -> Triple(s, restored, last) }
    ) { lifters, onboarded, pins, tours, (s, restored, last) ->
        val ids = lifters.map { it.id }.toSet()
        if (!restored) null else AccountState(
            onboarded = onboarded,
            signedInId = s.signedIn?.takeIf { it in ids },
            lastAccountId = last?.takeIf { it in ids },
            protectedIds = pins.keys.filter { it in ids }.toSet(),
            unlockedIds = s.unlocked,
            tours = tours.mapValues { TourStatus.parse(it.value) }
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** History without the workouts of PIN-protected profiles that aren't unlocked. */
    val visibleHistory: StateFlow<List<HistoryEntry>> = combine(history, account) { entries, a ->
        if (a == null) emptyList() else entries.filter { a.canSee(it.lifterId) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Open as whoever used the app last, unless their account has a PIN.
        viewModelScope.launch {
            val last = store.lastAccount.first()
            val pins = store.pinHashes.first()
            if (store.onboarded.first() && last != null && last !in pins) {
                session.value = Session(signedIn = last, unlocked = setOf(last))
            }
            sessionRestored.value = true
        }
    }

    /** True when [lifterId] has a PIN that hasn't been typed on this phone since the app opened. */
    fun needsPin(lifterId: String): Boolean = account.value?.needsPin(lifterId) ?: false

    /**
     * Checks [pin] for [lifterId]. Right: the profile is unlocked until the app closes or
     * someone switches account, then [onOk] runs. Wrong: [onWrong] gets the seconds to wait
     * before the next try (0 for the first few).
     */
    fun checkPin(lifterId: String, pin: String, onOk: () -> Unit, onWrong: (waitSeconds: Int) -> Unit) {
        val waitMs = session.value.lockedUntil - nowMillis()
        if (waitMs > 0) {
            onWrong(((waitMs + 999) / 1000).toInt())
            return
        }
        viewModelScope.launch {
            val stored = store.pinHashes.first()[lifterId]
            val ok = stored == null || withContext(Dispatchers.Default) { PinLock.verify(pin, stored) }
            if (ok) {
                session.update { it.copy(unlocked = it.unlocked + lifterId, wrongTries = 0, lockedUntil = 0L) }
                onOk()
            } else {
                val tries = session.value.wrongTries + 1
                val wait = PinLock.lockoutSeconds(tries)
                session.update {
                    it.copy(wrongTries = tries, lockedUntil = if (wait > 0) nowMillis() + wait * 1000L else 0L)
                }
                onWrong(wait)
            }
        }
    }

    /** Signs in as [lifterId]. Callers check the PIN first. */
    fun signIn(lifterId: String) {
        session.update { it.copy(signedIn = lifterId, unlocked = it.unlocked + lifterId) }
        viewModelScope.launch { store.setLastAccount(lifterId) }
    }

    /** Locks the app: back to the account picker, and every PIN-protected profile hidden again. */
    fun signOut() {
        session.update { Session(wrongTries = it.wrongTries, lockedUntil = it.lockedUntil) }
    }

    fun setPin(lifterId: String, pin: String) {
        if (!PinLock.isValid(pin)) return
        session.update { it.copy(unlocked = it.unlocked + lifterId) }
        viewModelScope.launch {
            val hash = withContext(Dispatchers.Default) { PinLock.hash(pin) }
            store.setPinHash(lifterId, hash)
        }
    }

    fun removePin(lifterId: String) {
        viewModelScope.launch { store.setPinHash(lifterId, null) }
    }

    /** Adds a profile and hands back its id once it exists. */
    fun createAccount(name: String, colorArgb: Int, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val before = gateway.lifters.first().map { it.id }.toSet()
            gateway.addLifter(name.trim(), colorArgb)
            val created = withTimeoutOrNull(5_000) {
                gateway.lifters.first { list -> list.any { it.id !in before } }
            }?.firstOrNull { it.id !in before } ?: return@launch
            onCreated(created.id)
        }
    }

    /** Ends onboarding signed in as [lifterId], who trains solo to start with. */
    fun finishOnboarding(lifterId: String) {
        signIn(lifterId)
        gateway.setActiveLifters(listOf(lifterId))
        viewModelScope.launch { store.setOnboarded(true) }
    }

    /** Records that [lifterId] finished ([done]) or skipped the app tour. */
    fun setTourStatus(lifterId: String, done: Boolean) {
        viewModelScope.launch { store.setTourStatus(lifterId, TourStatus.encode(done, nowMillis())) }
    }

    private fun relatedKey(lifterId: String, exerciseId: String) = "$lifterId$KEY_SEP$exerciseId"

    companion object {
        const val MAX_LIFTERS = 4
        private const val DEFAULT_LOAD_KG = 10f
        private const val KEY_SEP = "::"

        fun factory(gateway: TrainerGateway, store: CleanUiStore): ViewModelProvider.Factory =
            viewModelFactory { initializer { CleanViewModel(gateway, store) } }
    }
}
