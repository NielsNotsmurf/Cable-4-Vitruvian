package com.example.vitruvianredux.cleanui

import com.example.vitruvianredux.data.local.ExerciseEntity
import com.example.vitruvianredux.data.local.UserProfileEntity
import com.example.vitruvianredux.data.preferences.PreferencesManager
import com.example.vitruvianredux.data.preferences.SingleExerciseDefaults
import com.example.vitruvianredux.data.preferences.WorkoutModeId
import com.example.vitruvianredux.data.baseline.AnchorExerciseConfig
import com.example.vitruvianredux.data.baseline.SuggestionSource
import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.ConnectionState
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.Exercise
import com.example.vitruvianredux.domain.model.ExerciseBucket
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.Routine
import com.example.vitruvianredux.domain.model.RoutineExercise
import com.example.vitruvianredux.domain.model.WorkoutType
import com.example.vitruvianredux.domain.model.RepCount
import com.example.vitruvianredux.domain.model.WorkoutMetric
import com.example.vitruvianredux.domain.model.WorkoutParameters
import com.example.vitruvianredux.domain.model.WorkoutState
import com.example.vitruvianredux.domain.usecase.RepRanges
import com.example.vitruvianredux.domain.model.resolveDefaultCableConfig
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.logic.ExerciseSearch
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxTest
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.LastPlan
import nl.nielsnotsmurf.cleanui.model.SetKind
import nl.nielsnotsmurf.cleanui.model.SetSpec
import nl.nielsnotsmurf.cleanui.model.CleanSettings
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.LedScheme
import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.LivePhase
import nl.nielsnotsmurf.cleanui.model.LiveWorkout
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.TestAnchor
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import timber.log.Timber
import java.util.UUID
import com.example.vitruvianredux.domain.model.WeightUnit as AppWeightUnit
import com.example.vitruvianredux.util.ColorSchemes

/**
 * The single place where the Clean UI meets the app.
 *
 * Everything the clean-ui module needs goes through [TrainerGateway]; this class maps it
 * onto MainViewModel, the repositories and PreferencesManager. When upstream Phoenix
 * changes these APIs, this is the only file that should need to follow.
 */
