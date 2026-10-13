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
import com.example.vitruvianredux.domain.model.resolveDefaultCableConfig
import com.example.vitruvianredux.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import nl.nielsnotsmurf.cleanui.data.CleanUiStore
import nl.nielsnotsmurf.cleanui.gateway.TrainerGateway
import nl.nielsnotsmurf.cleanui.logic.LoadCalculator
import nl.nielsnotsmurf.cleanui.model.EchoLevelChoice
import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import nl.nielsnotsmurf.cleanui.model.LastPlan
import nl.nielsnotsmurf.cleanui.model.SetKind
import nl.nielsnotsmurf.cleanui.model.SetSpec
import nl.nielsnotsmurf.cleanui.model.ExerciseGuide
import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.Lifter
import nl.nielsnotsmurf.cleanui.model.SetPlan
import nl.nielsnotsmurf.cleanui.model.TestAnchor
import nl.nielsnotsmurf.cleanui.model.TrainerConnection
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import timber.log.Timber
import java.util.UUID
import com.example.vitruvianredux.domain.model.WeightUnit as AppWeightUnit

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
        vm.profiles.map { profiles -> profiles.map { it.toLifter() } }

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

    override fun startExercise(plan: SetPlan, onStarted: () -> Unit, onFailed: (String) -> Unit) {
        scope.launch {
            try {
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
                val routineExercise = RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    exercise = exercise,
                    cableConfig = cableConfig,
                    orderIndex = 0,
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
                vm.loadRoutine(
                    Routine(
                        id = "${MainViewModel.TEMP_SINGLE_EXERCISE_PREFIX}${UUID.randomUUID()}",
                        name = "Single Exercise: ${exercise.name}",
                        description = "Started from the Clean UI",
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
                Timber.e(e, "Clean UI failed to start exercise")
                onFailed("Couldn't start this exercise: ${e.message}")
            }
        }
    }

    override val oneRepMaxTestExercises: List<TestAnchor> =
        AnchorExerciseConfig.ANCHORS.map { TestAnchor(it.exerciseId, it.conservativeDefaultKg) }

    override suspend fun exerciseGuide(exerciseId: String): ExerciseGuide? {
        val entity = vm.exerciseRepository.getExerciseById(exerciseId) ?: return null
        val videos = runCatching { vm.exerciseRepository.getVideos(exerciseId) }.getOrDefault(emptyList())
        val video = videos.firstOrNull { it.isTutorial } ?: videos.firstOrNull { it.angle == "FRONT" } ?: videos.firstOrNull()
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
                    setReps = listOf(1),
                    weightPerCableKg = kg,
                    progressionKg = 0f,
                    setRestSeconds = listOf(0),
                    workoutType = WorkoutType.Program(ProgramMode.OldSchool),
                    eccentricLoad = EccentricLoad.LOAD_100,
                    echoLevel = EchoLevel.HARDER
                )
                vm.loadRoutine(
                    Routine(
                        id = "${MainViewModel.TEMP_SINGLE_EXERCISE_PREFIX}${UUID.randomUUID()}",
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
        region = ExerciseBucket.from(muscles.csv(), movement, muscleGroups.csv())?.region() ?: ""
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
