package nl.nielsnotsmurf.webapp

import nl.nielsnotsmurf.cleanui.logic.FailureDetector
import com.example.vitruvianredux.data.baseline.AnchorExerciseConfig
import com.example.vitruvianredux.util.ColorSchemes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import nl.nielsnotsmurf.cleanui.trainer.TrainerProtocol
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The browser's TrainerGateway. Profiles, history and settings live in localStorage and the
 * exercise library is the one the app bundles. The trainer and heart rate straps connect over
 * Web Bluetooth with the app's own protocol (TrainerProtocol). Without Web Bluetooth (Safari,
 * iOS home screen), or when the device picker is cancelled, a simulated trainer runs the set.
 */
class WebTrainerGateway(private val library: ExerciseLibrary) : TrainerGateway {
    private val scope: CoroutineScope = MainScope()
    private val data = MutableStateFlow(load())
    private val live = MutableStateFlow<LiveWorkout?>(null)

    private fun update(change: (Saved) -> Saved) {
        data.value = change(data.value)
        LocalStorage[STORAGE_KEY] = json.encodeToString(Saved.serializer(), data.value)
    }

    // Profiles

    private val heartRateSensors = MutableStateFlow<Map<String, String>>(emptyMap()) // lifter -> strap name

    override val lifters: Flow<List<Lifter>> = combine(data, heartRateSensors) { d, hr ->
        d.lifters.map { Lifter(it.id, it.name, it.color, heartRateSensor = hr[it.id], heartRateConnected = it.id in hr) }
    }

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

    private val bluetooth = bluetoothAvailable()
    private var trainer: TrainerLink? = null
    private val repCounters = MutableStateFlow<TrainerProtocol.Reps?>(null)
    private val connectionState = MutableStateFlow<TrainerConnection>(
        if (bluetooth) TrainerConnection.Disconnected else TrainerConnection.Connected(SIMULATED)
    )
    override val connection: Flow<TrainerConnection> = connectionState

    /** Connects on the first start, like the app does; a cancelled picker means simulated. */
    private suspend fun ensureTrainer(): Boolean {
        if (!bluetooth || trainer != null) return true
        connectionState.value = TrainerConnection.Connecting
        leaving = false
        return try {
            // The trainer picked earlier in this tab connects again without the picker.
            val link = TrainerLink.reconnect(onReps = { repCounters.value = it }, onGone = ::trainerGone)
                ?: TrainerLink.connect(onReps = { repCounters.value = it }, onGone = ::trainerGone)
            trainer = link
            connectionState.value = TrainerConnection.Connected(link?.name ?: SIMULATED)
            WebStats.event(if (link != null) "trainer/connected" else "trainer/simulated")
            true
        } catch (e: Throwable) {
            connectionState.value = TrainerConnection.Failed(e.message ?: "Bluetooth error")
            WebStats.event("trainer/failed")
            false
        }
    }

    private fun trainerGone() {
        trainer = null
        connectionState.value = if (leaving) TrainerConnection.Disconnected else TrainerConnection.Failed("Trainer disconnected")
        leaving = false
        endSet?.complete(Unit)
    }

    /** "Not connected" tapped: connects again, also in the middle of a workout (the next set waits for it). */
    override fun connectTrainer() {
        if (!bluetooth || trainer != null || connectionState.value == TrainerConnection.Connecting) return
        scope.launch { ensureTrainer() }
    }

    /**
     * Before a set: when the link to a real trainer dropped, connect again (quietly when the browser
     * still knows it) and wait for it, instead of simulating the rest of the workout.
     */
    private suspend fun awaitTrainer(waiting: LiveWorkout) {
        if (!bluetooth || trainer != null || connectionState.value is TrainerConnection.Connected) return
        live.value = waiting.copy(phase = LivePhase.STARTING)
        TrainerLink.reconnect(onReps = { repCounters.value = it }, onGone = ::trainerGone)?.let { link ->
            trainer = link
            connectionState.value = TrainerConnection.Connected(link.name)
        }
        if (trainer == null) connectionState.value = TrainerConnection.Failed("Trainer disconnected. Tap Connect.")
        while (trainer == null && connectionState.value !is TrainerConnection.Connected) delay(300)
    }

    /** Set while the user disconnects on purpose, so it isn't shown as an error. */
    private var leaving = false

