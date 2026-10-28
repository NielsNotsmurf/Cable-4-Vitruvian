package nl.nielsnotsmurf.webapp

import com.example.vitruvianredux.data.baseline.AnchorExerciseConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.LastPlan
import nl.nielsnotsmurf.cleanui.model.LedScheme
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.LivePhase
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import nl.nielsnotsmurf.cleanui.model.SetKind
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.SetSpec
import nl.nielsnotsmurf.cleanui.model.TestAnchor
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import nl.nielsnotsmurf.cleanui.platform.nowMillis
import kotlin.math.PI
import kotlin.math.sin

/**
 * The browser's TrainerGateway. Profiles, history and settings live in localStorage, the
 * exercise library is the one the app bundles, and the trainer is simulated until the Web
 * Bluetooth connection lands (docs/WEB_TRACK.md, phase 2). Every set behaves like a real
 * one: countdown, reps, set done, rest, next lifter.
 */
class WebTrainerGateway(private val library: ExerciseLibrary) : TrainerGateway {
    private val scope: CoroutineScope = MainScope()
    private val data = MutableStateFlow(load())

    private fun update(change: (Saved) -> Saved) {
        data.value = change(data.value)
        LocalStorage[STORAGE_KEY] = json.encodeToString(Saved.serializer(), data.value)
    }

    // Profiles

    override val lifters: Flow<List<Lifter>> = data.map { d -> d.lifters.map { Lifter(it.id, it.name, it.color) } }

    override val activeLifterIds: Flow<List<String>> = data.map { d ->
        d.active.filter { id -> d.lifters.any { it.id == id } }.ifEmpty { d.lifters.take(1).map { it.id } }
    }

    override fun setActiveLifters(lifterIds: List<String>) = update { it.copy(active = lifterIds) }

    override fun addLifter(name: String, colorArgb: Int) = update {
        it.copy(lifters = it.lifters + SavedLifter(randomId(), name, colorArgb))
    }

    override fun deleteLifter(lifterId: String) = update { d ->
        if (d.lifters.size <= 1) d else d.copy(lifters = d.lifters.filterNot { it.id == lifterId }, active = d.active - lifterId)
    }

    // Trainer

    private val connectionState = MutableStateFlow<TrainerConnection>(TrainerConnection.Connected(SIMULATED))
    override val connection: Flow<TrainerConnection> = connectionState

    override val weightUnit: Flow<WeightUnit> = data.map { it.settings.unit() }

    // Exercises

    override val exercises: Flow<List<ExerciseCard>> = data.map { d ->
        val last = d.history.groupBy { it.exerciseId }.mapValues { (_, l) -> l.maxOf { it.timestamp } }
        library.cards.map { it.copy(isFavorite = it.id in d.favorites, lastPerformedAt = last[it.id]) }
    }

    override val bestSetsByLifter: Flow<Map<String, List<LiftRecord>>> = data.map { d ->
        d.history.filter { it.topKg != null && it.reps > 0 }.groupBy { it.lifterId }.mapValues { (_, list) ->
            list.groupBy { it.exerciseId }.map { (_, sets) ->
                val best = sets.maxBy { it.topKg!! }
                LiftRecord(best.exerciseId, best.topKg!!, (best.reps / best.sets.coerceAtLeast(1)).coerceAtLeast(1), best.timestamp)
            }
        }
    }

    override suspend fun relatedOneRepMaxKg(lifterId: String, exercise: ExerciseCard): Float? = null

    override val defaultReps: Int get() = data.value.settings.defaultReps

    override suspend fun lastPlan(lifterId: String, exercise: ExerciseCard): LastPlan? =
        data.value.plans["$lifterId|${exercise.id}"]?.toLastPlan()

    override suspend fun toggleFavorite(exerciseId: String) = update {
        it.copy(favorites = if (exerciseId in it.favorites) it.favorites - exerciseId else it.favorites + exerciseId)
    }

