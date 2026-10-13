package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource

/**
 * Rules of the 1RM test: single-rep attempts that go up after every made rep until one is
 * missed. The 1RM is the heaviest made rep. Everything is kilograms per cable.
 */
object OneRepMaxTest {

    /** Where the first attempt comes from, so the screen can explain it. */
    enum class StartReason { KNOWN_ONE_REP_MAX, ESTIMATE, DEFAULT }

    data class Start(val kg: Float, val reason: StartReason)

    const val KNOWN_START_PERCENT = 0.90f
    const val ESTIMATE_START_PERCENT = 0.70f
    const val RETRY_AFTER_MISS_PERCENT = 0.85f
    const val REST_SECONDS = 120

    /**
     * 90% of a real 1RM. A related-exercise estimate is only a guess, so it starts lower.
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

    /** Heaviest made rep, or null when nothing was made yet. */
    fun best(attempts: List<Attempt>): Float? = attempts.filter { it.made }.maxOfOrNull { it.kg }

    /** The test ends at the first miss. */
    fun isFinished(attempts: List<Attempt>): Boolean = attempts.lastOrNull()?.outcome == AttemptOutcome.MISSED

    /** One extra try halfway between the best made rep and the last miss, if there's room. */
    fun midpoint(attempts: List<Attempt>): Float? {
        val best = best(attempts) ?: return null
        val miss = attempts.lastOrNull { !it.made }?.kg ?: return null
        val mid = clamp((best + miss) / 2f)
        return mid.takeIf { it > best && it < miss }
    }

    private fun clamp(kg: Float) =
        LoadCalculator.roundToStep(kg).coerceIn(LoadCalculator.STEP_KG, LoadCalculator.MAX_KG)
}
