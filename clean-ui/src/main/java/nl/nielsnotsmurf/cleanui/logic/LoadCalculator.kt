package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import kotlin.math.roundToInt

/**
 * Pure load maths for the Clean UI: 1RM estimates and %-of-1RM loads.
 * Everything is kilograms per cable.
 */
object LoadCalculator {

    /** Smallest load step the trainer handles comfortably. */
    const val STEP_KG = 0.5f

    /** Trainer limit per cable. */
    const val MAX_KG = 100f

    /**
     * Rep counts above this give unreliable 1RM estimates, so they're capped.
     * Capping only ever lowers the estimate, which keeps defaults on the safe side.
     */
    const val MAX_RELIABLE_REPS = 12

    val PERCENT_PRESETS = listOf(60, 70, 75, 80, 85)

    /** Epley: weight × (1 + reps / 30). A single rep is its own 1RM. */
    fun epleyOneRepMax(weightKg: Float, reps: Int): Float {
        if (weightKg <= 0f || reps <= 0) return 0f
        if (reps == 1) return weightKg
        val r = reps.coerceAtMost(MAX_RELIABLE_REPS)
        return weightKg * (1f + r / 30f)
    }

    /**
     * Converts a working weight that left some reps in reserve (for example the
     * baseline's "10 reps at RPE 7–8", about 2.5 in reserve) into a 1RM estimate.
     */
    fun oneRepMaxFromWorkingSet(weightKg: Float, reps: Int, repsInReserve: Float): Float {
        if (weightKg <= 0f || reps <= 0) return 0f
        return weightKg * (1f + (reps + repsInReserve) / 30f)
    }

    /** Load for [percent] of [oneRepMaxKg], rounded to [STEP_KG] and clamped to the trainer's range. */
    fun loadForPercent(oneRepMaxKg: Float, percent: Int): Float {
        if (oneRepMaxKg <= 0f || percent <= 0) return 0f
        val raw = oneRepMaxKg * percent / 100f
        return roundToStep(raw).coerceIn(STEP_KG, MAX_KG)
    }

    fun roundToStep(kg: Float): Float = (kg / STEP_KG).roundToInt() * STEP_KG

    /** Best Epley estimate across a lifter's sets for one exercise, or null without history. */
    fun bestHistoryEstimate(records: List<LiftRecord>): Float? =
        records.maxOfOrNull { epleyOneRepMax(it.weightPerCableKg, it.reps) }?.takeIf { it > 0f }

    /**
     * Picks the 1RM to show and load from:
     * the higher of a stored (benchmark or manual) 1RM and the best history estimate;
     * when neither exists, a related-exercise estimate marked as such.
     */
    fun resolveOneRepMax(
        stored: OneRepMax?,
        history: List<LiftRecord>,
        relatedKg: Float?
    ): OneRepMax? {
        val historyKg = bestHistoryEstimate(history)
        val historyMax = historyKg?.let {
            OneRepMax(it, OneRepMaxSource.HISTORY, history.maxOf { r -> r.timestamp })
        }
        val best = listOfNotNull(stored?.takeIf { it.kg > 0f }, historyMax).maxByOrNull { it.kg }
        if (best != null) return best
        return relatedKg?.takeIf { it > 0f }?.let { OneRepMax(it, OneRepMaxSource.RELATED) }
    }

    fun toDisplay(kg: Float, unit: WeightUnit): Float =
        if (unit == WeightUnit.LB) kg * KG_TO_LB else kg

    /** "46.5 kg" / "102.5 lb": one decimal only when it isn't zero. */
    fun format(kg: Float, unit: WeightUnit, withUnit: Boolean = true): String {
        val v = toDisplay(kg, unit)
        val rounded = (v * 2).roundToInt() / 2f
        val text = if (rounded % 1f == 0f) rounded.toInt().toString() else "%.1f".format(rounded)
        return if (withUnit) "$text ${if (unit == WeightUnit.LB) "lb" else "kg"}" else text
    }

    private const val KG_TO_LB = 2.20462f
}
