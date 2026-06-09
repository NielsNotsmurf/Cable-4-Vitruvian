package com.example.vitruvianredux.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vitruvianredux.data.local.AppPreferencesBackup
import com.example.vitruvianredux.data.local.ExerciseDefaultsBackup
import com.example.vitruvianredux.domain.model.CableConfiguration
import com.example.vitruvianredux.domain.model.EccentricLoad
import com.example.vitruvianredux.domain.model.EchoLevel
import com.example.vitruvianredux.domain.model.ProgramMode
import com.example.vitruvianredux.domain.model.UserPreferences
import com.example.vitruvianredux.domain.model.WeightUnit
import com.example.vitruvianredux.domain.model.WorkoutType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Workout mode identifiers for serialization.
 * Using stable string identifiers instead of enum names or display names for forward compatibility.
 */
object WorkoutModeId {
    const val OLD_SCHOOL = "old_school"
    const val PUMP = "pump"
    const val TUT = "tut"
    const val TUT_BEAST = "tut_beast"
    const val ECCENTRIC_ONLY = "eccentric_only"
    const val ECHO = "echo"

    fun fromWorkoutType(workoutType: WorkoutType): String = when (workoutType) {
        is WorkoutType.Program -> when (workoutType.mode) {
            ProgramMode.OldSchool -> OLD_SCHOOL
            ProgramMode.Pump -> PUMP
            ProgramMode.TUT -> TUT
            ProgramMode.TUTBeast -> TUT_BEAST
            ProgramMode.EccentricOnly -> ECCENTRIC_ONLY
        }
        is WorkoutType.Echo -> ECHO
    }

    fun toWorkoutType(
        modeId: String,
        eccentricLoad: EccentricLoad = EccentricLoad.LOAD_100,
        echoLevel: EchoLevel = EchoLevel.HARDER
    ): WorkoutType = when (modeId) {
        OLD_SCHOOL -> WorkoutType.Program(ProgramMode.OldSchool)
        PUMP -> WorkoutType.Program(ProgramMode.Pump)
        TUT -> WorkoutType.Program(ProgramMode.TUT)
        TUT_BEAST -> WorkoutType.Program(ProgramMode.TUTBeast)
        ECCENTRIC_ONLY -> WorkoutType.Program(ProgramMode.EccentricOnly)
        ECHO -> WorkoutType.Echo(echoLevel, eccentricLoad)
        else -> {
            Timber.w("Unknown workout mode ID: $modeId, defaulting to OldSchool")
            WorkoutType.Program(ProgramMode.OldSchool)
        }
    }
}

/**
 * Saved settings for Just Lift mode.
 * These are stored and recalled automatically when entering Just Lift mode.
 *
 * @param version Schema version for future migration support
 * @param workoutModeId Stable identifier for workout mode (see WorkoutModeId)
 * @param weightPerCableKg Weight per cable in kilograms (always stored in KG)
 * @param weightChangePerRep Progression/regression value in KG
 * @param eccentricLoadPercentage Eccentric load percentage (0, 50, 75, 100, 125, 150)
 * @param echoLevelValue Echo level ordinal (0=Hard, 1=Harder, 2=Hardest, 3=Epic)
 */
@Serializable
data class JustLiftDefaults(
    val version: Int = 1,
    val workoutModeId: String = WorkoutModeId.OLD_SCHOOL,
    val weightPerCableKg: Float = 10f,
    val weightChangePerRep: Int = 0,
    val eccentricLoadPercentage: Int = 100,
    val echoLevelValue: Int = 1
) {
    init {
        require(weightPerCableKg > 0) { "Weight must be positive" }
        require(eccentricLoadPercentage in listOf(0, 50, 75, 100, 125, 150)) {
            "Invalid eccentric load percentage: $eccentricLoadPercentage"
        }
        require(echoLevelValue in 0..3) { "Invalid echo level value: $echoLevelValue" }
    }

    fun toWorkoutType(): WorkoutType {
        val eccentricLoad = EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100
        val echoLevel = EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
        return WorkoutModeId.toWorkoutType(workoutModeId, eccentricLoad, echoLevel)
    }

    fun getEccentricLoad(): EccentricLoad =
        EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100

    fun getEchoLevel(): EchoLevel =
        EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
}

