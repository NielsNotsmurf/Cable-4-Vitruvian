package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.LiftRecord
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import nl.nielsnotsmurf.cleanui.model.WeightUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class LoadCalculatorTest {

    @Test
    fun `epley gives weight for a single rep`() {
        assertEquals(60f, LoadCalculator.epleyOneRepMax(60f, 1), 0.001f)
    }

    @Test
    fun `epley matches the formula`() {
        // 18 × (1 + 7/30) = 22.2
        assertEquals(22.2f, LoadCalculator.epleyOneRepMax(18f, 7), 0.001f)
    }

    @Test
    fun `epley caps high rep counts so estimates stay conservative`() {
        assertEquals(
            LoadCalculator.epleyOneRepMax(20f, 12),
            LoadCalculator.epleyOneRepMax(20f, 25),
            0.001f
        )
    }

    @Test
    fun `epley returns zero for empty input`() {
        assertEquals(0f, LoadCalculator.epleyOneRepMax(0f, 5), 0f)
        assertEquals(0f, LoadCalculator.epleyOneRepMax(20f, 0), 0f)
    }

    @Test
    fun `percent load rounds to half kilos`() {
        // 75% of 62 = 46.5
        assertEquals(46.5f, LoadCalculator.loadForPercent(62f, 75), 0f)
        // 75% of 34 = 25.5
        assertEquals(25.5f, LoadCalculator.loadForPercent(34f, 75), 0f)
        // 70% of 33 = 23.1 -> 23.0
        assertEquals(23f, LoadCalculator.loadForPercent(33f, 70), 0f)
        // 80% of 33 = 26.4 -> 26.5
        assertEquals(26.5f, LoadCalculator.loadForPercent(33f, 80), 0f)
    }

    @Test
    fun `percent load stays inside the trainer range`() {
        assertEquals(LoadCalculator.MAX_KG, LoadCalculator.loadForPercent(180f, 85), 0f)
        assertEquals(LoadCalculator.STEP_KG, LoadCalculator.loadForPercent(0.4f, 60), 0f)
    }

    @Test
    fun `working set with reps in reserve converts to a 1RM`() {
        // 20 kg × (1 + 12.5/30) ≈ 28.33
        assertEquals(28.333f, LoadCalculator.oneRepMaxFromWorkingSet(20f, 10, 2.5f), 0.01f)
    }

    @Test
    fun `normal sets never override a tested 1RM`() {
        val stored = OneRepMax(60f, OneRepMaxSource.BENCHMARK, 1L)
        val history = listOf(LiftRecord("bench", 50f, 8, 2L)) // 63.3, newer than the test
        assertEquals(stored, LoadCalculator.resolveOneRepMax(stored, history, relatedKg = 40f))
    }

    @Test
    fun `history estimates a 1RM when none was tested`() {
        val history = listOf(LiftRecord("bench", 50f, 8, 2L))
        val resolved = LoadCalculator.resolveOneRepMax(null, history, relatedKg = 40f)!!
        assertEquals(OneRepMaxSource.HISTORY, resolved.source)
        assertEquals(63.333f, resolved.kg, 0.01f)
    }

    @Test
    fun `resolve keeps a benchmark that beats history`() {
        val stored = OneRepMax(70f, OneRepMaxSource.BENCHMARK, 1L)
        val history = listOf(LiftRecord("bench", 50f, 8, 2L))
        assertEquals(stored, LoadCalculator.resolveOneRepMax(stored, history, relatedKg = null))
    }

    @Test
    fun `a newer lower benchmark replaces older history`() {
        val stored = OneRepMax(55f, OneRepMaxSource.BENCHMARK, 5L)
        val history = listOf(LiftRecord("bench", 50f, 8, 2L)) // 63.3, but older than the test
        assertEquals(stored, LoadCalculator.resolveOneRepMax(stored, history, relatedKg = null))
    }

    @Test
    fun `resolve falls back to a related estimate`() {
        val resolved = LoadCalculator.resolveOneRepMax(null, emptyList(), relatedKg = 28f)!!
        assertEquals(OneRepMaxSource.RELATED, resolved.source)
        assertEquals(true, resolved.isEstimate)
    }

    @Test
    fun `resolve returns null with nothing to go on`() {
        assertNull(LoadCalculator.resolveOneRepMax(null, emptyList(), relatedKg = null))
    }

    @Test
    fun `format drops trailing zero and converts pounds`() {
        assertEquals("46${decimalSeparator()}5 kg", LoadCalculator.format(46.5f, WeightUnit.KG))
        assertEquals("60 kg", LoadCalculator.format(60f, WeightUnit.KG))
        // 20 kg = 44.09 lb -> 44
        assertEquals("44 lb", LoadCalculator.format(20f, WeightUnit.LB))
        assertEquals("46${decimalSeparator()}5", LoadCalculator.format(46.5f, WeightUnit.KG, withUnit = false))
    }
}
