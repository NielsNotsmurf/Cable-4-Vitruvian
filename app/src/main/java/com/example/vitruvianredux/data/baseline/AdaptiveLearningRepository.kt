package com.example.vitruvianredux.data.baseline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vitruvianredux.data.local.SuggestionLogEntity
import com.example.vitruvianredux.data.local.dao.SuggestionLogDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.round

private val Context.adaptiveDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "adaptive_learning")

@Serializable
private data class CorrectionEntry(
    val factor: Float = 1.0f,
    val sampleCount: Int = 0,
    val lastUpdated: Long = 0L
)

/**
 * Learns from actual vs. suggested weight deltas and applies per-exercise correction factors.
 *
 * Correction lifecycle:
 *   1. suggestExerciseWeight() calls logSuggestion() — stores pending suggestion_log row
 *   2. saveWorkoutSession() calls recordObservation() — updates log row + adjusts EMA factor
 *   3. applyCorrectionIfAvailable() multiplies propagated suggestions by the learned factor
 *
 * EMA formula: newFactor = 0.75 × oldFactor + 0.25 × (actual / suggested)
 * Factor only applied after ≥ 3 observations and when magnitude > 5%.
 * Factor clamped to [0.5, 2.0] to prevent runaway adjustments.
 */
@Singleton
class AdaptiveLearningRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val suggestionLogDao: SuggestionLogDao
) {

    private fun correctionKey(profileId: String) =
        stringPreferencesKey("corrections_$profileId")

    private suspend fun getCorrectionMap(profileId: String): Map<String, CorrectionEntry> {
        val prefs = context.adaptiveDataStore.data.firstOrNull() ?: return emptyMap()
        return prefs[correctionKey(profileId)]?.let { json ->
            try { Json.decodeFromString(json) } catch (e: Exception) { emptyMap() }
        } ?: emptyMap()
    }

    private suspend fun saveCorrectionMap(profileId: String, map: Map<String, CorrectionEntry>) {
        context.adaptiveDataStore.edit { prefs ->
            prefs[correctionKey(profileId)] = Json.encodeToString(map)
        }
    }

    /**
     * If there is a learned correction factor (≥3 samples, >5% effect), apply it to the suggestion.
     * Returns the original suggestion unchanged if history is the source or data is insufficient.
     */
    suspend fun applyCorrectionIfAvailable(
        profileId: String,
        exerciseId: String,
        suggestion: WeightSuggestion
    ): WeightSuggestion {
        if (suggestion.source == SuggestionSource.ACTUAL_HISTORY) return suggestion
        val entry = getCorrectionMap(profileId)[exerciseId] ?: return suggestion
        if (entry.sampleCount < 3) return suggestion
        val factor = entry.factor.coerceIn(0.5f, 2.0f)
        if (abs(factor - 1.0f) < 0.05f) return suggestion
        val corrected = roundToHalf(suggestion.weightKg * factor)
        Timber.d("Adaptive correction: $exerciseId ×$factor ${suggestion.weightKg}→$corrected")
        return WeightSuggestion(weightKg = corrected, source = SuggestionSource.ADAPTIVE_CORRECTED)
    }

    /**
     * Called after a workout completes. Finds the most recent pending suggestion log for
     * this exercise (within 4 hours), updates it with the actual weight, then adjusts the EMA.
     */
    suspend fun recordObservation(
        profileId: String,
        exerciseId: String,
        actualKg: Float
    ) {
        if (actualKg <= 0f) return
        val since = System.currentTimeMillis() - 4L * 3600 * 1000
        val pending = suggestionLogDao.findPendingLog(profileId, exerciseId, since) ?: return

        val suggestedKg = pending.suggestedWeightKg
        if (suggestedKg <= 0f) return

        suggestionLogDao.updateActual(pending.id, actualKg, actualKg - suggestedKg)

        val ratio = actualKg / suggestedKg
        if (abs(ratio - 1.0f) < 0.10f) return  // Within 10% — treat as noise

        val map = getCorrectionMap(profileId).toMutableMap()
        val existing = map[exerciseId] ?: CorrectionEntry()
        val newFactor = if (existing.sampleCount == 0) ratio
                        else 0.75f * existing.factor + 0.25f * ratio
        map[exerciseId] = CorrectionEntry(
            factor = newFactor.coerceIn(0.5f, 2.0f),
            sampleCount = existing.sampleCount + 1,
            lastUpdated = System.currentTimeMillis()
        )
        saveCorrectionMap(profileId, map)
        Timber.d("Adaptive learning: $exerciseId ratio=$ratio newFactor=$newFactor samples=${existing.sampleCount + 1}")
    }

    /**
     * Logs a weight suggestion to the analytics table so actual outcome can be correlated later.
     */
    suspend fun logSuggestion(
        profileId: String,
        exerciseId: String,
        suggestion: WeightSuggestion,
        mode: String
    ) {
        try {
            suggestionLogDao.insert(
                SuggestionLogEntity(
                    timestamp = System.currentTimeMillis(),
                    profileId = profileId,
                    exerciseId = exerciseId,
                    mode = mode,
                    suggestedWeightKg = suggestion.weightKg,
                    confidence = suggestion.confidence,
                    source = suggestion.source.name
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to log suggestion for $exerciseId")
        }
    }

    /**
     * Export correction data for a profile as a JSON string (for backup).
     * Returns null if no data exists.
     */
    suspend fun exportCorrectionDataAsJson(profileId: String): String? {
        val map = getCorrectionMap(profileId)
        return if (map.isEmpty()) null else Json.encodeToString(map)
    }

    /**
     * Restore correction data for a profile from a JSON string.
     * Skips if the profile already has any correction data.
     */
    suspend fun importCorrectionDataIfEmpty(profileId: String, json: String) {
        val existing = getCorrectionMap(profileId)
        if (existing.isEmpty()) {
            try {
                val map: Map<String, CorrectionEntry> = Json.decodeFromString(json)
                saveCorrectionMap(profileId, map)
                Timber.d("Adaptive corrections restored for profile=$profileId, ${map.size} entries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to restore adaptive corrections for profile=$profileId")
            }
        } else {
            Timber.d("Adaptive corrections already exist for profile=$profileId, skipping restore")
        }
    }

    private fun roundToHalf(value: Float): Float =
        (round(value * 2f) / 2f).coerceAtLeast(1f)
}
