package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.ExerciseCard
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.Test

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

    private fun named(id: String, name: String, equipment: String, pop: Float) =
        ExerciseCard(id = id, name = name, muscleGroup = "Chest", equipment = equipment, region = "Chest", popularity = pop)

    private val dupes = listOf(
        named("b1", "Bench Press", "Bar", 9f),
        named("b2", "Bench Press ", "Handles", 8.5f),
        named("i1", "Incline Bench Press", "Handles", 8f),
        named("i2", "incline bench press", "Bar", 7.5f),
        named("fly", "Chest Fly", "Handles", 7f)
    )

    @Test
    fun `build never picks the same move twice under other ids`() {
        val w = WorkoutBuilder.build(dupes, listOf("Chest"), 3, score)
        assertEquals(3, w.map(WorkoutBuilder::sameKey).distinct().size)
    }

    @Test
    fun `swap skips moves already in the workout and ones swapped out before`() {
        val w = listOf(dupes[0], dupes[2])
        val first = WorkoutBuilder.substitute(dupes[0], w, dupes, score)!!
        assertEquals("fly", first.id)
        val pool = dupes + named("cp", "Cable Press", "Bar", 6f)
        val swapped = WorkoutBuilder.swap(w, dupes[0], first)
        assertEquals("b1", WorkoutBuilder.substitute(first, swapped, pool, score)?.id)
        assertEquals("cp", WorkoutBuilder.substitute(first, swapped, pool, score, setOf("bench press"))?.id)
    }

    @Test
    fun `adding by hand ignores a move that is already there`() {
        val w = listOf(dupes[0])
        assertEquals(1, WorkoutBuilder.add(w, dupes[1]).size)
        assertEquals(2, WorkoutBuilder.add(w, dupes[4]).size)
    }

    @Test
    fun `moving keeps the rest of a hand-made order`() {
        val w = listOf(dupes[0], dupes[4])
        assertEquals(listOf(dupes[4], dupes[0]), WorkoutBuilder.move(w, 1, -1))
        assertEquals(w, WorkoutBuilder.move(w, 0, -1))
    }
}
