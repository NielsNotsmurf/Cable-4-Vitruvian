package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingStatsTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    private fun entry(name: String, daysAgo: Int, kg: Float?, sets: Int = 3, reps: Int = 24) =
        HistoryEntry(
            id = "$name-$daysAgo-$kg", lifterId = "a", exerciseName = name, timestamp = now - daysAgo * day,
            mode = "Old School", sets = sets, reps = reps, topKgPerCable = kg, durationMs = 0, groupId = "$name-$daysAgo"
        )

    @Test
    fun totalsCountOnlyTheRangeAndOneWorkoutPerExercisePerDay() {
        val e = listOf(entry("Bench", 1, 40f), entry("Bench", 1, 40f), entry("Row", 1, 30f), entry("Bench", 20, 35f))
        val t = TrainingStats.totals(e, since = now - 7 * day)
        assertEquals(2, t.workouts)
        assertEquals(9, t.sets)
        assertEquals(72, t.reps)
    }

    @Test
    fun streakCountsWeeksInARowAndForgivesAnEmptyCurrentWeek() {
        assertEquals(3, TrainingStats.weekStreak(listOf(entry("B", 1, 1f), entry("B", 8, 1f), entry("B", 15, 1f), entry("B", 40, 1f)), now))
        assertEquals(2, TrainingStats.weekStreak(listOf(entry("B", 8, 1f), entry("B", 15, 1f)), now))
        assertEquals(0, TrainingStats.weekStreak(listOf(entry("B", 30, 1f)), now))
    }

    @Test
    fun progressKeepsTheHeaviestLoadPerDayOldestFirst() {
        val p = TrainingStats.progress(listOf(entry("Bench", 3, 40f), entry("Bench", 3, 42f), entry("Bench", 10, 38f), entry("Plank", 2, null)))
        assertEquals(1, p.size)
        assertEquals(listOf(38f, 42f), p.first().points.map { it.second })
        assertEquals(42f, p.first().best, 0f)
    }

    @Test
    fun recordsAreLoadsThatBeatEverythingBefore() {
        val r = TrainingStats.records(listOf(entry("Bench", 30, 40f), entry("Bench", 20, 45f), entry("Bench", 10, 44f), entry("Bench", 5, 47.5f)))
        assertEquals(listOf(47.5f, 45f), r.map { it.kg })
    }
}