/**
 * Saved settings for a specific exercise in Single Exercise mode.
 * Keyed by exerciseId + cableConfig (e.g., "exercise_123_DOUBLE")
 *
 * @param version Schema version for future migration support
 * @param exerciseId Unique identifier of the exercise
 * @param cableConfig Cable configuration name ("SINGLE", "DOUBLE", or "EITHER")
 * @param workoutModeId Stable identifier for workout mode (see WorkoutModeId)
 */
@Serializable
data class SingleExerciseDefaults(
    val version: Int = 1,
    val exerciseId: String,
    val cableConfig: String,
    val workoutModeId: String = WorkoutModeId.OLD_SCHOOL,
    val setReps: List<Int?> = listOf(10, 10, 10),
    val weightPerCableKg: Float = 20f,
    val setWeightsPerCableKg: List<Float> = emptyList(),
    val progressionKg: Float = 0f,
    val setRestSeconds: List<Int> = listOf(60, 60, 60),
    val perSetRestTime: Boolean = false,
    val eccentricLoadPercentage: Int = 100,
    val echoLevelValue: Int = 1,
    val duration: Int? = null,
    val isAMRAP: Boolean = false
) {
    init {
        require(exerciseId.isNotBlank()) { "Exercise ID must not be blank" }
        require(weightPerCableKg >= 0) { "Weight must be non-negative" }
        require(setReps.isNotEmpty()) { "Must have at least one set" }
        require(progressionKg in -50f..50f) { "Progression must be between -50kg and 50kg" }
        require(duration == null || duration > 0) { "Duration must be positive if set" }
        require(setWeightsPerCableKg.isEmpty() || setWeightsPerCableKg.size == setReps.size) {
            "setWeightsPerCableKg must be empty or match setReps size"
        }
        require(setRestSeconds.isEmpty() || setRestSeconds.size == setReps.size) {
            "setRestSeconds must be empty or match setReps size"
        }
    }

    fun toWorkoutType(): WorkoutType {
        val eccentricLoad = EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100
        val echoLevel = EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
        return WorkoutModeId.toWorkoutType(workoutModeId, eccentricLoad, echoLevel)
    }

    fun getCableConfiguration(): CableConfiguration =
        CableConfiguration.entries.find { it.name == cableConfig }
            ?: CableConfiguration.DOUBLE

    fun getEccentricLoad(): EccentricLoad =
        EccentricLoad.entries.find { it.percentage == eccentricLoadPercentage }
            ?: EccentricLoad.LOAD_100

    fun getEchoLevel(): EchoLevel =
        EchoLevel.entries.find { it.levelValue == echoLevelValue }
            ?: EchoLevel.HARDER
}

