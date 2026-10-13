package nl.nielsnotsmurf.cleanui.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource

private val Context.cleanUiDataStore: DataStore<Preferences> by preferencesDataStore(name = "clean_ui")

/**
 * Storage owned by the Clean UI, kept apart from upstream's Room schema so the two
 * never clash in a migration.
 *
 * Holds: whether the Clean UI is switched on, each lifter's stored 1RMs
 * (benchmark or manual), the last % used per exercise, and the trainer LED colour
 * last picked (the trainer doesn't report it, and the Clean UI takes its accent from it).
 */
class CleanUiStore private constructor(private val context: Context) {

    val isEnabled: Flow<Boolean> = context.cleanUiDataStore.data.map { it[ENABLED] ?: false }

    suspend fun setEnabled(enabled: Boolean) {
        context.cleanUiDataStore.edit { it[ENABLED] = enabled }
    }

    /** Stored 1RMs as lifterId -> exerciseId -> 1RM. */
    val storedOneRepMaxes: Flow<Map<String, Map<String, OneRepMax>>> =
        context.cleanUiDataStore.data.map { prefs ->
            val result = mutableMapOf<String, MutableMap<String, OneRepMax>>()
            prefs.asMap().forEach { (key, value) ->
                val name = key.name
                if (name.startsWith(ORM_PREFIX) && value is Float) {
                    val (lifterId, exerciseId) = splitKey(name.removePrefix(ORM_PREFIX)) ?: return@forEach
                    val source = prefs[stringPreferencesKey("$SRC_PREFIX$lifterId$SEP$exerciseId")]
                        ?.let { runCatching { OneRepMaxSource.valueOf(it) }.getOrNull() }
                        ?: OneRepMaxSource.MANUAL
                    val at = prefs[longPreferencesKey("$AT_PREFIX$lifterId$SEP$exerciseId")] ?: 0L
                    result.getOrPut(lifterId) { mutableMapOf() }[exerciseId] = OneRepMax(value, source, at)
                }
            }
            result
        }

    suspend fun saveOneRepMax(lifterId: String, exerciseId: String, oneRepMax: OneRepMax) {
        val id = "$lifterId$SEP$exerciseId"
        context.cleanUiDataStore.edit {
            it[floatPreferencesKey("$ORM_PREFIX$id")] = oneRepMax.kg
            it[stringPreferencesKey("$SRC_PREFIX$id")] = oneRepMax.source.name
            it[longPreferencesKey("$AT_PREFIX$id")] = oneRepMax.updatedAt
        }
    }

    suspend fun clearOneRepMax(lifterId: String, exerciseId: String) {
        val id = "$lifterId$SEP$exerciseId"
        context.cleanUiDataStore.edit {
            it.remove(floatPreferencesKey("$ORM_PREFIX$id"))
            it.remove(stringPreferencesKey("$SRC_PREFIX$id"))
            it.remove(longPreferencesKey("$AT_PREFIX$id"))
        }
    }

    fun lastPercent(exerciseId: String): Flow<Int?> =
        context.cleanUiDataStore.data.map { it[intPreferencesKey("$PCT_PREFIX$exerciseId")] }

    suspend fun setLastPercent(exerciseId: String, percent: Int) {
        context.cleanUiDataStore.edit { it[intPreferencesKey("$PCT_PREFIX$exerciseId")] = percent }
    }

    /** Index into the gateway's LED schemes, or null when none was picked yet. */
    val ledScheme: Flow<Int?> = context.cleanUiDataStore.data.map { it[LED_SCHEME] }

    suspend fun setLedScheme(index: Int) {
        context.cleanUiDataStore.edit { it[LED_SCHEME] = index }
    }

    private fun splitKey(id: String): Pair<String, String>? {
        val i = id.indexOf(SEP)
        if (i <= 0 || i == id.length - SEP.length) return null
        return id.substring(0, i) to id.substring(i + SEP.length)
    }

    companion object {
        private val ENABLED = booleanPreferencesKey("enabled")
        private val LED_SCHEME = intPreferencesKey("led_scheme")
        private const val ORM_PREFIX = "orm|"
        private const val SRC_PREFIX = "orm_src|"
        private const val AT_PREFIX = "orm_at|"
        private const val PCT_PREFIX = "pct|"
        // Profile ids are UUIDs or simple slugs and exercise ids are UUID-like, so this never collides.
        private const val SEP = "::"

        @Volatile private var instance: CleanUiStore? = null

        fun get(context: Context): CleanUiStore =
            instance ?: synchronized(this) {
                instance ?: CleanUiStore(context.applicationContext).also { instance = it }
            }
    }
}
