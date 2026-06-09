package com.example.vitruvianredux.data.baseline

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.vitruvianredux.domain.model.ExerciseBucket
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.vitruvianredux.data.baseline.SuggestionSource
import com.example.vitruvianredux.data.baseline.WeightSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.baselineDataStore: DataStore<Preferences> by preferencesDataStore(name = "baseline_strength")

/**
 * Per-profile baseline assessment results.
 * Stored in DataStore — one float per bucket per profile.
 * Profiles are completely independent.
 */
@Serializable
data class BaselineResults(
    val profileId: String,
    val completedAt: Long = 0L,
    val chestKg: Float? = null,
    val backKg: Float? = null,
    val posteriorChainKg: Float? = null,
    val quadsKg: Float? = null,
    val hamstringsGlutesKg: Float? = null,
    val shoulderPressKg: Float? = null,
    val lateralRaiseKg: Float? = null,
    val bicepsKg: Float? = null,
    val tricepsKg: Float? = null,
    val isComplete: Boolean = false
) {
    fun bucketValue(bucket: ExerciseBucket): Float? = when (bucket) {
        ExerciseBucket.CHEST -> chestKg
        ExerciseBucket.BACK -> backKg
        ExerciseBucket.POSTERIOR_CHAIN -> posteriorChainKg
        ExerciseBucket.QUADS -> quadsKg
        ExerciseBucket.HAMSTRINGS_GLUTES -> hamstringsGlutesKg
        ExerciseBucket.SHOULDERS -> shoulderPressKg ?: lateralRaiseKg
        ExerciseBucket.BICEPS -> bicepsKg
        ExerciseBucket.TRICEPS -> tricepsKg
        ExerciseBucket.CALVES -> null
        ExerciseBucket.CORE -> null
    }

    fun withBucketValue(bucket: ExerciseBucket, kg: Float): BaselineResults = when (bucket) {
        ExerciseBucket.CHEST -> copy(chestKg = kg)
        ExerciseBucket.BACK -> copy(backKg = kg)
        ExerciseBucket.POSTERIOR_CHAIN -> copy(posteriorChainKg = kg)
        ExerciseBucket.QUADS -> copy(quadsKg = kg)
        ExerciseBucket.HAMSTRINGS_GLUTES -> copy(hamstringsGlutesKg = kg)
        ExerciseBucket.SHOULDERS -> copy(shoulderPressKg = kg)
        ExerciseBucket.BICEPS -> copy(bicepsKg = kg)
        ExerciseBucket.TRICEPS -> copy(tricepsKg = kg)
        ExerciseBucket.CALVES -> this
        ExerciseBucket.CORE -> this
    }

    /** Number of anchor buckets that have been tested. */
    val testedBucketCount: Int
        get() = listOfNotNull(
            chestKg, backKg, posteriorChainKg, quadsKg, hamstringsGlutesKg,
            shoulderPressKg, lateralRaiseKg, bicepsKg, tricepsKg
        ).size

    val totalBucketCount: Int get() = 9
}

@Singleton
class BaselineRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun baselineKey(profileId: String) =
        stringPreferencesKey("baseline_results_$profileId")

    /**
     * Flow of baseline results for a profile.
     */
    fun baselineFlow(profileId: String): Flow<BaselineResults?> =
        context.baselineDataStore.data.map { prefs ->
            prefs[baselineKey(profileId)]?.let { json ->
                try {
                    Json.decodeFromString<BaselineResults>(json)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode baseline for profile=$profileId")
                    null
                }
            }
        }

    /**
     * Get baseline results for a profile (suspend, one-shot read).
     */
    suspend fun getBaseline(profileId: String): BaselineResults? {
        return baselineFlow(profileId).firstOrNull()
    }

    /**
     * Save a single bucket result during progressive assessment.
     */
    suspend fun saveBucketResult(profileId: String, bucket: ExerciseBucket, kg: Float) {
        val current = getBaseline(profileId) ?: BaselineResults(profileId = profileId)
        val updated = current
            .withBucketValue(bucket, kg)
            .copy(completedAt = System.currentTimeMillis())
        saveBaseline(updated)
    }

    /**
     * Mark the baseline as complete (all anchors tested).
     */
    suspend fun markComplete(profileId: String) {
        val current = getBaseline(profileId) ?: return
        saveBaseline(current.copy(isComplete = true, completedAt = System.currentTimeMillis()))
    }

    /**
     * Save the full baseline results object.
     */
    suspend fun saveBaseline(results: BaselineResults) {
        try {
            context.baselineDataStore.edit { prefs ->
                prefs[baselineKey(results.profileId)] = Json.encodeToString(results)
            }
            Timber.d("Saved baseline for profile=${results.profileId}, complete=${results.isComplete}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save baseline for profile=${results.profileId}")
        }
    }

    /**
     * Clear all baseline data for a profile (e.g., user wants to retake).
     */
    suspend fun clearBaseline(profileId: String) {
        context.baselineDataStore.edit { prefs ->
            prefs.remove(baselineKey(profileId))
        }
        Timber.d("Cleared baseline for profile=$profileId")
    }

    /**
     * Check whether a profile has completed any baseline tests (even partial).
     */
    suspend fun hasAnyBaseline(profileId: String): Boolean =
        getBaseline(profileId)?.testedBucketCount?.let { it > 0 } ?: false

    /**
     * Resolve the best available weight for an exercise:
     *   1. PropagationTable estimate from bucket baseline
     *   2. Anchor conservative default
     *   3. Global safe default
     *
     * Does NOT check actual history — that is the caller's responsibility.
     */
    suspend fun propagateDefault(
        profileId: String,
        exerciseId: String,
        bucket: ExerciseBucket?
    ): Float = propagateWithConfidence(profileId, exerciseId, bucket).weightKg

    /**
     * Same as propagateDefault but returns a WeightSuggestion with confidence and source.
     * Does NOT check actual history — caller must handle ACTUAL_HISTORY tier.
     */
    suspend fun propagateWithConfidence(
        profileId: String,
        exerciseId: String,
        bucket: ExerciseBucket?
    ): WeightSuggestion {
        if (bucket == ExerciseBucket.CALVES) return WeightSuggestion(
            weightKg = AnchorExerciseConfig.CALF_DEFAULT_KG,
            source = SuggestionSource.ANCHOR_CONSERVATIVE
        )
        if (bucket == null) return WeightSuggestion(
            weightKg = AnchorExerciseConfig.FIXED_SAFE_DEFAULT_KG,
            source = SuggestionSource.SAFE_DEFAULT
        )

        val baseline = getBaseline(profileId)
        val bucketKg = baseline?.bucketValue(bucket)

        if (bucketKg != null) {
            val propagated = PropagationTable.estimateDefault(exerciseId, bucket, bucketKg)
            if (propagated != null) return WeightSuggestion(
                weightKg = propagated,
                source = SuggestionSource.BUCKET_PROPAGATION
            )
            return WeightSuggestion(weightKg = bucketKg, source = SuggestionSource.BUCKET_PROPAGATION)
        }

        val anchor = AnchorExerciseConfig.anchorForBucket(bucket)
        return if (anchor != null) WeightSuggestion(
            weightKg = anchor.conservativeDefaultKg,
            source = SuggestionSource.ANCHOR_CONSERVATIVE
        ) else WeightSuggestion(
            weightKg = AnchorExerciseConfig.FIXED_SAFE_DEFAULT_KG,
            source = SuggestionSource.SAFE_DEFAULT
        )
    }
}
