package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.Attempt
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome.MADE_EASY
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome.MADE_TOUGH
import nl.nielsnotsmurf.cleanui.model.AttemptOutcome.MISSED
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneRepMaxTestTest {

    @Test
    fun startsAtEightyPercentOfAKnownOneRepMax() {
        val s = OneRepMaxTest.start(OneRepMax(60f, OneRepMaxSource.BENCHMARK), 15f)
        assertEquals(48f, s.kg, 0.001f)
        assertEquals(OneRepMaxTest.StartReason.KNOWN_ONE_REP_MAX, s.reason)
    }

    @Test
    fun historyEstimateCountsAsKnown() {
        val s = OneRepMaxTest.start(OneRepMax(50f, OneRepMaxSource.HISTORY), 15f)
        assertEquals(40f, s.kg, 0.001f)
    }

    @Test
    fun relatedEstimateStartsLower() {
        val s = OneRepMaxTest.start(OneRepMax(50f, OneRepMaxSource.RELATED), 15f)
        assertEquals(32.5f, s.kg, 0.001f)
        assertEquals(OneRepMaxTest.StartReason.ESTIMATE, s.reason)
    }

    @Test
    fun oneRepMaxIsEstimatedFromTheThreeRepMax() {
        assertEquals(55f, OneRepMaxTest.oneRepMaxFromTest(50f), 0.001f)
    }

    @Test
    fun noOneRepMaxUsesTheDefault() {
        assertEquals(15f, OneRepMaxTest.start(null, 15f).kg, 0.001f)
    }

    @Test
    fun weightAlwaysGoesUpAfterAMadeRep() {
        assertEquals(55f, OneRepMaxTest.next(50f, MADE_EASY, startedFromKnown = false), 0.001f)
        assertEquals(52.5f, OneRepMaxTest.next(50f, MADE_EASY, startedFromKnown = true), 0.001f)
        assertEquals(52f, OneRepMaxTest.next(50f, MADE_TOUGH, startedFromKnown = true), 0.001f)
        // Tiny loads still move up by at least one step.
        assertEquals(1.5f, OneRepMaxTest.next(1f, MADE_EASY, startedFromKnown = true), 0.001f)
    }

    @Test
    fun bestIsHeaviestMadeRep() {
        val a = listOf(Attempt(50f, MADE_EASY), Attempt(55f, MADE_TOUGH), Attempt(57f, MISSED))
        assertEquals(55f, OneRepMaxTest.best(a)!!, 0.001f)
        assertTrue(OneRepMaxTest.isFinished(a))
        assertEquals(56f, OneRepMaxTest.midpoint(a)!!, 0.001f)
    }

    @Test
    fun noMadeRepMeansNoBestAndNoMidpoint() {
        val a = listOf(Attempt(50f, MISSED))
        assertNull(OneRepMaxTest.best(a))
        assertNull(OneRepMaxTest.midpoint(a))
        assertFalse(OneRepMaxTest.isFinished(listOf(Attempt(50f, MADE_EASY))))
    }

    @Test
    fun noMidpointWhenTheGapIsOneStep() {
        val a = listOf(Attempt(50f, MADE_TOUGH), Attempt(50.5f, MISSED))
        assertNull(OneRepMaxTest.midpoint(a))
    }
}
