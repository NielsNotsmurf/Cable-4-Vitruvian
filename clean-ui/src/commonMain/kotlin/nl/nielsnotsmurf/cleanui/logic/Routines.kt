package nl.nielsnotsmurf.cleanui.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import nl.nielsnotsmurf.cleanui.model.TrainingMode
import kotlin.math.max

/** How a routine's load goes up by itself. It is checked each time the routine is finished. */
enum class Progression {
    /** Only by hand. */
    OFF,
    /** Once every [Routine.everyWeeks] weeks. */
    WEEKS,
    /** After a session where every set reached the target reps. */
    ALL_REPS
}

/**
 * What one exercise of a routine (or built workout) does differently from the routine's own
 * mode, sets and reps. A null field follows the routine.
 */
data class ExerciseSettings(
    val mode: TrainingMode? = null,
    val sets: Int? = null,
    val reps: Int? = null
) {
    val isDefault: Boolean get() = mode == null && sets == null && reps == null
}

/**
 * A saved workout: its exercises, sets, reps and load as % of each lifter's 1RM, so the loads
 * follow the 1RM test like everywhere else. Shared by everyone on this device.
 */
data class Routine(
    val id: String,
    val name: String,
    val exerciseIds: List<String>,
    val percent: Int = 60,
    val sets: Int = 3,
    val reps: Int = 10,
    /** "lifterId::exerciseId" -> load set by hand; it moves along with the %. */
    val overridesKg: Map<String, Float> = emptyMap(),
    val progression: Progression = Progression.OFF,
    /** Points of 1RM % added per step. */
    val stepPercent: Int = 2,
    val everyWeeks: Int = 2,
    /** When the % last went up by itself, or when progression was (re)set; the weekly clock runs from here. */
    val lastRaisedAt: Long = 0L,
    val lastDoneAt: Long? = null,
    /** What the last automatic step did, shown on the routine ("Raised to 64% on 3 Nov"). */
    val note: String? = null,
    /** The base training mode; each exercise can use another one in [perExercise]. */
    val mode: TrainingMode = TrainingMode.OLD_SCHOOL,
    /** exerciseId -> its own mode, sets or reps. */
    val perExercise: Map<String, ExerciseSettings> = emptyMap()
)

object Routines {
    const val MIN_PERCENT = 30
    const val MAX_PERCENT = 100
    const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    val STEPS = listOf(1, 2, 3, 5)
    val WEEK_OPTIONS = listOf(1, 2, 3, 4, 6, 8)

    /**
     * Moves the % to [percent] and every hand-set load by the same ratio, so "increase all
     * weights" really moves everything. Loads stay on the trainer's 0.5 kg steps.
     */
    fun scaleOverrides(overrides: Map<String, Float>, from: Int, to: Int): Map<String, Float> {
        if (from == to || from <= 0) return overrides
        return overrides.mapValues { (_, kg) ->
            LoadCalculator.roundToStep(kg * to / from).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
        }
    }

    /** The routine one progression step up (or down for a negative [steps]). */
    fun raise(routine: Routine, steps: Int, now: Long): Routine {
        val to = (routine.percent + steps * routine.stepPercent).coerceIn(MIN_PERCENT, MAX_PERCENT)
        return routine.copy(
            percent = to,
            overridesKg = scaleOverrides(routine.overridesKg, routine.percent, to),
            lastRaisedAt = now
        )
    }

    /**
     * Whether finishing the routine now earns a step up. [setReps] are the reps of every set
     * done in the session; [expectedSets] is how many the routine has (exercises × sets × lifters).
     * [targets], when given, is each set's own target reps (same order as [setReps]).
     */
    fun earnsStep(
        routine: Routine,
        now: Long,
        setReps: List<Int>,
        expectedSets: Int,
        targets: List<Int>? = null
    ): Boolean = when (routine.progression) {
        Progression.OFF -> false
        Progression.WEEKS -> now - routine.lastRaisedAt >= routine.everyWeeks * WEEK_MS
        Progression.ALL_REPS -> setReps.size >= max(1, expectedSets) &&
            setReps.indices.all { i -> setReps[i] >= (targets?.getOrNull(i) ?: routine.reps) }
    } && routine.percent < MAX_PERCENT