    override fun disconnectTrainer() {
        val link = trainer ?: run {
            // Simulated: back to "not connected", the next start opens the picker again.
            if (bluetooth) connectionState.value = TrainerConnection.Disconnected
            return
        }
        leaving = true
        trainer = null
        connectionState.value = TrainerConnection.Disconnected
        endSet?.complete(Unit)
        link.disconnect()
    }

    /** Clears this browser's workout history (Settings, "Delete all workouts"). */
    fun deleteAllWorkouts() = update { it.copy(history = emptyList()) }

    // Heart rate: one strap per lifter, connected for this browser session.

    private val heartRates = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val heartRateLinks = mutableMapOf<String, HeartRateLink>()

    override val liveHeartRate: Flow<Int?> = combine(heartRates, live) { rates, l ->
        (l?.lifterId ?: data.value.active.firstOrNull())?.let { rates[it] } ?: rates.values.firstOrNull()
    }

    val heartRateAvailable: Boolean get() = bluetooth

    /** Opens the browser's picker for a heart rate strap or watch; returns its name or null. */
    suspend fun pairHeartRate(lifterId: String): String? {
        forgetHeartRate(lifterId)
        val link = HeartRateLink.connect(
            onBpm = { bpm -> heartRates.value = heartRates.value + (lifterId to bpm) },
            onGone = { dropHeartRate(lifterId) }
        ) ?: return null
        heartRateLinks[lifterId] = link
        heartRateSensors.value = heartRateSensors.value + (lifterId to link.name)
        return link.name
    }

    fun forgetHeartRate(lifterId: String) {
        heartRateLinks.remove(lifterId)?.disconnect()
        dropHeartRate(lifterId)
    }

    fun heartRateSensor(lifterId: String): String? = heartRateSensors.value[lifterId]

    private fun dropHeartRate(lifterId: String) {
        heartRateLinks.remove(lifterId)
        heartRates.value = heartRates.value - lifterId
        heartRateSensors.value = heartRateSensors.value - lifterId
    }

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

    override val ledSchemes: List<LedScheme> = ColorSchemes.ALL.map { scheme ->
        LedScheme(scheme.name, scheme.colors.map { c -> (0xFF shl 24) or (c.r shl 16) or (c.g shl 8) or c.b })
    }

    override fun setLedScheme(index: Int) {
        val t = trainer ?: return
        val scheme = ColorSchemes.ALL.getOrElse(index) { ColorSchemes.ALL[0] }
        scope.launch {
            runCatching { t.send(TrainerProtocol.colorScheme(scheme.brightness, scheme.colors.map { (it.r shl 16) or (it.g shl 8) or it.b })) }
        }
    }

    override fun switchToClassicUi() {}

    // The set: on the trainer over Bluetooth, or simulated

    override val liveWorkout: Flow<LiveWorkout?> = live

