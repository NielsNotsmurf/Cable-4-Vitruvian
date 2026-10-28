package nl.nielsnotsmurf.cleanui.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.nielsnotsmurf.cleanui.logic.OneRepMaxMap
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource


/**
 * Storage owned by the Clean UI, kept apart from upstream's Room schema so the two
 * never clash in a migration.
 *
 * Holds: whether the Clean UI is switched on, each lifter's stored 1RMs
 * (benchmark or manual), the last % used per exercise, the trainer LED colour
 * last picked (the trainer doesn't report it, and the Clean UI takes its accent from it),
 * and the local accounts: whether onboarding ran, each profile's PIN hash, who signed in
 * last, and whether each profile has had the app tour.
 */
class CleanUiStore(
    private val kv: KeyValueStore,
    /** The bundled one_rep_max_map.json, or null when it can't be read. */
    private val oneRepMaxJson: () -> String?
) {

    /** Exercise -> anchor ratios for estimating 1RMs of exercises a lifter hasn't tested. */
    val oneRepMaxMap: OneRepMaxMap by lazy {
        runCatching {
            OneRepMaxMap.parse(oneRepMaxJson()!!)
        }.getOrDefault(OneRepMaxMap.EMPTY)
    }

    /** On unless someone switched to the classic layout. */
    val isEnabled: Flow<Boolean> = kv.data.map { it[ENABLED] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        kv.edit { it[ENABLED] = enabled }
    }

    /** Stored 1RMs as lifterId -> exerciseId -> 1RM. */
    val storedOneRepMaxes: Flow<Map<String, Map<String, OneRepMax>>> =
        kv.data.map { prefs ->
            val result = mutableMapOf<String, MutableMap<String, OneRepMax>>()
            prefs.forEach { (name, value) ->
                if (name.startsWith(ORM_PREFIX) && value is Float) {
                    val (lifterId, exerciseId) = splitKey(name.removePrefix(ORM_PREFIX)) ?: return@forEach
                    val source = prefs[Key<String>("$SRC_PREFIX$lifterId$SEP$exerciseId")]
                        ?.let { runCatching { OneRepMaxSource.valueOf(it) }.getOrNull() }
                        ?: OneRepMaxSource.MANUAL
                    val at = prefs[Key<Long>("$AT_PREFIX$lifterId$SEP$exerciseId")] ?: 0L
                    result.getOrPut(lifterId) { mutableMapOf() }[exerciseId] = OneRepMax(value, source, at)
                }
            }
            result
        }

    suspend fun saveOneRepMax(lifterId: String, exerciseId: String, oneRepMax: OneRepMax) {
        val id = "$lifterId$SEP$exerciseId"
        kv.edit {
            it[Key<Float>("$ORM_PREFIX$id")] = oneRepMax.kg
            it[Key<String>("$SRC_PREFIX$id")] = oneRepMax.source.name
            it[Key<Long>("$AT_PREFIX$id")] = oneRepMax.updatedAt
        }
    }

    suspend fun clearOneRepMax(lifterId: String, exerciseId: String) {
        val id = "$lifterId$SEP$exerciseId"
        kv.edit {
            it.remove(Key<Float>("$ORM_PREFIX$id"))
            it.remove(Key<String>("$SRC_PREFIX$id"))
            it.remove(Key<Long>("$AT_PREFIX$id"))
        }
    }

    fun lastPercent(exerciseId: String): Flow<Int?> =
        kv.data.map { it[Key<Int>("$PCT_PREFIX$exerciseId")] }

    suspend fun setLastPercent(exerciseId: String, percent: Int) {
        kv.edit { it[Key<Int>("$PCT_PREFIX$exerciseId")] = percent }
    }

    /** Sets per exercise from Settings; 3 until changed. */
    val defaultSets: Flow<Int> = kv.data.map { it[DEFAULT_SETS] ?: 3 }

    val warmupReps: Flow<Int> = kv.data.map { it[WARMUP_REPS] ?: 3 }

    suspend fun setWarmupReps(reps: Int) {
        kv.edit { it[WARMUP_REPS] = reps.coerceIn(1, 3) }
    }

    suspend fun setDefaultSets(sets: Int) {
        kv.edit { it[DEFAULT_SETS] = sets.coerceIn(1, 10) }
    }

    /** Training % of 1RM from Settings; 60 until changed. */
    val defaultPercent: Flow<Int> = kv.data.map { it[DEFAULT_PERCENT] ?: 60 }

    suspend fun setDefaultPercent(percent: Int) {
        kv.edit { it[DEFAULT_PERCENT] = percent.coerceIn(30, 100) }
    }

    /** Index into the gateway's LED schemes, or null when none was picked yet. */
    val ledScheme: Flow<Int?> = kv.data.map { it[LED_SCHEME] }

    suspend fun setLedScheme(index: Int) {
        kv.edit { it[LED_SCHEME] = index }
    }

    /** Menu and button sounds; on until switched off. */
    val uiSounds: Flow<Boolean> = kv.data.map { it[UI_SOUNDS] ?: true }

    suspend fun setUiSounds(on: Boolean) {
        kv.edit { it[UI_SOUNDS] = on }
    }

    /** True once the first-launch onboarding has been finished. */
    val onboarded: Flow<Boolean> = kv.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded(done: Boolean) {
        kv.edit { it[ONBOARDED] = done }
    }

    /** PIN hashes (see PinLock) by profile id. Profiles without a PIN are missing. */
    val pinHashes: Flow<Map<String, String>> = prefixed(PIN_PREFIX)

    suspend fun setPinHash(lifterId: String, hash: String?) {
        kv.edit {
            val key = Key<String>("$PIN_PREFIX$lifterId")
            if (hash == null) it.remove(key) else it[key] = hash
        }
    }

    /** The profile that signed in last, or null. */
    val lastAccount: Flow<String?> = kv.data.map { it[LAST_ACCOUNT] }

    suspend fun setLastAccount(lifterId: String?) {
        kv.edit { if (lifterId == null) it.remove(LAST_ACCOUNT) else it[LAST_ACCOUNT] = lifterId }
    }

    /** App tour status by profile id: "done:<epoch ms>" or "skipped:<epoch ms>". */
    val tourStatus: Flow<Map<String, String>> = prefixed(TOUR_PREFIX)

    suspend fun setTourStatus(lifterId: String, status: String?) {
        kv.edit {
            val key = Key<String>("$TOUR_PREFIX$lifterId")
            if (status == null) it.remove(key) else it[key] = status
        }
    }

    private fun prefixed(prefix: String): Flow<Map<String, String>> =
        kv.data.map { prefs ->
            prefs.mapNotNull { (name, value) ->
                if (name.startsWith(prefix) && value is String) name.removePrefix(prefix) to value else null
            }.toMap()
        }

    private fun splitKey(id: String): Pair<String, String>? {
        val i = id.indexOf(SEP)
        if (i <= 0 || i == id.length - SEP.length) return null
        return id.substring(0, i) to id.substring(i + SEP.length)
    }

    companion object {
        private val ENABLED = Key<Boolean>("enabled")
        private val LED_SCHEME = Key<Int>("led_scheme")
        private val UI_SOUNDS = Key<Boolean>("ui_sounds")
        private val DEFAULT_SETS = Key<Int>("default_sets")
        private val WARMUP_REPS = Key<Int>("warmup_reps")
        private val DEFAULT_PERCENT = Key<Int>("default_percent")
        private val ONBOARDED = Key<Boolean>("onboarded")
        private val LAST_ACCOUNT = Key<String>("last_account")
        private const val PIN_PREFIX = "pin|"
        private const val TOUR_PREFIX = "tour|"
        private const val ORM_PREFIX = "orm|"
        private const val SRC_PREFIX = "orm_src|"
        private const val AT_PREFIX = "orm_at|"
        private const val PCT_PREFIX = "pct|"
        // Profile ids are UUIDs or simple slugs and exercise ids are UUID-like, so this never collides.
        private const val SEP = "::"
    }
}
