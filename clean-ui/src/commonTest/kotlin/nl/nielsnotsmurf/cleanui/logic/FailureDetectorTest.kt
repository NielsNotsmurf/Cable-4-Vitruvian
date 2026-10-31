package nl.nielsnotsmurf.cleanui.logic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FailureDetectorTest {

    @Test
    fun `standing still before a full rep never stops the set`() {
        val d = FailureDetector()
        var t = 0L
        repeat(100) { d.update(30f, t); t += 100 }
        assertFalse(d.shouldStop)
    }

    @Test
    fun `stuck halfway after reps stops after five seconds`() {
        val d = FailureDetector()
        var t = 0L
        // Two reps of 400 mm.
        repeat(2) {
            for (p in 0..40) { d.update(p * 10f, t); t += 50 }
            for (p in 40 downTo 0) { d.update(p * 10f, t); t += 50 }
        }
        for (p in 0..20) { d.update(p * 10f, t); t += 50 }
        // Failure at 200 mm: no movement.
        repeat(45) { d.update(200f, t); t += 100 }
        assertFalse(d.shouldStop)
        repeat(10) { d.update(200f, t); t += 100 }
        assertTrue(d.shouldStop)
    }

    @Test
    fun `moving again resets the countdown`() {
        val d = FailureDetector()
        var t = 0L
        for (p in 0..40) { d.update(p * 10f, t); t += 50 }
        repeat(30) { d.update(300f, t); t += 100 }
        d.update(250f, t); t += 100
        for (p in 25 downTo 10) { d.update(p * 10f, t); t += 50 }
        repeat(30) { d.update(100f, t); t += 100 }
        assertFalse(d.shouldStop)
    }
}