    /** The mode, sets and reps [exerciseId] really uses in [routine]-like settings. */
    fun modeFor(base: TrainingMode, perExercise: Map<String, ExerciseSettings>, exerciseId: String): TrainingMode =
        perExercise[exerciseId]?.mode ?: base

    fun setsFor(base: Int, perExercise: Map<String, ExerciseSettings>, exerciseId: String): Int =
        perExercise[exerciseId]?.sets ?: base

    fun repsFor(base: Int, perExercise: Map<String, ExerciseSettings>, exerciseId: String): Int =
        perExercise[exerciseId]?.reps ?: base

    /** One line on how the routine progresses, for the list and the plan screen. */
    fun describe(routine: Routine): String = when (routine.progression) {
        Progression.OFF -> "Progression off"
        Progression.WEEKS -> "+${routine.stepPercent}% every ${routine.everyWeeks} week" + if (routine.everyWeeks == 1) "" else "s"
        Progression.ALL_REPS -> "+${routine.stepPercent}% after a session with all reps done"
    }

    fun encode(routines: List<Routine>): String = buildJsonArray {
        routines.forEach { r ->
            add(buildJsonObject {
                put("id", r.id)
                put("name", r.name)
                put("exercises", JsonArray(r.exerciseIds.map(::JsonPrimitive)))
                put("percent", r.percent)
                put("sets", r.sets)
                put("reps", r.reps)
                put("overrides", JsonObject(r.overridesKg.mapValues { JsonPrimitive(it.value) }))
                put("progression", r.progression.name)
                put("step", r.stepPercent)
                put("weeks", r.everyWeeks)
                put("raisedAt", r.lastRaisedAt)
                r.lastDoneAt?.let { put("doneAt", it) }
                r.note?.let { put("note", it) }
                put("mode", r.mode.name)
                put("perExercise", JsonObject(r.perExercise.filterValues { !it.isDefault }.mapValues { (_, x) ->
                    buildJsonObject {
                        x.mode?.let { put("mode", it.name) }
                        x.sets?.let { put("sets", it) }
                        x.reps?.let { put("reps", it) }
                    }
                }))
            })
        }
    }.toString()

    /** Reads what [encode] wrote; skips entries it can't read rather than losing all of them. */
    fun decode(json: String?): List<Routine> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { e ->
            runCatching {
                val o = e.jsonObject
                fun str(k: String) = o[k]?.jsonPrimitive?.content
                Routine(
                    id = str("id")!!,
                    name = str("name").orEmpty(),
                    exerciseIds = o["exercises"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                    percent = o["percent"]?.jsonPrimitive?.int ?: 60,
                    sets = o["sets"]?.jsonPrimitive?.int ?: 3,
                    reps = o["reps"]?.jsonPrimitive?.int ?: 10,
                    overridesKg = o["overrides"]?.jsonObject?.mapValues { it.value.jsonPrimitive.float }.orEmpty(),
                    progression = str("progression")?.let { p -> Progression.entries.firstOrNull { it.name == p } } ?: Progression.OFF,
                    stepPercent = o["step"]?.jsonPrimitive?.int ?: 2,
                    everyWeeks = o["weeks"]?.jsonPrimitive?.int ?: 2,
                    lastRaisedAt = o["raisedAt"]?.jsonPrimitive?.long ?: 0L,
                    lastDoneAt = o["doneAt"]?.jsonPrimitive?.long,
                    note = str("note"),
                    mode = modeNamed(str("mode")) ?: TrainingMode.OLD_SCHOOL,
                    perExercise = o["perExercise"]?.jsonObject?.mapValues { (_, v) ->
                        val x = v.jsonObject
                        ExerciseSettings(
                            mode = modeNamed(x["mode"]?.jsonPrimitive?.content),
                            sets = x["sets"]?.jsonPrimitive?.int,
                            reps = x["reps"]?.jsonPrimitive?.int
                        )
                    }.orEmpty()
                )
            }.getOrNull()
        }
    }

    private fun modeNamed(name: String?): TrainingMode? = TrainingMode.entries.firstOrNull { it.name == name }
}