    override suspend fun lastUsedLoadKg(lifterId: String, exercise: ExerciseCard): Float? =
        data.value.loads["$lifterId|${exercise.id}"]

    override val oneRepMaxTestExercises: List<TestAnchor> =
        AnchorExerciseConfig.ANCHORS.map { TestAnchor(it.exerciseId, it.conservativeDefaultKg) }

    override suspend fun exerciseGuide(exerciseId: String): ExerciseGuide? = library.guide(exerciseId)

    // History and settings

    override val history: Flow<List<HistoryEntry>> = data.map { d ->
        d.history.sortedByDescending { it.timestamp }.map { it.toEntry() }
    }

    override fun deleteWorkouts(ids: List<String>) = update { d -> d.copy(history = d.history.filterNot { it.id in ids }) }

    override val settings: Flow<CleanSettings> = data.map { it.settings.toClean() }

    override fun updateSettings(transform: (CleanSettings) -> CleanSettings) = update {
        it.copy(settings = SavedSettings.from(transform(it.settings.toClean())))
    }

    override val ledSchemes: List<LedScheme> = listOf(
        LedScheme("Orange", listOf(0xFFFF7A00.toInt())),
        LedScheme("Blue", listOf(0xFF2563EB.toInt())),
        LedScheme("Green", listOf(0xFF16A34A.toInt())),
        LedScheme("Purple", listOf(0xFF9333EA.toInt())),
        LedScheme("Red", listOf(0xFFDC2626.toInt()))
    )

    override fun setLedScheme(index: Int) {}

    override fun switchToClassicUi() {}

    // The simulated set

    private val live = MutableStateFlow<LiveWorkout?>(null)
    override val liveWorkout: Flow<LiveWorkout?> = live
    private val records = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val personalRecords: Flow<String> = records
    private var job: Job? = null
    private var proceed: CompletableDeferred<Unit>? = null
    private var endSet: CompletableDeferred<Unit>? = null
    private var skip: CompletableDeferred<Unit>? = null

    override fun startExercise(plan: SetPlan, onStarted: () -> Unit, onFailed: (String) -> Unit) =
        startWorkout(plan.exercise.name, listOf(plan), onStarted, onFailed)

    override fun startWorkout(name: String, plans: List<SetPlan>, onStarted: () -> Unit, onFailed: (String) -> Unit) {
        if (plans.isEmpty()) return onFailed("Nothing to train")
        plans.forEach { remember(it) }
        run(plans, attempt = false)
        onStarted()
    }

    override fun startSingleAttempt(lifterId: String, exercise: ExerciseCard, kg: Float, onStarted: () -> Unit, onFailed: (String) -> Unit) {
        setActiveLifters(listOf(lifterId))
        val plan = SetPlan(
            exercise = exercise, mode = TrainingMode.OLD_SCHOOL, percentOfOneRepMax = 100,
            sets = listOf(SetSpec(reps = 3, restSeconds = 0)),
            loads = listOf(nl.nielsnotsmurf.cleanui.model.LifterLoad(lifterId, listOf(kg)))
        )
        run(listOf(plan), attempt = true)
        onStarted()
    }

