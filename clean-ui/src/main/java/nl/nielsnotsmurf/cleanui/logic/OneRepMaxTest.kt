package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource

/**
 * Rules of the strength test: 3-rep attempts that go up after every made set until one is
 * missed. The heaviest made set is the 3RM; the lifter's stored 1RM is estimated from it, so
 * % loading keeps working. Everything is kilograms per cable.
 */
object OneRepMaxTest {

    /** Reps per attempt. A 3RM is safer to test than a true single and still a good marker. */
    const val TEST_REPS = 3

    /** Estimated 1RM from a 3RM (Epley, no reps in reserve: about 3RM × 1.1). */
    fun oneRepMaxFromTest(kg: Float): Float =
        LoadCalculator.roundToStep(LoadCalculator.oneRepMaxFromWorkingSet(kg, TEST_REPS, 0f))

    /** Where the first attempt comes from, so the screen can explain it. */
    enum class StartReason { KNOWN_ONE_REP_MAX, ESTIMATE, DEFAULT }

    data class Start(val kg: Float, val reason: StartReason)

    const val KNOWN_START_PERCENT = 0.80f
    const val ESTIMATE_START_PERCENT = 0.65f
    const val RETRY_AFTER_MISS_PERCENT = 0.85f
    const val REST_SECONDS = 120

    /**
     * 80% of a real 1RM (about 90% of the 3RM it implies). A related-exercise estimate is only a guess, so it starts lower.
     * Without either, the anchor's conservative default.
     */
    fun start(current: OneRepMax?, defaultKg: Float): Start = when {
        current != null && !current.isEstimate && current.source != OneRepMaxSource.RELATED ->
            Start(clamp(current.kg * KNOWN_START_PERCENT), StartReason.KNOWN_ONE_REP_MAX)
        current != null -> Start(clamp(current.kg * ESTIMATE_START_PERCENT), StartReason.ESTIMATE)
        else -> Start(clamp(defaultKg), StartReason.DEFAULT)
    }

    /**
     * The next attempt after a made rep. An easy rep takes a bigger step (smaller when we
     * started from a known 1RM, since we're already close); a tough rep a small one.
     */
    fun next(currentKg: Float, outcome: AttemptOutcome, startedFromKnown: Boolean): Float = when (outcome) {
        AttemptOutcome.MADE_EASY -> clamp(currentKg * if (startedFromKnown) 1.05f else 1.10f)
        AttemptOutcome.MADE_TOUGH -> clamp(currentKg * 1.025f + LoadCalculator.STEP_KG)
        AttemptOutcome.MISSED -> clamp(currentKg * RETRY_AFTER_MISS_PERCENT)
    }.let { if (outcome != AttemptOutcome.MISSED && it <= currentKg) clamp(currentKg + LoadCalculator.STEP_KG) else it }

    /** Heaviest made set (the 3RM), or null when nothing was made yet. */
    fun best(attempts: List<Attempt>): Float? = attempts.filter { it.made }.maxOfOrNull { it.kg }

    /** The test ends at the first miss. */
    fun isFinished(attempts: List<Attempt>): Boolean = attempts.lastOrNull()?.outcome == AttemptOutcome.MISSED

    /** One extra try halfway between the best made set and the last miss, if there's room. */
    fun midpoint(attempts: List<Attempt>): Float? {
        val best = best(attempts) ?: return null
        val miss = attempts.lastOrNull { !it.made }?.kg ?: return null
        val mid = clamp((best + miss) / 2f)
        return mid.takeIf { it > best && it < miss }
    }

    private fun clamp(kg: Float) =
        LoadCalculator.roundToStep(kg).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
}
