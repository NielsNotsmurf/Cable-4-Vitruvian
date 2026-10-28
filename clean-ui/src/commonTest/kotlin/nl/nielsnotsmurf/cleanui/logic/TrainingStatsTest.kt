package nl.nielsnotsmurf.cleanui.logic

import nl.nielsnotsmurf.cleanui.model.HistoryEntry
import nl.nielsnotsmurf.cleanui.model.OneRepMax
import nl.nielsnotsmurf.cleanui.model.OneRepMaxSource
import kotlin.test.assertEquals
import kotlin.test.Test

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
    fun exerciseAveragesCoverOneLifterAndExerciseInTheRange() {
        val e = listOf(entry("Bench", 1, 40f), entry("Bench", 10, 30f, reps = 30), entry("Bench", 60, 20f), entry("Row", 1, 50f))
        val month = TrainingStats.exerciseAverages(e, "bench", "a", since = now - 30 * day)!!
        assertEquals(2, month.workouts)
        assertEquals(35f, month.avgKg!!, 0.01f)
        assertEquals(9f, month.avgRepsPerSet, 0.01f)
        assertEquals(30f, TrainingStats.exerciseAverages(e, "Bench", "a")!!.avgKg!!, 0.01f)
        assertEquals(null, TrainingStats.exerciseAverages(e, "Bench", "b"))
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

    private val hour = 60 * 60 * 1000L

    private fun at(name: String, hoursAgo: Double) = entry(name, 0, 40f).copy(
        id = "$name-$hoursAgo", timestamp = now - (hoursAgo * hour).toLong()
    )

    @Test
    fun currentSessionChainsSetsLessThanFiveHoursApart() {
        val e = listOf(at("Bench", 0.5), at("Row", 4.0), at("Squat", 8.5), at("Curl", 14.0))
        // 0.5h -> 4h -> 8.5h are each under 5h apart; 14h is 5.5h after Squat.
        assertEquals(listOf("Squat", "Row", "Bench"), TrainingStats.currentSession(e, now).map { it.exerciseName })
    }

    @Test
    fun currentSessionIsEmptyWhenTheLastSetIsFiveHoursOld() {
        assertEquals(emptyList<HistoryEntry>(), TrainingStats.currentSession(listOf(at("Bench", 5.0), at("Row", 6.0)), now))
        assertEquals(emptyList<HistoryEntry>(), TrainingStats.currentSession(emptyList(), now))
    }

    @Test
    fun longestStandingListsTheOldestDatedOneRepMaxesOfTheGivenLifters() {
        val orms = mapOf(
            "a" to mapOf(
                "bench" to OneRepMax(60f, OneRepMaxSource.BENCHMARK, 300L),
                "row" to OneRepMax(50f, OneRepMaxSource.HISTORY, 100L),
                "curl" to OneRepMax(20f, OneRepMaxSource.RELATED),
                "squat" to OneRepMax(80f, OneRepMaxSource.MANUAL, 0L)
            ),
            "b" to mapOf("bench" to OneRepMax(40f, OneRepMaxSource.BENCHMARK, 200L)),
            "c" to mapOf("bench" to OneRepMax(40f, OneRepMaxSource.BENCHMARK, 1L))
        )
        val r = TrainingStats.longestStanding(orms, listOf("a", "b"), limit = 2)
        assertEquals(listOf("a" to "row", "b" to "bench"), r.map { it.lifterId to it.exerciseId })
    }
}
