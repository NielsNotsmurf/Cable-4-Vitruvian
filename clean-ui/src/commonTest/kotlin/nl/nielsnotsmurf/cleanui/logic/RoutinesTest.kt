package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.TrainingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutinesTest {

    private val r = Routine(
        id = "r1", name = "Push", exerciseIds = listOf("a", "b"), percent = 60, reps = 10,
        overridesKg = mapOf("me::a" to 30f), progression = Progression.ALL_REPS, stepPercent = 5,
        lastRaisedAt = 0L, note = "Raised", mode = TrainingMode.TUT,
        perExercise = mapOf("b" to ExerciseSettings(mode = TrainingMode.ECHO, sets = 4, reps = 6))
    )

    @Test
    fun `round trips through json`() {
        assertEquals(listOf(r), Routines.decode(Routines.encode(listOf(r))))
        assertEquals(emptyList(), Routines.decode("not json"))
    }

    @Test
    fun `raise moves the percent and hand-set loads together`() {
        val up = Routines.raise(r, 1, now = 5L)
        assertEquals(65, up.percent)
        assertEquals(32.5f, up.overridesKg["me::a"])
        assertEquals(5L, up.lastRaisedAt)
        assertEquals(100, Routines.raise(r.copy(percent = 98), 1, 0L).percent)
    }

    @Test
    fun `all reps progression needs every set at target`() {
        assertTrue(Routines.earnsStep(r, 0L, listOf(10, 12, 10, 10), expectedSets = 4))
        assertFalse(Routines.earnsStep(r, 0L, listOf(10, 9, 10, 10), expectedSets = 4))
        assertFalse(Routines.earnsStep(r, 0L, listOf(10, 10), expectedSets = 4))
    }

    @Test
    fun `weekly progression waits the chosen weeks`() {
        val w = r.copy(progression = Progression.WEEKS, everyWeeks = 2, lastRaisedAt = 0L)
        assertFalse(Routines.earnsStep(w, Routines.WEEK_MS, emptyList(), 0))
        assertTrue(Routines.earnsStep(w, 2 * Routines.WEEK_MS, emptyList(), 0))
        assertFalse(Routines.earnsStep(w.copy(progression = Progression.OFF), 9 * Routines.WEEK_MS, emptyList(), 0))
    }

    @Test
    fun `per exercise targets decide all reps progression`() {
        assertTrue(Routines.earnsStep(r, 0L, listOf(10, 6), expectedSets = 2, targets = listOf(10, 6)))
        assertFalse(Routines.earnsStep(r, 0L, listOf(10, 5), expectedSets = 2, targets = listOf(10, 6)))
        assertEquals(TrainingMode.ECHO, Routines.modeFor(r.mode, r.perExercise, "b"))
        assertEquals(TrainingMode.TUT, Routines.modeFor(r.mode, r.perExercise, "a"))
        assertEquals(4, Routines.setsFor(r.sets, r.perExercise, "b"))
    }
}
