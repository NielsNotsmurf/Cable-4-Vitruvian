package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutBuilderTest {

    private fun ex(id: String, region: String, equipment: String, muscle: String = region, pop: Float = 0f) =
        ExerciseCard(id = id, name = id, muscleGroup = muscle, equipment = equipment, region = region, popularity = pop)

    private val pool = listOf(
        ex("bench", "Chest", "Bar", pop = 9f),
        ex("fly", "Chest", "Handles", pop = 8f),
        ex("incline", "Chest", "Bar", pop = 7f),
        ex("pushup", "Chest", "Bodyweight", pop = 10f),
        ex("ohp", "Shoulders", "Bar", pop = 9f),
        ex("lateral", "Shoulders", "Handles", pop = 8f),
        ex("face", "Shoulders", "Rope", pop = 2f)
    )
    private val score: (ExerciseCard) -> Float = { it.popularity }

    @Test
    fun `takes turns over groups and skips bodyweight`() {
        val w = WorkoutBuilder.build(pool, listOf("Chest", "Shoulders"), 4, score)
        assertEquals(4, w.size)
        assertEquals(2, w.count { it.region == "Chest" })
        assertTrue(w.none { it.id == "pushup" })
    }

    @Test
    fun `same equipment sits together`() {
        val w = WorkoutBuilder.build(pool, listOf("Chest", "Shoulders"), 4, score)
        assertEquals(1, WorkoutBuilder.equipmentSwitches(w))
    }

    @Test
    fun `substitute uses other equipment in the same group`() {
        val workout = listOf(pool[0], pool[4])
        val sub = WorkoutBuilder.substitute(pool[0], workout, pool, score)!!
        assertEquals("Chest", sub.region)
        assertNotEquals("Bar", sub.equipment)
    }

    @Test
    fun `count follows time and lifters`() {
        assertEquals(4, WorkoutBuilder.exerciseCount(30, 1, 2))
        assertTrue(WorkoutBuilder.exerciseCount(30, 3, 2) < 4)
        assertEquals(3, WorkoutBuilder.exerciseCount(15, 2, 3))
    }
}