    private fun run(plans: List<SetPlan>, attempt: Boolean) {
        job?.cancel()
        val lifters = data.value.lifters.associateBy { it.id }
        job = scope.launch {
            val groupId = randomId()
            plans.forEachIndexed { p, plan ->
                val started = nowMillis()
                val done = mutableMapOf<String, Pair<Int, Float>>() // lifter -> reps, top kg
                val order = plan.loads.map { it.lifterId }.ifEmpty { data.value.active }
                plan.sets.forEachIndexed { s, spec ->
                    order.forEachIndexed { o, lifterId ->
                        val lifter = lifters[lifterId]
                        val kg = plan.loads.firstOrNull { it.lifterId == lifterId }?.setWeightsKg?.let { it.getOrNull(s) ?: it.lastOrNull() } ?: 0f
                        val base = LiveWorkout(
                            phase = LivePhase.COUNTDOWN, exerciseName = plan.exercise.name,
                            targetReps = if (spec.kind == SetKind.AMRAP) null else spec.reps,
                            loadKg = kg, liveLoadKg = kg, setNumber = s + 1, totalSets = plan.sets.size,
                            isLastExercise = p == plans.lastIndex, lifterId = lifterId,
                            lifterName = lifter?.name, lifterColorArgb = lifter?.color,
                            nextName = plans.getOrNull(p + 1)?.exercise?.name.orEmpty()
                        )
                        for (c in 3 downTo 1) { live.value = base.copy(countdown = c); delay(1000) }
                        val reps = lift(base, spec)
                        val prev = done[lifterId]
                        done[lifterId] = ((prev?.first ?: 0) + reps) to maxOf(prev?.second ?: 0f, live.value?.loadKg ?: kg)
                        if (attempt) { live.value = null; return@launch }
                        val lastSet = s == plan.sets.lastIndex && o == order.lastIndex
                        if (!lastSet) {
                            live.value = live.value?.copy(phase = LivePhase.SET_DONE, doneReps = reps)
                            proceed = CompletableDeferred<Unit>().also { it.await() }
                            rest(base, spec.restSeconds)
                        }
                    }
                }
                done.forEach { (lifterId, result) ->
                    record(groupId, lifterId, plan, result.first, result.second, nowMillis() - started)
                }
                if (p < plans.lastIndex) rest(live.value ?: return@forEachIndexed, plan.sets.last().restSeconds)
            }
            live.value = live.value?.copy(phase = LivePhase.FINISHED)
        }
    }

    /** Reps at a steady tempo; the handles go up and down with them. */
    private suspend fun lift(base: LiveWorkout, spec: SetSpec): Int {
        val target = if (spec.kind == SetKind.AMRAP) 12 else spec.reps
        val end = CompletableDeferred<Unit>().also { endSet = it }
        var reps = 0
        live.value = base.copy(phase = LivePhase.LIFTING)
        val start = nowMillis()
        while (reps < target && !end.isCompleted) {
            delay(80)
            val t = (nowMillis() - start) / REP_MS.toDouble()
            reps = t.toInt().coerceAtMost(target)
            val position = ((1 - kotlin.math.cos(2 * PI * t)) / 2).toFloat()
            val power = (base.loadKg * 9.81f * 2 * (0.6f + 0.4f * sin(PI * t).toFloat().let { if (it < 0) -it else it }))
            live.value = live.value?.copy(
                reps = reps, position = position, doneReps = reps,
                peakPower = maxOf(live.value?.peakPower ?: 0f, power), avgPower = power
            )
        }
        return reps
    }

    private suspend fun rest(base: LiveWorkout, seconds: Int) {
        if (seconds <= 0) return
        val s = CompletableDeferred<Unit>().also { skip = it }
        for (left in seconds downTo 1) {
            if (s.isCompleted) break
            live.value = base.copy(phase = LivePhase.RESTING, restSeconds = left)
            delay(1000)
        }
    }

    private fun remember(plan: SetPlan) = update { d ->
        val saved = SavedPlan.from(plan)
        d.copy(
            plans = d.plans + plan.loads.map { "${it.lifterId}|${plan.exercise.id}" to saved },
            loads = d.loads + plan.loads.map { "${it.lifterId}|${plan.exercise.id}" to it.weightPerCableKg }
        )
    }

    private fun record(groupId: String, lifterId: String, plan: SetPlan, reps: Int, topKg: Float, durationMs: Long) {
        val best = data.value.history.filter { it.lifterId == lifterId && it.exerciseId == plan.exercise.id }.mapNotNull { it.topKg }.maxOrNull()
        if (best != null && topKg > best) records.tryEmit("${plan.exercise.name} · ${topKg} kg")
        update {
            it.copy(history = it.history + SavedHistory(
                randomId(), lifterId, plan.exercise.id, plan.exercise.name, nowMillis(), plan.mode.label,
                plan.sets.size, reps, topKg.takeIf { kg -> kg > 0f }, durationMs, groupId
            ))
        }
    }

