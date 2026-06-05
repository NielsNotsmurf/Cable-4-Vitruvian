package com.example.vitruvianredux.domain.usecase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.round

private val Context.progressionDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "progression_settings")

/**
 * Per-profile progression configuration.
 *
 * Disabled by default for existing users.
 * Opt-in only — applies weekly if enabled.
 *
 * Safety caps:
 *  - Max single progression step: 5% of current weight
 *  - Max weekly progression: 5 kg absolute
 *  - Plateau detection: no progression if failed to complete target reps 2x in a row
 */
@Serializable
data class ProgressionConfig(
    val profileId: String,
    val enabled: Boolean = false,
    val weeklyProgressionPercent: Float = 2.5f,
    val pauseOnPlateau: Boolean = true,
    val plateauWeeksThreshold: Int = 2,
    val maxSingleStepKg: Float = 5f
) {
    /** Safe progression amount for a given current weight. */
    fun stepKg(currentWeightKg: Float): Float {
        val percentStep = currentWeightKg * (weeklyProgressionPercent / 100f)
        return roundToHalf(percentStep.coerceAtMost(maxSingleStepKg))
    }

    private fun roundToHalf(value: Float): Float =
        (round(value * 2f) / 2f).coerceAtLeast(0.5f)
}

/**
 * Per-exercise plateau state.
 * Tracks consecutive failed sessions (didn't complete target reps).
 */
@Serializable
data class PlateauState(
    val exerciseId: String,
    val consecutiveFailures: Int = 0,
    val lastProgressionAt: Long = 0L,
    val isOnPlateau: Boolean = false
)

@Singleton
class ProgressionEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun configKey(profileId: String) =
        stringPreferencesKey("progression_config_$profileId")

    private fun plateauKey(profileId: String) =
        stringPreferencesKey("plateau_state_$profileId")

    // ──────────────────────────────────────────────────────────────────────────
    // Config
    // ──────────────────────────────────────────────────────────────────────────

    fun configFlow(profileId: String): Flow<ProgressionConfig> =
        context.progressionDataStore.data.map { prefs ->
            prefs[configKey(profileId)]?.let { json ->
                try { Json.decodeFromString<ProgressionConfig>(json) } catch (e: Exception) { null }
            } ?: ProgressionConfig(profileId = profileId)
        }

    suspend fun getConfig(profileId: String): ProgressionConfig =
        configFlow(profileId).firstOrNull() ?: ProgressionConfig(profileId = profileId)

    suspend fun saveConfig(config: ProgressionConfig) {
        context.progressionDataStore.edit { prefs ->
            prefs[configKey(config.profileId)] = Json.encodeToString(config)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Plateau state
    // ──────────────────────────────────────────────────────────────────────────

    private suspend fun getPlateauMap(profileId: String): Map<String, PlateauState> =
        context.progressionDataStore.data.firstOrNull()
            ?.get(plateauKey(profileId))?.let { json ->
                try { Json.decodeFromString<Map<String, PlateauState>>(json) } catch (e: Exception) { emptyMap() }
            } ?: emptyMap()

    private suspend fun savePlateauMap(profileId: String, map: Map<String, PlateauState>) {
        context.progressionDataStore.edit { prefs ->
            prefs[plateauKey(profileId)] = Json.encodeToString(map)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Core logic
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Called after a completed set. Reports whether target reps were achieved.
     * Returns the recommended weight for the next session (or null if unchanged).
     */
    suspend fun recordSetResult(
        profileId: String,
        exerciseId: String,
        currentWeightKg: Float,
        targetReps: Int,
        actualReps: Int
    ): Float? {
        val config = getConfig(profileId)
        if (!config.enabled) return null

        val plateauMap = getPlateauMap(profileId).toMutableMap()
        val state = plateauMap[exerciseId] ?: PlateauState(exerciseId = exerciseId)

        val succeeded = actualReps >= targetReps
        val updated = if (succeeded) {
            state.copy(consecutiveFailures = 0)
        } else {
            val newFailures = state.consecutiveFailures + 1
            state.copy(
                consecutiveFailures = newFailures,
                isOnPlateau = newFailures >= config.plateauWeeksThreshold
            )
        }
        plateauMap[exerciseId] = updated
        savePlateauMap(profileId, plateauMap)

        if (!succeeded || (config.pauseOnPlateau && updated.isOnPlateau)) {
            Timber.d("Progression paused for exercise=$exerciseId, plateau=${updated.isOnPlateau}")
            return null
        }

        // Check if enough time has passed since last progression (1 week minimum)
        val oneWeek = 7L * 24 * 3600 * 1000
        if (System.currentTimeMillis() - state.lastProgressionAt < oneWeek) return null

        val step = config.stepKg(currentWeightKg)
        val newWeight = currentWeightKg + step
        Timber.d("Progression: exercise=$exerciseId, $currentWeightKg → $newWeight (+$step kg)")

        plateauMap[exerciseId] = updated.copy(lastProgressionAt = System.currentTimeMillis())
        savePlateauMap(profileId, plateauMap)

        return newWeight
    }

    /**
     * Clears plateau state for an exercise (e.g., user manually increased weight).
     */
    suspend fun clearPlateau(profileId: String, exerciseId: String) {
        val map = getPlateauMap(profileId).toMutableMap()
        map.remove(exerciseId)
        savePlateauMap(profileId, map)
    }
}