/**
 * Manager for user preferences using DataStore
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {
    /** Mutex to prevent concurrent save race conditions */
    private val exerciseDefaultsSaveMutex = Mutex()

    /** Maximum number of exercise defaults before triggering cleanup warning */
    private val MAX_EXERCISE_DEFAULTS_SIZE = 200

    private val WEIGHT_UNIT_KEY = stringPreferencesKey("weight_unit")
    private val AUTOPLAY_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("autoplay_enabled")
    private val STOP_AT_TOP_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("stop_at_top")
    private val ENABLE_VIDEO_PLAYBACK_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("enable_video_playback")
    private val BEEPS_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("beeps_enabled")
    private val DEFAULT_REP_COUNT_KEY = intPreferencesKey("default_rep_count")

    /**
     * Flow of user preferences
     */
    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .map { preferences ->
            val weightUnitString = preferences[WEIGHT_UNIT_KEY]
            val weightUnit = try {
                weightUnitString?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Invalid weight unit in preferences: $weightUnitString, defaulting to KG")
                WeightUnit.KG
            }
            val autoplayEnabled = preferences[AUTOPLAY_ENABLED_KEY] ?: true
            val stopAtTop = preferences[STOP_AT_TOP_KEY] ?: false
            val enableVideoPlayback = preferences[ENABLE_VIDEO_PLAYBACK_KEY] ?: true
            val beepsEnabled = preferences[BEEPS_ENABLED_KEY] ?: true
            val defaultRepCount = preferences[DEFAULT_REP_COUNT_KEY] ?: 10

            UserPreferences(
                weightUnit = weightUnit,
                autoplayEnabled = autoplayEnabled,
                stopAtTop = stopAtTop,
                enableVideoPlayback = enableVideoPlayback,
                beepsEnabled = beepsEnabled,
                defaultRepCount = defaultRepCount
            )
        }

    /**
     * Set the weight unit preference
     */
    suspend fun setWeightUnit(unit: WeightUnit) {
        context.dataStore.edit { preferences ->
            preferences[WEIGHT_UNIT_KEY] = unit.name
        }
        Timber.d("Weight unit preference set to: ${unit.name}")
    }

    /**
     * Set the autoplay enabled preference
     */
    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTOPLAY_ENABLED_KEY] = enabled
        }
        Timber.d("Autoplay enabled preference set to: $enabled")
    }

    /**
     * Set the stop at top preference
     */
    suspend fun setStopAtTop(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STOP_AT_TOP_KEY] = enabled
        }
        Timber.d("Stop at top preference set to: $enabled")
    }

    /**
     * Set the enable video playback preference
     */
    suspend fun setEnableVideoPlayback(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ENABLE_VIDEO_PLAYBACK_KEY] = enabled
        }
        Timber.d("Enable video playback preference set to: $enabled")
    }

    /**
     * Set the beeps enabled preference (workout audio cues)
     */
    suspend fun setBeepsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BEEPS_ENABLED_KEY] = enabled
        }
        Timber.d("Beeps enabled preference set to: $enabled")
    }

    /**
     * Set the default rep count used as the starting point for new exercise configurations.
     */
    suspend fun setDefaultRepCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_REP_COUNT_KEY] = count.coerceIn(1, 30)
        }
        Timber.d("Default rep count set to: $count")
    }

    /**
     * Get the current default rep count (one-shot read).
     */
    suspend fun getDefaultRepCount(): Int {
        return context.dataStore.data.firstOrNull()?.get(DEFAULT_REP_COUNT_KEY) ?: 10
    }

    // ========== Backup / Restore helpers ==========

    /**
     * Export a snapshot of all app-level preferences (non-exercise-specific).
     */
    suspend fun exportPreferencesSnapshot(): AppPreferencesBackup {
        val prefs = context.dataStore.data.firstOrNull()
        val weightUnit = try {
            prefs?.get(WEIGHT_UNIT_KEY)?.let { WeightUnit.valueOf(it) } ?: WeightUnit.KG
        } catch (e: IllegalArgumentException) { WeightUnit.KG }
        return AppPreferencesBackup(
            weightUnit = weightUnit.name,
            autoplayEnabled = prefs?.get(AUTOPLAY_ENABLED_KEY) ?: true,
            stopAtTop = prefs?.get(STOP_AT_TOP_KEY) ?: false,
            enableVideoPlayback = prefs?.get(ENABLE_VIDEO_PLAYBACK_KEY) ?: true,
            beepsEnabled = prefs?.get(BEEPS_ENABLED_KEY) ?: true,
            defaultRepCount = prefs?.get(DEFAULT_REP_COUNT_KEY) ?: 10
        )
    }

    /**
     * Restore app-level preferences from backup.
     * Only writes keys that the device has not yet set (skip-if-exists per key).
     */
    suspend fun restorePreferencesSnapshot(backup: AppPreferencesBackup) {
        context.dataStore.edit { prefs ->
            if (prefs[WEIGHT_UNIT_KEY] == null) prefs[WEIGHT_UNIT_KEY] = backup.weightUnit
            if (prefs[AUTOPLAY_ENABLED_KEY] == null) prefs[AUTOPLAY_ENABLED_KEY] = backup.autoplayEnabled
            if (prefs[STOP_AT_TOP_KEY] == null) prefs[STOP_AT_TOP_KEY] = backup.stopAtTop
            if (prefs[ENABLE_VIDEO_PLAYBACK_KEY] == null) prefs[ENABLE_VIDEO_PLAYBACK_KEY] = backup.enableVideoPlayback
            if (prefs[BEEPS_ENABLED_KEY] == null) prefs[BEEPS_ENABLED_KEY] = backup.beepsEnabled
            if (prefs[DEFAULT_REP_COUNT_KEY] == null) prefs[DEFAULT_REP_COUNT_KEY] = backup.defaultRepCount
        }
        Timber.d("Preferences snapshot restored")
    }

    /**
     * Export all exercise defaults for a profile as raw JSON strings (for backup).
     */
    suspend fun exportExerciseDefaults(profileId: String): ExerciseDefaultsBackup {
        val prefs = context.dataStore.data.firstOrNull()
        val justLiftJson = prefs?.get(justLiftDefaultsKey(profileId))
            ?: if (profileId == "default") prefs?.get(JUST_LIFT_DEFAULTS_KEY_LEGACY) else null
        val singleExerciseJson = prefs?.get(singleExerciseDefaultsMapKey(profileId))
            ?: if (profileId == "default") prefs?.get(SINGLE_EXERCISE_DEFAULTS_KEY_LEGACY) else null
        return ExerciseDefaultsBackup(
            profileId = profileId,
            justLiftDefaultsJson = justLiftJson,
            singleExerciseDefaultsMapJson = singleExerciseJson
        )
    }

    /**
     * Restore exercise defaults for a profile from backup.
     * Only writes if the profile currently has no data for that key (skip-if-exists).
     */
    suspend fun restoreExerciseDefaults(backup: ExerciseDefaultsBackup) {
        context.dataStore.edit { prefs ->
            val profileKey = justLiftDefaultsKey(backup.profileId)
            if (prefs[profileKey] == null && backup.justLiftDefaultsJson != null) {
                prefs[profileKey] = backup.justLiftDefaultsJson
            }
            val mapKey = singleExerciseDefaultsMapKey(backup.profileId)
            if (prefs[mapKey] == null && backup.singleExerciseDefaultsMapJson != null) {
                prefs[mapKey] = backup.singleExerciseDefaultsMapJson
            }
        }
        Timber.d("Exercise defaults restored for profile=${backup.profileId}")
    }

    // ========== Just Lift Defaults ==========

    private val JUST_LIFT_DEFAULTS_KEY_LEGACY = stringPreferencesKey("just_lift_defaults")
    private fun justLiftDefaultsKey(profileId: String) = stringPreferencesKey("just_lift_defaults_$profileId")

    /**
     * Save Just Lift mode defaults, keyed by profileId
     */
    suspend fun saveJustLiftDefaults(defaults: JustLiftDefaults, profileId: String = "default") {
        context.dataStore.edit { preferences ->
            preferences[justLiftDefaultsKey(profileId)] = Json.encodeToString(defaults)
        }
        Timber.d("Just Lift defaults saved for profile=$profileId: mode=${defaults.workoutModeId}, weight=${defaults.weightPerCableKg}kg")
    }

    /**
     * Load Just Lift mode defaults for a profile.
     * Falls back to the legacy key for the default profile (backward compatibility).
     */
    suspend fun getJustLiftDefaults(profileId: String = "default"): JustLiftDefaults? {
        return try {
            context.dataStore.data.firstOrNull()?.let { preferences ->
                preferences[justLiftDefaultsKey(profileId)]?.let { json ->
                    Json.decodeFromString<JustLiftDefaults>(json)
                } ?: if (profileId == "default") {
                    preferences[JUST_LIFT_DEFAULTS_KEY_LEGACY]?.let { json ->
                        Json.decodeFromString<JustLiftDefaults>(json)
                    }
                } else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Just Lift defaults for profile=$profileId, returning null")
            null
        }
    }

    // ========== Single Exercise Defaults (per exercise+equipment) ==========

    private val SINGLE_EXERCISE_DEFAULTS_KEY_LEGACY = stringPreferencesKey("single_exercise_defaults_map")
    private fun singleExerciseDefaultsMapKey(profileId: String) = stringPreferencesKey("single_exercise_defaults_map_$profileId")

    /**
     * Generate a unique key for exercise + cable config combination
     */
    private fun getExerciseKey(exerciseId: String, cableConfig: String): String {
        return "${exerciseId}_${cableConfig}"
    }

    /**
     * Save Single Exercise mode defaults for a specific exercise+equipment combination, keyed by profileId.
     */
    suspend fun saveSingleExerciseDefaults(defaults: SingleExerciseDefaults, profileId: String = "default") {
        exerciseDefaultsSaveMutex.withLock {
            val exerciseKey = getExerciseKey(defaults.exerciseId, defaults.cableConfig)
            val storeKey = singleExerciseDefaultsMapKey(profileId)
            context.dataStore.edit { preferences ->
                val existingJson = preferences[storeKey]
                val existingMap: MutableMap<String, SingleExerciseDefaults> = if (existingJson != null) {
                    try {
                        Json.decodeFromString<Map<String, SingleExerciseDefaults>>(existingJson).toMutableMap()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse existing exercise defaults for profile=$profileId - resetting map")
                        mutableMapOf()
                    }
                } else {
                    mutableMapOf()
                }
                existingMap[exerciseKey] = defaults
                if (existingMap.size > MAX_EXERCISE_DEFAULTS_SIZE) {
                    Timber.w("Exercise defaults map for profile=$profileId has ${existingMap.size} entries")
                }
                preferences[storeKey] = Json.encodeToString(existingMap)
            }
            Timber.d("Single Exercise defaults saved for profile=$profileId: exerciseId=${defaults.exerciseId}, cableConfig=${defaults.cableConfig}")
        }
    }

    /**
     * Load Single Exercise mode defaults for a specific exercise+equipment+profile combination.
     * Falls back to the legacy key for the default profile (backward compatibility).
     */
    suspend fun getSingleExerciseDefaults(exerciseId: String, cableConfig: String, profileId: String = "default"): SingleExerciseDefaults? {
        val exerciseKey = getExerciseKey(exerciseId, cableConfig)
        val storeKey = singleExerciseDefaultsMapKey(profileId)
        return try {
            context.dataStore.data.firstOrNull()?.let { preferences ->
                // Try profile-specific key first
                val profileJson = preferences[storeKey]
                val fromProfile = profileJson?.let { json ->
                    try {
                        Json.decodeFromString<Map<String, SingleExerciseDefaults>>(json)[exerciseKey]
                    } catch (e: Exception) { null }
                }
                if (fromProfile != null) return@let fromProfile
                // Fall back to legacy key for default profile
                if (profileId == "default") {
                    preferences[SINGLE_EXERCISE_DEFAULTS_KEY_LEGACY]?.let { json ->
                        try {
                            Json.decodeFromString<Map<String, SingleExerciseDefaults>>(json)[exerciseKey]
                        } catch (e: Exception) { null }
                    }
                } else null
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load Single Exercise defaults for $exerciseKey profile=$profileId, returning null")
            null
        }
    }

    /**
     * Clear all saved Single Exercise defaults (legacy key + all profile keys not tracked here,
     * but at least clears the legacy key).
     */
    suspend fun clearAllSingleExerciseDefaults() {
        context.dataStore.edit { preferences ->
            preferences.remove(SINGLE_EXERCISE_DEFAULTS_KEY_LEGACY)
        }
        Timber.d("All Single Exercise defaults cleared (legacy key)")
    }

    /**
     * Clear Just Lift defaults for a profile (and the legacy key for default).
     */
    suspend fun clearJustLiftDefaults(profileId: String = "default") {
        context.dataStore.edit { preferences ->
            preferences.remove(justLiftDefaultsKey(profileId))
            if (profileId == "default") preferences.remove(JUST_LIFT_DEFAULTS_KEY_LEGACY)
        }
        Timber.d("Just Lift defaults cleared for profile=$profileId")
    }
}