    override fun skipRest() { skip?.complete(Unit) }
    override fun adjustLiveWeight(perCableKg: Float) { live.value = live.value?.copy(loadKg = perCableKg, liveLoadKg = perCableKg) }
    override fun proceedFromSummary() { proceed?.complete(Unit) }
    override fun stopWorkout() { job?.cancel(); live.value = null }
    override fun advanceToNextExercise() { skip?.complete(Unit) }
    override fun resetForNewWorkout() { job?.cancel(); live.value = null }
    override fun finishSetEarly() { endSet?.complete(Unit) }

    private companion object {
        const val STORAGE_KEY = "web_gateway"
        const val SIMULATED = "Simulated trainer"
        const val REP_MS = 2200L
        val json = Json { ignoreUnknownKeys = true }

        fun load(): Saved = runCatching { json.decodeFromString(Saved.serializer(), LocalStorage[STORAGE_KEY]!!) }.getOrDefault(Saved())
    }
}

@Serializable
private data class Saved(
    val lifters: List<SavedLifter> = emptyList(),
    val active: List<String> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val history: List<SavedHistory> = emptyList(),
    val plans: Map<String, SavedPlan> = emptyMap(),
    val loads: Map<String, Float> = emptyMap(),
    val settings: SavedSettings = SavedSettings()
)

@Serializable
private data class SavedLifter(val id: String, val name: String, val color: Int)

@Serializable
private data class SavedHistory(
    val id: String, val lifterId: String, val exerciseId: String, val exerciseName: String,
    val timestamp: Long, val mode: String, val sets: Int, val reps: Int, val topKg: Float?,
    val durationMs: Long, val groupId: String
) {
    fun toEntry() = HistoryEntry(id, lifterId, exerciseName, timestamp, mode, sets, reps, topKg, durationMs, groupId = groupId, exerciseId = exerciseId)
}

@Serializable
private data class SavedSet(val amrap: Boolean, val reps: Int, val percent: Int?, val rest: Int)

@Serializable
private data class SavedPlan(
    val mode: String, val sets: List<SavedSet>, val duration: Int?, val cable: String,
    val eccentric: Int, val echo: String, val progression: Float
) {
    fun toLastPlan() = LastPlan(
        mode = runCatching { TrainingMode.valueOf(mode) }.getOrDefault(TrainingMode.OLD_SCHOOL),
        sets = sets.map { SetSpec(if (it.amrap) SetKind.AMRAP else SetKind.REPS, it.reps, it.percent, it.rest) },
        durationSeconds = duration, cableConfig = cable, eccentricLoadPercent = eccentric,
        echoLevel = runCatching { EchoLevelChoice.valueOf(echo) }.getOrDefault(EchoLevelChoice.HARDER),
        progressionKg = progression
    )

    companion object {
        fun from(p: SetPlan) = SavedPlan(
            p.mode.name, p.sets.map { SavedSet(it.kind == SetKind.AMRAP, it.reps, it.percent, it.restSeconds) },
            p.durationSeconds, p.cableConfig, p.eccentricLoadPercent, p.echoLevel.name, p.progressionKg
        )
    }
}

@Serializable
private data class SavedSettings(
    val lb: Boolean = false, val defaultReps: Int = 10, val defaultSets: Int = 3, val defaultPercent: Int = 60,
    val autoplay: Boolean = true, val stopAtTop: Boolean = false, val beeps: Boolean = true, val videos: Boolean = true
) {
    fun unit() = if (lb) WeightUnit.LB else WeightUnit.KG
    fun toClean() = CleanSettings(unit(), defaultReps, defaultSets, defaultPercent, autoplay, stopAtTop, beeps, videos)

    companion object {
        fun from(s: CleanSettings) = SavedSettings(
            s.unit == WeightUnit.LB, s.defaultReps, s.defaultSets, s.defaultPercent, s.autoplay, s.stopAtTop, s.beeps, s.videos
        )
    }
}