class CleanUiBridge(
    private val vm: MainViewModel,
    private val preferencesManager: PreferencesManager,
    private val store: CleanUiStore,
    private val scope: CoroutineScope
) : TrainerGateway {

    override val lifters: Flow<List<Lifter>> =
        combine(vm.profiles, vm.connectedHeartRateSensors) { profiles, connected ->
            profiles.map { p ->
                p.toLifter().copy(
                    heartRateSensor = p.hrSensorName,
                    heartRateConnected = p.hrSensorAddress != null && p.hrSensorAddress in connected
                )
            }
        }

    override val activeLifterIds: Flow<List<String>> =
        combine(vm.workoutParticipants, vm.activeProfileId) { participants, activeId ->
            if (participants.isNotEmpty()) participants.map { it.id } else listOf(activeId)
        }

    override fun addLifter(name: String, colorArgb: Int) = vm.createProfile(name, colorArgb)

    override fun deleteLifter(lifterId: String) {
        val profile = vm.profiles.value.firstOrNull { it.id == lifterId } ?: return
        // Take them out of the rotation first, so nobody trains as a deleted profile.
        val participants = vm.workoutParticipants.value.map { it.id }
        if (lifterId in participants) {
            val rest = participants - lifterId
            setActiveLifters(rest.ifEmpty { vm.profiles.value.map { it.id }.filter { it != lifterId }.take(1) })
        }
        vm.deleteProfile(profile)
    }

    override fun setActiveLifters(lifterIds: List<String>) {
        if (lifterIds.isEmpty()) return
        val byId = vm.profiles.value.associateBy { it.id }
        val chosen = lifterIds.mapNotNull { byId[it] }
        // The first lifter is the active profile, so solo history and defaults follow them.
        vm.setActiveProfile(lifterIds.first())
        if (chosen.size > 1) vm.setWorkoutParticipants(chosen) else vm.clearWorkoutParticipants()
    }

    override val connection: Flow<TrainerConnection> = vm.connectionState.map {
        when (it) {
            is ConnectionState.Connected -> TrainerConnection.Connected(it.deviceName)
            ConnectionState.Connecting, ConnectionState.Scanning -> TrainerConnection.Connecting
            is ConnectionState.Error -> TrainerConnection.Failed(it.message)
            ConnectionState.Disconnected -> TrainerConnection.Disconnected
        }
    }

    override val liveWorkout: Flow<LiveWorkout?> = combine(
        combine(vm.workoutState, vm.repCount, vm.workoutParameters, vm.currentMetric, vm.repRanges) { st, reps, params, metric, ranges ->
            LiveInputs(st, reps, params, metric, ranges)
        },
        combine(vm.loadedRoutine, vm.currentExerciseIndex, vm.isCurrentExerciseBodyweight, vm.currentPerformer, vm.currentPerformerSetDisplay) {
            routine, index, bodyweight, performer, setDisplay ->
            RoutineInputs(routine, index, bodyweight, performer, setDisplay)
        }
    ) { a, b ->
        val st = a.state
        val phase = when (st) {
            WorkoutState.Initializing -> LivePhase.STARTING
            is WorkoutState.Countdown -> LivePhase.COUNTDOWN
            WorkoutState.Active -> LivePhase.LIFTING
            is WorkoutState.SetSummary -> LivePhase.SET_DONE
            is WorkoutState.Resting -> LivePhase.RESTING
            WorkoutState.Completed -> LivePhase.FINISHED
            else -> LivePhase.OTHER
        }
        val exercises = b.routine?.exercises.orEmpty()
        val position = a.metric?.let { m ->
            val lo = a.ranges?.minPosA
            val hi = a.ranges?.maxPosA
            if (lo != null && hi != null && hi - lo > 1f) ((m.positionA - lo) / (hi - lo)).coerceIn(0f, 1f) else null
        }
        LiveWorkout(
            phase = phase,
            exerciseName = exercises.getOrNull(b.index)?.exercise?.name.orEmpty(),
            countdown = (st as? WorkoutState.Countdown)?.secondsRemaining ?: 0,
            reps = a.reps.workingReps,
            targetReps = a.params.reps.takeIf { it > 0 && !a.params.isJustLift },
            warmupReps = a.reps.warmupReps,
            warmupTarget = a.params.warmupReps,
            warmupDone = a.reps.isWarmupComplete || a.params.warmupReps == 0,
            loadKg = a.params.weightPerCableKg,
            liveLoadKg = a.metric?.let { maxOf(it.loadA, it.loadB) } ?: 0f,
            position = position,
            setNumber = (st as? WorkoutState.Resting)?.currentSet ?: b.setDisplay?.first ?: 0,
            totalSets = (st as? WorkoutState.Resting)?.totalSets ?: b.setDisplay?.second ?: 0,
            restSeconds = (st as? WorkoutState.Resting)?.restSecondsRemaining ?: 0,
            nextName = (st as? WorkoutState.Resting)?.nextExerciseName.orEmpty(),
            isLastExercise = (st as? WorkoutState.Resting)?.isLastExercise ?: (b.index >= exercises.size - 1),
            lifterId = b.performer?.id,
            lifterName = b.performer?.name,
            lifterColorArgb = b.performer?.colorInt,
            doneReps = (st as? WorkoutState.SetSummary)?.repCount ?: a.reps.workingReps,
            peakPower = (st as? WorkoutState.SetSummary)?.peakPower ?: 0f,
            avgPower = (st as? WorkoutState.SetSummary)?.averagePower ?: 0f,
            bodyweight = b.bodyweight
        )
    }

    override val personalRecords: Flow<String> = vm.prCelebrationEvent.map { e ->
        "${e.exerciseName} · ${vm.formatWeight(e.weightPerCableKg, vm.weightUnit.value)} × ${e.reps}"
    }

    override fun skipRest() = vm.skipRest()
    override fun adjustLiveWeight(perCableKg: Float) = vm.adjustWeightLive(perCableKg)
    override fun proceedFromSummary() = vm.proceedFromSummary()
    override fun stopWorkout() = vm.stopWorkout()
    override fun advanceToNextExercise() = vm.advanceToNextExercise()
    override fun resetForNewWorkout() = vm.resetForNewWorkout()
    override fun finishSetEarly() = vm.finishSetEarly()
    override fun moveSet(delta: Int) = vm.moveUpcomingSet(delta)
    override fun moveExercise(delta: Int) = vm.moveUpcomingExercise(delta)
    override fun setUpcomingLoads(exerciseId: String, loadsByLifter: Map<String, List<Float>>) =
        vm.setUpcomingLoads(exerciseId, loadsByLifter)

    override val liveHeartRate: Flow<Int?> = vm.currentHeartRate

    override val weightUnit: Flow<WeightUnit> = vm.userPreferences.map {
        if (it.weightUnit == AppWeightUnit.LB) WeightUnit.LB else WeightUnit.KG
    }

    override val exercises: Flow<List<ExerciseCard>> =
        vm.exerciseRepository.getAllExercises().map { list ->
            list.filterNot { it.archived }.map { it.toCard() }
        }

    override val bestSetsByLifter: Flow<Map<String, List<LiftRecord>>> =
        vm.personalRecordRepository.getAllPRs().map { prs ->
            prs.groupBy({ it.profileId }) { LiftRecord(it.exerciseId, it.weightPerCableKg, it.reps, it.timestamp) }
        }

    override suspend fun relatedOneRepMaxKg(lifterId: String, exercise: ExerciseCard): Float? {
        val entity = vm.exerciseRepository.getExerciseById(exercise.id) ?: return null
        val bucket = ExerciseBucket.from(entity.muscles.csv(), entity.movement, entity.muscleGroups.csv())
        val suggestion = vm.baselineRepository.propagateWithConfidence(lifterId, exercise.id, bucket)
        // Only scale from a real benchmark; conservative and safe defaults aren't a 1RM.
        if (suggestion.confidence < SuggestionSource.BUCKET_PROPAGATION.confidence) return null
        // Baseline weights are "10 reps at RPE 7–8", about 2.5 reps in reserve.
        return LoadCalculator.oneRepMaxFromWorkingSet(suggestion.weightKg, BASELINE_REPS, BASELINE_RIR)
    }

    override val defaultReps: Int
        get() = vm.userPreferences.value.defaultRepCount

    override suspend fun lastPlan(lifterId: String, exercise: ExerciseCard): LastPlan? {
        val cable = exercise.toExercise().resolveDefaultCableConfig().name
        val d = preferencesManager.getSingleExerciseDefaults(exercise.id, cable, lifterId) ?: return null
        val mode = when (d.workoutModeId) {
            WorkoutModeId.PUMP -> TrainingMode.PUMP
            WorkoutModeId.TUT -> TrainingMode.TUT
            WorkoutModeId.TUT_BEAST -> TrainingMode.TUT_BEAST
            WorkoutModeId.ECCENTRIC_ONLY -> TrainingMode.ECCENTRIC_ONLY
            WorkoutModeId.ECHO -> TrainingMode.ECHO
            else -> TrainingMode.OLD_SCHOOL
        }
        val sets = d.setReps.mapIndexed { i, reps ->
            SetSpec(
                kind = if (reps == null) SetKind.AMRAP else SetKind.REPS,
                reps = reps ?: defaultReps,
                restSeconds = d.setRestSeconds.getOrNull(i) ?: 90
            )
        }
        return LastPlan(
            mode = mode,
            sets = sets,
            durationSeconds = d.duration,
            cableConfig = d.cableConfig.takeIf { it == "SINGLE" || it == "DOUBLE" } ?: cable,
            eccentricLoadPercent = d.eccentricLoadPercentage,
            echoLevel = EchoLevelChoice.entries.getOrNull(d.echoLevelValue) ?: EchoLevelChoice.HARDER,
            progressionKg = d.progressionKg
        )
    }

    override suspend fun toggleFavorite(exerciseId: String) {
        vm.exerciseRepository.toggleFavorite(exerciseId)
    }

    override suspend fun lastUsedLoadKg(lifterId: String, exercise: ExerciseCard): Float? =
        preferencesManager.getSingleExerciseDefaults(exercise.id, exercise.toExercise().resolveDefaultCableConfig().name, lifterId)
            ?.weightPerCableKg

    override fun startExercise(plan: SetPlan, onStarted: () -> Unit, onFailed: (String) -> Unit) =
        startRoutine(
            id = "${MainViewModel.TEMP_SINGLE_EXERCISE_PREFIX}${UUID.randomUUID()}",
            name = "Single Exercise: ${plan.exercise.name}",
            plans = listOf(plan),
            onStarted = onStarted,
            onFailed = onFailed
        )

    override fun startWorkout(name: String, plans: List<SetPlan>, onStarted: () -> Unit, onFailed: (String) -> Unit) =
        startRoutine(
            id = "${MainViewModel.CLEAN_WORKOUT_PREFIX}${UUID.randomUUID()}",
            name = name,
            plans = plans,
            onStarted = onStarted,
            onFailed = onFailed
        )

    private fun startRoutine(
        id: String,
        name: String,
        plans: List<SetPlan>,
        onStarted: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        scope.launch {
            try {
                val exercises = plans.mapIndexed { index, plan -> routineExerciseFor(plan, index) }
                vm.loadRoutine(
                    Routine(
                        id = id,
                        name = name,
                        description = "Started from the Clean UI",
                        exercises = exercises
                    )
                )
                // Every lifter's own loads, so the rotation never falls back to the first lifter's.
                plans.forEach { plan ->
                    vm.setUpcomingLoads(plan.exercise.id, plan.loads.associate { it.lifterId to it.setWeightsKg })
                }
                vm.ensureConnection(
                    onConnected = {
                        vm.startWorkout()
                        onStarted()
                    },
                    onFailed = { onFailed("Couldn't reach the trainer. Check it's switched on and nearby.") }
                )
            } catch (e: Exception) {
                Timber.e(e, "Clean UI failed to start exercise")
                onFailed("Couldn't start this exercise: ${e.message}")
            }
        }
    }

    private suspend fun routineExerciseFor(plan: SetPlan, orderIndex: Int): RoutineExercise {
        val exercise = plan.exercise.toExercise()
        val cableConfig = runCatching { CableConfiguration.valueOf(plan.cableConfig) }
            .getOrDefault(exercise.resolveDefaultCableConfig())
        val workoutType = plan.toWorkoutType()
        // AMRAP sets are null reps, as in the classic exercise config.
        val setReps: List<Int?> = plan.sets.map { if (it.kind == SetKind.AMRAP) null else it.reps }
        val rest = plan.sets.map { it.restSeconds }
        val isAmrap = plan.sets.any { it.kind == SetKind.AMRAP }
        val perSetRest = rest.distinct().size > 1

        // Each lifter's loads go in as their per-exercise defaults. The existing
        // rotation loads the next performer's defaults after every set, so this is
        // what makes everyone lift their own % of 1RM, set by set.
        plan.loads.forEach { load ->
            preferencesManager.saveSingleExerciseDefaults(
                SingleExerciseDefaults(
                    exerciseId = plan.exercise.id,
                    cableConfig = cableConfig.name,
                    workoutModeId = WorkoutModeId.fromWorkoutType(workoutType),
                    setReps = setReps,
                    weightPerCableKg = load.weightPerCableKg,
                    setWeightsPerCableKg = load.setWeightsKg.takeIf { it.size == setReps.size }.orEmpty(),
                    progressionKg = plan.progressionKg,
                    setRestSeconds = rest,
                    perSetRestTime = perSetRest,
                    eccentricLoadPercentage = plan.eccentricLoadPercent,
                    echoLevelValue = plan.echoLevel.toApp().levelValue,
                    duration = plan.durationSeconds,
                    isAMRAP = isAmrap
                ),
                profileId = load.lifterId
            )
        }

        val first = plan.loads.first()
        return RoutineExercise(
            id = UUID.randomUUID().toString(),
            exercise = exercise,
            cableConfig = cableConfig,
            orderIndex = orderIndex,
            setReps = setReps,
            weightPerCableKg = first.weightPerCableKg,
            setWeightsPerCableKg = first.setWeightsKg.takeIf { it.size == setReps.size }.orEmpty(),
            progressionKg = plan.progressionKg,
            setRestSeconds = rest,
            workoutType = workoutType,
            eccentricLoad = eccentricLoad(plan.eccentricLoadPercent),
            echoLevel = plan.echoLevel.toApp(),
            duration = plan.durationSeconds,
            isAMRAP = isAmrap,
            perSetRestTime = perSetRest
        )
    }

    override val oneRepMaxTestExercises: List<TestAnchor> =
        AnchorExerciseConfig.ANCHORS.map { TestAnchor(it.exerciseId, it.conservativeDefaultKg) }

    override suspend fun exerciseGuide(exerciseId: String): ExerciseGuide? {
        val entity = vm.exerciseRepository.getExerciseById(exerciseId) ?: return null
        val videos = runCatching { vm.exerciseRepository.getVideos(exerciseId) }.getOrDefault(emptyList())
        // The short angle clips loop like a GIF (as on the classic screens); the long tutorial is a fallback.
        val clips = videos.filterNot { it.isTutorial }
        val video = clips.firstOrNull { it.angle == "FRONT" } ?: clips.firstOrNull() ?: videos.firstOrNull()
        return ExerciseGuide(
            description = entity.description,
            videoUrl = video?.videoUrl,
            thumbnailUrl = video?.thumbnailUrl
        )
    }

    override fun startSingleAttempt(
        lifterId: String,
        exercise: ExerciseCard,
        kg: Float,
        onStarted: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        scope.launch {
            try {
                // Only this lifter takes the attempt, so the rotation doesn't hand it to someone else.
                setActiveLifters(listOf(lifterId))
                val ex = exercise.toExercise()
                val routineExercise = RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    exercise = ex,
                    cableConfig = ex.resolveDefaultCableConfig(),
                    orderIndex = 0,
                    setReps = listOf(OneRepMaxTest.TEST_REPS),
                    weightPerCableKg = kg,
                    progressionKg = 0f,
                    setRestSeconds = listOf(0),
                    // Old School: the same load up and down, so the eccentric is 100%, never boosted.
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                    eccentricLoad = EccentricLoad.LOAD_100,
                    echoLevel = EchoLevel.HARDER
                )
                vm.loadRoutine(
                    Routine(
                        id = "${MainViewModel.ONE_REP_MAX_ATTEMPT_PREFIX}${UUID.randomUUID()}",
                        name = "1RM attempt: ${ex.name}",
                        description = "1RM test from the Clean UI",
                        exercises = listOf(routineExercise)
                    )
                )
                vm.ensureConnection(
                    onConnected = {
                        vm.startWorkout()
                        onStarted()
                    },
                    onFailed = { onFailed("Couldn't reach the trainer. Check it's switched on and nearby.") }
                )
            } catch (e: Exception) {
                Timber.e(e, "Clean UI failed to start a 1RM attempt")
                onFailed("Couldn't start the attempt: ${e.message}")
            }
        }
    }

    override val history: Flow<List<HistoryEntry>> = vm.allWorkoutSessions.map { sessions ->
        sessions.sortedByDescending { it.timestamp }.map { s ->
            HistoryEntry(
                id = s.id,
                lifterId = s.profileId,
                exerciseName = s.exerciseName ?: if (s.isJustLift) "Just Lift" else "Workout",
                timestamp = s.timestamp,
                mode = s.mode,
                // Same set count the classic history card shows.
                sets = when {
                    s.reps == 0 -> 1
                    s.workingReps > 0 -> (s.workingReps / s.reps.coerceAtLeast(1)).coerceAtLeast(1)
                    else -> 0
                },
                reps = s.workingReps.takeIf { it > 0 } ?: s.totalReps,
                topKgPerCable = s.weightPerCableKg.takeUnless { s.mode.contains("Echo", ignoreCase = true) },
                durationMs = s.duration,
                avgHeartRate = s.avgHeartRate,
                maxHeartRate = s.maxHeartRate,
                groupId = s.routineSessionId ?: s.id,
                exerciseId = s.exerciseId.orEmpty()
            )
        }
    }

    override fun deleteWorkouts(ids: List<String>) {
        ids.forEach { vm.deleteWorkout(it) }
    }

    override val settings: Flow<CleanSettings> =
        combine(vm.userPreferences, store.defaultSets, store.defaultPercent) { p, sets, pct ->
            currentSets = sets
            currentPercent = pct
            p.toCleanSettings().copy(defaultSets = sets, defaultPercent = pct)
        }

    private var currentSets = 3
    private var currentPercent = 60

    override fun updateSettings(transform: (CleanSettings) -> CleanSettings) {
        val old = vm.userPreferences.value.toCleanSettings().copy(defaultSets = currentSets, defaultPercent = currentPercent)
        val new = transform(old)
        if (new.defaultPercent != old.defaultPercent) {
            currentPercent = new.defaultPercent.coerceIn(30, 100)
            scope.launch { store.setDefaultPercent(currentPercent) }
        }
        if (new.defaultSets != old.defaultSets) {
            currentSets = new.defaultSets.coerceIn(1, 10)
            scope.launch { store.setDefaultSets(currentSets) }
        }
        if (new.unit != old.unit) vm.setWeightUnit(if (new.unit == WeightUnit.LB) AppWeightUnit.LB else AppWeightUnit.KG)
        if (new.defaultReps != old.defaultReps) vm.setDefaultRepCount(new.defaultReps.coerceIn(1, 50))
        if (new.autoplay != old.autoplay) vm.setAutoplayEnabled(new.autoplay)
        if (new.stopAtTop != old.stopAtTop) vm.setStopAtTop(new.stopAtTop)
        if (new.beeps != old.beeps) vm.setBeepsEnabled(new.beeps)
        if (new.videos != old.videos) vm.setEnableVideoPlayback(new.videos)
    }

    override val ledSchemes: List<LedScheme> = ColorSchemes.ALL.map { scheme ->
        LedScheme(scheme.name, scheme.colors.map { c -> (0xFF shl 24) or (c.r shl 16) or (c.g shl 8) or c.b })
    }

    override fun setLedScheme(index: Int) = vm.setColorScheme(index)

    private fun com.example.vitruvianredux.domain.model.UserPreferences.toCleanSettings() = CleanSettings(
        unit = if (weightUnit == AppWeightUnit.LB) WeightUnit.LB else WeightUnit.KG,
        defaultReps = defaultRepCount,
        autoplay = autoplayEnabled,
        stopAtTop = stopAtTop,
        beeps = beepsEnabled,
        videos = enableVideoPlayback
    )

    override fun switchToClassicUi() {
        scope.launch { store.setEnabled(false) }
    }

    private fun UserProfileEntity.toLifter() = Lifter(id = id, name = name, colorArgb = colorInt)

    /** Same grouping as the classic muscle-group picker (posterior chain counts as back). */
    private fun ExerciseBucket.region(): String = when (this) {
        ExerciseBucket.CHEST -> "Chest"
        ExerciseBucket.BACK, ExerciseBucket.POSTERIOR_CHAIN -> "Back"
        ExerciseBucket.SHOULDERS -> "Shoulders"
        ExerciseBucket.BICEPS, ExerciseBucket.TRICEPS -> "Arms"
        ExerciseBucket.QUADS, ExerciseBucket.HAMSTRINGS_GLUTES, ExerciseBucket.CALVES -> "Legs"
        ExerciseBucket.CORE -> "Core"
    }

    private fun ExerciseEntity.toCard() = ExerciseCard(
        id = id,
        name = name,
        muscleGroup = muscleGroups.csv().firstOrNull() ?: "",
        equipment = equipment.csv().firstOrNull() ?: "",
        cableConfig = defaultCableConfig,
        isFavorite = isFavorite,
        lastPerformedAt = lastPerformed,
        popularity = popularity,
        region = ExerciseBucket.from(muscles.csv(), movement, muscleGroups.csv())?.region() ?: "",
        muscles = muscles.csv().map(ExerciseSearch::muscleLabel),
        // A one-line summary for every exercise; the library's long how-to stays in the guide.
        description = ExerciseSearch.describe(
            name,
            muscles.csv().map(ExerciseSearch::muscleLabel),
            equipment.csv().map(ExerciseSearch::equipmentLabel),
            ExerciseBucket.from(muscles.csv(), movement, muscleGroups.csv())?.region() ?: ""
        ) + aliases.csv().takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " Also called ", postfix = ".").orEmpty()
    )

    private fun ExerciseCard.toExercise() = Exercise(
        name = name,
        muscleGroup = muscleGroup.ifBlank { "Full Body" },
        equipment = equipment,
        defaultCableConfig = runCatching { CableConfiguration.valueOf(cableConfig) }.getOrDefault(CableConfiguration.DOUBLE),
        id = id
    )

    private fun SetPlan.toWorkoutType(): WorkoutType = when (mode) {
        TrainingMode.OLD_SCHOOL -> WorkoutType.Program(ProgramMode.OldSchool)
        TrainingMode.PUMP -> WorkoutType.Program(ProgramMode.Pump)
        TrainingMode.TUT -> WorkoutType.Program(ProgramMode.TUT)
        TrainingMode.TUT_BEAST -> WorkoutType.Program(ProgramMode.TUTBeast)
        TrainingMode.ECCENTRIC_ONLY -> WorkoutType.Program(ProgramMode.EccentricOnly)
        TrainingMode.ECHO -> WorkoutType.Echo(echoLevel.toApp(), eccentricLoad(eccentricLoadPercent))
    }

    private fun EchoLevelChoice.toApp(): EchoLevel = when (this) {
        EchoLevelChoice.HARD -> EchoLevel.HARD
        EchoLevelChoice.HARDER -> EchoLevel.HARDER
        EchoLevelChoice.HARDEST -> EchoLevel.HARDEST
        EchoLevelChoice.EPIC -> EchoLevel.EPIC
    }

    private fun eccentricLoad(percent: Int): EccentricLoad =
        EccentricLoad.entries.minByOrNull { kotlin.math.abs(it.percentage - percent) } ?: EccentricLoad.LOAD_100

    private fun String.csv(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        const val BASELINE_REPS = 10
        const val BASELINE_RIR = 2.5f
    }
}

private data class LiveInputs(
    val state: WorkoutState,
    val reps: RepCount,
    val params: WorkoutParameters,
    val metric: WorkoutMetric?,
    val ranges: RepRanges?
)

private data class RoutineInputs(
    val routine: Routine?,
    val index: Int,
    val bodyweight: Boolean,
    val performer: UserProfileEntity?,
    val setDisplay: Pair<Int, Int>?
)