    init {
        // Keeps the screen on while a workout runs (Screen Wake Lock), as the Android app does.
        scope.launch { live.map { it != null && it.phase != LivePhase.FINISHED }.distinctUntilChanged().collect { ScreenWakeLock.keep(it) } }
    }
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
        scope.launch {
            if (!ensureTrainer()) return@launch onFailed("Couldn't connect to your Vitruvian")
            run(plans, attempt = false)
            onStarted()
        }
    }

    override fun startSingleAttempt(lifterId: String, exercise: ExerciseCard, kg: Float, onStarted: () -> Unit, onFailed: (String) -> Unit) {
        setActiveLifters(listOf(lifterId))
        val plan = SetPlan(
            exercise = exercise, mode = TrainingMode.OLD_SCHOOL, percentOfOneRepMax = 100,
            sets = listOf(SetSpec(reps = 3, restSeconds = 0)),
            loads = listOf(nl.nielsnotsmurf.cleanui.model.LifterLoad(lifterId, listOf(kg)))
        )
        scope.launch {
            if (!ensureTrainer()) return@launch onFailed("Couldn't connect to your Vitruvian")
            run(listOf(plan), attempt = true)
            onStarted()
        }
    }

    /**
     * The workout as a cursor over (exercise, turn), where a turn is one lifter's set. It opens on
     * the Continue screen for the first set; after each set comes a short summary, then the rest
     * and (autoplay off) the Continue screen again. During rest and on the Continue screen
     * [moveSet] and [moveExercise] move the cursor, and the weight, reps and % of the coming set
     * can be changed, like the app. Every finished set is saved straight away, so ending or
     * leaving the workout keeps it.
     */
    private fun run(original: List<SetPlan>, attempt: Boolean) {
        job?.cancel()
        overrides.clear()
        val plans = original.toMutableList()
        runPlans = plans
        val lifters = data.value.lifters.associateBy { it.id }
        fun order(p: Int) = plans[p].loads.map { it.lifterId }.ifEmpty { data.value.active }.ifEmpty { listOf("") }
        fun turns(p: Int) = plans[p].sets.size * order(p).size
        fun baseFor(p: Int, turn: Int): LiveWorkout {
            val plan = plans[p]
            val ord = order(p)
            val s = (turn / ord.size).coerceIn(0, plan.sets.lastIndex)
            val lifterId = ord[turn % ord.size]
            val lifter = lifters[lifterId]
            val spec = specFor(p, turn, plan.sets[s])
            val kg = overrides[p to turn]?.kg
                ?: plan.loads.firstOrNull { it.lifterId == lifterId }?.setWeightsKg?.let { it.getOrNull(s) ?: it.lastOrNull() } ?: 0f
            return LiveWorkout(
                phase = LivePhase.COUNTDOWN, exerciseName = plan.exercise.name,
                targetReps = if (spec.kind == SetKind.AMRAP) null else spec.reps,
                loadKg = kg, liveLoadKg = kg, setNumber = s + 1, totalSets = plan.sets.size,
                isLastExercise = p == plans.lastIndex, lifterId = lifterId,
                lifterName = lifter?.name, lifterColorArgb = lifter?.color,
                nextName = plans.getOrNull(p + 1)?.exercise?.name.orEmpty()
            )
        }
        turnCount = { turns(it) }
        navigate = { p, turn, delta, byExercise ->
            if (byExercise) {
                (p + delta).coerceIn(0, plans.lastIndex) to 0
            } else {
                var e = p
                var t = turn + delta
                while (t < 0 && e > 0) { e--; t += turns(e) }
                while (e < plans.lastIndex && t >= turns(e)) { t -= turns(e); e++ }
                e to t.coerceIn(0, turns(e)) // turns(last) = past the end: the rest leads to the finish
            }
        }
        job = scope.launch {
            val groupId = randomId()
            val started = mutableMapOf<Int, Long>()
            val done = mutableMapOf<Pair<Int, String>, Pair<Int, Float>>() // (exercise, lifter) -> reps, top kg
            var p = 0
            var turn = 0
            var lastReps: Int? = null
            fun upcoming(c: Pair<Int, Int>, fallback: LiveWorkout) =
                if (c.first < plans.size && c.second < turns(c.first)) baseFor(c.first, c.second).copy(lastReps = lastReps)
                else fallback.copy(lastReps = lastReps)
            if (!attempt) {
                // Opens on the Continue screen, so the first set or exercise can still be changed.
                cursor = 0 to 0
                rest(0, hold = true) { c -> upcoming(c, baseFor(0, 0)) }
                val first = cursor ?: (0 to 0)
                p = first.first
                turn = first.second
            }
            while (p < plans.size && turn < turns(p)) {
                val plan = plans[p]
                val spec = specFor(p, turn, plan.sets[turn / order(p).size])
                val base = baseFor(p, turn)
                val lifterId = base.lifterId.orEmpty()
                awaitTrainer(base)
                for (c in 3 downTo 1) { live.value = base.copy(countdown = c); delay(1000) }
                val begun = started.getOrPut(p) { nowMillis() }
                val reps = trainer?.let { lift(it, base, spec, plan) } ?: simulate(base, spec)
                val prev = done[p to lifterId]
                val result = ((prev?.first ?: 0) + reps) to maxOf(prev?.second ?: 0f, live.value?.loadKg ?: base.loadKg)
                done[p to lifterId] = result
                if (lifterId.isNotEmpty()) record(groupId, lifterId, plan, result.first, result.second, nowMillis() - begun)
                if (attempt) { live.value = null; return@launch }
                if (p == plans.lastIndex && turn == turns(p) - 1) break
                // Assign before waiting: Continue completes this exact deferred.
                val go = CompletableDeferred<Unit>()
                proceed = go
                live.value = live.value?.copy(phase = LivePhase.SET_DONE, doneReps = reps)
                go.await()
                lastReps = reps
                cursor = navigate?.invoke(p, turn, 1, false) ?: (p to turn + 1)
                rest(spec.restSeconds, hold = !data.value.settings.autoplay) { c -> upcoming(c, base) }
                val next = cursor ?: break
                p = next.first
                turn = next.second
            }
            cursor = null
            navigate = null
            live.value = live.value?.copy(phase = LivePhase.FINISHED)
            if (!attempt) WebStats.event("workout/finished")
        }
    }

    /** The set coming after the rest, moved by [moveSet] / [moveExercise]. */
    private var cursor: Pair<Int, Int>? = null

    /** The running workout's plans; [setUpcomingLoads] changes their loads mid-workout. */
    private var runPlans: MutableList<SetPlan> = mutableListOf()

    /** What was changed by hand for one set (exercise, turn): its load per cable and target reps. */
    private data class SetOverride(val kg: Float? = null, val reps: Int? = null)
    private val overrides = mutableMapOf<Pair<Int, Int>, SetOverride>()

    private fun specFor(p: Int, turn: Int, spec: SetSpec): SetSpec =
        overrides[p to turn]?.reps?.let { spec.copy(reps = it) } ?: spec
    private var navigate: ((p: Int, turn: Int, delta: Int, byExercise: Boolean) -> Pair<Int, Int>)? = null
    /** Sets × lifters of exercise p in the running workout. */
    private var turnCount: ((p: Int) -> Int)? = null

    /**
     * One set on the trainer, as the app runs it: reset, the mode's frame, then poll the cables
     * every 100 ms while the trainer counts reps; the set ends at the target (not for AMRAP) or
     * when finished early, and always ends with the reset command so the cables let go.
     */
    private suspend fun lift(t: TrainerLink, base: LiveWorkout, spec: SetSpec, plan: SetPlan): Int {
        val amrap = spec.kind == SetKind.AMRAP
        val warmupTarget = data.value.settings.warmupReps.coerceIn(1, 3)
        fun frame(kg: Float) = if (plan.mode == TrainingMode.ECHO) {
            TrainerProtocol.echoControl(TrainerProtocol.EchoLevel.valueOf(plan.echoLevel.name), warmupTarget, spec.reps, amrap, plan.eccentricLoadPercent)
        } else {
            TrainerProtocol.programParams(spec.reps, warmupTarget, amrap, plan.mode.profile(), kg, plan.progressionKg)
        }
        val end = CompletableDeferred<Unit>().also { endSet = it }
        var sentKg = base.loadKg
        var warmup = 0
        var working = 0
        var counters: TrainerProtocol.Reps? = null
        var low = Float.MAX_VALUE
        var high = -Float.MAX_VALUE
        var last: TrainerProtocol.Sample? = null
        var lastAt = 0L
        var powerSum = 0f
        var powerCount = 0
        // AMRAP: ends the set on failure (stuck) or handles down. Every set: stops after 5 s without
        // movement, for safety. Armed only after the warm-up and a full rep range.
        val failure = FailureDetector(stopAtBottom = amrap)
        var autoStop: Int? = null
        suspend fun begin() {
            repCounters.value = null
            counters = null
            t.send(TrainerProtocol.init())
            delay(50)
            t.send(frame(sentKg))
            delay(100)
        }
        try {
            begin()
            live.value = base.copy(phase = LivePhase.LIFTING, warmupTarget = warmupTarget, warmupDone = false)
            while (!end.isCompleted) {
                val shown = live.value ?: break
                // A weight change during warm-up restarts the set at the new weight, as in the app.
                if (shown.loadKg != sentKg && warmup < warmupTarget) { sentKg = shown.loadKg; warmup = 0; working = 0; begin() }
                // Reset ROM: the set starts over, so the warm-up reps set the range of motion again.
                if (restart) {
                    restart = false; warmup = 0; working = 0; low = Float.MAX_VALUE; high = -Float.MAX_VALUE; failure.reset(); begin()
                    live.value = live.value?.copy(reps = 0, doneReps = 0, warmupReps = 0, warmupDone = false)
                }
                val now = nowMillis()
                val sample = t.readSample()
                val reps = repCounters.value
                if (reps != null && reps != counters) {
                    if (reps.isLegacy) {
                        val ups = (reps.up - (counters?.up ?: reps.up)).coerceAtLeast(0)
                        repeat(ups) { if (warmup < warmupTarget) warmup++ else working++ }
                    } else {
                        warmup = reps.romCount.coerceAtMost(warmupTarget)
                        working = reps.setCount
                    }
                    counters = reps
                }
                var power = shown.avgPower
                if (sample != null) {
                    low = minOf(low, sample.positionA)
                    high = maxOf(high, sample.positionA)
                    val prev = last
                    if (prev != null && now > lastAt) {
                        val seconds = (now - lastAt) / 1000f
                        val va = abs(sample.positionA - prev.positionA) / 1000f / seconds
                        val vb = abs(sample.positionB - prev.positionB) / 1000f / seconds
                        power = (sample.loadA * va + sample.loadB * vb) * 9.81f
                        powerSum += power
                        powerCount++
                    }
                    last = sample
                    lastAt = now
                    if (warmup >= warmupTarget) autoStop = failure.update(maxOf(sample.positionA, sample.positionB), now)
                }
                live.value = shown.copy(
                    reps = working, doneReps = working, warmupReps = warmup,
                    warmupDone = warmup >= warmupTarget,
                    liveLoadKg = sample?.let { maxOf(it.loadA, it.loadB) } ?: shown.liveLoadKg,
                    position = if (high - low > 1f && sample != null) ((sample.positionA - low) / (high - low)).coerceIn(0f, 1f) else null,
                    peakPower = maxOf(shown.peakPower, power),
                    avgPower = if (powerCount > 0) powerSum / powerCount else 0f,
                    autoStopSeconds = autoStop
                )
                if (!amrap && working >= spec.reps) break
                if (failure.shouldStop) break
                delay(100)
            }
        } finally {
            withContext(NonCancellable) { runCatching { t.send(TrainerProtocol.reset()) } }
        }
        return working
    }

    /** Reps at a steady tempo; the handles go up and down with them. */
    private suspend fun simulate(base: LiveWorkout, spec: SetSpec): Int {
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

    /**
     * Rest before the set at [cursor]; the screen shows that set, also after moving it. The time
     * runs on the clock, not on ticks, so it keeps going while the browser is in the background
     * (iOS pauses the page). With [hold] the Continue screen waits after the rest.
     */
    private suspend fun rest(seconds: Int, hold: Boolean, upcoming: (Pair<Int, Int>) -> LiveWorkout) {
        val s = CompletableDeferred<Unit>().also { skip = it }
        val endsAt = nowMillis() + seconds.coerceAtLeast(0) * 1000L
        while (!s.isCompleted) {
            val c = cursor ?: return
            val left = ((endsAt - nowMillis() + 999) / 1000).toInt().coerceAtLeast(0)
            if (left == 0 && !hold) return
            live.value = upcoming(c).copy(phase = LivePhase.RESTING, restSeconds = left)
            // Short steps so a moved set or a change shows at once.
            delay(100)
        }
    }

    private fun move(delta: Int, byExercise: Boolean) {
        val c = cursor ?: return
        if (live.value?.phase != LivePhase.RESTING) return
        cursor = navigate?.invoke(c.first, c.second, delta, byExercise) ?: return
    }

    override fun moveSet(delta: Int) = move(delta, byExercise = false)
    override fun moveExercise(delta: Int) = move(delta, byExercise = true)

    private fun remember(plan: SetPlan) = update { d ->
        val saved = SavedPlan.from(plan)
        d.copy(
            plans = d.plans + plan.loads.map { "${it.lifterId}|${plan.exercise.id}" to saved },
            loads = d.loads + plan.loads.map { "${it.lifterId}|${plan.exercise.id}" to it.weightPerCableKg }
        )
    }

    /** Saves the exercise so far for this lifter, replacing its entry from an earlier set of this workout. */
    private fun record(groupId: String, lifterId: String, plan: SetPlan, reps: Int, topKg: Float, durationMs: Long) {
        val same = { h: SavedHistory -> h.groupId == groupId && h.lifterId == lifterId && h.exerciseId == plan.exercise.id }
        val existing = data.value.history.firstOrNull(same)
        val best = data.value.history.filter { it.lifterId == lifterId && it.exerciseId == plan.exercise.id && !same(it) }.mapNotNull { it.topKg }.maxOrNull()
        if (best != null && topKg > best && topKg > (existing?.topKg ?: 0f)) records.tryEmit("${plan.exercise.name} · ${topKg} kg")
        update {
            it.copy(history = it.history.filterNot(same) + SavedHistory(
                existing?.id ?: randomId(), lifterId, plan.exercise.id, plan.exercise.name, existing?.timestamp ?: nowMillis(), plan.mode.label,
                plan.sets.size, reps, topKg.takeIf { kg -> kg > 0f }, durationMs, groupId
            ))
        }
    }

    override fun skipRest() { skip?.complete(Unit) }
    override fun adjustLiveWeight(perCableKg: Float) {
        val c = cursor
        if (live.value?.phase == LivePhase.RESTING && c != null) {
            overrides[c] = (overrides[c] ?: SetOverride()).copy(kg = perCableKg)
            live.value = live.value?.copy(loadKg = perCableKg)
            return
        }
        live.value = live.value?.copy(loadKg = perCableKg, liveLoadKg = perCableKg)
    }
    override fun adjustNextReps(reps: Int) {
        val c = cursor ?: return
        if (live.value?.phase != LivePhase.RESTING) return
        // The coming set and every later set of this exercise.
        for (t in c.second until (turnCount?.invoke(c.first) ?: (c.second + 1))) {
            overrides[c.first to t] = (overrides[c.first to t] ?: SetOverride()).copy(reps = reps.coerceIn(1, 50))
        }
        live.value = live.value?.copy(targetReps = reps.coerceIn(1, 50))
    }
    override fun adjustEchoLevel(level: EchoLevelChoice) {
        val c = cursor ?: return
        if (live.value?.phase != LivePhase.RESTING || c.first !in runPlans.indices) return
        runPlans[c.first] = runPlans[c.first].copy(echoLevel = level)
    }
    override fun setUpcomingLoads(exerciseId: String, loadsByLifter: Map<String, List<Float>>) {
        val plans = runPlans
        plans.indices.filter { plans[it].exercise.id == exerciseId }.forEach { i ->
            val plan = plans[i]
            plans[i] = plan.copy(loads = plan.loads.map { l -> loadsByLifter[l.lifterId]?.let { l.copy(setWeightsKg = it) } ?: l })
            // A new % replaces loads set by hand for this exercise.
            overrides.keys.filter { it.first == i }.forEach { k -> overrides[k] = overrides.getValue(k).copy(kg = null) }
        }
    }
    override fun proceedFromSummary() { proceed?.complete(Unit) }
    override fun stopWorkout() { job?.cancel(); cursor = null; live.value = null }
    override fun advanceToNextExercise() { skip?.complete(Unit) }
    override fun resetForNewWorkout() { job?.cancel(); cursor = null; live.value = null }
    override fun finishSetEarly() { endSet?.complete(Unit) }
    override fun restartSet() { restart = true }
    private var restart = false

    private fun TrainingMode.profile() = when (this) {
        TrainingMode.PUMP -> TrainerProtocol.Profile.PUMP
        TrainingMode.TUT -> TrainerProtocol.Profile.TUT
        TrainingMode.TUT_BEAST -> TrainerProtocol.Profile.TUT_BEAST
        TrainingMode.ECCENTRIC_ONLY -> TrainerProtocol.Profile.ECCENTRIC_ONLY
        else -> TrainerProtocol.Profile.OLD_SCHOOL
    }

    private companion object {
        const val STORAGE_KEY = "web_gateway"
        /** The app's default warm-up before the working reps count. */
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
    val autoplay: Boolean = false, val stopAtTop: Boolean = false, val beeps: Boolean = true, val videos: Boolean = true,
    val warmupReps: Int = 3, val defaultAmrap: Boolean = false, val defaultRest: Int = 90
) {
    fun unit() = if (lb) WeightUnit.LB else WeightUnit.KG
    fun toClean() = CleanSettings(unit(), defaultReps, defaultSets, defaultPercent, autoplay, stopAtTop, warmupReps, beeps, videos, defaultAmrap, defaultRest)

    companion object {
        fun from(s: CleanSettings) = SavedSettings(
            s.unit == WeightUnit.LB, s.defaultReps, s.defaultSets, s.defaultPercent, s.autoplay, s.stopAtTop, s.beeps, s.videos, s.warmupReps, s.defaultAmrap,
            s.defaultRestSeconds.coerceIn(15, 600)
        )
    }
}
